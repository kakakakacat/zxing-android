#!/usr/bin/env bash
# L4: what sustained scanning costs. Specified in docs/maint/BENCHMARKS.md §12.
#
#   tools/maint/l4-soak.sh <camera1|camerax> [minutes] [serial]
#
# Scans continuously for N minutes on a physical device and records CPU time, RSS, Java and
# native heap, thermal status and battery over the whole run, plus whatever GC lines ART
# logged. Its purpose is what a per-frame median hides: a leak, an allocation treadmill, or
# a win that evaporates once the device throttles.
#
# **This is the miss path, deliberately, unless something is put in front of the lens.**
# A scanner with nothing to read decodes nothing on every frame, which §10 measured as the
# most expensive case and which is what live preview actually does almost all the time. A
# hit-path soak needs the fixed scene §11 still does not have; the result file records which
# it was rather than leaving it to be assumed.
set -u

BACKEND="${1:-}"
MINUTES="${2:-10}"
SERIAL="${3:-}"
INTERVAL="${L4_INTERVAL:-5}"
SLOW_EVERY="${L4_SLOW_EVERY:-6}"

case "$BACKEND" in
  camera1) USE_CAMERAX=false ;;
  camerax) USE_CAMERAX=true ;;
  *) echo "usage: $0 <camera1|camerax> [minutes] [serial]" >&2; exit 2 ;;
esac

cd "$(dirname "$0")/../.."
ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

PKG=io.github.kakakakacat.zxing.android
ACT="$PKG/io.github.kakakakacat.zxing.android.CaptureActivity"
PREFS="/data/data/$PKG/shared_prefs/${PKG}_preferences.xml"
OUT_DIR="benchmark/l4/out"
DEVICE_TSV=/data/local/tmp/l4-samples.tsv

if [ "$("${ADB[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" = "1" ]; then
  echo "FAIL: L4 measures a camera and a thermal envelope; an emulator has neither." >&2
  exit 2
fi
if [ "$("${ADB[@]}" shell id -u | tr -d '\r')" != "0" ]; then
  echo "This needs root adb to write the app's preferences: try '${ADB[*]} root'." >&2
  exit 2
fi

mkdir -p "$OUT_DIR"
[ -n "$SERIAL" ] && export ANDROID_SERIAL="$SERIAL"

echo "device: $("${ADB[@]}" shell getprop ro.product.model | tr -d '\r') android $("${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r')"
echo "backend: $BACKEND, duration: ${MINUTES}m, interval: ${INTERVAL}s"

./gradlew --quiet :app:installDebug || exit 1

"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" shell mkdir -p "/data/data/$PKG/shared_prefs"
"${ADB[@]}" shell "cat > $PREFS" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="preferences_camerax" value="$USE_CAMERAX" />
    <boolean name="preferences_auto_focus" value="true" />
    <boolean name="preferences_disable_exposure" value="true" />
    <boolean name="preferences_disable_metering" value="true" />
    <boolean name="preferences_disable_barcode_scene_mode" value="true" />
    <boolean name="preferences_disable_continuous_focus" value="false" />
    <string name="preferences_front_light_mode">OFF</string>
</map>
XML
# Same uid trap as l3-run.sh: `dumpsys package` prints no userId= line on the reference
# device, so the uid is read off the data directory and an empty one is fatal rather than a
# silent chown that leaves the file unreadable and the app on its defaults.
APP_UID=$("${ADB[@]}" shell stat -c %u "/data/data/$PKG" | tr -d '\r')
case "$APP_UID" in
  ''|*[!0-9]*) echo "FAIL: could not read the app's uid (got \"$APP_UID\")." >&2; exit 1 ;;
esac
"${ADB[@]}" shell chown "$APP_UID:$APP_UID" "/data/data/$PKG/shared_prefs" "$PREFS"
"${ADB[@]}" shell chmod 771 "/data/data/$PKG/shared_prefs"
"${ADB[@]}" shell chmod 660 "$PREFS"
"${ADB[@]}" shell restorecon -R "/data/data/$PKG/shared_prefs" 2>/dev/null || true

"${ADB[@]}" shell pm grant "$PKG" android.permission.CAMERA 2>/dev/null || true
"${ADB[@]}" shell cmd appops set "$PKG" CAMERA allow 2>/dev/null || true
"${ADB[@]}" shell svc power stayon true
"${ADB[@]}" shell input keyevent KEYCODE_WAKEUP
"${ADB[@]}" shell wm dismiss-keyguard 2>/dev/null || true

"${ADB[@]}" push tools/maint/l4-sample.sh /data/local/tmp/l4-sample.sh >/dev/null
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am start -n "$ACT" >/dev/null
sleep 8

PID=$("${ADB[@]}" shell pidof "$PKG" | tr -d '\r' | awk '{print $1}')
case "$PID" in
  ''|*[!0-9]*) echo "FAIL: the app is not running; nothing to soak." >&2; exit 1 ;;
esac
echo "pid: $PID — soaking"

# Logcat streamed to a file for the duration, not read back afterwards. The reference
# device's main buffer turns over in seconds under load; polling `logcat -d` at the end is
# how the camera spike lost every decode line it was looking for.
"${ADB[@]}" logcat -v time > "$OUT_DIR/logcat-$BACKEND.log" &
LOGCAT_PID=$!

"${ADB[@]}" shell sh /data/local/tmp/l4-sample.sh "$PID" \
    "$(( MINUTES * 60 ))" "$INTERVAL" "$SLOW_EVERY" "$DEVICE_TSV"
SAMPLER_STATUS=$?

kill "$LOGCAT_PID" 2>/dev/null
wait "$LOGCAT_PID" 2>/dev/null

"${ADB[@]}" pull "$DEVICE_TSV" "$OUT_DIR/samples-$BACKEND.tsv" >/dev/null
"${ADB[@]}" shell rm -f "$DEVICE_TSV" /data/local/tmp/l4-sample.sh
"${ADB[@]}" shell svc power stayon false

# Did it still have a camera at the end? A soak that silently lost the camera half way
# through has flat CPU for the wrong reason, and that is the exact shape of a result that
# looks healthy and is not.
STILL_RUNNING=$("${ADB[@]}" shell pidof "$PKG" | tr -d '\r')
"${ADB[@]}" shell am force-stop "$PKG"

if [ "$SAMPLER_STATUS" = "3" ]; then
  echo "FAIL: the app died during the soak; see $OUT_DIR/samples-$BACKEND.tsv" >&2
  exit 1
fi
if [ -z "$STILL_RUNNING" ]; then
  echo "FAIL: the app was gone at the end of the soak." >&2
  exit 1
fi

echo "--- outputs"
echo "  $OUT_DIR/samples-$BACKEND.tsv"
echo "  $OUT_DIR/logcat-$BACKEND.log"
wc -l < "$OUT_DIR/samples-$BACKEND.tsv" | sed 's/^/  rows: /'
