#!/usr/bin/env bash
#
# L1 on a device: push the corpus, run it through one decoder backend, pull the result.
#
# Correctness only. Nothing here is timed and nothing here may be quoted as a speed
# result; see docs/maint/BENCHMARKS.md §2 and §9.1.
#
#   benchmark/tools/l1_device.sh --backend native --out build/benchmark/l1-native.json
#
# Options:
#   --backend java|native   which decoder runs                    (default: java)
#   --serial <id>           adb device, if more than one           (default: ANDROID_SERIAL)
#   --out <file>            where to put the pulled result file
#   --skip-push             the corpus is already on the device and unchanged
set -euo pipefail

BACKEND=java
SERIAL="${ANDROID_SERIAL:-}"
OUT=""
SKIP_PUSH=0

while [ $# -gt 0 ]; do
  case "$1" in
    --backend) BACKEND="$2"; shift 2 ;;
    --serial) SERIAL="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --skip-push) SKIP_PUSH=1; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

cd "$(dirname "$0")/../.."
REPO="$(pwd)"
[ -n "$OUT" ] || OUT="$REPO/build/benchmark/l1-device-$BACKEND.json"

if [ -z "$SERIAL" ]; then
  COUNT=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
  if [ "$COUNT" != "1" ]; then
    echo "found $COUNT devices; name one with --serial. A result file has to say which" >&2
    echo "device produced it, so this is not guessed at." >&2
    exit 2
  fi
  SERIAL=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
fi
ADB="adb -s $SERIAL"

# The androidTest APK of :benchmark-android is self-instrumenting, so these are its own
# directories. Android/media and not Android/data: a file pushed into the latter stays
# owned by the shell user and the app cannot read it back, which looks exactly like a
# push that did not happen. androidx.benchmark writes its own output to Android/media for
# the same reason.
TEST_PKG=io.github.kakakakacat.zxing.benchmark.test
DEVICE_DIR="/sdcard/Android/media/$TEST_PKG/l1"
# Named by the script rather than left to the runner's default, which spells the backend
# the way a result file records it ("zxing-java") rather than the way it is asked for.
DEVICE_OUT="/sdcard/Android/media/$TEST_PKG/l1-out/l1-$BACKEND.json"

COMMIT=$(git rev-parse --short HEAD)
DIRTY=$(git status --porcelain | wc -l | tr -d ' ')
if [ "$DIRTY" != "0" ]; then
  COMMIT="$COMMIT-dirty"
fi
RUN_ID="l1-device-$BACKEND-$(date -u +%Y%m%dT%H%M%SZ)"
LOG=$(mktemp -t l1-instrument)
trap 'rm -f "$LOG"' EXIT

echo "device:  $SERIAL ($($ADB shell getprop ro.product.model | tr -d '\r'))"
echo "backend: $BACKEND"
echo "commit:  $COMMIT"

# Install before pushing. A first install creates the app's external files directory
# from scratch and anything adb put there beforehand is gone -- which looks exactly like
# a push that silently failed. A reinstall over the same APK keeps it.
echo "--- installing the test APK"
ANDROID_SERIAL="$SERIAL" ./gradlew --console=plain -q :benchmark-android:installReleaseAndroidTest

if [ "$SKIP_PUSH" = "0" ]; then
  echo "--- pushing the corpus (109 MB; --sync, so only what changed)"
  # Every directory first, in one shell call. adb's own remote mkdir is refused under
  # /sdcard/Android/data ("secure_mkdirs failed: Operation not permitted"), so a plain
  # `adb push <tree>` dies on the first subdirectory it has to create -- but pushing
  # into a directory that already exists is allowed. `mkdir -p` from the shell is not
  # subject to the same restriction.
  $ADB shell "mkdir -p $DEVICE_DIR && cd $DEVICE_DIR && mkdir -p $(cd "$REPO" && find core/src/test/resources/blackbox -type d | tr '\n' ' ')"
  # Manifest paths are repo-relative, and the runner resolves them against the corpus
  # directory, so the tree has to keep its shape.
  $ADB push --sync "$REPO/core/src/test/resources/blackbox" \
      "$DEVICE_DIR/core/src/test/resources/" >/dev/null
  $ADB push "$REPO/benchmark/dataset/manifest.json" "$DEVICE_DIR/manifest.json" >/dev/null
fi

echo "--- running"
# Driven with `am instrument` rather than `gradlew connectedAndroidTest`, because that
# task reinstalls the test APK and an install wipes the app's external files directory --
# taking the corpus that was just pushed with it. The install above is the only one.
#
# The class filter matters too: without it this would also run the L2 benchmark, whose
# 27 cases take far longer and whose numbers have nothing to do with this question.
$ADB shell am instrument -w \
  -e class io.github.kakakakacat.zxing.benchmark.L1CorpusRun \
  -e l1.backend "$BACKEND" \
  -e l1.commit "$COMMIT" \
  -e l1.runId "$RUN_ID" \
  -e l1.out "$DEVICE_OUT" \
  "$TEST_PKG/androidx.benchmark.junit4.AndroidBenchmarkRunner" | tee "$LOG"

# `am instrument` prints a failure and still exits 0, so the result is read out of its
# output. Checked for the positive: a run that crashed before reporting anything would
# pass a search for the word FAILURES.
if ! grep -q "^OK (" "$LOG"; then
  echo "the run did not pass; see the output above" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT")"
$ADB pull "$DEVICE_OUT" "$OUT" >/dev/null
echo "--- pulled $OUT"
python3 - "$OUT" <<'PY'
import json, sys
report = json.load(open(sys.argv[1], encoding="utf-8"))
print("%s on %s, %s" % (report["backend"], report["device"], report["backend_build"]))
for status, count in report["status_counts"].items():
    if count:
        print("  %-18s %d" % (status, count))
PY
