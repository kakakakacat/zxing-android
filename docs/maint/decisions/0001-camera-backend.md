# 0001 — The camera backend: Camera1, Camera2 or CameraX

**Status:** **accepted and adopted**, 2026-11-02. The minSdk question was answered — 23 —
and the bump landed the same day. `:scanner` now carries both backends; see "What adoption
actually did" at the end. The one recommendation not yet carried out is making CameraX the
default, and the reason is stated there.
**Date:** 2026-11-02, **revised the same day on a second physical device** — the revision
changed the recommendation's reasoning, not the recommendation. Read "Time to a decodable
frame" before quoting any latency figure from an earlier reading of this file.
**Phase:** 6. Also unblocks Phase 5 item 8, which has been waiting on it.

Everything below that is a number was measured here, on the devices named, by the scripts
named. Where something was not measured, it says so instead of arguing.

---

## The short version

**CameraX is the recommendation, but not because it is faster.** It was measured as faster
on the first device and is **indistinguishable from Camera1 on the second**, so startup
latency is not an argument either way. What survives two devices is the maintenance
argument, the deprecation argument, and the size figure — and the size figure is the one
CameraX loses, mostly to a single optional artifact this project does not need.

| | Camera1 (today) | CameraX | CameraX without `camera-view` |
| --- | --- | --- | --- |
| `onCreate` → first decodable frame, **DN-ENG41** (API 34, no-GMS) | 955 ms | **682 ms** | **665 ms** |
| `onCreate` → first decodable frame, **Mix5** (API 35, SM8550) | **742 ms** | **741 ms** | 745 ms |
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
- `tools/maint/camera-spike-run-ondevice.sh` is the same protocol as a shell script that
  runs *on* the phone, for the second device, which is reached through a private root-agent
  control channel rather than adb. No ADB listener is opened on that phone.
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

Two physical devices, 15 cold launches per flavor per device, screen on, clocks **not**
locked. Median, with the full range in brackets. Every run in these tables got a 1920x1080
frame, on both backends.

**DN-ENG41** — Android 14, no GMS, the phone every other measurement in this repository
comes from. One session, the three flavors run back to back.

| | camera1 | camerax | cameraxlean |
| --- | --- | --- | --- |
| process start → first frame | 1024 (996–1157) | 751 (721–1142) | 737 (696–782) |
| `onCreate` → first frame | 955 (927–1081) | **682** (656–1074) | **665** (635–707) |
| camera open → first frame | 891 (858–1004) | 661 (638–1048) | 649 (619–686) |

**Mix5** — Xiaomi 25010PN30C, Android 15, SM8550, five cameras. Same session rule.

| | camera1 | camerax | cameraxlean |
| --- | --- | --- | --- |
| process start → first frame | 789 (776–810) | 796 (769–824) | 791 (768–833) |
| `onCreate` → first frame | **742** (728–764) | **741** (724–775) | 745 (722–783) |
| camera open → first frame | 667 (642–679) | 723 (706–755) | 724 (700–765) |

Logs: `reports/camera-spike-*-DN-ENG41-20261102.log` and
`reports/camera-spike-*-mix5-20261102.log`.

**So the latency argument does not generalise, and the first version of this file was wrong
to lead with it.** On DN-ENG41 CameraX reaches a frame 273 ms (29%) sooner with ranges that
barely touch; on the Mix5 the two are **1 ms apart on 15 runs each, with the ranges almost
entirely overlapping**. One device is not a result — which is the rule this repository
already applies to every L2 number, and it applies here too.

A plausible reading, offered as a hypothesis and **not measured**: the Camera1 path's cost
is `CameraConfigurationManager`'s parameter negotiation, which is a long sequence of
`getParameters`/`setParameters` round trips into the HAL, and that hurts far more on a slow
device than on a flagship. If that is right, CameraX's advantage is real but only on weak
hardware. Testing it means instrumenting the open sequence, which the spike does not do.

Three honest qualifications, all of which were found by looking rather than assumed:

- **The "camera open → first frame" row flatters Camera1.** Camera1 cannot be opened until
  there is a surface to draw into, so its clock starts at `surfaceCreated` — after a wait
  the row does not count. CameraX's clock starts at the first line of its own setup. This is
  why the `onCreate` row, where both clocks start in the same place, is the one to read.
- **The first reading of this comparison had a confound, now removed.** CameraX was
  answering with 1920x1440 while Camera1 negotiated 1920x1080 — 38% more pixels per frame,
  in a comparison whose whole point is that the two sides differ in one dimension. It was
  visible in the frame sizes the spike prints, which is why it prints them. Pinning
  `AspectRatioStrategy(RATIO_16_9)` fixed it; it moved DN-ENG41's CameraX median by about
  35 ms and did not change any ordering.
- **DN-ENG41 drifts between sessions.** Its camera1 median read 810 ms earlier in the same
  day and 955 ms in the session above, with nothing changed. It is the thermally-saturated
  phone described in HANDOFF.md trap 3 and these runs do not lock its clocks. Only compare
  flavors measured in the same session; the tables above obey that.

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

(These four are from an earlier session, before the aspect ratio was pinned, and so do not
line up with section 1's tables. Both sides of each pair were measured alike, which is what
this comparison needs; nothing here is a cross-session comparison.)

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

### 5. What the two devices actually are

`dumpsys media.camera` on DN-ENG41: two cameras, HAL `device@3.5`, and
`android.info.supportedHardwareLevel = 3` — `LEVEL_3` — on **both**. So Camera2 and CameraX
run natively here and not through the `LEGACY` compatibility shim, and nothing in the
latency numbers above is a shim artifact.

Worth recording while the dump was open, because it bears on an earlier defect: the back
camera reports `Has a flash unit: false` and `android.control.afAvailableModes = [0]`, which
is auto-focus OFF only. This device has no torch and no auto-focus. A-12's torch work and
`AutoFocusManager` are therefore both untested on it.

**Mix5** — Xiaomi 25010PN30C (`xuanyuan`), Android 15 / API 35, HyperOS
`OS2.0.207.0.VOACNXM`, SM8550 (Qualcomm), arm64-v8a, **five cameras**, `getconf PAGE_SIZE`
= 4096. A flagship, and the first Android 15 hardware this project has been able to touch.
Both lens facings exist, so CameraX's camera validator never fires here.

Its page size being 4096 is worth stating plainly: **this device does not test 16 KB page
support.** The AAR is built and verified for 16 KB alignment, and that check still has no
hardware behind it. What this device does prove is that `libzxingnative.so` loads and
decodes on real Android 15.

### 5b. What the second device cost to measure, which is itself a finding

Two failures on the Mix5 looked exactly like backend bugs and were neither. Both are in
`reports/camera-spike-*-mix5-20261102.log`, and both are the reason that file says how to
reproduce it rather than just carrying numbers.

- **Fifteen runs in a row reported no result at all, with no exception anywhere.** The cause
  was in the CameraX log: `cannot open camera 0 without camera permission`, repeating in a
  700 ms retry loop forever. `pm grant` had been run and had succeeded — and HyperOS had
  revoked it again, because the app opened the camera without a user-visible prompt.
  `dumpsys package` said `granted=false` and `appops` said `Uid mode: CAMERA: ignore`.
  `cmd appops set <pkg> CAMERA allow` alongside `pm grant` is what makes it stick.
- **A run that worked for 11 launches and then stopped** was the screen timing out: a paused
  activity receives no frames. `svc power stayon true` first.

Neither is a CameraX defect and neither would have shown up on a device driven by adb from a
desk. They are recorded because **the next person to run this on a third device will hit
them**, and because a silent infinite retry on a permission failure is worth knowing about
in a library this project is considering shipping.

---

## What was *not* measured, and why

**Camera2 directly was not prototyped.** No number here compares it. The argument against it
is not performance — it would very likely match CameraX, since CameraX is a layer over it —
but maintenance, and the honest form of that argument is this: CameraX ships a
`CameraQuirks` database of per-device workarounds, and adopting Camera2 directly means
rediscovering the entries that matter on hardware this project does not own. This project has
**one** physical device. That is the whole case, and it is a judgement rather than a
measurement; anyone who disagrees should build a fourth flavor rather than argue.

**Nothing on a third physical device, and nothing non-Qualcomm.** There are two now, and
they disagreed about the thing this record originally led with, which is the whole argument
for not trusting one. Both are Qualcomm; neither is low-end-with-a-modern-OS, which is the
combination most likely to behave differently again.

**Nothing on hardware with 16 KB pages.** Both devices report `PAGE_SIZE` 4096.

**No sustained-scanning comparison.** The spike measures the first frame of a cold start and
stops. Whether CameraX's steady-state frame delivery costs more or less CPU than Camera1's is
an L4 question and L4 does not exist.

> **Answered, 2026-11-02, after adoption.** L4 exists now, and the answer is **more, by
> about 60%**: 118% of one core against Camera1's 74% over a 12-minute soak on DN-ENG41,
> ranges not touching, plus +17 MB PSS and +17 threads at an identical Java heap. Neither
> backend leaks. `reports/l4-verdict-DN-ENG41-20261102.md`.
>
> It does not reverse the recommendation, which never rested on performance — but it is a
> real cost this record did not price, and it is the third independent measurement pointing
> the same way about CameraX's frame path on this hardware. The other two are in
> "What adoption actually did" below and in `reports/l3-verdict-DN-ENG41-20261102.md`.

**Preview appearance on the lean path.** The scale/crop transform `PreviewView` does was
deliberately not reimplemented in the spike, so its preview may be stretched. Nothing here
measures what the preview looks like; adopting the lean path means writing that transform.

---

## The decision this needed: minSdk 21 or 23 — **answered: 23**

Settled by the user on 2026-11-02 and applied the same day. The reasoning is kept below
because the next person will ask why Android 5.x was dropped.

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

The two options were:

1. **Raise minSdk to 23** and track current CameraX. Drops Android 5.0 and 5.1. ← chosen
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

**The reasoning changed when the second device arrived; the recommendation did not.** The
case for CameraX is no longer "it is faster" — on the Mix5 it is not. It is:

- `android.hardware.Camera` has been deprecated since API 21 and this project carries
  **1,285 lines** of its quirk handling, written for hardware from 2010.
- CameraX ships a per-device quirk database that this project cannot reproduce with two
  phones, and `CameraConfigurationUtils` is this project's attempt at the same thing.
- On weak hardware CameraX did start measurably faster; on fast hardware it costs nothing.
  A scanner's worst case is the cheap phone, and that is where the 273 ms was.

The case against is 207 KB of APK and minSdk 23. Both are stated below and neither is hidden.

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
6. **Grant the camera permission the way a real app does, and handle the denial.** The
   Mix5 showed CameraX responding to a revoked permission with a silent 700 ms retry loop
   that never ends and never reports. `ScannerView` already reports
   `ScannerError.PERMISSION_DENIED` before opening anything; whatever replaces it must keep
   doing that rather than inherit CameraX's silence.

**If adopted, the second Phase 6 checkbox still has to be earned**: preview, ROI, result
points and screen-coordinate mapping verified on a device, plus the 100× open/close stability
loop and the 21 instrumentation tests, before the Camera1 path stops being the default. Both
devices are available for that now, and the 21 tests already pass on the Android 15 one —
`reports/instrumentation-21-mix5-api35-20261102.log`.

---

## What adoption actually did, 2026-11-02

All six recommendations above are implemented, except the part of 5 that makes CameraX the
default. The shape is `ScannerOptions.CameraBackend`, a per-caller choice exactly like
`setDecoderBackend`, with `CAMERA1` still the default value.

- `CameraXBackend` binds `Preview` + `ImageAnalysis` and nothing else. `camera-view` is not
  a dependency: the preview goes into the `SurfaceView` `ScannerView` already owns, and the
  measured result is **+0 resource entries** in `:app`'s release APK.
- `setAvailableCamerasLimiter(DEFAULT_BACK_CAMERA)` is applied once per process before
  anything asks for a provider.
- Frames reach `YPlaneLuminanceSource` as the luma plane in place. `ScanFrame` is the
  interface that carries a frame and its ownership to the decode thread, and the decode
  thread closes it in a `finally` — on this backend a frame nobody closes holds a buffer
  slot and the camera silently stops.
- `ScannerView` still checks the CAMERA permission before it opens anything, so CameraX's
  silent 700 ms retry loop on a revoked permission is never reached.
- CameraX is **1.6.2** here, the current stable line. The spike stays on 1.4.2 so that every
  number above keeps matching the code that produced it.

**Three things this record claimed, which adoption either confirmed or has to qualify.**

- *"The lean path means writing the scale/crop transform."* It did not, and the reason is
  worth recording: the Camera1 path never did that transform either — it stretches the
  preview over a `MATCH_PARENT` `SurfaceView`, and `PreviewCoordinateMapper` is written
  against exactly that assumption. So the lean path reproduces what shipped, rather than
  falling short of `PreviewView`. The viewfinder rectangle lands in the same place on both
  backends, photographed side by side on DN-ENG41.
- *The APK figures.* `classes.dex` grew **+930,544 B** in `:app` against the spike's
  +377,808 for the lean flavor. Two differences and neither was chased: 1.6.2 against 1.4.2,
  and a whole application for R8 to work on rather than one activity. The resource figure —
  the one the whole `camera-view` argument turned on — held exactly.
- *Startup latency.* Re-measured through `ScannerView` by L3 afterwards, and it does not
  agree with the spike: CameraX reaches the window's first frame ~330 ms **sooner** and a
  frame at the decoder ~200 ms **later**. Different intervals, not necessarily contradictory
  — the spike timed one interval containing both effects. `reports/l3-verdict-DN-ENG41-20261102.md`.

**And one thing the spike could not have found.** The 100-cycle open/close loop crashed once
on the emulator, in `onPause`: pausing while `ProcessCameraProvider.getInstance` was still
resolving left the backend's `LifecycleRegistry` at `INITIALIZED`, which cannot go straight
to `DESTROYED`. A one-in-a-hundred race that takes the app down, found only because the loop
is a hundred cycles and not one. Fixed; both loops pass clean afterwards.

**Why the default has not moved.** Three reasons now, where there were two. The third
arrived with L4 and is the strongest: **CameraX costs about 60% more CPU to keep scanning**
on the one device that can measure it, and L3 separately has it 200 ms slower to a frame.
Neither is fatal — this record never rested on performance — but flipping a shipping default
*towards* the measurably more expensive frame path, on the strength of one phone, is not a
step to take quietly.

The other two: the verification is from one phone (API 34) and one emulator (API 36). The Android 15 device was unreachable for the whole of this session, and
no decode has been driven off real camera hardware through `ScannerView` — DN-ENG41 has no
barcode in front of it and the emulator is where the decode was proved. This record already
has one headline that two devices disagreed about; flipping the shipping default on the
strength of one of them would be the same mistake with higher stakes. The full account is
`reports/camerax-adoption-verification-20261102.md`.
