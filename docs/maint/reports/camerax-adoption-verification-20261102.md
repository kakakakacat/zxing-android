# Phase 6 adoption — what was verified, on what, 2026-11-02

The second Phase 6 checkbox asks for preview, ROI, result points and screen-coordinate
mapping on a device, plus the 100x open/close stability loop and the 21 instrumentation
tests, before the Camera1 path stops being the default. This is what that produced.

Driven through :app, which gained a `preferences_camerax` switch for exactly this purpose,
so the thing under test is `ScannerView` on the real backend and not a spike harness.

## Devices

- **DN-ENG41** — Android 14, API 34, no GMS, 640x960 screen, two cameras. The phone every
  other measurement in this repository comes from.
- **Android 16 emulator** (`zxing_api36`), virtual-scene camera. Timings from it remain
  inadmissible under BENCHMARKS.md 10.7; nothing here is a timing.
- **Mix5** (Xiaomi 25010PN30C, Android 15) — **not reached.** Its control channel answered
  `{"live":false}` all through this session, so Android 15 is unverified for the adoption.

## Results

| | DN-ENG41 (API 34) | emulator (API 36) |
| --- | --- | --- |
| Preview appears, live | yes | yes |
| Viewfinder rectangle and laser animation | yes | yes |
| Viewfinder rectangle identical to the Camera1 path | yes, same position and size | not compared |
| A real barcode decoded off the camera | **not possible, no barcode in front of it** | yes, QR_CODE |
| Thumbnail and result points on the result screen | — | yes |
| 100x open/close stability loop | 0 fatals, 0 camera errors | 0 fatals, 0 camera errors |
| 21 :scanner instrumentation tests | 21/21 | not run |

Evidence:

- `camerax-adoption-viewfinder-camerax-DN-ENG41-20261102.png` and
  `camerax-adoption-viewfinder-camera1-DN-ENG41-20261102.png` — the same scene through both
  backends, minutes apart. The rectangle lands in the same place, which is the
  screen-coordinate mapping holding across a different camera stack.
- `camerax-adoption-decode-api36-20261102.png` — the result screen after a QR decoded off
  the virtual-scene camera: format, type, thumbnail, and result points drawn on it. That
  exercises the whole path, including `YPlaneLuminanceSource.detach()`, which is what
  renders a thumbnail from a frame that was read in place.
- `stability-100-camerax-DN-ENG41-20261102.log`,
  `stability-100-camerax-api36-20261102.log`.
- `instrumentation-21-camerax-DN-ENG41-20261102.xml`.
- `camerax-adoption-apk-size-20261102.log`.

## What the stability loop found, which is why it is run

The first 100-cycle run on the emulator crashed once, in `onPause`:

```
java.lang.IllegalStateException: State must be at least CREATED to move to DESTROYED,
  but was INITIALIZED in component CameraXBackend$ScannerLifecycleOwner
```

Pausing the host while `ProcessCameraProvider.getInstance` was still resolving left the
backend's `LifecycleRegistry` at `INITIALIZED`, and `LifecycleRegistry` refuses to go
straight from there to `DESTROYED`. It is a once-in-a-hundred race and it took the app down.
Fixed by stepping through `CREATED` first; both loops above are after that fix.

Worth stating plainly: a single launch, and a hundred of them, are not the same test. This
one would have shipped.

## One more hole, found by reading rather than running

Not a crash, and nothing observed it — it was found going back over the teardown path after
the loops were green, and it is recorded because the failure mode is silent.

`stopScanning` tells the backend to stop delivering and then posts `MSG_QUIT` to the decode
thread. A frame the analyzer had already claimed can be posted *between* those two, landing
behind `MSG_QUIT` in the queue — and `Looper.quit()` discards it unhandled, taking the
`ImageProxy` it carries with it. One leaked buffer slot at teardown, with the camera about
to be released anyway, so nothing would ever have shown it. Fixed both ways: `quitSafely`
delivers the straggler, and the decode handler now closes any frame that reaches it after
it has stopped running. Re-ran the 100-cycle loop and the 21 instrumentation tests on
DN-ENG41 afterwards; both still clean.

## What is still not verified

- **Android 15**, and this is the gap that matters most: the two devices this project owns
  already disagreed once about a Phase 6 result taken on one of them.
- **A decode off real camera hardware.** The emulator proves the frame path end to end; it
  does not prove it against a real sensor's strides and timing. The spike did decode a real
  QR out of a real `ImageProxy` on DN-ENG41 (`camera-spike-decode-api36-20261102.log` is the
  emulator one; the device runs are in `camera-spike-*-DN-ENG41-20261102.log`), but that was
  the spike's own binding, not `ScannerView`'s.
- **The torch and auto-focus on the CameraX path.** DN-ENG41 reports no flash unit and
  auto-focus OFF only, so `setTorch` is a no-op there by construction. Untested anywhere.
- **Anything non-Qualcomm, low-end-with-a-modern-OS, or 16 KB pages.**
