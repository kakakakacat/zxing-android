#!/usr/bin/env bash
# What CameraX costs in an APK, measured rather than quoted.
#
#   tools/maint/camera-spike-size.sh
#
# Builds all three flavors of :camera-spike at release (R8 and resource shrinking on, the
# same settings :app uses) and prints the differences. The APKs are the same application
# with the same decoder and the same ABI filter; the only thing that differs is which camera
# stack is compiled in, so a delta is a camera stack.
#
#   camera1      what ships today
#   camerax      CameraX the documented way, with PreviewView from camera-view
#   cameraxlean  the same CameraX binding, a plain SurfaceView, no camera-view
set -eu
cd "$(dirname "$0")/../.."

./gradlew --quiet :camera-spike:assembleCamera1Release :camera-spike:assembleCameraxRelease \
    :camera-spike:assembleCameraxleanRelease

apk() { echo "benchmark/camera-spike/build/outputs/apk/$1/release/camera-spike-$1-release.apk"; }
FLAVORS=(camera1 camerax cameraxlean)

size() { stat -f%z "$1" 2>/dev/null || stat -c%s "$1"; }
entry() { unzip -l "$1" | awk -v want="$2" '$4 ~ want {s += $1} END {print s + 0}'; }

row() { # label, then one measurement per flavor
  printf '  %-26s' "$1"; shift
  for v in "$@"; do printf ' %10d' "$v"; done
  BASE="$1"; shift
  for v in "$@"; do printf ' %+10d' "$(( v - BASE ))"; done
  echo
}

printf '  %-26s %10s %10s %10s %10s %10s\n' "" camera1 camerax lean "camerax-d" "lean-d"
row "APK on disk (compressed)" "$(size "$(apk camera1)")" "$(size "$(apk camerax)")" \
    "$(size "$(apk cameraxlean)")"
row "classes.dex (uncompressed)" "$(entry "$(apk camera1)" 'classes.*\.dex')" \
    "$(entry "$(apk camerax)" 'classes.*\.dex')" "$(entry "$(apk cameraxlean)" 'classes.*\.dex')"
row "native libs (uncompressed)" "$(entry "$(apk camera1)" '\.so$')" \
    "$(entry "$(apk camerax)" '\.so$')" "$(entry "$(apk cameraxlean)" '\.so$')"
row "resources (entry count)" \
    "$(unzip -l "$(apk camera1)" | awk '$4 ~ /^res\// {n++} END {print n + 0}')" \
    "$(unzip -l "$(apk camerax)" | awk '$4 ~ /^res\// {n++} END {print n + 0}')" \
    "$(unzip -l "$(apk cameraxlean)" | awk '$4 ~ /^res\// {n++} END {print n + 0}')"

echo
echo "AndroidX libraries each flavor ends up carrying (from the .version markers R8 kept)"
for f in "${FLAVORS[@]}"; do
  printf '  %-12s %s\n' "$f" "$(unzip -l "$(apk "$f")" | awk '$4 ~ /^META-INF\/.*\.version$/ {print $4}' \
      | sed 's|META-INF/||; s|\.version$||' | sort | tr '\n' ' ')"
done

echo
echo "native libraries CameraX adds"
diff <(unzip -l "$(apk camera1)" | awk '$4 ~ /\.so$/ {print $4}' | sort) \
     <(unzip -l "$(apk camerax)" | awk '$4 ~ /\.so$/ {print $4}' | sort) | grep '^>' || true
