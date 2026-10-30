#!/usr/bin/env bash
# Open and close the scan screen N times and report crashes / leaked camera sessions.
# Usage: tools/maint/stability-loop.sh [cycles] [serial]
set -u
CYCLES="${1:-100}"
SERIAL="${2:-}"
PKG="${PKG:-com.google.zxing.client.android.community}"
ACT="$PKG/com.google.zxing.client.android.CaptureActivity"
ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

"${ADB[@]}" logcat -c
"${ADB[@]}" shell am force-stop "$PKG"
echo "device: $("${ADB[@]}" shell getprop ro.product.model | tr -d '\r') android $("${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r') (sdk $("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r'))"
echo "package: $PKG, cycles: $CYCLES"

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
