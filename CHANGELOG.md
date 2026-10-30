# Changelog

Release notes for this maintenance fork. Each released version gets a section here, and
the GitHub Release for the matching `v*` tag quotes it.

Upstream ZXing's own history is in [CHANGES](CHANGES); this file only covers the fork.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions
follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Until 1.0.0 the AAR's
API may still change between minor versions.

## [Unreleased]

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
- `benchmark/tools/noise_floor.py` and the measured effect floor it produced: 3% for
  cases at or above 100 µs, 10% below, from two runs of identical code. The 5%
  placeholder in BENCHMARKS.md §10.6 is gone.
- `benchmark/tools/compare.py`, which reads two result sets and returns pass / fail /
  **inconclusive** against the gates of BENCHMARKS.md §10.6, with a CSV, a JSON verdict
  and a Markdown report. It exits 0 / 1 / 2, and 3 when the two sets describe different
  measurements and cannot be compared at all.
- `benchmark/tools/l2_report.py`, which turns Microbenchmark's output into the common
  result envelope with bootstrap confidence intervals, and records every condition that
  makes a comparison inconclusive instead of suppressing it.
- `docs/maint/BENCHMARKS.md`: the measurement standard — four layers, dataset and device
  rules, statistics, result schema, and the pass / fail / **inconclusive** gates a
  comparison has to clear. Written before the harness, so the method cannot be fitted to
  the numbers.
- CI: assemble + test + lint on every push, instrumentation tests on API 21/34/35/36
  emulators, and a tagged release workflow that publishes to GitHub Packages.

### Fixed
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
