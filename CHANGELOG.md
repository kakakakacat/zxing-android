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

### Changed
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
