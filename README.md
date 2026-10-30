# ZXing Android — community maintenance fork

An independent, community-maintained fork of the Android side of
[zxing/zxing](https://github.com/zxing/zxing): the **Barcode Scanner** app and a reusable
scanning library.

> **Not affiliated with, endorsed by, or published by the ZXing project.** This fork is
> not signed with the upstream key and cannot update the published Barcode Scanner app.
> The original project's copyright and Apache-2.0 licence are preserved.

Upstream's Android modules stopped being buildable years ago: they only compile through a
Maven profile gated on `<jdk>[,9)</jdk>` and the unmaintained `android-maven-plugin`, with
`targetSdk 22`. This fork exists to make that code build, run and keep evolving on current
Android — and to make the scanning pipeline reusable instead of copy-pasteable.

## What is here

| Module | What it is |
| --- | --- |
| `scanner/` | **The AAR.** `ScannerView` + the camera and decode pipeline |
| `android/` | The Barcode Scanner app, built on the AAR |
| `core/` | The decoder. Forked from ZXing core and maintained here |
| `android-integration/` | The Intent helper third-party apps embed |
| `benchmark/` | Dataset manifest and the L1 correctness/timing runner |

The desktop (`javase`) and web modules are not maintained here; see
[docs/maint/MIGRATION.md](docs/maint/MIGRATION.md).

## Using the scanner library

```kotlin
dependencies {
  // Brings the decoder (io.github.kakakakacat.zxing:zxing-core) with it.
  implementation("io.github.kakakakacat.zxing:zxing-android-scanner:0.1.0")
}
```

Repository setup (GitHub Packages or JitPack) is in
[docs/maint/PUBLISHING.md](docs/maint/PUBLISHING.md).

```xml
<io.github.kakakakacat.zxing.android.scanner.ScannerView
    android:id="@+id/scanner_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
```

```java
scannerView = findViewById(R.id.scanner_view);
scannerView.setOptions(new ScannerOptions.Builder()
    .setAutoFocus(true)
    .setFrontLightMode(FrontLightMode.AUTO)
    .build());
scannerView.setFormats(EnumSet.of(BarcodeFormat.QR_CODE));   // or null for everything
scannerView.setListener(new ScannerView.Listener() {
  @Override public void onScanned(ScanOutcome outcome) {
    String text = outcome.getResult().getText();
    // ... then scannerView.resumeScanning() to scan again
  }
  @Override public void onScannerError(ScannerError error, Exception cause) {
    // PERMISSION_DENIED / NO_CAMERA / CAMERA_UNAVAILABLE / PREVIEW_FAILED
  }
});

@Override protected void onResume() { super.onResume(); scannerView.resume(); }
@Override protected void onPause()  { scannerView.pause(); super.onPause(); }
```

**You request `CAMERA` yourself.** `ScannerView` never shows a permission dialog; if the
permission is missing it reports `ScannerError.PERMISSION_DENIED` and leaves the
conversation with the user to the host app. `CaptureActivity` in `android/` is a worked
example of doing it, including the "don't ask again" case.

Results arrive on the main thread, and each carries the id of the scanning session that
produced it, so a decode that finishes after `pause()` is dropped rather than delivered
into a screen that is already gone.

## Building

Requires JDK 17+ and an Android SDK with platform 36 and build-tools 36.0.0.
Gradle and AGP versions are pinned; the wrapper fetches Gradle itself.

```sh
./gradlew :scanner:assembleRelease     # the AAR
./gradlew :app:assembleDebug           # the app, debug-signed
./gradlew :scanner:connectedDebugAndroidTest   # fixed-frame decode test, needs a device
./gradlew :benchmark-jvm:run --args="--config default"   # L1 correctness/timing
```

`assembleRelease` for the app produces an **unsigned** APK: no production signing config
is checked in, and none should be.

## Support range

| | |
| --- | --- |
| `minSdk` | 21 (raised from upstream's 19 — rationale in [AUDIT.md](docs/maint/AUDIT.md) §2) |
| `compileSdk` / `targetSdk` | 36 |
| Camera | Camera1 (`android.hardware.Camera`), as upstream. A Camera2/CameraX backend is under evaluation, not yet decided |
| Google Play Services | Not required. The scan path is fully offline |

Verified on **Android 14 (API 34), DN-ENG41, no GMS**. Other versions are listed as
expected-to-work until they have been run; the current state of that matrix is in
[docs/maint/AUDIT.md](docs/maint/AUDIT.md).

## Status

Ticked when the work is done **and verified**, with the evidence named. Items are the
audit IDs from [AUDIT.md](docs/maint/AUDIT.md), so nothing here is a vague intention.

### Phase 1 — build and basic compatibility

- [x] **A-03** Android modules build again (Gradle + AGP 9.4.1; upstream's Maven build was
      gated on JDK < 9) — `./gradlew :app:assembleDebug` from a clean tree
- [x] **A-04** Compiles under current AGP (`switch` over `R.id` removed)
- [x] **A-02** Launcher icon and the externally-reachable activities work on API 31+
      (`exported` fixed) — installed and launched on Android 14
- [x] **A-01** `CAMERA` requested at runtime, with a real message on refusal and a route
      into system settings when permanently denied — reproduced and re-verified on device
- [x] **A-05** Dead permissions dropped (`FLASHLIGHT`, `READ_HISTORY_BOOKMARKS`,
      `WRITE_EXTERNAL_STORAGE`)
- [x] Reusable scanning library extracted and published as an AAR
- [x] 100× open/close stability loop: 0 crashes, 0 camera errors on Android 14
      — [report](docs/maint/reports/stability-100-b0-DN-ENG41.log)
- [x] Deterministic on-device decode test: 35 fixed frames, 16 formats, 0 false positives

### Phase 2 — Android 15 / 16 compatibility

- [x] **A-17** Edge-to-edge enforcement (API 35+): the status hint no longer sits under
      the gesture navigation bar — reproduced and fixed, verified on API 35 and 36
- [x] **A-13** The decoded region now follows the viewfinder through the display-to-camera
      rotation and front-camera mirroring — 9 unit tests, reproduced on API 36
- [ ] **A-16** Android 16 ignores `screenOrientation` on large screens, so the
      landscape-designed capture UI runs portrait. Crop mapping is fixed; the layout and
      the framing rect shape still assume landscape
- [ ] Predictive back
- [x] App launches, previews and survives on API 35 and API 36 emulators
- [ ] Instrumentation suite run on API 35 and API 36 in CI (workflow written, not yet run)
- [ ] Verified on a physical Android 15 or 16 device *(no such device available here —
      will stay unticked until one is)*

### Phase 3 — carried-forward defects

- [ ] **A-06** History CSV export writes to the legacy external storage root; broken under
      scoped storage since Android 10
- [ ] **A-07** Share-barcode-as-image has the same problem
- [ ] **A-08** Wi-Fi result handler cannot add networks on Android 10+
- [ ] **A-09** Book search and several outbound links use cleartext `http://`, blocked
      from targetSdk 28
- [ ] **A-10** App-share picker and the browser special case are hit by Android 11
      package visibility
- [ ] **A-12** Ambient-light torch control rebuilds the autofocus manager on every toggle

### Phase 4 — measurement before optimisation

- [x] Versioned dataset manifest (1168 licensed source images, SHA-256 per sample)
- [x] L1 correctness/timing runner on the host JVM
- [ ] L2 Android frame-pipeline benchmark (AndroidX Microbenchmark)
- [ ] L3 app/camera benchmark (Macrobenchmark) on a real device
- [ ] L4 sustained-scanning CPU/memory/thermal run
- [ ] Comparison tool with pass / fail / **inconclusive** verdicts and 95% intervals
- [ ] B0 baseline results committed for every layer

### Phase 5 — Kotlin and native decoding

- [ ] Convert the maintained Android code to Kotlin, module by module, keeping the Java
      API source-compatible
- [ ] **zxing-cpp + ARM NEON** backend behind the existing decoder interface: CMake
      building `arm64-v8a` / `armeabi-v7a`, shipped in the same AAR
- [ ] Feed `ImageProxy` `YUV_420_888` straight to the decoder: honour `rowStride` /
      `pixelStride`, crop to the scan region, no full-frame copy
- [ ] API to enable/disable formats per scan, so the native reader does less work
- [ ] 16 KB page-size alignment for the native libraries (required by Android 15+)
- [ ] A/B the native backend against the Java one on the fixed dataset, then decide the
      default. No speedup is claimed before that comparison exists

### Phase 6 — camera backend

- [ ] Decision record comparing Camera1 / Camera2 / CameraX on OS coverage, device
      compatibility, APK size, startup latency and maintenance cost
- [ ] If adopted: preview, ROI, result points and screen-coordinate mapping verified

## Namespace and independence

Everything this project ships lives under `io.github.kakakakacat.zxing`, including the
decoder. Nothing at runtime depends on an upstream ZXing artifact.

That namespace move is not cosmetic. Publishing a second artifact whose classes are
named `com.google.zxing.*` would break any consumer that also had upstream core on its
classpath — directly or pulled in by some other barcode library — with
`Duplicate class com.google.zxing.BarcodeFormat found in modules ...`. Moving the
package makes the two coexist.

The cost is real and worth stating: `Result`, `BarcodeFormat` and the rest are
`io.github.kakakakacat.zxing.*` types, so they are **not** interchangeable with
`com.google.zxing.*` types from other libraries. Code that needs to hand a result to
something else ZXing-based has to convert at the boundary.

Upstream fixes are still reachable:
`tools/maint/sync-core-from-upstream.sh` pulls fresh core sources from `zxing/zxing` and
re-applies this namespace mechanically, so a sync is a reviewable diff rather than a
merge that conflicts on every package line.

**The Intent contract is deliberately unchanged.** `com.google.zxing.client.android.SCAN`
and friends are action *strings*, not class names; renaming them would break fifteen
years of callers and buy nothing.

## Known problems right now

| ID | Problem | Where |
| --- | --- | --- |
| A-06 / A-07 | History export and image sharing fail on Android 10+ (scoped storage) | app |
| A-08 | Scanning a `WIFI:` barcode silently does nothing on Android 10+ | app |
| A-09 | In-app book search fails; cleartext HTTP is blocked | app |
| A-10 | "Share → App" picker is mostly empty on Android 11+ | app |
| A-16 | On a large screen, Android 16 forces the landscape-designed capture UI into portrait | app |
| — | No performance numbers exist yet for any layer | all |
| — | Only one device has been tested: Android 14, no GMS | all |
| — | Maven Central publishing is not set up; GitHub Packages and JitPack only | release |
| — | Decoder types are `io.github.kakakakacat.zxing.*`, so results cannot be passed straight to other ZXing-based libraries | AAR |

## Documentation

- [AUDIT.md](docs/maint/AUDIT.md) — what was found, with evidence levels, and what is fixed
- [MIGRATION.md](docs/maint/MIGRATION.md) — what changed against upstream and why
- [PUBLISHING.md](docs/maint/PUBLISHING.md) — releasing and consuming the AAR
- [BENCHMARKS.md](docs/maint/BENCHMARKS.md) — how performance is measured, and what is not yet measured
- [CHANGELOG.md](CHANGELOG.md) — release notes, one section per version

## Performance claims

There are none yet. The benchmark harness exists so that any future claim comes with a
baseline, a dataset hash, a device and a confidence interval. Where this fork has removed
obviously wasteful work, that is described as simpler code, not as a measured speedup,
until it has been measured. See [BENCHMARKS.md](docs/maint/BENCHMARKS.md).

## Licence

Apache 2.0, as upstream. See [LICENSE](LICENSE), [NOTICE](NOTICE) and [AUTHORS](AUTHORS).
