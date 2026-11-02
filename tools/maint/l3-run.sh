#!/usr/bin/env bash
# L3: launch to camera-ready to first frame, on a physical device.
# Specified in docs/maint/BENCHMARKS.md §11.
#
#   tools/maint/l3-run.sh <camera1|camerax> [iterations] [serial]
#
# What this script owns, and the benchmark deliberately does not: choosing the camera
# backend. The Macrobenchmark target is the `benchmark` build type, which is NOT debuggable
# -- that is the point of it -- so neither `run-as` nor the instrumentation's own shell can
# write the app's preferences. Root adb can, and that is a host-side privilege, so it
# belongs in a host-side script.
#
# The run does not take that on trust. ScannerView emits `zxing.backend.<name>` into the
# trace for whichever backend it actually used, the benchmark collects it, and
# l3_report.py refuses a result whose trace disagrees with the backend the run claims.
# "The preference did not take" is otherwise indistinguishable from "the two backends are
# the same speed", which is exactly the kind of silent wrong answer this repository keeps
# finding in its own tooling.
set -u

BACKEND="${1:-}"
ITERATIONS="${2:-10}"
SERIAL="${3:-}"

case "$BACKEND" in
  camera1) USE_CAMERAX=false ;;
  camerax) USE_CAMERAX=true ;;
  *) echo "usage: $0 <camera1|camerax> [iterations] [serial]" >&2; exit 2 ;;
esac

cd "$(dirname "$0")/../.."

ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

PKG=io.github.kakakakacat.zxing.android
TEST_PKG=io.github.kakakakacat.zxing.benchmark.l3
PREFS="/data/data/$PKG/shared_prefs/${PKG}_preferences.xml"

if [ "$("${ADB[@]}" shell getprop ro.build.characteristics | tr -d '\r')" = "emulator" ] \
   || [ "$("${ADB[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" = "1" ]; then
  echo "FAIL: L3 measures a camera; an emulator result is not one. BENCHMARKS.md §6." >&2
  exit 2
fi

SDK=$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$SDK" -lt 29 ]; then
  echo "FAIL: ScannerTrace's async sections are API 29+; this device is API $SDK." >&2
  echo "      The startup metric would be collected and every camera span would be absent." >&2
  exit 2
fi

if [ "$("${ADB[@]}" shell id -u | tr -d '\r')" != "0" ]; then
  echo "This needs root adb to write the app's preferences: try '${ADB[*]} root'." >&2
  exit 2
fi

echo "device: $("${ADB[@]}" shell getprop ro.product.model | tr -d '\r') android $("${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r') (sdk $SDK)"
echo "backend: $BACKEND, iterations: $ITERATIONS"

# Build and install both APKs FIRST, and run the benchmark with `am instrument` rather
# than with `connectedBenchmarkAndroidTest`.
#
# Not a preference: AGP's install uninstalls before it installs, which takes the app's
# shared_prefs with it. Anything written before Gradle runs is gone by the time the
# benchmark starts, and the app falls back to every default -- including camera1. That is
# precisely the mismatch the backend span in the trace exists to catch, and it caught it on
# the first run that worked.
[ -n "$SERIAL" ] && export ANDROID_SERIAL="$SERIAL"
./gradlew --quiet :app:installBenchmark :benchmark-l3:assembleBenchmark || exit 1
"${ADB[@]}" install -r -t benchmark/l3/build/outputs/apk/benchmark/benchmark-l3-benchmark.apk || exit 1

"${ADB[@]}" shell am force-stop "$PKG"
# shared_prefs does not exist until the app has run once, and a fresh install has not.
"${ADB[@]}" shell mkdir -p "/data/data/$PKG/shared_prefs"
# Written whole rather than patched. PreferenceManager fills in defaults only the first
# time it sees the file, so a file carrying one key would leave the camera options unset,
# and "unset" is not "default" for the ones ScannerOptions reads.
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
# The file is created by root and read by the app's own uid; without this the app cannot
# open it and silently falls back to every default, including camera1 -- which is what
# happened, and which the backend span in the trace is what caught.
#
# Read off the data directory rather than parsed out of `dumpsys package`: this device
# prints no `userId=` line at all, so that parse produced an empty string, `chown ":"` did
# nothing, and the file stayed root-owned and unreadable. An empty uid is now fatal.
APP_UID=$("${ADB[@]}" shell stat -c %u "/data/data/$PKG" | tr -d '\r')
case "$APP_UID" in
  ''|*[!0-9]*)
    echo "FAIL: could not read the app's uid from /data/data/$PKG (got \"$APP_UID\")." >&2
    exit 1 ;;
esac
"${ADB[@]}" shell chown "$APP_UID:$APP_UID" "/data/data/$PKG/shared_prefs" "$PREFS"
"${ADB[@]}" shell chmod 771 "/data/data/$PKG/shared_prefs"
"${ADB[@]}" shell chmod 660 "$PREFS"
"${ADB[@]}" shell restorecon -R "/data/data/$PKG/shared_prefs" 2>/dev/null || true
# Prove the app can actually read what was just written, before spending two minutes
# measuring the wrong backend.
OWNER=$("${ADB[@]}" shell stat -c %u "$PREFS" | tr -d '\r')
if [ "$OWNER" != "$APP_UID" ]; then
  echo "FAIL: $PREFS is owned by $OWNER, not the app's $APP_UID; the app would not read it." >&2
  exit 1
fi

"${ADB[@]}" shell pm grant "$PKG" android.permission.CAMERA 2>/dev/null || true
# HyperOS revokes a runtime grant again when an app opens the camera with no visible
# prompt; harmless where it is not needed. See docs/maint/decisions/0001-camera-backend.md.
"${ADB[@]}" shell cmd appops set "$PKG" CAMERA allow 2>/dev/null || true
"${ADB[@]}" shell svc power stayon true
"${ADB[@]}" shell input keyevent KEYCODE_WAKEUP
"${ADB[@]}" shell wm dismiss-keyguard 2>/dev/null || true

OUT_DIR=benchmark/l3/build/outputs/l3
DEVICE_OUT=/sdcard/Download/zxing-l3
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
"${ADB[@]}" shell rm -rf "$DEVICE_OUT"
"${ADB[@]}" shell mkdir -p "$DEVICE_OUT"

"${ADB[@]}" shell am instrument -w -r \
    -e l3.backend "$BACKEND" \
    -e l3.iterations "$ITERATIONS" \
    -e additionalTestOutputDir "$DEVICE_OUT" \
    -e androidx.benchmark.output.enable true \
    "$TEST_PKG/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tee "$OUT_DIR/instrument.log"

"${ADB[@]}" shell svc power stayon false

"${ADB[@]}" pull "$DEVICE_OUT" "$OUT_DIR" >/dev/null 2>&1 || true

# The protocol this run actually used, recorded by the thing that used it. :benchmark-android
# writes the same record from Gradle; here Gradle is not what runs the benchmark, so a
# Gradle-written record could describe a run that never happened.
cat > "$OUT_DIR/l3-runner-args.json" <<JSON
{
  "l3.backend": "$BACKEND",
  "l3.iterations": "$ITERATIONS",
  "device_serial_present": "$([ -n "$SERIAL" ] && echo true || echo false)",
  "commit": "$(git rev-parse --short HEAD)"
}
JSON

# `am instrument` reports a failed test in its output and still exits 0, and AGP's runner
# has been seen letting a failed APK install through as BUILD SUCCESSFUL. A run that
# produced no result file did not happen, whatever any exit code says.
RESULTS=$(find "$OUT_DIR" -name "*benchmarkData.json" 2>/dev/null | wc -l | tr -d ' ')
echo "--- outputs"
find "$OUT_DIR" -type f 2>/dev/null | sed 's/^/  /'
if grep -q "FAILURES\|INSTRUMENTATION_CODE: 0" "$OUT_DIR/instrument.log" 2>/dev/null; then
  echo "FAIL: the instrumentation reported a failure; see $OUT_DIR/instrument.log" >&2
  exit 1
fi
if [ "$RESULTS" = "0" ]; then
  echo "FAIL: the run produced no benchmarkData.json, so there is no result." >&2
  exit 1
fi
exit 0
