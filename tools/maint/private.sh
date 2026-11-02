#!/usr/bin/env bash
# Encrypt, decrypt and edit the few notes that must not be public but must still travel
# with this repository: hostnames, private addresses and the exact commands that reach the
# second test device.
#
#   tools/maint/private.sh encrypt <file.md>        -> file.md.enc, and removes the plaintext
#   tools/maint/private.sh decrypt <file.md.enc>    -> prints the plaintext on stdout
#   tools/maint/private.sh edit    <file.md.enc>    -> decrypt to a temp file, $EDITOR, re-encrypt
#   tools/maint/private.sh check                    -> refuse-to-commit sanity checks
#
# ## Why openssl and not age, gpg or git-crypt
#
# Because it is already on both machines. This repository has to be readable on a laptop
# that may have none of those installed, and a scheme that cannot be opened where it is
# needed is not a scheme. AES-256-CBC with PBKDF2 at 600k iterations is sufficient for a
# page of hostnames; it is not a secrets-management system and does not pretend to be.
# Verified in both directions between macOS's LibreSSL 3.3.6 and Homebrew's OpenSSL 3.6.3,
# because "openssl is everywhere" is only useful if the two spellings agree.
#
# ## Where the passphrase lives
#
# In your password manager or macOS Keychain, on each machine that needs it. **Not in this
# repository, and not in a GitHub Actions secret** -- an Actions secret cannot be read back
# by a person, so a second laptop cloning the repo still could not decrypt, and routing the
# plaintext out through a workflow artifact would publish it, since artifacts of a public
# repository are downloadable by anyone. No CI job needs these files.
#
# Optionally put it in the Keychain and let this script find it:
#   security add-generic-password -a "$USER" -s zxing-android-private -w
#
# ## What stops a mistake
#
# docs/maint/private/*.md is git-ignored, so a decrypted working copy cannot be committed by
# accident. `private.sh check` fails if a plaintext file is nevertheless staged.
set -euo pipefail

CIPHER=(-aes-256-cbc -pbkdf2 -iter 600000)
KEYCHAIN_SERVICE="zxing-android-private"
cd "$(dirname "$0")/../.."

die() { echo "$*" >&2; exit 1; }

passphrase() {
  if [ -n "${ZXING_PRIVATE_PASSPHRASE:-}" ]; then
    printf '%s' "$ZXING_PRIVATE_PASSPHRASE"
    return
  fi
  if command -v security >/dev/null 2>&1 &&
     security find-generic-password -s "$KEYCHAIN_SERVICE" -w >/dev/null 2>&1; then
    security find-generic-password -s "$KEYCHAIN_SERVICE" -w
    return
  fi
  # No stored passphrase: ask, on the terminal, without echoing it.
  local pw
  read -r -s -p "passphrase for $KEYCHAIN_SERVICE: " pw < /dev/tty
  echo >&2
  printf '%s' "$pw"
}

with_pass() { # with_pass <openssl args...>
  local pw; pw="$(passphrase)"
  [ -n "$pw" ] || die "empty passphrase"
  # The passphrase reaches openssl on a file descriptor, never on the command line, where
  # it would be visible in ps(1) to every other process on the machine.
  openssl "$@" -pass fd:3 3< <(printf '%s' "$pw")
}

cmd_encrypt() {
  local src="${1:?usage: private.sh encrypt <file.md>}"
  [ -f "$src" ] || die "no such file: $src"
  case "$src" in *.enc) die "that is already ciphertext: $src";; esac
  with_pass enc "${CIPHER[@]}" -salt -in "$src" -out "$src.enc"
  rm -f "$src"
  echo "encrypted -> $src.enc (plaintext removed)"
}

cmd_decrypt() {
  local src="${1:?usage: private.sh decrypt <file.md.enc>}"
  [ -f "$src" ] || die "no such file: $src"
  with_pass enc -d "${CIPHER[@]}" -in "$src"
}

# Not `local`: the EXIT trap below runs after this function has returned, and under
# `set -u` a trap that names a dead local aborts the script -- which printed
# "tmp: unbound variable" after every successful edit, looking exactly like a failure to
# re-encrypt.
EDIT_TMP=""

cmd_edit() {
  local src="${1:?usage: private.sh edit <file.md.enc>}"
  EDIT_TMP="$(mktemp -t zxing-private)"
  local tmp="$EDIT_TMP"
  chmod 600 "$tmp"
  trap 'rm -f "$EDIT_TMP"' EXIT
  if [ -f "$src" ]; then
    with_pass enc -d "${CIPHER[@]}" -in "$src" -out "$tmp"
  fi
  "${EDITOR:-vi}" "$tmp"
  with_pass enc "${CIPHER[@]}" -salt -in "$tmp" -out "$src"
  echo "re-encrypted -> $src"
}

cmd_check() {
  local staged bad=0
  staged="$(git diff --cached --name-only --diff-filter=ACM || true)"
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    case "$f" in
      # The same two exceptions .gitignore makes: ciphertext, and the directory's own
      # README, which explains the scheme and deliberately contains no secrets.
      docs/maint/private/*.enc|docs/maint/private/README.md) ;;
      docs/maint/private/*) echo "STAGED PLAINTEXT: $f" >&2; bad=1 ;;
    esac
  done <<< "$staged"
  # A ciphertext that is accidentally plaintext is the other way to get this wrong.
  for f in docs/maint/private/*.enc; do
    [ -e "$f" ] || continue
    if head -c 8 "$f" | grep -q "Salted__"; then :; else
      echo "NOT ENCRYPTED: $f" >&2; bad=1
    fi
  done
  [ "$bad" -eq 0 ] || die "private.sh check failed"
  echo "private.sh check: ok"
}

case "${1:-}" in
  encrypt) shift; cmd_encrypt "$@" ;;
  decrypt) shift; cmd_decrypt "$@" ;;
  edit)    shift; cmd_edit "$@" ;;
  check)   shift; cmd_check "$@" ;;
  *) die "usage: private.sh {encrypt|decrypt|edit|check} [file]" ;;
esac
