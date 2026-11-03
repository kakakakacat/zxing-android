# Camera1's synchronous open, removed — what L3 says before and after

2026-11-03, DN-ENG41, Android 14 / API 34, clocks locked, sustained performance mode off
(recorded as a warning in both result files, as it was in the baseline). Commit under test
`aff9b8105`.

Result files: `l3-camera1-DN-ENG41-20261103-async.json`,
`l3-camerax-DN-ENG41-20261103-async.json`. A first pair was measured the same hour and its
`benchmarkData.json` files were overwritten by the second arm before they were converted;
its medians survive in `l3-async-open-pair1-DN-ENG41-20261103.log` and are quoted below
only as a reproduction, never as a result.

## What was changed

`ScannerView.surfaceCreated` runs on the main thread and `Camera1Backend.open` called
`CameraManager.openDriver` straight from it. `l3-verdict-DN-ENG41-20261102.md` measured
that span at 401–436 ms and said in so many words that **the shipping Camera1 path blocks
the main thread for roughly 400 ms at startup**, whatever is decided about backends.

It now runs on a serial worker and answers through the callback the interface always
specified. Two things had to move with it or the stall would only have changed its name:
every `CameraManager` accessor is `@Synchronized`, so main-thread `stopPreview`,
`closeDriver` and `isOpen` would have waited out the open on that monitor; and
`ViewfinderView.onDraw` reads the scan rectangle through the same monitor, so the camera is
now handed to the viewfinder in `onCameraReady` rather than in `resume()`.

## The measurement, and why it is a comparison against CameraX

**CameraX is unchanged by this commit, so it is the control.** That matters because
`BENCHMARKS.md` §2 forbids quoting a cross-session difference, and the 2026-11-02 baseline
is a different session on a device that drifts. What survives that rule is the *within-session*
difference between the two backends, which both sessions measured.

Median milliseconds with the 95% interval on the median.

| | camera1 | camerax | camera1 − camerax |
| --- | --- | --- | --- |
| **2026-11-02 pair 1** | 1747 [1695, 1803] | 1398 [1192, 1471] | **+349** |
| **2026-11-02 pair 2** | 1811 [1741, 2191] | 1492 [1458, 1518] | **+319** |
| **2026-11-03 pair 1** (log only) | 1117 | 1477 | **−360** |
| **2026-11-03 pair 2** (result files) | 1170 [1086, 1479] | 1461 [1120, 1474] | **−291** |

`startup_to_initial_display`, which is the metric the block was costing.

**The sign has reversed.** Camera1 went from reaching a drawn window ~330 ms *after*
CameraX, twice, to reaching it ~300 ms *before*, twice. CameraX's own value sits at
1461–1477 against the baseline's 1398 and 1492, which is the control doing its job: the
device is comparable and the thing that moved is the arm that was changed.

**One qualification, stated rather than buried.** In the baseline the intervals did not
overlap in either pair. In 2026-11-03 pair 2 they do — camera1 [1086, 1479] against camerax
[1120, 1474] — because camera1's spread widened. So the direction and the magnitude
reproduce across two pairs, and the second pair's intervals do not separate them. This is a
real effect of consistent size, not a clean non-overlap, and the same words the L3 baseline
used for its own +200 ms finding apply here.

## The other three cases

From the 2026-11-03 result files, both arms in one session.

| | camera1 | camerax |
| --- | --- | --- |
| `camera_open_to_ready` | 499 [486, 530] | 425 [388, 446] |
| `ready_to_first_frame` | 921 [858, 1437] | 1073 [819, 1120] |
| `open_to_first_frame` | 1483 [1347, 1981] | 1493 [1236, 1562] |

**The open span itself got longer, and that is expected rather than a regression.** It read
401–436 ms in the baseline and 499 here. The work is the same work; it now runs on a worker
competing with the main thread's own startup, and the span closes after a hop back to the
main thread. What changed is that nothing waits on it: 100 ms added to a span nobody blocks
on, against ~300 ms taken off the window's first frame.

`ready_to_first_frame` is unmoved — 921 against the baseline's 912 and 923 — so the change
did not cost a frame anywhere.

## Correctness, before any of the above was believed

- **21/21 `:scanner` instrumentation tests**, on DN-ENG41, DN-ENG81 and the API 36 emulator.
- **100 open/close cycles on the Camera1 backend**: 0 fatal exceptions, 0 camera errors —
  `stability-100-camera1-async-DN-ENG41-20261103.log`. This is the loop that found the
  CameraX `LifecycleRegistry` race at one-in-a-hundred, and the pause-during-open path this
  change introduces is the same shape of race.
- **A real Code 128 decoded off a second device's screen, 4/4 on each backend** —
  `real-barcode-camera1-async-DN-ENG41-20261103.txt`.
- Both result files record `observed_in_trace` matching `claimed`, so neither arm is the
  other backend wearing its name.

## What this does to the CameraX-default question

It removes CameraX's only measured advantage on this device. The Phase 6 record's remaining
argument for flipping the default was maintenance and deprecation; the argument against was
L4's ~60% more CPU to keep scanning. Startup latency had been the one number pointing the
other way, and it now points at Camera1.

## Not measured

- **Any other device.** One phone, one Android version, same as every other L3 number here.
- **The pause-during-open path under load.** It is exercised by the 100-cycle loop and by
  every `surfaceDestroyed`, but no test drives a pause *during* the open window on purpose.
- **Whether the main thread is actually free during the open.** The improvement in
  `startup_to_initial_display` is consistent with the block being gone and with the code
  reading that way, which is the same standard of evidence the baseline used for the
  original finding. Nothing here profiles the main thread directly.
- **`ready_to_first_scan`**, still absent: §11's fixed scene does not exist.
