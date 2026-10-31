#!/usr/bin/env bash
# Open and close the scan screen N times and report crashes / leaked camera sessions.
# Usage: tools/maint/stability-loop.sh [cycles] [serial]
set -u
CYCLES="${1:-100}"
SERIAL="${2:-}"
# Both of these moved out of com.google.zxing at 849921bfa; this script was last touched
# before that and still named the old ones, so every `am start` did nothing and the loop
# reported a clean hundred cycles of nothing happening. The run now fails if the activity
# does not actually come up.
PKG="${PKG:-io.github.kakakakacat.zxing.android}"
ACT="${ACT:-$PKG/io.github.kakakakacat.zxing.android.CaptureActivity}"
ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

"${ADB[@]}" logcat -c
"${ADB[@]}" shell am force-stop "$PKG"
if ! "${ADB[@]}" shell cmd package resolve-activity --brief "$PKG" >/dev/null 2>&1 \
     || ! "${ADB[@]}" shell pm list packages | grep -q "^package:$PKG$"; then
  echo "FAIL: $PKG is not installed on this device" >&2
  exit 2
fi
echo "device: $("${ADB[@]}" shell getprop ro.product.model | tr -d '\r') android $("${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r') (sdk $("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r'))"
echo "package: $PKG, cycles: $CYCLES"

# One unmuted start first. The loop below throws `am start` output away, which is how a
# wrong activity name turns into a clean run of a hundred no-ops.
FIRST_START="$("${ADB[@]}" shell am start -n "$ACT" 2>&1)"
case "$FIRST_START" in
  *Error*|*does\ not\ exist*)
    echo "FAIL: could not start $ACT" >&2
    echo "$FIRST_START" >&2
    exit 2
    ;;
esac
"${ADB[@]}" shell input keyevent KEYCODE_BACK >/dev/null 2>&1

for i in $(seq 1 "$CYCLES"); do
  "${ADB[@]}" shell am start -n "$ACT" >/dev/null 2>&1
  sleep 0.8
  "${ADB[@]}" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 0.4
  if [ $((i % 10)) -eq 0 ]; then echo "  ...$i"; fi
done

sleep 2
"${ADB[@]}" shell am force-stop "$PKG"
echo "--- FATAL exceptions"
"${ADB[@]}" logcat -d 2>/dev/null | grep -E "FATAL EXCEPTION|ANR in $PKG" | sed 's/^/  /' || true
FATAL=$("${ADB[@]}" logcat -d 2>/dev/null | grep -c "FATAL EXCEPTION")
echo "--- camera errors"
"${ADB[@]}" logcat -d 2>/dev/null | grep -iE "CameraService.*(Denial|error)|Fail to connect to camera|camera.*in use" | tail -5 | sed 's/^/  /' || true
echo "--- summary"
echo "  fatal_exceptions=$FATAL"
