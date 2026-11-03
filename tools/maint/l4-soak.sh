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
# §12.3: the miss path is the default, and now it is also checked rather than declared.
# Set L4_SCENE=hit when a symbol is deliberately in view.
SCENE="${L4_SCENE:-miss}"
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

# The scene is declared on the command line and was never checked, which is how two
# twelve-minute soaks were spent measuring a result screen: a second device three feet away
# was still displaying the Code 128 from an earlier test, the scanner decoded it in the
# first second, stopped scanning and sat there. CPU read 3.4% of one core against the
# baseline's 74% and the result file still said `scene: miss`.
#
# `contents_text_view` is the result screen's own view id. If it is up, this is not the
# miss path -- whatever the caller declared.
check_scene() {
  "${ADB[@]}" shell "uiautomator dump /sdcard/l4-ui.xml" >/dev/null 2>&1
  DECODED=$("${ADB[@]}" shell "cat /sdcard/l4-ui.xml" 2>/dev/null | grep -c 'contents_text_view')
  "${ADB[@]}" shell "rm -f /sdcard/l4-ui.xml" >/dev/null 2>&1
  echo "${DECODED:-0}" | tr -d '\r'
}
if [ "$SCENE" = "miss" ] && [ "$(check_scene)" != "0" ]; then
  echo "FAIL: the scanner is showing a decoded result, so the lens is not looking at an" >&2
  echo "      empty scene. This run would measure a result screen, not scanning." >&2
  "${ADB[@]}" shell am force-stop "$PKG"
  exit 1
fi

# Logcat streamed to a file for the duration, not read back afterwards. The reference
# device's main buffer turns over in seconds under load; polling `logcat -d` at the end is
# how the camera spike lost every decode line it was looking for.
#
# `--pid` is not a tidiness measure. Unfiltered, this device wrote **717 MB in twelve
# minutes** -- it logs an `AppsFilter: interaction ... BLOCKED` line for essentially every
# package pair -- and that is a megabyte a second of USB traffic and host disk writes
# sitting inside a measurement of the phone's own power draw. The only lines the converter
# reads are ART's GC lines from this process, and that is exactly what `--pid` leaves.
"${ADB[@]}" logcat -v time --pid="$PID" > "$OUT_DIR/logcat-$BACKEND.log" &
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
# A live process is not a live scan. `InactivityTimer` finishes CaptureActivity after five
# minutes **when the device is on battery** -- and the only thing that keeps resetting it is
# the ACTION_BATTERY_CHANGED broadcast, which is not a schedule anyone controls. On charge
# the timer is cancelled outright, which is why every soak so far ran past it without
# noticing. Off charge, the activity can end half way through: the camera closes, CPU
# collapses, the process stays cached and `pidof` still answers. That is a soak that looks
# healthy and measures nothing, so the window's focus is checked rather than the process.
STILL_FOCUSED=$("${ADB[@]}" shell dumpsys window 2>/dev/null \
    | grep -m1 'mCurrentFocus' | grep -c "$PKG" | tr -d '\r')
SCENE_AT_END=$(check_scene)
"${ADB[@]}" shell am force-stop "$PKG"

if [ "$SAMPLER_STATUS" = "3" ]; then
  echo "FAIL: the app died during the soak; see $OUT_DIR/samples-$BACKEND.tsv" >&2
  exit 1
fi
if [ -z "$STILL_RUNNING" ]; then
  echo "FAIL: the app was gone at the end of the soak." >&2
  exit 1
fi
if [ "$SCENE" = "miss" ] && [ "$SCENE_AT_END" != "0" ]; then
  echo "FAIL: the scanner was showing a decoded result at the end of the soak, so something" >&2
  echo "      came into view during it. Part of this run measured a result screen." >&2
  exit 1
fi
if [ "$STILL_FOCUSED" != "1" ]; then
  echo "FAIL: $PKG no longer held the focused window at the end of the soak." >&2
  echo "      On battery that is most likely InactivityTimer finishing CaptureActivity" >&2
  echo "      after five minutes. The samples after that point are not a scan." >&2
  exit 1
fi

echo "--- outputs"
echo "  $OUT_DIR/samples-$BACKEND.tsv"
echo "  $OUT_DIR/logcat-$BACKEND.log"
wc -l < "$OUT_DIR/samples-$BACKEND.tsv" | sed 's/^/  rows: /'
