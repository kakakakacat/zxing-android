# Encrypted maintenance notes

This directory holds the few things that must not be public but must still travel with the
repository — hostnames, private addresses, and the exact command lines that reach the second
test device. **This repository is public.** Only `*.enc` is committed; plaintext is
git-ignored so a decrypted working copy cannot be committed by accident.

```sh
tools/maint/private.sh decrypt docs/maint/private/second-device.md.enc   # to stdout
tools/maint/private.sh edit    docs/maint/private/second-device.md.enc   # decrypt, $EDITOR, re-encrypt
tools/maint/private.sh check                                             # before committing
```

AES-256-CBC, PBKDF2 at 600k iterations, via `openssl` — chosen because it is already on
every machine this repository has to open on, and verified to round-trip between macOS's
system LibreSSL and Homebrew's OpenSSL. It is enough for a page of hostnames and is not a
secrets-management system.

## The passphrase

In the macOS Keychain under the service name `zxing-android-private`, which
`tools/maint/private.sh` reads automatically. To read it on another machine, or to copy it
into a password manager:

```sh
security find-generic-password -s zxing-android-private -w
```

On a machine that does not have it yet:

```sh
security add-generic-password -a "$USER" -s zxing-android-private -w
```

`ZXING_PRIVATE_PASSPHRASE` overrides the Keychain if it is set; otherwise the script prompts
on the terminal.

**Not in this repository and not in GitHub Actions.** An Actions secret cannot be read back
by a person, so a second machine cloning this repository still could not decrypt, and
routing the plaintext out through a workflow artifact would publish it — artifacts of a
public repository are downloadable by anyone. No CI job needs these files, and none should.

## What is deliberately *not* in here

The lessons from working with that device — the silent CameraX retry on a revoked camera
permission, the HyperOS appops behaviour, the double evaluation of the exec string, the
screen timeout — are in `../HANDOFF.md`. None of them is a secret, all of them are useful,
and burying them behind a passphrase would only mean nobody reads them.
