# L3 baseline — DN-ENG41, both camera backends, 2026-11-02

The first L3 numbers this project has. Two pairs, 12 cold launches per backend per pair,
each pair measured back to back in one session per the same-session rule everything else
here obeys. Clocks locked (`cpuLocked: true`); sustained performance mode off, which the
result files record as a warning.

Result files: `l3-{camera1,camerax}-DN-ENG41-20261102-run{1,2}.json`.

## What was measured

| | camera1 pair 1 | camerax pair 1 | camera1 pair 2 | camerax pair 2 |
| --- | --- | --- | --- | --- |
| `startup_to_initial_display` | 1747 [1695, 1803] | **1398** [1192, 1471] | 1811 [1741, 2191] | **1492** [1458, 1518] |
| `camera_open_to_ready` | 436 [380, 450] | 467 [460, 495] | 401 [354, 423] | 416 [390, 429] |
| `ready_to_first_frame` | 912 [858, 952] | 1134 [922, 1148] | 923 [878, 1107] | 1118 [1039, 1163] |
| `open_to_first_frame` | **1332** [1286, 1388] | 1532 [1431, 1629] | **1341** [1257, 1520] | 1541 [1452, 1592] |
| `ready_to_first_scan` | absent | absent | absent | absent |

Median milliseconds, with the 95% interval on the median in brackets. Lower is better.

## Two findings, both reproduced across the pairs

**1. CameraX puts the UI on screen about 330 ms sooner** — 349 ms in pair 1, 319 ms in
pair 2, with intervals that do not overlap either time.

There is a mechanical explanation and it is visible in the code rather than inferred from
the numbers: `ScannerView.surfaceCreated` runs on the main thread and `Camera1Backend.open`
calls `CameraManager.openDriver` **synchronously** from it. So the Camera1 path blocks the
main thread for the whole of `camera_open_to_ready`, measured here at 401–436 ms, and the
window cannot draw until it returns. CameraX's open is asynchronous and does not.

The two facts are independent — one from reading the code, one from the trace — and they
agree in magnitude. That is as close to a cause as this harness can get; nothing here
profiles the main thread directly, so it is stated as consistent-with rather than proven.

Worth saying plainly whatever is decided about backends: **the shipping Camera1 path blocks
the main thread for roughly 400 ms at startup.** That is a jank and ANR smell independent
of which backend wins on latency.

**2. CameraX is about 200 ms slower from asking for a camera to a frame at the decoder** —
+199 ms in pair 1, +200 ms in pair 2. The magnitude is strikingly stable; the intervals do
not overlap in pair 1 and do overlap slightly in pair 2, so this is a real effect of
consistent size rather than a clean non-overlap.

Its two halves localise it: `camera_open_to_ready` differs by only +31 and +15 ms, and
`ready_to_first_frame` by +222 and +195 ms. The time goes between CameraX reporting its use
cases bound and the first `ImageProxy` arriving. Not investigated further here.

## This does not agree with the Phase 6 spike, and that is the point of recording it

`docs/maint/decisions/0001-camera-backend.md` has CameraX reaching a decodable frame
**273 ms sooner** than Camera1 on this same device. L3 measures CameraX **200 ms slower**
to a frame — and 330 ms *faster* to a drawn window.

These are not necessarily contradictory; they are different intervals. The spike timed
`onCreate` → first frame, which contains both effects, and on its numbers the startup win
dominated. L3 separates them and they have opposite signs. Other differences that were not
controlled for: the spike runs CameraX **1.4.2** and `:scanner` now ships **1.6.2**, and the
spike's harness is one activity while this drives the whole app.

**Nothing here supersedes the spike's tables**, and the decision record's reasoning does not
rest on latency anyway — it rests on maintenance and deprecation, precisely because two
devices already disagreed about the latency once. This is a third reading that disagrees
again, from a different interval, and it belongs on the record for that reason.

## What this harness still cannot answer

**Launch → first frame at the decoder, end to end.** The obvious number, and it is not
derivable from these spans: `camera_open_to_ready` begins at `surfaceCreated`, which on the
Camera1 path is *before* the window's first frame, so the startup metric and the camera
spans overlap by an unknown amount and must not be added. `open_to_first_frame` is derived
by summing only because its two halves are sequential by construction.

Getting it would mean the app calling `reportFullyDrawn()` when the scanner goes live,
which needs `ScannerView` to tell its host that frames have started — a new callback on a
published `Listener` interface. That is a library API decision, not a measurement one, and
it is left open rather than taken quietly.

**`ready_to_first_scan`.** Nothing was in front of the lens. §11's scan target needs a
fixed, photographed scene and no run has built one. Reported as `null`, never zero.

**One device, one Android version.** Same caveat as everything else here.
