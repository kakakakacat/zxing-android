#!/usr/bin/env bash
# Pull fresh core sources from upstream zxing/zxing and re-apply this fork's namespace.
#
# This fork maintains its own decoder: core lives under io.github.kakakakacat.zxing
# rather than com.google.zxing, so the published artifact cannot collide with upstream's
# com.google.zxing:core on a consumer's classpath. The cost of that rename is that a
# plain `git merge upstream/master` conflicts on every package line. This script pays
# that cost mechanically instead.
#
# It does NOT commit, and it does NOT resolve semantic conflicts. Review the diff, run
# the tests and the L1 correctness run, then commit with the upstream commit recorded.
#
# Usage:
#   tools/maint/sync-core-from-upstream.sh [upstream-ref]        # default: upstream/master
set -euo pipefail

REF="${1:-upstream/master}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OLD_PKG="com.google.zxing"
NEW_PKG="io.github.kakakakacat.zxing"
OLD_DIR="com/google/zxing"
NEW_DIR="io/github/kakakakacat/zxing"

cd "$REPO_ROOT"

if ! git remote get-url upstream >/dev/null 2>&1; then
  echo "No 'upstream' remote. Add it with:" >&2
  echo "  git remote add upstream https://github.com/zxing/zxing.git" >&2
  exit 1
fi

if [ -n "$(git status --porcelain core/)" ]; then
  echo "core/ has uncommitted changes. Commit or stash them first." >&2
  exit 1
fi

echo "Fetching $REF ..."
git fetch upstream --quiet
UPSTREAM_SHA="$(git rev-parse "$REF")"
echo "Upstream commit: $UPSTREAM_SHA"

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

# Take upstream's core sources and test resources verbatim.
for sub in src/main/java src/test/java src/test/resources; do
  if git cat-file -e "$REF:core/$sub" 2>/dev/null; then
    mkdir -p "$STAGE/$sub"
    git archive "$REF" "core/$sub" | tar -x -C "$STAGE" --strip-components=1
  fi
done

# Re-apply this fork's namespace: move the directories, then rewrite package/import
# statements and javadoc references.
for sub in src/main/java src/test/java; do
  if [ -d "$STAGE/$sub/$OLD_DIR" ]; then
    mkdir -p "$STAGE/$sub/$(dirname "$NEW_DIR")"
    mv "$STAGE/$sub/$OLD_DIR" "$STAGE/$sub/$NEW_DIR"
    rmdir -p "$STAGE/$sub/$(dirname "$OLD_DIR")" 2>/dev/null || true
  fi
done

python3 -I "$REPO_ROOT/tools/maint/rename_packages.py" \
  --map "$OLD_PKG=$NEW_PKG" "$STAGE/src"

# Javadoc links too, so the imported sources do not carry dead references.
python3 -I - "$STAGE/src" <<'PY'
import io, os, re, sys
COMMENT = re.compile(r'^\s*(\*|//|/\*)')
OLD = re.compile(r'com\.google\.zxing\.(?!client\.android)')
for dirpath, _d, files in os.walk(sys.argv[1]):
    for name in files:
        if not name.endswith('.java'):
            continue
        p = os.path.join(dirpath, name)
        lines = io.open(p, encoding='utf-8').read().split('\n')
        touched = False
        for i, line in enumerate(lines):
            if COMMENT.match(line) and OLD.search(line):
                lines[i] = OLD.sub('io.github.kakakakacat.zxing.', line)
                touched = True
        if touched:
            io.open(p, 'w', encoding='utf-8').write('\n'.join(lines))
PY

echo "Replacing core/src ..."
rm -rf core/src/main/java core/src/test/java core/src/test/resources
cp -R "$STAGE/src/." core/src/

cat <<EOF

Done. core/src now holds $REF with this fork's namespace applied.

Next:
  git -C "$REPO_ROOT" diff --stat core/
  ./gradlew :core:test :scanner:testDebugUnitTest :scanner:connectedDebugAndroidTest
  python3 -I benchmark/dataset/build_manifest.py && git diff --stat benchmark/dataset/manifest.json
  ./gradlew :benchmark-jvm:run --args="--config default"   # compare correctness counts

Record the upstream commit in the commit message:
  core: sync from upstream $UPSTREAM_SHA
EOF
