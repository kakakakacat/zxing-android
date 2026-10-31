#!/usr/bin/env bash
# Compare a module's Java-visible API against the same module at another git revision.
#
# Converting a module to Kotlin keeps the source compiling while quietly changing the
# shape Java consumers see: a method that is no longer overridable, a wildcard return
# type that widened, a constant that stopped being a compile-time constant, a
# package-private constructor that became public. None of that fails a Kotlin build, and
# :scanner is published, so a consumer finds out instead.
#
# This builds the release AAR on both sides and diffs javap's signature dump. Members are
# sorted, because javac and kotlinc emit them in different orders and that is not a
# difference anyone needs to read.
#
# Usage: tools/maint/api-diff.sh [module] [baseline-ref]
#   module        Gradle path, default :scanner
#   baseline-ref  git revision to compare against, default HEAD
#
# Exit status is 0 when the two sides agree and 1 when they do not. A difference is not
# automatically a defect -- Kotlin adds a `Companion` field and `final` on members of a
# final class, and neither is something Java can name -- so the diff is for reading, not
# for gating.
set -euo pipefail

MODULE="${1:-:scanner}"
BASELINE="${2:-HEAD}"

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/zxing-api-diff.XXXXXX")"
MODULE_DIR="${MODULE#:}"
MODULE_DIR="${MODULE_DIR//://}"

cleanup() {
  git -C "$REPO" worktree remove --force "$WORK/baseline" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

# The module's directory can differ from its Gradle path (:app lives in android/), so ask
# Gradle rather than guessing.
module_projectdir() {
  local root="$1"
  (cd "$root" && ./gradlew -q "${MODULE}:properties" --console=plain 2>/dev/null \
      | awk -F': ' '/^projectDir: /{print $2; exit}')
}

# javap over every class in the AAR, public members only, with members sorted inside each
# class and the "Compiled from" line dropped -- it names the source language, which is
# exactly what is expected to change.
dump_api() {
  local aar="$1" out="$2" tmp
  tmp="$(mktemp -d "$WORK/aar.XXXXXX")"
  (cd "$tmp" && unzip -oq "$aar" classes.jar && mkdir c && cd c && unzip -oq ../classes.jar)
  : > "$out"
  # Synthetic Kotlin artefacts (WhenMappings, $inlined$, lambdas) are implementation, not API.
  while read -r class; do
    (cd "$tmp/c" && javap -cp . "$class" 2>/dev/null) | {
      read -r first
      case "$first" in
        Compiled\ from*) read -r header ;;
        *) header="$first" ;;
      esac
      echo "$header"
      # Everything up to the closing brace, sorted; the brace is re-added after.
      sed '$d' | sort
      echo "}"
      echo
    } >> "$out"
  done < <(cd "$tmp/c" && find . -name '*.class' \
      | sed 's|^\./||; s|\.class$||; s|/|.|g' \
      | grep -vE '\$WhenMappings|\$\$inlined|\$\$Lambda' \
      | sort)
}

echo "building $MODULE at $BASELINE"
git -C "$REPO" worktree add -f --detach "$WORK/baseline" "$BASELINE" >/dev/null
# local.properties carries sdk.dir and is deliberately not committed.
[ -f "$REPO/local.properties" ] && cp "$REPO/local.properties" "$WORK/baseline/"
# A linked worktree starts with empty submodule directories, and :scanner's CMake step
# stops at "third_party/zxing-cpp is empty" (HANDOFF.md trap 5). The checkout is cheap
# here because the worktree shares this repository's object store.
git -C "$WORK/baseline" submodule update --init --recursive >/dev/null
BASE_DIR="$(module_projectdir "$WORK/baseline")"
(cd "$WORK/baseline" && ./gradlew -q "${MODULE}:assembleRelease" --console=plain)

echo "building $MODULE in the working tree"
WORK_DIR="$(module_projectdir "$REPO")"
(cd "$REPO" && ./gradlew -q "${MODULE}:assembleRelease" --console=plain)

BASE_AAR="$(find "$BASE_DIR/build/outputs/aar" -name '*-release.aar' | head -1)"
WORK_AAR="$(find "$WORK_DIR/build/outputs/aar" -name '*-release.aar' | head -1)"
[ -n "$BASE_AAR" ] || { echo "no release AAR at $BASELINE" >&2; exit 2; }
[ -n "$WORK_AAR" ] || { echo "no release AAR in the working tree" >&2; exit 2; }

dump_api "$BASE_AAR" "$WORK/baseline.txt"
dump_api "$WORK_AAR" "$WORK/current.txt"

echo
if diff -u --label "$MODULE @ $BASELINE" "$WORK/baseline.txt" \
             --label "$MODULE @ working tree" "$WORK/current.txt"; then
  echo "no Java-visible API difference"
  exit 0
fi
exit 1
