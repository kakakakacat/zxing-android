#!/usr/bin/env bash
# Phase 6 camera-backend spike: launch one flavor N times and report what it cost to get a
# frame. See docs/maint/decisions/0001-camera-backend.md.
#
# Usage: tools/maint/camera-spike-run.sh <camera1|camerax> [runs] [serial]
#
# Each run is a genuine cold start: the package is force-stopped first, so the process is
# gone and the camera HAL has released. The activity prints one SPIKE-RESULT line per start
# and this script takes the median of them, because a single launch is dominated by whatever
# else the device was doing.
#
# This is a spike, not a benchmark layer. It does not lock clocks, does not wait for a
# thermal floor and does not emit a result envelope, so its numbers are not admissible as an
# L-layer result under BENCHMARKS.md §2. What it is good for is the comparison it was built
# for: the same N launches of two APKs that differ only in their camera stack.
set -u

FLAVOR="${1:-}"
RUNS="${2:-15}"
SERIAL="${3:-}"
# CameraX flavors only: ask CameraX to look for the back camera and nothing else. Without it,
# CameraX verifies that both lens facings exist and retries for about five seconds on a
# device that has only one camera. See docs/maint/decisions/0001-camera-backend.md.
BACK_CAMERA_ONLY="${BACK_CAMERA_ONLY:-0}"

case "$FLAVOR" in
  camera1|camerax|cameraxlean) ;;
  *) echo "usage: $0 <camera1|camerax|cameraxlean> [runs] [serial]" >&2; exit 2 ;;
esac

PKG="io.github.kakakakacat.zxing.spike.$FLAVOR"
ACT="$PKG/io.github.kakakakacat.zxing.spike.SpikeActivity"
TAG="ZXingCameraSpike"

ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

if ! "${ADB[@]}" shell pm list packages | tr -d '\r' | grep -qx "package:$PKG"; then
  echo "FAIL: $PKG is not installed." >&2
  echo "  ./gradlew :camera-spike:install${FLAVOR^}Release" >&2
  exit 2
fi

# Granted up front: a permission dialog on the first run would be timed as camera startup.
"${ADB[@]}" shell pm grant "$PKG" android.permission.CAMERA >/dev/null 2>&1 || true
if ! "${ADB[@]}" shell dumpsys package "$PKG" | tr -d '\r' | grep -q "android.permission.CAMERA: granted=true"; then
  echo "FAIL: CAMERA is not granted to $PKG and could not be granted." >&2
  exit 2
fi

MODEL="$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
SDK="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
FINGERPRINT="$("${ADB[@]}" shell getprop ro.build.fingerprint | tr -d '\r')"
echo "device: $MODEL, sdk $SDK"
echo "fingerprint: $FINGERPRINT"
EXTRAS=()
if [ "$FLAVOR" != camera1 ] && [ "$BACK_CAMERA_ONLY" = 1 ]; then
  EXTRAS=(--ez backCameraOnly true)
  echo "option: backCameraOnly"
fi
echo "package: $PKG, runs: $RUNS"
echo

RESULTS=()
# The log is *streamed* to a file rather than read back with `logcat -d` afterwards. On the
# reference device the main buffer is busy enough that a line is gone within a few seconds:
# polling it found SPIKE-RESULT and lost SPIKE-DECODE every time, which looked exactly like
# a decode that never ran. A reader that is already attached cannot miss either.
LOGDIR="$(mktemp -d)"
trap 'rm -rf "$LOGDIR"; [ -n "${LOGPID:-}" ] && kill "$LOGPID" 2>/dev/null' EXIT

for i in $(seq 1 "$RUNS"); do
  "${ADB[@]}" shell am force-stop "$PKG" >/dev/null 2>&1
  # The camera HAL needs a moment after a force-stop, and a run that opens the camera
  # while the previous process is still releasing it measures the release, not the open.
  sleep 2
  "${ADB[@]}" logcat -c >/dev/null 2>&1

  LOGFILE="$LOGDIR/run-$i.log"
  : > "$LOGFILE"
  "${ADB[@]}" logcat -s "$TAG" > "$LOGFILE" 2>/dev/null &
  LOGPID=$!
  sleep 0.5

  START_OUT="$("${ADB[@]}" shell am start -n "$ACT" ${EXTRAS[@]+"${EXTRAS[@]}"} 2>&1 | tr -d '\r')"
  case "$START_OUT" in
    *Error*|*does\ not\ exist*)
      kill "$LOGPID" 2>/dev/null
      echo "FAIL: could not start $ACT" >&2
      echo "$START_OUT" >&2
      exit 2
      ;;
  esac

  LINE=""
  for _ in $(seq 1 40); do
    sleep 0.25
    LINE="$(tr -d '\r' < "$LOGFILE" | grep -F 'SPIKE-RESULT' | head -1)"
    [ -n "$LINE" ] && break
  done
  # One more beat so the decode that follows the first frame lands in the same capture.
  sleep 0.75
  kill "$LOGPID" 2>/dev/null
  wait "$LOGPID" 2>/dev/null
  LOGPID=""

  if [ -z "$LINE" ]; then
    FAILED="$(tr -d '\r' < "$LOGFILE" | grep -F 'SPIKE-FAILED' | head -1)"
    echo "run $i: NO RESULT ${FAILED:-(no SPIKE-FAILED line either)}" >&2
    continue
  fi
  DECODE="$(tr -d '\r' < "$LOGFILE" | grep -F 'SPIKE-DECODE' | head -1)"
  echo "run $i: ${LINE#*SPIKE-RESULT }"
  if [ -n "$DECODE" ]; then
    echo "        ${DECODE#*SPIKE-DECODE }"
  else
    echo "        no SPIKE-DECODE line" >&2
  fi
  RESULTS+=("$LINE")
done

"${ADB[@]}" shell am force-stop "$PKG" >/dev/null 2>&1

if [ "${#RESULTS[@]}" -eq 0 ]; then
  echo "FAIL: no run produced a result." >&2
  exit 1
fi

echo
printf '%s\n' "${RESULTS[@]}" | python3 -c '
import sys, re, statistics

fields = ["processToFirstFrameMs", "createToFirstFrameMs", "openToFirstFrameMs"]
values = {f: [] for f in fields}
for line in sys.stdin:
    for f in fields:
        m = re.search(r"\b" + f + r"=(\d+)\b", line)
        if m:
            values[f].append(int(m.group(1)))

n = max(len(v) for v in values.values())
print("runs with a result: %d" % n)
print("%-24s %7s %7s %7s %7s" % ("", "min", "median", "max", "n"))
for f in fields:
    v = sorted(values[f])
    if not v:
        print("%-24s %7s" % (f, "unknown"))
        continue
    print("%-24s %7d %7d %7d %7d" % (f, v[0], statistics.median(v), v[-1], len(v)))
'
