#!/bin/bash
#
# Put a known symbol on one device's screen and read it with the other device's camera,
# N cold starts per camera backend, printing what each scan decoded.
#
#   tools/maint/scan-off-screen.sh CODE_128 'ZXING-CAMERAX-001' 6
#
# Why it exists: until 2026-11-03 nothing in this repository had ever put a real barcode in
# front of a real camera. Every "decode" was an emulator virtual scene or a synthetic frame
# handed straight to the decoder, and the one functional check standing between CameraX and
# the shipping default was exactly this one. A second Android device is a barcode holder
# that an operator does not have to keep steady.
#
# What it does NOT produce: timings. Neither device locks its clocks here and the symbol's
# distance and angle are whatever the desk happens to be -- BENCHMARKS.md §6 keeps numbers
# like that out of every L layer. This answers "what text came back", nothing else.
#
# Environment:
#   SCANNER  serial of the phone that scans      (default: DN-ENG41)
#   PANEL    serial of the device that displays  (default: DN-ENG81)
set -u

FORMAT=${1:?format, e.g. CODE_128}
TEXT=${2:?the text to encode}
N=${3:-6}

SCANNER=${SCANNER:-T3515NNFBC000055}
PANEL=${PANEL:-ZT812U128GB254233100034}
PKG=io.github.kakakakacat.zxing.android
PREFS=/data/data/$PKG/shared_prefs/${PKG}_preferences.xml
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

CORE=$(ls core/build/libs/core-*.jar 2>/dev/null | head -1)
if [ -z "$CORE" ]; then
  echo "no core jar; run ./gradlew :core:jar" >&2
  exit 2
fi

echo "== generating $FORMAT, and reading it back =="
javac -cp "$CORE" -d "$WORK" tools/maint/BarcodeSheet.java || exit 2
java -cp "$CORE:$WORK" BarcodeSheet "$FORMAT" "$TEXT" 1000 320 "$WORK/sheet.png" || exit 2

echo "== showing it on $PANEL =="
adb -s "$PANEL" push "$WORK/sheet.png" /sdcard/Pictures/zxing-sheet.png >/dev/null
adb -s "$PANEL" shell "settings put system screen_brightness_mode 0; settings put system screen_brightness 255"
adb -s "$PANEL" shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/zxing-sheet.png" >/dev/null 2>&1
adb -s "$PANEL" shell "am start -a android.intent.action.VIEW -d file:///sdcard/Pictures/zxing-sheet.png -t image/png" >/dev/null 2>&1
sleep 3

for backend in camera1 camerax; do
  case $backend in camerax) value=true ;; camera1) value=false ;; esac
  adb -s "$SCANNER" shell "am force-stop $PKG"
  adb -s "$SCANNER" shell \
    "sed -i 's|\"preferences_camerax\" value=\"[a-z]*\"|\"preferences_camerax\" value=\"$value\"|' $PREFS"
  # A preference that did not take is the trap this repository has already paid for twice:
  # a run that reports one backend and exercises the other. Refuse rather than report.
  got=$(adb -s "$SCANNER" shell "grep -o 'preferences_camerax\" value=\"[a-z]*' $PREFS" | sed 's/.*"//' | tr -d '\r')
  if [ "$got" != "$value" ]; then
    echo "preference did not take: wanted $value, file says '$got'" >&2
    exit 3
  fi

  for i in $(seq 1 "$N"); do
    adb -s "$SCANNER" shell "am force-stop $PKG" >/dev/null
    adb -s "$SCANNER" shell "am start -n $PKG/.CaptureActivity" >/dev/null 2>&1
    text=""
    for _ in $(seq 1 20); do
      sleep 1
      adb -s "$SCANNER" shell "uiautomator dump /sdcard/ui.xml" >/dev/null 2>&1
      text=$(adb -s "$SCANNER" shell "cat /sdcard/ui.xml" 2>/dev/null \
        | tr '>' '\n' | grep 'contents_text_view' | sed 's/.*text="//; s/" resource-id.*//')
      [ -n "$text" ] && break
    done
    printf '%s\t%s\t%s\n' "$backend" "$i" "${text:-<no result in 20s>}"
  done
done

adb -s "$SCANNER" shell "am force-stop $PKG; rm -f /sdcard/ui.xml" >/dev/null
adb -s "$PANEL" shell "rm -f /sdcard/Pictures/zxing-sheet.png; settings put system screen_brightness_mode 1" >/dev/null
