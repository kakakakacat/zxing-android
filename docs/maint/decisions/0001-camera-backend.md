# 0001 — The camera backend: Camera1, Camera2 or CameraX

**Status:** proposed. One question is the user's to answer — see "The decision this needs".
**Date:** 2026-11-02
**Phase:** 6. Also unblocks Phase 5 item 8, which has been waiting on it.

Everything below that is a number was measured here, on the devices named, by the scripts
named. Where something was not measured, it says so instead of arguing.

---

## The short version

**CameraX wins on every dimension that was measured**, and the one it loses — APK size —
is mostly the fault of a single optional artifact that this project does not need.

| | Camera1 (today) | CameraX | CameraX without `camera-view` |
| --- | --- | --- | --- |
| Cold launch to first decodable frame, DN-ENG41 | **860 ms** | **705 ms** | **726 ms** |
| Release APK, R8 on, two ABIs | 1,608,303 B | +558,174 B (+34.7%) | +207,382 B (+12.9%) |
| AndroidX libraries dragged in | — | AppCompat, fragment, activity, viewpager, … | none beyond lifecycle |
| Resource entries added | — | 273 | 0 |
| Camera code this project maintains | 1,285 lines | the binding only | the binding plus a `SurfaceRequest` |
| Minimum SDK | 21 | 23 (CameraX ≥ 1.5.0) | 23 |

The recommendation is **CameraX without `camera-view`**, with
`setAvailableCamerasLimiter(DEFAULT_BACK_CAMERA)` set, at **minSdk 23**.

---

## How these numbers were taken

`benchmark/camera-spike` is an application module with three product flavors over one
activity. Each flavor compiles in exactly one camera backend; everything else — the
decoder, the ABI filter, the requested resolution, the scan rectangle, R8 and resource
shrinking — is identical. So the difference between two flavors is the camera stack and
nothing else.

- `tools/maint/camera-spike-run.sh <flavor> [runs] [serial]` launches a flavor N times,
  genuinely cold each time (force-stop, 2 s for the HAL to release, fresh process), and
  reports the median of the lines the activity prints.
- `tools/maint/camera-spike-size.sh` builds all three at release and prints the size table.

**This is a spike, not a benchmark layer.** It does not lock clocks, does not wait for a
thermal floor and does not produce a result envelope, so under `BENCHMARKS.md` §2 none of
these timings is admissible as an L-layer result and none of them may be quoted as one. What
it is good for is the thing it was built for: the same N launches of APKs that differ in one
dimension, on one device, within minutes of each other. The size figures are not timings and
carry none of that caveat.

"First frame" throughout means the first frame delivered **to this process**, which is what a
decoder waits for. It is not the first frame drawn on screen, and the two are not the same.

---

## What was measured

### 1. Time to a decodable frame

DN-ENG41 (Android 14, two cameras), 15 cold launches per flavor, device idle on AC, clocks
**not** locked. Median, with the full range in brackets.

| | camera1 | camerax | camerax + limiter | cameraxlean + limiter |
| --- | --- | --- | --- | --- |
| process start → first frame | 860 (839–911) | 705 (688–743) | 698 (671–759) | 726 (710–780) |
| `onCreate` → first frame | 810 (788–857) | 652 (635–689) | 646 (620–698) | 673 (657–717) |
| camera open → first frame | 759 (738–806) | 640 (622–676) | 631 (605–680) | 659 (642–701) |

Logs: `reports/camera-spike-camera1-DN-ENG41-20261102.log`,
`reports/camera-spike-camerax-DN-ENG41-20261102.log`,
`reports/camera-spike-camerax-backcameraonly-DN-ENG41-20261102.log`,
`reports/camera-spike-cameraxlean-DN-ENG41-20261102.log`.

**The ranges do not overlap**: Camera1's best launch of fifteen is slower than CameraX's
worst. On the `onCreate` measurement, which is the fairest of the three because both clocks
start in the same place, CameraX is **158 ms (20%) faster**.

Two honest qualifications:

- **The "camera open → first frame" row flatters Camera1.** Camera1 cannot be opened until
  there is a surface to draw into, so its clock starts at `surfaceCreated` — after a wait
  that the row does not count. CameraX's starts at the first line of its own setup. Camera1
  still loses.
- **This is one device.** Every number above belongs to DN-ENG41, which is also the only
  physical device this project has. See "What this does not settle".

### 2. APK size

Release build, R8 and resource shrinking on, ABI filter `arm64-v8a` + `armeabi-v7a`, the
same settings `:app` uses. `reports/camera-spike-apk-size-20261102.log`.

| | camera1 | camerax | cameraxlean | camerax Δ | lean Δ |
| --- | --- | --- | --- | --- | --- |
| APK on disk | 1,608,303 | 2,166,477 | 1,815,685 | +558,174 | +207,382 |
| `classes.dex`, uncompressed | 265,508 | 841,288 | 643,316 | +575,780 | +377,808 |
| native libraries, uncompressed | 3,193,040 | 3,250,700 | 3,250,700 | +57,660 | +57,660 |
| `res/` entries | 0 | 273 | 0 | +273 | 0 |

**Nearly two thirds of CameraX's cost is `camera-view`, and it is avoidable.** `camera-view`
1.4.2 depends on `androidx.appcompat:appcompat` and on `camera-video`; through AppCompat the
APK gains fragment, activity, viewpager, drawerlayout, loader, savedstate, cursoradapter,
customview and both vectordrawable artifacts, plus 273 resources. The `cameraxlean` flavor
drops `camera-view`, hands CameraX the `SurfaceView` this project already draws into and
answers the `SurfaceRequest` itself. That is **350,792 bytes back**, and it previews and
decodes — `reports/camera-spike-cameraxlean-preview-api36.png`.

What the lean path gives up is real and is not nothing: `PreviewView` also does the
scale/crop transform between the preview's aspect ratio and the view's, the display-rotation
transform, and `getOutputTransform` for an overlay drawn on top. This project already owns
the last of those (`PreviewCoordinateMapper`, written for defect A-13), which is why the
trade is worth taking here and would not be in a greenfield app.

CameraX also ships two native libraries of its own, `libimage_processing_util_jni.so` and
`libsurface_util_jni.so`. The 57,660 bytes above are the two ABIs this project filters to; an
app that does not filter gets x86 and x86_64 copies too, a further 95,036 bytes.

### 3. A trap worth knowing about: CameraX validates cameras it will never use

On the Android 16 emulator, CameraX took **5,987 ms** (median of 3) to reach its first
frame — seven times slower than Camera1 on the same emulator, and the opposite of the
ordering on real hardware. The cause is in the log, verbatim in
`reports/camera-spike-camerax-validator-api36-20261102.log`:

```
E/CameraValidator: Camera LensFacing verification failed, existing cameras: [Camera@…[id=10]]
W/CameraX: Retry init. Start time 14706507 current time 14707029
W/CameraX: Expected camera missing from device.
```

CameraX checks that **both** lens facings exist and, when one does not, retries
initialisation for about five seconds before carrying on. That AVD is configured
`hw.camera.front=none` and `dumpsys media.camera` reports one camera.

One line fixes it — `CameraXConfig` with
`setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)`, which a barcode scanner
wants anyway:

| | default | with the limiter |
| --- | --- | --- |
| Android 16 emulator, 1 camera | 5,987 ms | **114 ms** |
| DN-ENG41, 2 cameras | 705 ms | 698 ms |

So it costs nothing where both cameras exist and saves five seconds where one is missing.
**A single-camera device is not exotic** — kiosk hardware, scanners, cheap tablets — and this
is exactly the class of surprise that only shows up on a device nobody tested on. The
limiter is part of the recommendation, not an optimisation to consider later.

(The emulator timings themselves remain inadmissible under §10.7. What is admissible here is
the ratio within one device and the log line naming the cause.)

### 4. `YPlaneLuminanceSource` works against a real `ImageProxy`

Phase 5 item 8 built `YPlaneLuminanceSource` *for* CameraX and could never run it against
CameraX, so its 18 unit tests all feed it synthetic planes. The spike closed that:

- A real QR code decoded out of a real camera frame, through the luma plane read in place,
  on both CameraX flavors: `found=true`, with `rowStride=1920 pixelStride=1` on the device
  and `rowStride=1280 pixelStride=1` on the emulator.
  `reports/camera-spike-decode-api36-20261102.log`.
- No copy, and the `ImageProxy` is closed only after the decode returns, which is the
  ownership rule that class documents.

That is the structural claim of item 8 shown working end to end for the first time.

### 5. What the reference device actually is

`dumpsys media.camera` on DN-ENG41: two cameras, HAL `device@3.5`, and
`android.info.supportedHardwareLevel = 3` — `LEVEL_3` — on **both**. So Camera2 and CameraX
run natively here and not through the `LEGACY` compatibility shim, and nothing in the
latency numbers above is a shim artifact.

Worth recording while the dump was open, because it bears on an earlier defect: the back
camera reports `Has a flash unit: false` and `android.control.afAvailableModes = [0]`, which
is auto-focus OFF only. This device has no torch and no auto-focus. A-12's torch work and
`AutoFocusManager` are therefore both untested on the only physical device available.

---

## What was *not* measured, and why

**Camera2 directly was not prototyped.** No number here compares it. The argument against it
is not performance — it would very likely match CameraX, since CameraX is a layer over it —
but maintenance, and the honest form of that argument is this: CameraX ships a
`CameraQuirks` database of per-device workarounds, and adopting Camera2 directly means
rediscovering the entries that matter on hardware this project does not own. This project has
**one** physical device. That is the whole case, and it is a judgement rather than a
measurement; anyone who disagrees should build a fourth flavor rather than argue.

**Nothing on a second physical device, or on a non-Qualcomm one.** Same gap as every other
measurement in this repository.

**No sustained-scanning comparison.** The spike measures the first frame of a cold start and
stops. Whether CameraX's steady-state frame delivery costs more or less CPU than Camera1's is
an L4 question and L4 does not exist.

**Preview appearance on the lean path.** The scale/crop transform `PreviewView` does was
deliberately not reimplemented in the spike, so its preview may be stretched. Nothing here
measures what the preview looks like; adopting the lean path means writing that transform.

---

## The decision this needs: minSdk 21 or 23

This is the one thing that is not a technical matter and is the user's to settle.

`:scanner`, `:android-integration` and `:app` all declare **minSdk 21**. CameraX's floor:

| camera-core | minSdk | released |
| --- | --- | --- |
| 1.3.4 | 14 | — |
| 1.4.0, 1.4.1, **1.4.2** | **21** | 1.4.2 on **2025-03-26** |
| 1.5.0 | **23** | 2025-09-10 |
| 1.6.2 (current stable) | 23 | 2026-08-26 |
| 1.7.0-rc01 | 23 | 2026-10-21 |

(Read from each artifact's `AndroidManifest.xml` and the POM's `Last-Modified` on Google's
Maven, 2026-11-02.)

So the two options are:

1. **Raise minSdk to 23** and track current CameraX. Drops Android 5.0 and 5.1.
2. **Stay at minSdk 21** and pin CameraX 1.4.2, whose last release was **19 months ago** and
   which will not receive the quirk entries for hardware that ships from here on — which is
   most of the reason to adopt CameraX at all.

The spike is built against 1.4.2, so every number in this document is from the *older* line;
option 1 is not worse than what was measured.

**No distribution data was gathered**, deliberately: this file does not quote a percentage
for Android 5.x that it cannot verify. What the project's own constraints say is that the
only physical device here is API 34 and the two emulators are 35 and 36, so nothing below 23
is being tested on anything today either.

---

## Recommendation

1. Adopt **CameraX**, at **minSdk 23**, current stable line.
2. Do **not** take `camera-view`. Drive a `SurfaceView` through `Preview.setSurfaceProvider`
   and keep `PreviewCoordinateMapper` for the overlay mapping. Saves 350,792 bytes of the
   558,174-byte bill and every AppCompat transitive with it.
3. Configure `setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)` before the
   first `getInstance`. Free on a two-camera device, five seconds on a one-camera device.
4. Feed `ImageAnalysis` frames to `YPlaneLuminanceSource` as the spike does, closing the
   `ImageProxy` only after the decode returns. This finishes Phase 5 item 8.
5. Keep the Camera1 stack reachable for one release rather than deleting it, so a device that
   CameraX handles badly has somewhere to fall back to — the same shape as
   `ScannerOptions.setDecoderBackend`, which is already how the decoder backends are
   selected.

**If adopted, the second Phase 6 checkbox still has to be earned**: preview, ROI, result
points and screen-coordinate mapping verified on a device, plus the 100× open/close stability
loop and the 21 instrumentation tests, before the Camera1 path stops being the default.
