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
// Safe to call again while scanning: it reaches the live decoder, no camera restart.
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

**Feeding frames from CameraX.** `YPlaneLuminanceSource` wraps the luma plane of a
`YUV_420_888` image where it lies — pass `getPlanes()[0]`'s buffer, its `rowStride` and
`pixelStride`, and the scan rectangle. Row padding and interleaved luma are handled, only
the scan rectangle is ever copied, and the buffer's position is left alone. The buffer
must outlive the decode, or call `detach()` first. `ScannerView` itself still drives
Camera1; this is the input path for a host that already runs CameraX.

**Narrow the formats.** `setFormats` is the cheapest performance control the library
has, and it can be changed at any time — including while scanning, which reaches the
running decoder without reopening the camera. On the reference device a preview frame
that decodes nothing costs **81 ms** at 1920x1080 with every format enabled and **1.2 ms**
with a single 1D format; that is the path live preview spends almost all of its time on.
It also removes misreads: two images in the test corpus are read as the wrong symbology
by an all-formats reader. Measurements and method in
[`docs/maint/BENCHMARKS.md`](docs/maint/BENCHMARKS.md) §9 and §10.

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
./gradlew :benchmark-jvm:run --args="--config default"   # L1 correctness/timing, host
benchmark/tools/l1_device.sh --backend native            # L1 correctness on a device
```

`assembleRelease` for the app produces an **unsigned** APK: no production signing config
is checked in, and none should be.

## Support range

| | |
| --- | --- |
| `minSdk` | 23 (upstream was 19; raised to 21, then to 23 — see [AUDIT.md](docs/maint/AUDIT.md) §2 and [the camera-backend decision](docs/maint/decisions/0001-camera-backend.md)) |
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
- [x] **A-16** The capture UI works in the portrait window Android 16 gives it on a
      large screen: the frame turns with the window, the status line follows it and the
      result screen stacks
- [ ] Predictive back
- [x] App launches, previews and survives on API 35 and API 36 emulators
- [x] Instrumentation suite runs on API 23 / 34 / 35 / 36 emulators in CI — 21 tests,
      0 failures on each, green on `2e9e5ce16`
- [x] Verified on a physical **Android 15** device: the 21 `:scanner` instrumentation tests
      pass on a Xiaomi 25010PN30C (API 35, SM8550), reached through a root agent rather than
      adb — `docs/maint/reports/instrumentation-21-mix5-api35-20261102.log`. Android 16 is
      still emulator-only, and neither device has 16 KB pages
- [ ] Verified on a physical Android 16 device *(none available — will stay unticked until
      one is)*

### Phase 3 — carried-forward defects

- [x] **A-06** History CSV export writes to app-specific storage and is shared through a
      `FileProvider`
- [x] **A-07** Share-barcode-as-image, the same way
- [x] **A-08** Wi-Fi result handler adds networks through the system prompt on
      Android 11+, a network suggestion on Android 10, the old path below that
- [x] **A-09** Book search and every outbound link this app builds are https; the
      book-search URL matcher accepts https too
- [x] **A-10** App-share picker and the browser special case: `<queries>` declared, and
      the picker asks for launcher activities instead of every installed application
- [x] **A-12** Ambient-light torch control: a rate check, so the torch's own light
      cannot flap it and rebuild the autofocus manager each time

### Phase 4 — measurement before optimisation

- [x] Measurement method, layers and comparison gates written down
      ([BENCHMARKS.md](docs/maint/BENCHMARKS.md)) — before the numbers, on purpose
- [x] Versioned dataset manifest (1168 licensed source images, SHA-256 per sample)
- [x] L1 correctness/timing runner on the host JVM
- [x] L1 on a device, through either decoder backend (`benchmark/tools/l1_device.sh`) —
      correctness only, no timing
- [x] L2 Android frame-pipeline benchmark (`:benchmark-android`, AndroidX
      Microbenchmark): five stages, nine generated replay frames, three hint sets,
      with the frame hashes committed as the comparability key
- [x] Microbenchmark output converted into the common result envelope
      (`benchmark/tools/l2_report.py`)
- [ ] L3 app/camera benchmark (Macrobenchmark) on a real device
- [ ] L4 sustained-scanning CPU/memory/thermal run
- [x] Comparison tool with pass / fail / **inconclusive** verdicts and 95% intervals
      (`benchmark/tools/compare.py`) — exits 0 / 1 / 2, and 3 when the two result sets
      cannot be compared at all
- [x] L2 baseline committed: DN-ENG41, clocks locked, the full 135-case matrix twice
      plus an hd/default slice (`docs/maint/reports/`)
- [x] Effect floor measured instead of guessed: 3% for cases at or above 100 µs, 10%
      below, from two full-matrix runs of identical code
- [ ] L3 and L4 baselines (neither harness exists yet)

### Phase 5 — Kotlin and native decoding

- [x] Convert the maintained Android code to Kotlin, module by module, keeping the Java
      API source-compatible. **`:android-integration`, `:scanner` and `:app` are all
      done** — no Java source left in any of them, with the Java-facing shape checked per
      commit by `tools/maint/api-diff.sh` and, for the published API, by compatibility
      tests still written in Java so javac has to accept it. `:core` stays Java: it is
      synced from upstream
- [x] **zxing-cpp backend** behind the `FrameDecoder` seam: zxing-cpp v3.1.1 as a
      submodule, CMake building `arm64-v8a` / `armeabi-v7a`, shipped in the same AAR, and
      the default since `ebe1708b4`. NEON is the auto-vectorizer's, not intrinsics —
      zxing-cpp has none; the audit is in `docs/maint/reports/neon-audit-*.log`
- [ ] Feed `ImageProxy` `YUV_420_888` straight to the decoder: honour `rowStride` /
      `pixelStride`, crop to the scan region, no full-frame copy. **Half done** —
      `YPlaneLuminanceSource` exists and is tested, but `ScannerView` still runs Camera1
      and nothing in the app uses it yet; the rest is Phase 6's decision
- [x] API to enable/disable formats per scan, so the native reader does less work
      (`ScannerView.setFormats`, which had silently done nothing on a running scanner)
- [x] 16 KB page-size alignment for the native libraries (required by Android 15+),
      checked at build time
- [x] A/B the native backend against the Java one on the fixed dataset — speed
      (BENCHMARKS.md §10.6c) and correctness (§9.1). zxing-cpp is 56–82% faster on 2D and
      on all-format misses, **and** reads 890 of the 1168 corpus samples against the Java
      decoder's 862, with no false positives
- [x] Native results rendered the way this library always has: UPC-E, Codabar, RSS-14,
      control characters and GS1 parentheses
- [x] zxing-cpp's result format names mapped variant by variant — five of them matched
      nothing, which silently cost 90 corpus reads

### Phase 6 — camera backend

- [x] Decision record comparing Camera1 / Camera2 / CameraX on OS coverage, device
      compatibility, APK size, startup latency and maintenance cost —
      [`docs/maint/decisions/0001-camera-backend.md`](docs/maint/decisions/0001-camera-backend.md).
      Measured on two physical devices, not argued: `benchmark/camera-spike` builds the same
      app three times over three camera stacks, so a size delta and a startup delta are
      attributable to the stack and nothing else. **The two devices disagree about
      latency** — CameraX reaches a decodable frame 273 ms (29%) sooner on the Android 14
      no-GMS phone and is 1 ms apart from Camera1 on the Android 15 flagship, over 15 cold
      launches each — so the recommendation rests on maintenance and deprecation, not speed.
      CameraX costs **+558 KB**, of which **351 KB is `camera-view` alone** and avoidable.
- [x] **minSdk raised 21 → 23**, the one question the record left open. Current CameraX
      needs 23; staying at 21 would have pinned it to 1.4.2, last released 2025-03-26
- [ ] CameraX adopted in `:scanner` (unblocked, not started)
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
| — | No L3 or L4 numbers exist: nothing is known about camera-ready or first-scan latency | all |
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

**Still none.** There is now an L2 baseline — what one preview frame costs on one device
at one pinned operating point — but a baseline is not a claim. Nothing has been optimised
and nothing has been compared, so there is nothing to be faster than.

What the baseline does describe is where the cost sits. On DN-ENG41 with clocks locked, a
1920×1080 frame containing no barcode takes **81 ms** with every format enabled and
**1.2 ms** with one 1D format enabled. The no-barcode case is the live-preview case, and
restricting the format set is worth up to 67× on it. That is a property of the decoder as
it stands, not the effect of any change.

Where this fork has removed obviously wasteful work, that is still described as simpler
code rather than a measured speedup. See [BENCHMARKS.md](docs/maint/BENCHMARKS.md).

## Licence

Apache 2.0, as upstream. See [LICENSE](LICENSE), [NOTICE](NOTICE) and [AUTHORS](AUTHORS).
