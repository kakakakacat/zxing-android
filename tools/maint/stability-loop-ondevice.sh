#!/system/bin/sh
# On-device twin of stability-loop.sh, for a phone reached over something other than adb.
# Pushed to /data/local/tmp and run there, so the transport carries one file and one
# shell command.
#
#   sh /data/local/tmp/stability-loop.sh [cycles] [package]
#
# Same protocol as the adb version and the same two guards, which are the whole reason
# that script is worth copying rather than retyping:
#
#   - One unmuted `am start` first, and the run stops if the activity does not come up.
#     The loop throws `am start` output away, which is exactly how a wrong activity name
#     once turned into a clean hundred cycles of nothing happening.
#   - A fatal is counted from the log, not inferred from the app still being there.
#
# One difference, and it is this device's: the log is **streamed to a file** for the whole
# run instead of read back with `logcat -d` at the end. The main buffer on these phones is
# busy enough that a line is gone within seconds, which already cost this project one
# wrong reading in the camera spike.
#
# The screen has to stay awake for the whole run: a paused activity stops receiving
# frames, and a loop that cycles a stopped activity reports a clean hundred of nothing.
# `svc power stayon true` is the usual advice and **it does nothing on a phone that is not
# plugged in** -- that flag is STAY_ON_WHILE_PLUGGED_IN and nothing else. What actually
# keeps this loop awake is its own `input keyevent`, which resets the idle timer every
# cycle. So the script does not ask the caller to arrange anything; it checks instead, at
# the start and at the end, and says plainly if the device fell asleep, because a
# sleeping run looks exactly like a passing one.
CYCLES="${1:-100}"
PKG="${2:-io.github.kakakakacat.zxing.android}"
ACT="$PKG/$PKG.CaptureActivity"
LOG=/data/local/tmp/stability-loop.log

echo "device: $(getprop ro.product.model), android $(getprop ro.build.version.release) (sdk $(getprop ro.build.version.sdk))"
echo "fingerprint: $(getprop ro.build.fingerprint)"
echo "package: $PKG, cycles: $CYCLES"

pm list packages | grep -q "^package:$PKG$" || { echo "FAIL: $PKG is not installed"; exit 2; }

input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
wm dismiss-keyguard >/dev/null 2>&1
dumpsys power > /data/local/tmp/power.txt 2>/dev/null
WAKE_BEFORE="$(grep mWakefulness= /data/local/tmp/power.txt | head -1 | tr -d ' ')"
echo "screen_off_timeout: $(settings get system screen_off_timeout) ms, $WAKE_BEFORE at the start"

am force-stop "$PKG" >/dev/null 2>&1
rm -f "$LOG"
logcat -c >/dev/null 2>&1
logcat > "$LOG" 2>/dev/null &
LP=$!
sleep 1

FIRST="$(am start -n "$ACT" 2>&1)"
case "$FIRST" in
  *Error*|*does\ not\ exist*) kill "$LP" 2>/dev/null; echo "FAIL: could not start $ACT"; echo "$FIRST"; exit 2 ;;
esac
input keyevent KEYCODE_BACK >/dev/null 2>&1

i=1
while [ "$i" -le "$CYCLES" ]; do
  am start -n "$ACT" >/dev/null 2>&1
  sleep 0.8
  input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 0.4
  [ $((i % 10)) -eq 0 ] && echo "  ...$i"
  i=$((i+1))
done

sleep 2
am force-stop "$PKG" >/dev/null 2>&1
sleep 1
kill "$LP" 2>/dev/null

echo "--- FATAL exceptions"
grep -E "FATAL EXCEPTION|ANR in $PKG" "$LOG" | sed 's/^/  /'
echo "--- camera errors"
grep -iE "CameraService.*(Denial|error)|Fail to connect to camera|camera.*in use|CameraAccessException" "$LOG" | tail -5 | sed 's/^/  /'
dumpsys power > /data/local/tmp/power.txt 2>/dev/null
WAKE_AFTER="$(grep mWakefulness= /data/local/tmp/power.txt | head -1 | tr -d ' ')"
rm -f /data/local/tmp/power.txt
echo "--- summary"
echo "  cycles=$CYCLES"
echo "  wakefulness_before=${WAKE_BEFORE#mWakefulness=}"
echo "  wakefulness_after=${WAKE_AFTER#mWakefulness=}"
case "$WAKE_AFTER" in
  *Awake*) ;;
  *) echo "  WARNING: the device was not awake at the end -- cycles after it slept proved nothing" ;;
esac
echo "  fatal_exceptions=$(grep -c 'FATAL EXCEPTION' "$LOG")"
echo "  log_lines=$(wc -l < "$LOG")"
