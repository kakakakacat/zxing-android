#!/system/bin/sh
# On-device twin of camera-spike-run.sh, for a phone that is reached over something other
# than adb. It is pushed to /data/local/tmp and run there; everything it needs is on the
# phone, so the transport only has to carry one file and one shell command.
#
#   sh /data/local/tmp/spike-run.sh <camera1|camerax|cameraxlean> [runs] [backCameraOnly:0|1]
#
# The second physical device this project has access to is reached through a private
# root-agent control channel rather than adb, and no ADB listener is opened on it. The
# protocol is otherwise identical to the adb runner, deliberately: cold start, force-stop,
# two seconds for the HAL to release, logcat streamed to a file rather than read back
# afterwards, and the same lines printed, so one aggregator reads both.
#
# Two things the caller must do first on a HyperOS device, both learned by watching it fail
# fifteen times in a row with no error anywhere:
#
#   svc power stayon true
#   cmd appops set io.github.kakakakacat.zxing.spike.<flavor> CAMERA allow
#
# The first because the screen times out partway through the loop and a paused activity
# stops receiving frames. The second because HyperOS revokes the runtime CAMERA grant again
# after an app opens the camera without a user-visible prompt, and CameraX's response to
# that is a silent 700 ms retry loop that never ends.
FLAVOR="$1"; RUNS="${2:-15}"; BACKONLY="${3:-}"
PKG="io.github.kakakakacat.zxing.spike.$FLAVOR"
ACT="$PKG/io.github.kakakakacat.zxing.spike.SpikeActivity"
echo "device: $(getprop ro.product.model), sdk $(getprop ro.build.version.sdk)"
echo "fingerprint: $(getprop ro.build.fingerprint)"
echo "package: $PKG, runs: $RUNS, backCameraOnly=${BACKONLY:-0}"
echo
i=1
while [ "$i" -le "$RUNS" ]; do
  am force-stop "$PKG" >/dev/null 2>&1
  sleep 2
  logcat -c >/dev/null 2>&1
  LOG="/data/local/tmp/spike-run-$i.log"
  rm -f "$LOG"
  logcat -s ZXingCameraSpike > "$LOG" 2>/dev/null &
  LP=$!
  sleep 0.5
  if [ "$BACKONLY" = 1 ]; then
    am start -n "$ACT" --ez backCameraOnly true >/dev/null 2>&1
  else
    am start -n "$ACT" >/dev/null 2>&1
  fi
  n=0
  while [ "$n" -lt 40 ]; do
    sleep 0.25
    grep -q SPIKE-RESULT "$LOG" 2>/dev/null && break
    n=$((n+1))
  done
  sleep 1
  kill "$LP" 2>/dev/null
  R="$(grep -h SPIKE-RESULT "$LOG" 2>/dev/null | head -1)"
  D="$(grep -h SPIKE-DECODE "$LOG" 2>/dev/null | head -1)"
  F="$(grep -h SPIKE-FAILED "$LOG" 2>/dev/null | head -1)"
  if [ -n "$R" ]; then
    echo "run $i: ${R#*SPIKE-RESULT }"
    [ -n "$D" ] && echo "        ${D#*SPIKE-DECODE }"
  else
    echo "run $i: NO RESULT ${F:-(no SPIKE-FAILED line either)}"
  fi
  rm -f "$LOG"
  i=$((i+1))
done
am force-stop "$PKG" >/dev/null 2>&1
