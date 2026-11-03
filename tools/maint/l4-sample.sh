#!/system/bin/sh
# L4's sampler, which runs ON the device. See docs/maint/BENCHMARKS.md §12.
#
#   sh /data/local/tmp/l4-sample.sh <pid> <seconds> <interval> <slow-every> <out.tsv>
#
# On the device rather than a loop of `adb shell` calls from the host, for the same reason
# the camera spike streams logcat instead of polling it: a per-sample round trip is tens of
# milliseconds of jitter on every row, and a USB link that stalls turns into a gap in the
# series that looks like the app went quiet.
#
# Two sampling rates. The cheap row -- /proc, two file reads -- runs every interval. The
# expensive one -- dumpsys meminfo, which walks the heap and costs ~100 ms -- runs every
# `slow-every` rows, because sampling it at the fast rate would measure the sampler.
PID="$1"
SECONDS_TOTAL="$2"
INTERVAL="$3"
SLOW_EVERY="$4"
OUT="$5"

CLK=$(getconf CLK_TCK 2>/dev/null)
[ -z "$CLK" ] && CLK=100

echo "# clk_tck=$CLK interval=$INTERVAL slow_every=$SLOW_EVERY pid=$PID" > "$OUT"
printf 'uptime_ms\tutime\tstime\tmajflt\tthreads\trss_kb\tjava_heap_kb\tnative_heap_kb\ttotal_pss_kb\tthermal_status\tbattery_level\tbattery_temp\tcharge_uah\tbatt_current_ua\tusb_current_ua\tusb_voltage_uv\n' >> "$OUT"

# Power, two ways, because on a phone that cannot be unplugged neither one alone is enough.
#
# `battery_level` is a whole percent: over a soak of a few minutes it moves one step or
# none, which cannot separate two backends. `charge_counter` is microamp-hours remaining
# and moves continuously, so it is the right source **when the device is actually
# discharging**.
#
# The reference device cannot be: it is held at 100% by the USB cable that carries adb, and
# `battery/current_now` reads 0. What is still true there is that a battery which is full
# and not charging draws nothing, so the **USB input current is the whole phone's draw**.
# That is a different quantity from the app's consumption -- it includes the screen, the
# radios and everything else -- and it is only worth anything as a difference between two
# soaks measured the same way minutes apart. The converter says so in its own warning
# rather than leaving the number to be read as the app's power.
#
# Any node that is missing is left empty. The converter reports a missing node as not
# measured, never as zero.
CHARGE_NODE=""
BATT_CURRENT_NODE=""
for supply in /sys/class/power_supply/battery /sys/class/power_supply/bms; do
  [ -z "$CHARGE_NODE" ] && [ -r "$supply/charge_counter" ] && CHARGE_NODE="$supply/charge_counter"
  [ -z "$BATT_CURRENT_NODE" ] && [ -r "$supply/current_now" ] && BATT_CURRENT_NODE="$supply/current_now"
done
USB_CURRENT_NODE=""
USB_VOLTAGE_NODE=""
[ -r /sys/class/power_supply/usb/current_now ] && USB_CURRENT_NODE=/sys/class/power_supply/usb/current_now
[ -r /sys/class/power_supply/usb/voltage_now ] && USB_VOLTAGE_NODE=/sys/class/power_supply/usb/voltage_now
echo "# charge_node=${CHARGE_NODE:-none} batt_current_node=${BATT_CURRENT_NODE:-none} usb_current_node=${USB_CURRENT_NODE:-none} usb_voltage_node=${USB_VOLTAGE_NODE:-none}" >> "$OUT"

i=0
END=$(( $(date +%s) + SECONDS_TOTAL ))
while [ "$(date +%s)" -lt "$END" ]; do
  # Liveness first: a soak whose process died is not a soak with flat memory.
  if [ ! -d "/proc/$PID" ]; then
    echo "# process $PID is gone at sample $i" >> "$OUT"
    exit 3
  fi

  UPTIME_MS=$(awk '{printf "%d", $1 * 1000}' /proc/uptime)
  # Everything after the ") " that closes the comm field, so a process name containing a
  # space cannot shift every column after it. $1 is then field 3 (state), so proc(N) is
  # positional N-2.
  STAT_REST=$(cat "/proc/$PID/stat")
  STAT_REST=${STAT_REST#*) }
  set -- $STAT_REST
  # Braces are not optional: in POSIX sh `$14` is `$1` followed by a literal 4, which is
  # how the first version of this script recorded the pid with a digit stuck on the end as
  # the CPU time, for every row, without erroring.
  MAJFLT=${10}
  UTIME=${12}
  STIME=${13}
  THREADS=${18}
  RSS=$(awk '/^VmRSS:/ {print $2}' "/proc/$PID/status")

  JAVA=""; NATIVE=""; PSS=""; THERMAL=""; BATT=""; BTEMP=""
  CHARGE=""; BATT_CUR=""; USB_CUR=""; USB_VOLT=""
  if [ $(( i % SLOW_EVERY )) -eq 0 ]; then
    MEM=$(dumpsys meminfo "$PID" 2>/dev/null)
    JAVA=$(echo "$MEM" | awk '/^ *Java Heap:/ {print $3; exit}')
    NATIVE=$(echo "$MEM" | awk '/^ *Native Heap:/ {print $3; exit}')
    PSS=$(echo "$MEM" | awk '/^ *TOTAL PSS:/ {print $3; exit}')
    THERMAL=$(dumpsys thermalservice 2>/dev/null | awk '/^Thermal Status:/ {print $3; exit}')
    BATT=$(dumpsys battery 2>/dev/null | awk '/^ *level:/ {print $2; exit}')
    BTEMP=$(dumpsys battery 2>/dev/null | awk '/^ *temperature:/ {print $2; exit}')
    [ -n "$CHARGE_NODE" ] && CHARGE=$(cat "$CHARGE_NODE" 2>/dev/null)
    [ -n "$BATT_CURRENT_NODE" ] && BATT_CUR=$(cat "$BATT_CURRENT_NODE" 2>/dev/null)
    [ -n "$USB_CURRENT_NODE" ] && USB_CUR=$(cat "$USB_CURRENT_NODE" 2>/dev/null)
    [ -n "$USB_VOLTAGE_NODE" ] && USB_VOLT=$(cat "$USB_VOLTAGE_NODE" 2>/dev/null)
  fi

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$UPTIME_MS" "$UTIME" "$STIME" "$MAJFLT" "$THREADS" "$RSS" \
      "$JAVA" "$NATIVE" "$PSS" "$THERMAL" "$BATT" "$BTEMP" \
      "$CHARGE" "$BATT_CUR" "$USB_CUR" "$USB_VOLT" >> "$OUT"

  i=$(( i + 1 ))
  sleep "$INTERVAL"
done
echo "# finished after $i samples" >> "$OUT"
