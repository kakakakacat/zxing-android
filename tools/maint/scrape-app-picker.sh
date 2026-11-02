#!/bin/sh
# Scrape every row of the "share an application" picker on a connected device.
#
# Exists because defect A-10 is a *list* bug: the fix changes which apps the picker can
# see, and the only honest way to say so is to read every row before and after. Two
# traps, both hit while measuring this, both of them silent:
#
#   - a fast fling skips whole screens between dumps, so the scrape misses rows and the
#     count comes out low. The swipe below is a slow 300 px step for that reason.
#   - two scrapes running at once overwrite each other's scratch file and produce a
#     plausible-looking list that is neither run. Hence mktemp, and one at a time.
#
# usage: tools/maint/scrape-app-picker.sh <adb-serial> <output-file>
set -eu

serial=$1
out=$2
pkg=io.github.kakakakacat.zxing.android
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

dump() {
  adb -s "$serial" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb -s "$serial" shell cat /sdcard/ui.xml 2>/dev/null
}

# The dump is one very long line, so everything here works on it with grep -o rather
# than by splitting it. `tr '>' '>\n'` does NOT split it -- tr pads the shorter set, it
# does not expand one character into two -- and an earlier version of this script quietly
# relied on that non-split, took the first bounds in the document (the root node's) and
# tapped the middle of the screen.
labels() {
  grep -o 'text="[^"]*"' | grep -v 'text=""' | sed 's/text="//;s/"$//'
}

adb -s "$serial" shell "am force-stop $pkg" >/dev/null 2>&1
adb -s "$serial" shell "am start -a com.google.zxing.client.android.SHARE" >/dev/null 2>&1
sleep 2

# Tap the Application button where it actually is, rather than at coordinates that only
# suit one screen size.
bounds=$(dump | grep -o '<node[^>]*share_app_button[^>]*>' \
  | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1)
if [ -z "$bounds" ]; then
  echo "share_app_button not found; is the SHARE screen up?" >&2
  exit 1
fi
# `set --` rather than cut: tr leaves a leading separator, so cut -f1 comes back empty.
set -- $(echo "$bounds" | tr -cs '0-9' ' ')
x1=$1; y1=$2; x2=$3; y2=$4
tap_x=$(( (x1 + x2) / 2 ))
tap_y=$(( (y1 + y2) / 2 ))
adb -s "$serial" shell input tap "$tap_x" "$tap_y" >/dev/null 2>&1
sleep 5

# The picker is a different screen from SHARE; if the tap missed, the scrape below would
# happily return the SHARE screen's own seven labels and call it an app list.
if ! dump | grep -q 'app_picker_list_item_label'; then
  echo "the app picker did not open (tapped $tap_x,$tap_y)" >&2
  exit 1
fi

stall=0
i=0
while [ $i -lt 60 ]; do
  i=$((i + 1))
  before=$(sort -u "$tmp" | wc -l)
  dump | labels >> "$tmp"
  after=$(sort -u "$tmp" | wc -l)
  if [ "$before" = "$after" ]; then stall=$((stall + 1)); else stall=0; fi
  [ $stall -ge 4 ] && break
  adb -s "$serial" shell input swipe 320 800 320 500 500 >/dev/null 2>&1
  sleep 1
done

sort -u "$tmp" | grep -v '^Applications$' | tr -d '\r' > "$out"
echo "rows: $(wc -l < "$out")"
