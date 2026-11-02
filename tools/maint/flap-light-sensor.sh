#!/bin/sh
# Make an emulator's light sensor flap across both torch thresholds, and count how often
# the app actually touches the camera's flash mode.
#
# This is the measurement behind defect A-12. The scan screen's front light has to be set
# to AUTO first, or AmbientLightManager never registers for the sensor at all:
#
#   adb -s <serial> shell "run-as io.github.kakakakacat.zxing.android \
#     sed -i 's|>OFF<|>AUTO<|' shared_prefs/io.github.kakakakacat.zxing.android_preferences.xml"
#
# Count it on a build with the fix and on one without. Before, every reading that crossed
# a threshold changed the flash mode, and CameraManager.setTorch tears down and rebuilds
# the AutoFocusManager each time it does. After, TorchPolicy's interval holds it to one.
#
# Emulator only: a physical device's light sensor cannot be driven from here.
#
# usage: tools/maint/flap-light-sensor.sh <emulator-serial>
set -eu

serial=$1
pkg=io.github.kakakakacat.zxing.android

adb -s "$serial" shell "am force-stop $pkg" >/dev/null 2>&1
adb -s "$serial" shell "am start -n $pkg/.CaptureActivity" >/dev/null 2>&1
sleep 5
adb -s "$serial" logcat -c

# 12 readings, one every half second: six seconds of a sensor that cannot make up its
# mind, alternating well below 45 lux and well above 450.
i=0
while [ $i -lt 12 ]; do
  if [ $((i % 2)) -eq 0 ]; then lux=5; else lux=900; fi
  adb -s "$serial" emu sensor set light $lux >/dev/null 2>&1
  sleep 0.5
  i=$((i + 1))
done
sleep 2

echo "flash-mode changes requested during 6 s of flapping:"
adb -s "$serial" logcat -d 2>/dev/null | grep -c "Requesting flash mode value from among" || true
adb -s "$serial" logcat -d 2>/dev/null \
  | grep "Requesting flash mode value from among" | sed 's/.*among: /  /' || true
