# Audit — ZXing Android community maintenance fork

Baseline (U0): upstream `zxing/zxing` commit `56b8a9a7b53392097891463023a26c406b422225`
(2026-10-28, master). All line references are to that commit unless stated.

Audit device: **DN-ENG41, Android 14 (API 34), no GMS** (`pm list packages | grep gms`
returns nothing). Host: macOS 27.2 arm64, JDK 25 (Android Studio JBR), Android SDK at
`$ANDROID_HOME` with platforms 26/33/34/35/36 and build-tools 36.0.0.

Every **R** below was reproduced on DN-ENG41 or on an emulator, as each row says. Two more
devices were reached later and carry verification rather than the audit itself: a Xiaomi
25010PN30C (Android 15, API 35) and, over a cloud ADB tunnel that did not last, a Samsung
SM-S948B (Android 16, API 36). All three are Qualcomm with 4 KB pages — see D-06.

Evidence levels used below:

| Level | Meaning |
| --- | --- |
| **R** | Reproduced on the audit device, with the observed symptom recorded |
| **S** | Provable from the source and the platform contract, not yet exercised on a device |
| **D** | Needs device/matrix verification before any claim is made |

Nothing in this file is a guess about behaviour that was not read or run.

---

## 1. State of the upstream Android build

`pom.xml:686` gates the three Android modules behind a profile that activates only on
`<jdk>[,9)</jdk>`, and the build uses `com.simpligility.maven.plugins:android-maven-plugin`.
On any JDK released in the last seven years the `android`, `android-core` and
`android-integration` modules are **not part of the reactor at all** — `mvn package`
silently builds only `core` and `javase`.

`android/pom.xml` targets `android.platform` 22 (`pom.xml:72`) and pins
`com.google.zxing:core` to **3.3.3** with the comment *"fixed at the last compatible
version"*. `android/AndroidManifest.xml` declared `minSdkVersion="19"
targetSdkVersion="22"`.

**Conclusion (R):** there is no way to produce an APK from a clean checkout of U0 on a
current toolchain. Per §10.2 of the plan, U0 is therefore recorded as *not runnable* —
it is **not** scored as infinite time or infinite improvement. All comparisons start at
B0, the first commit of this fork that builds and runs.

### B0 identity

B0 = `50e6e3860` (`fix: request the CAMERA permission at runtime`), the first commit at
which the app builds, installs, launches and decodes on the audit device. Patches
applied between U0 and B0, in order:

| Commit | Change |
| --- | --- |
| `0b9a70d1f` | Gradle build for the Android modules (no source moved) |
| `26faa5ce1` | Manifest: `exported`, dead permissions, DSL-owned sdk/version attrs |
| `70ea967f9` | `switch` over `R.id` → `if/else` (R fields are no longer constants) |
| `50e6e3860` | Runtime CAMERA permission flow |

No performance work is in that set, by design.

---

## 2. Toolchain and support range chosen for the first maintenance release

| Setting | Value | Why |
| --- | --- | --- |
| Gradle | 9.7.1 | Minimum for AGP 9.4 is Gradle 9.6.0 |
| AGP | 9.4.1 | Current stable line; minimum JDK 17, build-tools 36.0.0 |
| JDK (build) | 17 target, 25 daemon | AGP 9.4 requires JDK 17+ |
| `compileSdk` | 36 | Highest platform installed locally; AGP 9.4 supports up to 37 |
| `targetSdk` | 36 | Required for the current Play target-API policy and for the API 31+ `exported` rules to be enforced consistently |
| `minSdk` | **23** (was 19, then 21) | Justification below |

**Raising minSdk 19 → 21 → 23.** The second step came later, at `2e9e5ce16`, and is Phase 6's:
the CameraX line `:scanner` ships against requires API 23, and staying at 21 would have pinned
the fork to CameraX 1.4.2, last released 2025-03-26. The reasoning for the first step follows,
and still holds.

**Raising minSdk 19 → 21.** This is a deliberate, narrow raise, not a convenience:
API 19/20 (KitKat) devices are below the floor of the current AGP/D8 and AndroidX
toolchain, and the `android-core` and `android-integration` modules have to be real
Android library modules under Gradle, which inherit that floor. The camera, permission
and threading code this fork maintains has no API-19-only path, so nothing is lost
functionally. Devices at API 19/20 remain served by the upstream published APK.
This is recorded in `docs/maint/MIGRATION.md` as a breaking support change.

**core version.** The Gradle build compiles `:core` from this repository's sources
instead of the Maven build's pinned core 3.3.3 binary. The pin existed
because the old `android-maven-plugin`/`dx` pipeline could not desugar core's Java 8
constructs; D8 does. A scan of `core/src/main/java` found no API-24+ platform calls
(lambdas and method references are desugared; `StandardCharsets` is API 19). This is a
real behaviour change for the app and is listed in `MIGRATION.md`.

---

## 3. Call chain as read at U0

**This is the baseline chain, not current code.** It is kept because the findings below
are anchored to it; the one thing already added at the time of reading is marked inline.
The scanning path has since moved into `:scanner` behind `ScannerView` / `DecoderThread`
(`30bda4529`), become Kotlin end to end (`39fdc78e0`), gained bounded Camera1 buffer
leases in place of `setOneShotPreviewCallback` (`9d7679090`) and a selectable CameraX
backend (`a90619e9a`) — so the JPEG hop at `bundleThumbnail` and the single-frame
ownership argument below describe U0, and A-11 and A-15 record what became of them.

```
CaptureActivity.onResume
  └─ CameraManager ctor  (new instance every resume)
  └─ SurfaceView holder: hasSurface ? maybeInitCamera : addCallback(this)
SurfaceHolder.Callback.surfaceCreated
  └─ maybeInitCamera  → [permission gate, added in this fork] → initCamera
       └─ CameraManager.openDriver(holder)
            ├─ OpenCameraInterface.open(requestedCameraId)      // Camera1 API
            ├─ CameraConfigurationManager.initFromCameraParameters
            ├─ setDesiredCameraParameters  (RuntimeException → re-flatten, safe mode)
            └─ camera.setPreviewDisplay(holder)
       └─ new CaptureActivityHandler
            ├─ new DecodeThread  → Looper.prepare / new DecodeHandler / Looper.loop
            ├─ CameraManager.startPreview  → new AutoFocusManager
            └─ restartPreviewAndDecode → requestPreviewFrame(decodeHandler, R.id.decode)
                 └─ Camera.setOneShotPreviewCallback(previewCallback)
PreviewCallback.onPreviewFrame(data, camera)          // camera thread
  └─ obtainMessage(R.id.decode, w, h, data).sendToTarget()   // → decode thread
DecodeHandler.decode                                   // decode thread
  ├─ CameraManager.buildLuminanceSource → PlanarYUVLuminanceSource (crop only, no copy)
  ├─ new BinaryBitmap(new HybridBinarizer(source))     // per frame
  ├─ MultiFormatReader.decodeWithState  → reset()
  ├─ success: bundleThumbnail → renderThumbnail → Bitmap → JPEG 50 → byte[]
  └─ sendToTarget(decode_succeeded | decode_failed)    // → main thread
CaptureActivityHandler.handleMessage                   // main thread
  ├─ decode_failed → requestPreviewFrame(...)          // next frame, one at a time
  └─ decode_succeeded → BitmapFactory.decodeByteArray → copy(ARGB_8888, true)
       └─ CaptureActivity.handleDecode → history, beep, draw points, result UI
CaptureActivity.onPause
  └─ handler.quitSynchronously (stopPreview, R.id.quit, join 500 ms, removeMessages)
  └─ inactivityTimer.onPause / ambientLight.stop / beepManager.close
  └─ CameraManager.closeDriver (camera.release)
```

Threading model, as read: exactly one decode in flight at a time, driven by
`setOneShotPreviewCallback`. There is no decode queue and therefore no unbounded
backlog. Frame data is **not** copied: `PlanarYUVLuminanceSource` keeps the array the
camera handed over, and the next frame is only requested after the current decode
finishes, so ownership is safe as written.

Permissions, before this fork: none requested at runtime, anywhere.
Lifecycle: all camera ownership hangs off `onResume`/`onPause`; `CameraManager` is
reconstructed on every resume.

---

## 4. Findings

### Fixed in this fork

| ID | Sev | Ev | Finding | Fix |
| --- | --- | --- | --- | --- |
| A-01 | critical | R | `CaptureActivity` never requested CAMERA. On a denied permission `Camera.open()` throws `RuntimeException: Fail to connect to camera service`, caught by the generic handler, which shows *"Sorry, the Android camera encountered a problem. You may need to restart the device."* and exits. The app has been unusable on first run since Android 6 unless the permission was pre-granted. | `50e6e3860` |
| A-02 | critical | R | `CaptureActivity` carried `android:exported="false"` with a MAIN/LAUNCHER intent filter. On API 31+ the launcher cannot start it: the app installs, the icon does nothing. `EncodeActivity`, `SearchBookContentsActivity` and `ShareActivity` were likewise unreachable from other apps despite publishing actions. | `26faa5ce1` |
| A-03 | blocker | R | No Android module builds on a current JDK (§1). | `0b9a70d1f` |
| A-04 | blocker | R | `switch` over `R.id` fails to compile — R fields are no longer compile-time constants. | `70ea967f9` |
| A-05 | low | S | `FLASHLIGHT` (not grantable since API 23) and `com.android.browser.permission.READ_HISTORY_BOOKMARKS` (provider removed in Android 6) were requested and unusable. | `26faa5ce1` |
| A-06 | high | S | **Scoped storage: history CSV export was broken.** The CSV is written under the app's own cache (`cache/exports/history/`) and shared as a `content://` URI from a `FileProvider`, with `FLAG_GRANT_READ_URI_PERMISSION`. The "unmount USB" dialog is gone with its string: `msg_unmount_usb` said the SD card was not accessible, which describes a condition that has nothing to do with it, and is replaced by `msg_export_failed`. Evidence: `reports/a06-a07-scoped-storage-DN-ENG41-20261102.log`. | `c80ac0496` |
| A-07 | high | S | **Scoped storage: share-barcode-as-image was broken.** Fixed through the same `ExportFiles` helper as A-06. Verified end to end: the system print service opened the shared `content://` URI and rendered the QR in its print preview, and the exported PNG pulled back off the device decodes to its own contents. The A/B also found a defect in the first version of the fix — `Intent.createChooser` copies the target's ClipData when it is built, and `startActivity` builds that ClipData later, so the share sheet itself was denied the file and drew a broken preview while the chosen app read it fine. `ExportFiles.grantRead` sets the ClipData rather than leaving it to `startActivity`. | `c80ac0496` |
| A-08 | high | S | **Wi-Fi result handler was a no-op on Android 10+.** `WifiNetworkAdder` picks the route the platform allows: on Android 11+ a `WifiNetworkSuggestion` handed to settings through `ACTION_WIFI_ADD_NETWORKS`, which prompts the user and saves the network for real; on Android 10 `addNetworkSuggestions`; below that the old `WifiConfiguration` path, which still works there and is why `WifiConfigManager` stays. WEP cannot be expressed as a suggestion, so it is reported instead of silently dropped, and the toast no longer claims a connection was requested when nothing was. Verified end to end on an Android 16 emulator by scanning a real `WIFI:` QR off the virtual-scene camera: before, `Unable to add network "A08TestNet"`; after, the system "Save this network?" prompt and the SSID in `cmd wifi list-networks`. Evidence: `reports/a08-wifi-DN-ENG41-api36-20261102.log`. | `173541d91` |
| A-09 | medium | S | **Cleartext HTTP endpoints were blocked by default.** Every URL this app builds is https — the book-search API call in `SearchBookContentsActivity`, the read-this-page link in `BrowseBookListener`, the book link in `BookResultInfoRetriever`, and the `maps.google` / product-search / book-search `VIEW` launches in `ResultHandler` — and the three `BROWSABLE` filters take https alongside http. Two matchers that run against *scanned* data were widened to both schemes rather than switched: `LocaleManager.isBookSearchUrl` and the product-search deep-link test in `CaptureActivity`; the first had been http-only, so a scanned https book URL was read as a literal ISBN. Cleartext stays off, so a *scanned* http URL is still not fetched for supplemental info by design — see the notes in `TitleRetriever` and `URIResultInfoRetriever`. Evidence: `reports/a09-cleartext-ab-DN-ENG41-20261102.log`. | `d48de88f3` |
| A-10 | medium | S | **Package visibility (Android 11).** A `<queries>` element declares the two intents this app actually asks about — MAIN/LAUNCHER for the picker, VIEW on http(s) for `launchProductQuery` — rather than `QUERY_ALL_PACKAGES`, which is for apps that genuinely need the whole list. `LoadPackagesAsyncTask` now asks `queryIntentActivities` for launcher activities instead of `getInstalledApplications`: an intent query is what the platform can match the `<queries>` declaration against, and a launchable app is what this screen meant all along. Measured on DN-ENG41: the picker went from 79 rows (15 of them apps you could launch, the rest unlabelled vendor services) to 49, with 34 installed apps appearing that the app could not see before. Evidence: `reports/a10-package-visibility-DN-ENG41-20261102.log`, reproduced by `tools/maint/scrape-app-picker.sh`. | `a85e87079` |
| A-11 | medium | S | **Decode-thread JPEG work on every successful scan.** `DecodeHandler.bundleThumbnail` rendered a thumbnail, allocated an `ARGB_8888` `Bitmap`, JPEG-compressed it at quality 50 into a `ByteArrayOutputStream`, shipped the bytes through a `Bundle`, and `CaptureActivityHandler` then `decodeByteArray`d it and made a **second** mutable `ARGB_8888` copy — a compress/decompress round trip to move a bitmap between two threads in the same process. **Gone with the AAR extraction**, which put both ends of that hop in one module: `DecoderThread` renders the thumbnail into a mutable bitmap once and hands the `Bitmap` itself to `ScannerView` inside a `ScanOutcome`, so callers can draw result points straight onto it. The remaining cost is the one allocation and pixel copy, on the single frame per session that decodes, and `ScannerView.setThumbnailsEnabled(false)` removes even that. Never measured as a defect, and no longer worth measuring as one. | `30bda4529` |
| A-12 | medium | S | **Ambient-light torch control fought the autofocus manager.** `TorchPolicy` holds the decision — the existing dead band between 45 and 450 lux, plus a three-second floor on how often the state may flip — and `AmbientLightManager` only calls `setTorch` when the answer changes, so a steady scene never touches the camera at all. The loop that actually bites is the torch's own light raising the measured lux, which no threshold can see. Measured on an Android 16 emulator with the light sensor driven across both thresholds for six seconds: 6 flash-mode changes before, 1 after, each of the 6 having rebuilt the `AutoFocusManager`. 8 unit tests, verified to fail when the interval check is removed. The teardown in `CameraManager.setTorch` is unchanged and is now bounded by the rate check; replacing it belongs with the Camera2/CameraX decision in Phase 6. Evidence: `reports/a12-torch-flapping-api36-20261102.log`. | `69b3c7062` |
| A-13 | high | R | **The decoded region did not match the viewfinder.** `getFramingRectInPreview()` scaled the rectangle from screen size to camera resolution with independent x/y ratios, ignoring the rotation `setDisplayOrientation` applies and the front camera's mirroring. Correct only at rotation 0 — the one case `screenOrientation="sensorLandscape"` guaranteed. Reproduced on an API 36 emulator at 1600x2560: a 338x450 screen region became an 800x190 band of the frame. | `db915d799` |
| A-14 | low | S | `Looper.myLooper().quit()` in `DecodeHandler` (`DecodeHandler.java:57`) dereferenced a nullable return; harmless in practice because it ran on the decode thread, but an unchecked assumption. **Fixed with the same extraction**: the quit site is `Looper.myLooper()?.quitSafely()` in `DecoderThread.kt`. The one `Looper.myLooper()!!` left is the `DecodeHandler` super-constructor, which runs between `Looper.prepare()` and `Looper.loop()` on the thread's own `run()`, so there the value cannot be null. | `30bda4529` |
| A-15 | info | S | `PreviewCallback.previewHandler` was written on the main thread and read on the camera thread without synchronization. The one-shot callback pattern made a stale read benign, but nothing in the memory model guaranteed it, and a continuous-callback change would have made it a real hazard — which is what Camera1 buffer leases turned out to be. Both callbacks now hold the request in an `AtomicReference<FrameRequest?>` and take it with `getAndSet`, so a request is delivered or cancelled exactly once. | `9d7679090` |
| A-16 | high | R | **Android 16 ignores `android:screenOrientation` on large screens.** Fixed in the sense available: the orientation cannot be forced back, so the capture UI now works in the portrait window it is given. Three parts. The viewfinder's size caps follow the window's long and short axes instead of x and y, so at 1600x2560 the frame goes from 1000x675 (62% of the width, **26%** of the height) to 675x1200; a landscape window is untouched, since there the long axis is x. The status line sits under the frame rather than at the bottom of a 2560px window. The result row stacks instead of placing the thumbnail beside the text, done in `CaptureActivity` rather than a `layout-port` variant because a second copy of `capture.xml` is how A-18 happened. `FramingRectSizerTest` pins both orientations. Evidence: `reports/a16-large-screen-portrait-api36-20261102.log` with before/after screenshots. | `2038009ba` |
| A-17 | high | R | **Bottom status text invisible on Android 15/16.** API 35 enforces edge-to-edge for targetSdk 35+, so the hint at `bottom\|center` was drawn under the gesture navigation bar. `navigationBars` inset starts at y=1017 of 1080; the text occupied 1029..1080. | `3a5620764` |
| A-18 | high | S | **`layout-ldpi/capture.xml` was left on the pre-AAR layout** when the capture screen moved to `ScannerView`, so on an ldpi device `findViewById(R.id.scanner_view)` returned null and `CaptureActivity.onCreate` crashed. Introduced by this fork, found while renaming packages. Fixed in the rename commit. | `849921bfa` |
| A-19 | high | R | **Predictive back silently disabled both back handlers.** From API 33 an app that opts into `OnBackInvokedDispatcher` stops receiving the back key in `onKeyDown`, and targetSdk 36 opts in by default, so `CaptureActivity`'s return-to-preview handler and `HistoryActivity`'s were both dead: back with a result on screen left the app. Fixed with `OnBackPressedDispatcher` callbacks in both activities. Cause isolated by A/B against `enableOnBackInvokedCallback`, reproduced on API 36. Evidence: `reports/a19-predictive-back-api36-20261102.log`. | `7d032431b` |

### Open — confirmed, not yet fixed

**Nothing.** Every confirmed finding above is fixed, each with the commit that did it and,
where the symptom was reproducible, a log or screenshot under `reports/`. New findings go
here first; what is genuinely unfinished is not a defect register but the device matrix (D
below), the two measurements that no device here can take — first-scan latency and power —
and the testing gaps listed in `HANDOFF.md`.

### Needs device verification (D)

| ID | Question |
| --- | --- |
| D-01 | Behaviour on a device with no camera at all, and with the camera held by another app (`CameraService` returns busy). |
| D-02 | Torch/autofocus on devices that reject `FOCUS_MODE_CONTINUOUS_PICTURE` or torch parameters — the safe-mode re-flatten path in `CameraManager.openDriver` is still untested. **Partly answered**: the torch itself is exercised on both devices, and the Android 15 run found and fixed a CameraX path that never received `FrontLightMode.ON` (`reports/camerax-android15-mix5-20261103.md`). **Autofocus on the CameraX path is tested nowhere** — DN-ENG41 offers auto-focus OFF only, and the 2026-11-03 decodes were off a screen at a fixed distance, which exercises focus on neither backend. |
| D-03 | Front-camera path (`Intents.Scan.CAMERA_ID`), including mirroring. Mirroring has unit coverage (`PreviewCoordinateMapperTest`, part of the A-13 fix); no front-camera run on any device. |
| D-04 | Configuration change / Activity recreation under "don't keep activities". |
| D-05 | ~~API 21 (the new minSdk floor) and Android 15/16 devices.~~ **Mostly answered.** minSdk is 23 (raised at `2e9e5ce16` for CameraX) and CI runs the suite on an API 23 emulator; a physical **Android 15** device passes 21/21 (`reports/instrumentation-21-mix5-api35-20261102.log`). **Android 16 hardware is still partial**: a Samsung SM-S948B gave 21/21 instrumentation over a cloud ADB tunnel before the link failed (`reports/android16-SM-S948B-20261103.md`), with no real camera, no root and therefore no L-layer timing. |
| D-06 | **Nothing non-Qualcomm, nothing with 16 KB pages, nothing low-end on a modern OS.** All three devices reached so far are Qualcomm with `PAGE_SIZE` 4096, so the 16 KB alignment in the shipped `.so` is verified in the ELF and not at runtime. |
| D-07 | **Real barcodes beyond the easy case.** Both backends read symbols off another phone's screen (`reports/real-barcode-decode-DN-ENG41-20261103.md`); paper, angles, poor light and movement are untouched. |

---

## 5. Intent contract, as read from U0

Recorded so that any later change to it is deliberate. Source: `Intents.java`,
`DecodeFormatManager`, `DecodeHintManager`, `CaptureActivity.onResume/handleDecodeExternally`.

- Actions: `com.google.zxing.client.android.SCAN`, `...ENCODE`,
  `...SEARCH_BOOK_CONTENTS`, `...SHARE`; plus `VIEW` on `http://zxing.appspot.com/scan`,
  `http://www.google.com/m/products/scan`, `http://www.google.co.uk/m/products/scan`,
  `zxing://scan/`.
- SCAN input extras: `SCAN_MODE`, `SCAN_FORMATS`, `CHARACTER_SET`, `SCAN_WIDTH`,
  `SCAN_HEIGHT`, `SCAN_CAMERA_ID`, `PROMPT_MESSAGE`, `RESULT_DISPLAY_DURATION_MS`,
  `SAVE_HISTORY`, `BEEP_ENABLED`, plus `SCAN_FORMATS`-equivalent hint extras parsed by
  `DecodeHintManager`.
- SCAN result extras: `SCAN_RESULT`, `SCAN_RESULT_FORMAT`, `SCAN_RESULT_BYTES`,
  `SCAN_RESULT_ORIENTATION`, `SCAN_RESULT_ERROR_CORRECTION_LEVEL`, and
  `SCAN_RESULT_BYTE_SEGMENTS_0..n`.
- Cancel: `RESULT_CANCELED` with no extras, on back-press while
  `source == NATIVE_APP_INTENT`.
- Explicit-package calls: upstream's `IntentIntegrator` sets the package to
  `com.google.zxing.client.android`. **This fork's applicationId is
  `io.github.kakakakacat.zxing.android`, so explicit calls aimed at the upstream package
  will not reach it.** The action strings themselves are unchanged. See `MIGRATION.md`.

**Namespace.** Everything this fork ships, the decoder included, was moved from
`com.google.zxing.**` to `io.github.kakakakacat.zxing.**`. Publishing classes with
upstream's fully-qualified names from a different groupId is a duplicate-class build
failure waiting for any consumer that also resolves `com.google.zxing:core`. Verified:
the published AAR and jar contain zero `com/google` entries.

---

## 6. What was measured, and what was not

**This section recorded the state at B0, when no harness existed. All four measurement
layers now exist and are baselined**, so the current answer lives in
`docs/maint/BENCHMARKS.md` and the verdict reports under `docs/maint/reports/`, not here.
What was true at B0: install, launch, CAMERA grant and denial paths, one live UPC-A
decode, and the 100-cycle open/close stability loop (`tools/maint/stability-loop.sh`).

Measured since: decode correctness and timing (L1, host and device), the frame pipeline
(L2, full 135-case matrix twice on DN-ENG41, with a measured effect floor), app startup,
camera-open and first-frame latency (L3, both camera backends), and sustained-scan CPU,
memory, GC and thermal state (L4, twelve-minute soaks per backend in one session).

**Still not measured, and therefore still not claimed: first-scan latency and power.**
First scan needs a fixed, photographed scene that does not exist yet; power needs a device
that can be made to discharge, which DN-ENG41 cannot — it has no charging switch anywhere
under `/sys/class/power_supply`, so the USB-input figure gives a bound of +15 mW with a 95%
interval of [−272, +308] mW rather than a measurement. Every L2 number still comes from
DN-ENG41 alone, at one pinned operating point, in one permanently throttled thermal state.
The testing gaps in `HANDOFF.md` carry the rest. (`BENCHMARKS.md` §5, §6 and §14 still
describe the pre-harness state and have not been brought forward.)
