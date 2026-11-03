#!/usr/bin/env bash
# What this project can verify on an Android 16 device reached over a cloud ADB tunnel,
# with no root and nothing in front of the lens.
#
#   tools/maint/android16-checks.sh <serial> <out-dir> [--with-l1]
#
# Two constraints shape every choice below, and both are the device's, not a preference.
#
# **No root.** L2, L3 and L4 are therefore out. Not merely unavailable: `lockClocks` needs
# root, L3 and L4 write the app's preferences into /data/data, and BENCHMARKS.md §10.6
# turns an unlocked-clock run into `inconclusive` *by rule*. A run here would produce a
# result file that cannot be compared with anything, and the way to stop a number being
# quoted is not to produce it. What is left is correctness and behaviour, which have no
# clock protocol to fail.
#
# **A link that drops.** Long-running `adb shell` invocations on this tunnel end in
# `error: closed`, and the transport then sits at `offline`. So every step here is short,
# every step is retried through `dev`, and the steps are independent: one that fails costs
# itself and not the run.
set -u

SERIAL="${1:?serial, e.g. localhost:37000}"
OUT="${2:?output directory}"
WITH_L1="${3:-}"
PKG=io.github.kakakakacat.zxing.android
TEST_PKG=io.github.kakakakacat.zxing.android.scanner.test
RUNNER=androidx.test.runner.AndroidJUnitRunner
cd "$(dirname "$0")/../.."
mkdir -p "$OUT"

# Gradle device tasks take every attached device unless this is set, and the other phones
# here may be mid-soak. Pinning it is not tidiness. Nothing below uses Gradle, but a
# careless addition later would.
export ANDROID_SERIAL="$SERIAL"

# One short command, reconnected and retried. Prints the output; returns non-zero when
# every attempt failed, so a caller can report a step as failed rather than as empty.
# A transport that has gone `offline` is NOT repaired by `adb connect`: that answers
# "already connected" and leaves the dead transport in place. It has to be disconnected
# first. A retry loop that only reconnects therefore tries the same broken connection five
# times and reports the device as unreachable while it is sitting there working -- which is
# exactly what the first run of this script did, failing every single step.
reset_link() {
  adb disconnect "$SERIAL" >/dev/null 2>&1
  sleep 1
  adb connect "$SERIAL" >/dev/null 2>&1
  sleep 2
}

dev() {
  local tries=0 out
  while [ $tries -lt 6 ]; do
    if out=$(timeout 90 adb -s "$SERIAL" "$@" 2>&1); then
      case "$out" in
        *"device offline"*|*"error: closed"*|*"device '$SERIAL' not found"*) ;;
        *) printf '%s\n' "$out" | tr -d '\r'; return 0 ;;
      esac
    fi
    tries=$((tries + 1))
    reset_link
  done
  printf 'FAILED after 6 attempts: adb %s\n' "$*"
  return 1
}

say() { echo; echo "=== $* ==="; }

say "device"
: > "$OUT/device.txt"
for p in ro.product.model ro.product.manufacturer ro.product.device \
         ro.build.version.release ro.build.version.sdk ro.build.version.security_patch \
         ro.build.fingerprint ro.product.cpu.abilist \
         ro.soc.manufacturer ro.soc.model ro.board.platform; do
  printf '%-32s %s\n' "$p" "$(dev shell getprop "$p")" | tee -a "$OUT/device.txt"
done
for c in "getconf PAGE_SIZE" "nproc" "id -u"; do
  printf '%-32s %s\n' "$c" "$(dev shell "$c")" | tee -a "$OUT/device.txt"
done

# The minified build, not the debug one: 4.5 MB against 61 MB over a tunnel that drops,
# and it is also the more valuable thing to verify -- it is the shape that ships. The
# Android 15 verification on the second device made the same choice for the same reason.
say "install the minified build"
dev install -r -g android/build/outputs/apk/benchmark/app-benchmark.apk | tail -2
dev shell pm grant "$PKG" android.permission.CAMERA
dev shell cmd appops set "$PKG" CAMERA allow

say "it starts"
dev shell am force-stop "$PKG"
dev shell input keyevent KEYCODE_WAKEUP
dev shell wm dismiss-keyguard
dev shell "am start -n $PKG/.CaptureActivity" | tail -1
sleep 8
adb -s "$SERIAL" exec-out screencap -p > "$OUT/app-benchmark.png" 2>/dev/null \
  && echo "screenshot: $OUT/app-benchmark.png"

PID=$(dev shell pidof "$PKG" | awk '{print $1}')
case "${PID:-}" in
  ''|*[!0-9]*) echo "no pid -- the app is not running; skipping the process checks" \
                 | tee "$OUT/pid.txt"; PID="" ;;
  *) echo "pid=$PID" | tee "$OUT/pid.txt" ;;
esac

if [ -n "$PID" ]; then
say "the native decoder is loaded on this hardware"
dev shell "grep -o '[^ ]*libzxingnative.so' /proc/$PID/maps | sort -u" | tee "$OUT/native-lib.txt"

say "what the camera service says"
dev shell "dumpsys media.camera | grep -m10 -E 'Number of camera devices|Device status|Active Camera Clients|Client Package Name'" \
  | tee "$OUT/camera.txt"

say "do frames reach the decoder"
CLK=$(dev shell getconf CLK_TCK)
A=$(dev shell "awk '{print \$14+\$15}' /proc/$PID/stat")
sleep 10
B=$(dev shell "awk '{print \$14+\$15}' /proc/$PID/stat")
if [ -n "${A:-}" ] && [ -n "${B:-}" ] && [ -n "${CLK:-}" ]; then
  echo "cpu over 10 s: $(( (B - A) * 10 / CLK ))% of one core (jiffies $A -> $B at ${CLK}Hz)" \
    | tee "$OUT/decode-cpu.txt"
fi
dev shell "ls /proc/$PID/task | wc -l" | sed 's/^/threads: /' | tee -a "$OUT/decode-cpu.txt"
dev shell "for t in /proc/$PID/task/*; do cat \$t/comm; done" | sort -u | tee "$OUT/threads.txt"
fi

say "21 scanner instrumentation tests"
dev install -r scanner/build/outputs/apk/androidTest/debug/scanner-debug-androidTest.apk | tail -2
dev shell "am instrument -w -r $TEST_PKG/$RUNNER" > "$OUT/instrumentation.log" 2>&1
grep -E "^INSTRUMENTATION_(RESULT|CODE)|OK \(|FAILURES|Tests run" "$OUT/instrumentation.log" | tail -6
grep -c "^INSTRUMENTATION_STATUS_CODE: 0" "$OUT/instrumentation.log" | sed 's/^/passed: /'
grep -c "^INSTRUMENTATION_STATUS_CODE: -2" "$OUT/instrumentation.log" | sed 's/^/failed: /'

say "100 open/close cycles"
PKG="$PKG" tools/maint/stability-loop.sh 100 "$SERIAL" > "$OUT/stability-100.log" 2>&1
tail -6 "$OUT/stability-100.log"

if [ "$WITH_L1" = "--with-l1" ]; then
  say "L1 corpus, both decoder backends (109 MB to push -- link permitting)"
  benchmark/tools/l1_device.sh --backend java --config default \
      --serial "$SERIAL" --out "$OUT/l1-java.json" 2>&1 | tail -12
  benchmark/tools/l1_device.sh --backend native --config default \
      --serial "$SERIAL" --skip-push --out "$OUT/l1-native.json" 2>&1 | tail -12
fi

say "done -- artifacts in $OUT"
ls -la "$OUT"
