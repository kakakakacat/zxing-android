# Phase 6 on Android 15 — what the second device verified, and the defect it found

2026-11-03, Xiaomi 25010PN30C (`xuanyuan`, "mix5"), **Android 15 / API 35**, HyperOS
`OS2.0.207.0.VOACNXM`, SM8550, arm64-v8a, 4 KB pages, five cameras, thermal status **0**
at rest. Reached over the private root-agent control channel; no ADB listener was opened.

This closes the gap `camerax-adoption-verification-20261102.md` named as mattering most:
that record's Android 15 column read *not reached*. It is also the first torch
verification anywhere in this project, and that is where it found something.

**The build under test was the `benchmark` variant** — R8, resource shrinking, not
debuggable, debug-signed — not the debug build the DN-ENG41 column used. So this doubles
as the R8 check on Android 15. It also means `Log.i` is stripped
(`proguard-android-optimize.txt`), which is why every row below is evidence from outside
the app: the camera service, the kernel's own counters, the framebuffer.

## Results

| | mix5 (API 35), CameraX | mix5 (API 35), Camera1 |
| --- | --- | --- |
| App runs, preview appears | yes | yes |
| Camera actually opened | `dumpsys media.camera`: client, **Device status ACTIVE** | same |
| Which stack really ran | 15 CameraX threads (`CameraX-*`, `CXCP-*`) | none of them; HAL `device@1.1`, `preview-format: yuv420sp` — the API1 shim |
| Stream the camera was asked for | 1920x1080 | 2400x1080 |
| Viewfinder rectangle | laser rows **556–558**, x **642–1838** | **identical, to the pixel** |
| Frames reach the decoder | `zxing-decode-1` burned **0.71 s of CPU in 10 s** | — |
| 100x open/close | 0 fatals, 0 camera errors, awake throughout | 0 fatals, 0 camera errors |
| 21 `:scanner` instrumentation tests | **21/21**, including 12 native-decoder tests | — |
| Torch, `FrontLightMode.AUTO` | works: `flash.mode=TORCH` in a dark room | — |
| Torch, `FrontLightMode.ON` | **did nothing** — see below | works: `FLASH_MODE_TORCH` |

Evidence: `stability-100-camerax-mix5-20261103.log`,
`stability-100-camera1-mix5-20261103.log`, `instrumentation-21-camerax-mix5-20261103.log`,
`torch-frontlight-on-mix5-20261103.png`.

## The viewfinder rectangle, measured rather than eyeballed

The preview is a dark room, so the framing rectangle's border is invisible and "it looks
the same" would be worth nothing. The laser spans the rectangle's full width at its
vertical centre, so its bounding box **is** the rectangle's geometry. Red pixels, both
screenshots:

```
camera1: rows 556,557,558   x 642..1838   (1197 px per row)
camerax: rows 556,557,558   x 642..1838   (1197 px per row)
```

Identical, although the two backends asked the camera for **different stream sizes**
(2400x1080 against 1920x1080). That is `setFixedSize` plus `PreviewCoordinateMapper`
doing exactly what the decision record says they do, now on a second device and a
different Android version.

The two captures are not the same capture: 2,040,369 of 2,767,680 pixels differ, which is
sensor noise in a dark frame — and is itself the evidence that **both previews were live**
rather than a frozen surface.

## The defect: `FrontLightMode.ON` was silently a no-op on CameraX

With the same preference set to `ON`:

| | `android.flash.mode` | preview mean luminance |
| --- | --- | --- |
| Camera1 | `TORCH` | 2.2 → **37.3** |
| CameraX | **`OFF`** | 2.2 → **2.0** |

Then, to find out whether `CameraXBackend.setTorch` was broken or never called, the same
backend with `AUTO` in the same dark room:

| | `android.flash.mode` | preview mean |
| --- | --- | --- |
| CameraX, `AUTO` | **`TORCH`** | **36.4** |

So the backend's torch works and nothing was calling it. **The cause:** `ON` was
implemented in `CameraConfigurationManager.initializeTorch`, inside the Camera1 parameter
configuration, which the CameraX backend never goes through. `AUTO` worked because
`AmbientLightManager` drives it through `ScannerCamera.setTorch`, which both backends
implement.

**The fix** puts the rule in `ScannerView`, in the open callback both backends pass
through, rather than in a second backend-specific place. It costs the Camera1 path
nothing: `CameraManager.setTorch` only acts when the requested state differs from the
camera's current one, and on that path the parameters already carry it — so the call is a
no-op there and no preview restart is added to a path this project has kept unchanged on
purpose.

Re-verified on the device, both backends, after the fix:

| | `android.flash.mode` | preview mean |
| --- | --- | --- |
| CameraX, `ON` | `TORCH` | **34.4** |
| Camera1, `ON` | `TORCH` | **35.2** |

Worth saying plainly: this would have shipped. A setting that silently does nothing
produces no crash, no log line and no error — and on DN-ENG41, which reports no flash
unit, no test anywhere could have caught it. It took the second device.

## Three traps, each of which cost something here

- **`{"live":false}` from the control channel is idle, not down.** The `ctl` client
  creates a session lazily on the first real request and releases it after a minute so the
  phone's web UI can take over. The previous session read that reply as "the device is
  unreachable" and recorded Android 15 as unverified for the whole day. One `/exec` brings
  it up; the first one tried here worked.
- **`svc power stayon true` does nothing on a phone that is not plugged in**, and this one
  is not — `AC powered: false`, battery 100%, status discharging. That flag is
  `STAY_ON_WHILE_PLUGGED_IN` and nothing else. The runbook has advised it since the spike
  runs. What actually keeps a loop awake is its own `input keyevent` resetting the idle
  timer, so `stability-loop-ondevice.sh` now **checks and reports** wakefulness at the
  start and the end instead of asking the caller to arrange it: a loop that cycles a
  stopped activity reports a clean hundred of nothing.
- **A reinstall re-arms the HyperOS appops revocation.** After `pm install -r -g`,
  `cmd appops get <pkg> CAMERA` read `Uid mode: CAMERA: ignore` and the camera never
  opened — resumed activity, live process, `Active Camera Clients: []`. `cmd appops set
  <pkg> CAMERA allow` has to be repeated after **every** install, not once per device.

## What this still does not verify

- **A real barcode decoded off this camera.** Nothing was in front of it; the phone sits
  face down in a dark room. The decode path is exercised end to end — frames arrive and
  are decoded, 0.71 s of decode CPU per 10 s of scanning — but nothing decoded a symbol.
- **Any timing.** This device has no clock locking and is reached over a link that drops;
  BENCHMARKS.md §6 keeps it out of every L-layer. Nothing here is a number about speed.
- **16 KB pages**, non-Qualcomm hardware, and anything low-end with a modern OS.
