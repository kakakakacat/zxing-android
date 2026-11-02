# Changelog

Release notes for this maintenance fork. Each released version gets a section here, and
the GitHub Release for the matching `v*` tag quotes it.

Upstream ZXing's own history is in [CHANGES](CHANGES); this file only covers the fork.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions
follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Until 1.0.0 the AAR's
API may still change between minor versions.

## [Unreleased]

### Added
- **`ScannerTrace`: three asynchronous trace spans** — `zxing.camera.open`,
  `zxing.first.frame`, `zxing.first.scan`, plus one naming the camera backend in use.
  Internal, no new dependency, and no-ops below API 29 and whenever nothing is tracing.
  They exist because "the camera is ready" and "a frame reached the decoder" are events
  only this library can see, and the L3 benchmark has to be able to time them.
- **A CameraX backend for `ScannerView`**, selected with
  `ScannerOptions.setCameraBackend(CameraBackend.CAMERAX)`. Camera1 remains the default
  and remains selectable; which one runs is a per-caller choice, the same shape as
  `setDecoderBackend`. The CameraX path binds `Preview` + `ImageAnalysis`, draws into the
  `SurfaceView` the scanner already owns rather than taking `camera-view`, limits CameraX
  to the back camera before it initialises, and hands each frame's luma plane to
  `YPlaneLuminanceSource` where the camera left it — **no copy per frame**, with the
  `ImageProxy` closed only once the decode has returned. That finishes Phase 5 item 8,
  which had built `YPlaneLuminanceSource` for CameraX and could never run it against one.

  Not taking `camera-view` is the difference between **+0 and +273 resource entries** and
  saves the AppCompat tree with it; `classes.dex` grows 931 KB and CameraX's own native
  libraries 65 KB across the two ABIs the AAR ships
  (`docs/maint/reports/camerax-adoption-apk-size-20261102.log`).

  Verified on Android 14 hardware and an Android 16 emulator — preview, the viewfinder
  rectangle in the same place as the Camera1 path, a real decode with its thumbnail and
  result points, 100 open/close cycles and the 21 instrumentation tests. **Not verified on
  Android 15**, and no decode has been driven off real camera hardware through
  `ScannerView` yet, which is why the default has not moved:
  `docs/maint/reports/camerax-adoption-verification-20261102.md`.
- `ScannerCamera`, the small interface the viewfinder and the ambient-light manager read an
  open camera through, implemented by both backends.
- The Barcode Scanner app has a **"Use CameraX"** setting, off by default, under Camera.

### Changed
- **`ViewfinderView.setCameraManager(CameraManager)` is now `setCamera(ScannerCamera)`**,
  and `ScannerView.getCameraManager()` is joined by `getCamera()`, which answers for either
  backend. `getCameraManager()` still returns the Camera1 `CameraManager` and now returns
  null whenever the CameraX backend is in use, because there is no `CameraManager` behind
  it. Nothing has been released, so no consumer is being moved.
- `CameraManager.requestPreviewFrame` delivers a cropped frame object instead of the raw
  `byte[]` with its size in `arg1`/`arg2`. The crop belongs to the backend, which is the
  only thing that knows the frame geometry, and a CameraX frame is not a `byte[]` and has
  to be handed back when the decode finishes.
- **`minSdk` raised from 21 to 23**, dropping Android 5.0 and 5.1. Taken as a decision
  rather than drift: CameraX requires API 23 from 1.5.0 onward, and the last release that
  supported 21 was 1.4.2 on 2025-03-26, so staying at 21 would have meant adopting a camera
  library frozen nineteen months ago — and the point of adopting it is the per-device quirk
  fixes that keep arriving. The comparison behind that choice, measured on two physical
  devices, is in `docs/maint/decisions/0001-camera-backend.md`. Three code paths the bump
  made unreachable were removed with it: the install-time-permission branches in
  `ScannerView.hasCameraPermission` and `CaptureActivity`, and the version check around the
  browser-bookmark button in `ShareActivity`, which Android 6.0 had already made useless on
  every supported release. The bookmark button, `BookmarkPickerActivity` and the reply path
  are all still present and simply disabled; removing the feature is a product decision, not
  part of raising a build floor.

### Fixed
- **A-16: the scan screen works on a large screen.** Android 16 ignores
  `android:screenOrientation` on a large screen, so the landscape-designed capture UI runs
  in a portrait window. The viewfinder rectangle now turns with the window instead of
  staying a shallow band across it — at 1600x2560 it covered 26% of the height and now
  covers 47%, while a landscape window is unchanged — the status line sits under the frame
  rather than at the bottom of the window, and the result screen stacks the barcode image
  above its text instead of beside it.
- **A-12: the automatic front light no longer flaps.** With the front light set to AUTO,
  every light-sensor reading called into the camera, and every reading that crossed a
  threshold changed the flash mode — which tears down and rebuilds the autofocus manager
  each time. The decision now lives in one place with a three-second floor between
  changes, which is what the torch's own light needs: switching it on raises the measured
  lux, and no threshold can tell that apart from the room getting brighter. Six seconds of
  a sensor flapping across both thresholds cost six flash-mode changes before and one
  after.
- **A-08: scanning a `WIFI:` barcode adds the network again.** `WifiManager.addNetwork`
  has returned failure for ordinary apps since Android 10, so the button logged "Unable
  to add network" behind a toast claiming a connection had been requested. The network is
  now offered to the system through `ACTION_WIFI_ADD_NETWORKS` on Android 11+, where the
  user confirms it in a system prompt and it is saved for real, registered with
  `addNetworkSuggestions` on Android 10, and added the old way below that. A WEP barcode
  cannot be expressed as a network suggestion, so it says so instead of appearing to work.
- **A-06 / A-07: exporting the history and sharing a barcode image work again.** Both
  wrote into the legacy external storage root and shared a `file://` URI; scoped storage
  has forbidden the first since Android 10 and `FileUriExposedException` the second since
  targetSdk 24. Exports now go to the app's own cache and are handed over as a
  `content://` URI from a `FileProvider`, with a read grant set on the Intent's ClipData
  so the share sheet can draw its preview as well as the receiving app read the file. Old
  exports are swept after a day. The "Sorry, the SD card is not accessible" message is
  gone, along with the condition it described.
- **A-10: the "share an application" picker can see installed apps again.** A `<queries>`
  element declares the two intents this app asks about — MAIN/LAUNCHER for the picker and
  VIEW on http(s) for the product-search browser hand-off — rather than
  `QUERY_ALL_PACKAGES`. The picker asks `queryIntentActivities` for launcher activities
  instead of `getInstalledApplications`, since an intent query is what the platform can
  match that declaration against. On the test device the list went from 79 entries, of
  which 15 were apps a user could launch and the rest unlabelled vendor services, to 49
  launchable apps — 34 of them previously invisible to this app.
- **A-09: the in-app book search is no longer blocked before it leaves the device, and
  the book-URL matcher stops missing https.** Every URL the app builds is https now — the Google Books API call, the
  read-this-page link, the book link under a scanned ISBN, and the maps / product-search /
  book-search `VIEW` launches — so the request is no longer rejected by Android's
  cleartext block before it leaves the device. The three `BROWSABLE` deep links accept
  https as well as http. Separately, `LocaleManager.isBookSearchUrl` tested for `http://`
  only, so a scanned https Google Books URL was treated as a literal ISBN and the
  volume-id path was unreachable; it accepts both schemes now. Cleartext stays off: a
  scanned `http://` URL is still not fetched for supplemental info, and the link opens in
  the browser under the browser's own policy.

### Added
- **`:scanner` AAR** (`io.github.kakakakacat.zxing:zxing-android-scanner`): `ScannerView`
  with the camera, viewfinder and decode pipeline, configured through `ScannerOptions`.
  Hosts own the `CAMERA` permission conversation; a missing permission is reported as
  `ScannerError.PERMISSION_DENIED`.
- `:core` published as `io.github.kakakakacat.zxing:zxing-core`, since the AAR is built
  against core from source rather than upstream's released artifact.
- Gradle build for the Android modules (AGP 9.4.1 / Gradle 9.7.1, `compileSdk` 36). The
  upstream Maven build could not build them on any JDK from 9 onward.
- Instrumentation test running 35 fixed frames across 16 barcode formats plus negative
  samples through the device's own ART.
- Benchmark groundwork: a versioned dataset manifest over the 1168 licensed blackbox
  images in `core/`, and `:benchmark-jvm`, an L1 correctness/timing runner.
- `:benchmark-android`, the L2 frame-pipeline benchmark (BENCHMARKS.md §10): the crop,
  luminance source, binarize and decode stages plus the whole path, measured on the
  device's own ART with AndroidX Microbenchmark, over nine generated replay frames at
  640x480 / 1280x720 / 1920x1080 and three hint sets. The frames are generated rather
  than committed, and their SHA-256 values are committed instead, so a run that would not
  be comparable with the baseline fails on the host and again on the device rather than
  producing a number.
- First Android measurements of any kind: an L2 baseline on DN-ENG41 with clocks locked —
  the full 135-case matrix twice and an hd/default slice, in `docs/maint/reports/`. A
  baseline, not a claim: nothing has been optimised and nothing compared.
- `tools/maint/check_release_publishes.py`, run in CI: it reads the publish tasks out
  of `release.yml` and fails if a published POM depends on an artifact of this
  project's own that the release does not publish. It caught `:core` being left out
  of the release, which would have made the AAR unresolvable.
- `benchmark/tools/noise_floor.py` and the measured effect floor it produced: 3% for
  cases at or above 100 µs, 10% below, from two runs of identical code. The 5%
  placeholder in BENCHMARKS.md §10.6 is gone.
- `benchmark/tools/compare.py`, which reads two result sets and returns pass / fail /
  **inconclusive** against the gates of BENCHMARKS.md §10.6, with a CSV, a JSON verdict
  and a Markdown report. It exits 0 / 1 / 2, and 3 when the two sets describe different
  measurements and cannot be compared at all. A warning describing a standing condition
  of the reference device — it is hot at rest, and has no sustained performance mode — is
  read through rather than blocking, but only when both sides carry the same set of them
  and the device's effect floor was measured under those conditions, and every such
  read-through is named in the verdict file and the report header.
- `benchmark/tools/l2_report.py`, which turns Microbenchmark's output into the common
  result envelope with bootstrap confidence intervals, and records every condition that
  makes a comparison inconclusive instead of suppressing it.
- `docs/maint/BENCHMARKS.md`: the measurement standard — four layers, dataset and device
  rules, statistics, result schema, and the pass / fail / **inconclusive** gates a
  comparison has to clear. Written before the harness, so the method cannot be fitted to
  the numbers.
- **The first measured comparison of the two decoders** (BENCHMARKS.md §10.6c): 27 cases
  per backend on a physical device with clocks locked. zxing-cpp is 56–82% faster on
  everything 2D and on all-format misses — the path live preview spends almost every
  frame on — and 5–20× slower on 1D hits, where ZXing Java's lazy row binarization lets
  it stop after a few rows. The Java decoder remains the default: the open question is no
  longer speed but that zxing-cpp renders UPC-A as 13 digits and reports it as `EAN_13`,
  which would change the text every host receives.
- **`TRY_HARDER` is honoured by the native backend**, mapped onto zxing-cpp's `tryHarder`
  and `tryRotate` — the two things this decoder's hint has always meant. Other hints are
  still dropped there.
- **L1 on a device** (`benchmark/tools/l1_device.sh`, `L1CorpusRun`): the 1168-sample
  corpus through either decoder backend and at either effort level on a real phone, scored by the same rules as the
  host run, with `benchmark/tools/l1_compare.py` to subtract two runs sample by sample.
  Correctness only — it records no timing on purpose. It also showed that host and device
  are not interchangeable: 231 samples reach the decoder as different pixels, because
  `ImageIO` gamma-encodes greyscale PNGs and ignores transparency where Android does
  neither (`BENCHMARKS.md` §9.1).
- **A `zxing-cpp` decoder backend**, behind the new `FrameDecoder` seam: zxing-cpp v3.1.1
  as a pinned submodule, built by CMake for `arm64-v8a` and `armeabi-v7a` and shipped in
  the AAR. Readers only — the writers and their bundled libzint are switched off.
  **16 KB page alignment** (mandatory on Android 15+) is set by the linker *and* verified
  on the built artifact by `:scanner:verifyNativeAlignment`, which fails the build; the
  check was confirmed to fail when alignment is forced to 4 KB. 13 instrumentation tests,
  including agreement with the Java backend on QR, Code 128, EAN-13, Data Matrix, Aztec
  and PDF417 frames. A device without the right ABI falls back rather than crashing.
  **Not the default, and not claimed to be faster** — that needs the A/B of Phase 5 item
  10 under the gates in BENCHMARKS.md §10.6.
- A build-time guard that the native decoder is compiled with NEON: zxing-cpp has no
  NEON intrinsics and relies on the auto-vectorizer, testing `__ARM_NEON` in one place to
  decide whether to pack a strided column before thresholding. Undefined, that path
  vanishes silently, so the C++ refuses to compile without it on an ARM ABI. Audited on
  the shipped libraries in `docs/maint/reports/neon-audit-zxingcpp-v3.1.1-ndk29.log` — a
  census of emitted SIMD, not a speed claim.
- `FrameDecoder`, the seam a decoder backend sits behind, taking a `LuminanceSource`
  rather than a `BinaryBitmap` so a backend brings its own binarization — the most
  expensive single stage in the pipeline.
- `YPlaneLuminanceSource`: the luma plane of a `YUV_420_888` image read where it already
  lies, for CameraX's `ImageProxy`. It honours `rowStride` and `pixelStride`, crops to the
  scan region, and copies that region and nothing else — the usual "drain the buffer into
  a `byte[]` and wrap it in `PlanarYUVLuminanceSource`" costs a full-frame copy per
  preview frame to reach the same result. No CameraX dependency is added to the AAR: it
  takes a `ByteBuffer` and three integers. **Not yet used by `ScannerView`, which still
  runs Camera1** — the camera-stack decision is still open — so nothing in the shipping
  app changes, and no speedup is claimed or measured. `detach()` is there for callers that
  must release the image before the decode finishes.
- CI: assemble + test + lint on every push, instrumentation tests on API 21/34/35/36
  emulators, and a tagged release workflow that publishes to GitHub Packages.

### Fixed
- **`ScannerView.setFormats` silently did nothing on a running scanner.** The format
  selection was only read when a decode thread was constructed, so a host narrowing the
  formats mid-session kept paying for every format until the next `resume()`. It now
  reaches the live decoder without reopening the camera; a frame already being decoded
  finishes under the previous selection and the next frame uses the new one. This is the
  library's cheapest performance control — a miss frame is 81 ms at 1920x1080 with every
  format enabled and 1.2 ms with one 1D format on the reference device — and it also
  removes two corpus misreads that only an all-formats reader can make.
- **L1 scored nine QR samples as misreads that decode perfectly.** The corpus runner
  stripped a trailing newline from the expected text but compared it against the raw
  decoded text, so any payload genuinely ending in a newline could never match. The
  corpus reads 862 correct / 4 incorrect, not 853 / 13. No decoder behaviour changed —
  this was only ever a flaw in how the result was scored, and nine new tests cover the
  scoring rules.
- **Camera permission is requested at runtime.** The app had been unusable on a first run
  since Android 6: it opened the camera without asking, got a `RuntimeException` from the
  camera service, and told the user to restart their device.
- **The launcher icon works again.** `CaptureActivity` declared `exported="false"`
  alongside a MAIN/LAUNCHER filter, which on API 31+ stops the launcher starting it.
  `ENCODE`, `SEARCH_BOOK_CONTENTS` and `SHARE` were likewise unreachable from other apps.
- **Camera failures are told apart**: no camera, camera held by another app, preview
  failure and permission denial each get their own message.
- **Results from a finished scanning session are dropped** instead of being delivered into
  a screen that has already been torn down.
- Compilation under current AGP: `switch` over `R.id` no longer works, as R fields are not
  compile-time constants.
- `layout-ldpi/capture.xml` still declared the pre-AAR `SurfaceView` and `ViewfinderView`,
  so on an ldpi device `findViewById(R.id.scanner_view)` returned null and the capture
  screen crashed on start.
- **Android 15/16: the bottom status hint was invisible**, drawn under the gesture
  navigation bar once API 35 began enforcing edge-to-edge for targetSdk 35+.
- **The decoded region now follows the viewfinder.** The crop was mapped from screen to
  frame coordinates with independent x/y ratios, which ignores the rotation applied to
  the preview and the front camera's mirroring. Only correct at rotation 0 — which is all
  upstream ever hit, until Android 16 started ignoring `screenOrientation` on large
  screens and running the activity in portrait.

### Changed
- **The native zxing-cpp decoder is now the default**, with the Java decoder as the
  fallback where there is no native build for the device's ABI. Measured on the reference
  device: 56–82% faster on every 2D case and on frames that decode nothing under all
  formats — what live preview does almost every frame — and slower only on 1D hits, by
  about 1.7 ms on the single frame of a session that succeeds. `BENCHMARKS.md` §10.6c.
  `ScannerOptions.setDecoderBackend(JAVA)` restores the old decoder.

  **The behaviour changes that come with it.** The viewfinder's animated candidate points
  stop appearing: zxing-cpp reports a symbol's corners once it has found one and has no
  equivalent of `NEED_RESULT_POINT_CALLBACK`. The points of a *found* symbol are still
  reported, so result thumbnails are unaffected. And decode hints other than the format
  selection are not honoured, because no mapping to zxing-cpp's options exists. A caller
  relying on either should select the Java backend.

  **Running the 1168-sample corpus through both backends on the device found two defects
  in this fork, both now fixed, and the second one had been hiding the first's real
  size.** `BENCHMARKS.md` §9.1 has the tables.

  The native backend used to return text spelled zxing-cpp's way — Codabar with its
  start/stop guards, UPC-E expanded to 13 digits, control characters escaped as `<GS>`,
  GS1 parentheses missing, RSS-14 behind its application identifier. It now returns what
  this library has always returned.

  And a result's format name is a *variant* in zxing-cpp (`"DataBar Stacked"`,
  `"Compact PDF417"`) where a request is a symbology, so five variant names matched
  nothing and every symbol carrying one was reported as no result. That cost 90 reads,
  all of them DataBar. An unmapped name is now logged once instead of disappearing.

  With both fixed the native backend reads **890** of the 1168 samples against the Java
  decoder's 862, is wrong about 3 against 4, and produces no false positives against 2.
  It wins 2D by a distance (Aztec 22→39, Data Matrix 39→57, QR 158→165) and loses PDF417
  50 against 56, which is the one place `ScannerOptions.setDecoderBackend(JAVA)` still
  buys something.

- **Everything moved out of the `com.google.zxing` namespace** into
  `io.github.kakakakacat.zxing`, the decoder included, and `applicationId` is now
  `io.github.kakakakacat.zxing.android`. Publishing classes named `com.google.zxing.*`
  from a second groupId would break any consumer that also resolved upstream core with
  `Duplicate class ... found in modules`. The cost: decoder types are this fork's own and
  are not interchangeable with upstream's. The **Intent contract is unchanged** —
  `com.google.zxing.client.android.SCAN` and friends are strings naming a protocol.
  `tools/maint/sync-core-from-upstream.sh` keeps upstream core syncs a reviewable diff.
- `ShareActivity` targets this app's own encoder instead of the hardcoded upstream
  package, which in a fork would have handed the share flow to a different app or to
  nothing at all.
- `IntentIntegrator` separates the action namespace from the package to launch, and
  lists this fork's app in `TARGET_ALL_KNOWN`.
- `core` is built from source (3.5.5-SNAPSHOT) instead of the pin to 3.3.3 that upstream's
  `android/pom.xml` carried. The app gains seven years of decoder fixes; this is the
  largest behavioural change in the fork.
- `minSdk` 19 → 21, `targetSdk` 22 → 36. Rationale in `docs/maint/AUDIT.md` §2.
- The result thumbnail is no longer JPEG round-tripped between two threads in the same
  process. Not claimed as a measured speedup — see A-11 in `docs/maint/AUDIT.md`.
- Application ID is `com.google.zxing.client.android.community`. This build is not signed
  with the upstream key and cannot update the published Barcode Scanner.

### Removed
- `javase`, `zxingorg` and `zxing.appspot.com`: this fork maintains the Android side only.
- The Maven build, which could no longer build anything Android.
- `android-core`, folded into `:scanner`.
- Manifest permissions that were already no-ops: `FLASHLIGHT`,
  `READ_HISTORY_BOOKMARKS`, `WRITE_EXTERNAL_STORAGE`.
