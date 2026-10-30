# Audit — ZXing Android community maintenance fork

Baseline (U0): upstream `zxing/zxing` commit `56b8a9a7b53392097891463023a26c406b422225`
(2026-10-28, master). All line references are to that commit unless stated.

Audit device: **DN-ENG41, Android 14 (API 34), no GMS** (`pm list packages | grep gms`
returns nothing). Host: macOS 27.2 arm64, JDK 25 (Android Studio JBR), Android SDK at
`$ANDROID_HOME` with platforms 26/33/34/35/36 and build-tools 36.0.0.

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
| `minSdk` | **21** (was 19) | Justification below |

**Raising minSdk 19 → 21.** This is a deliberate, narrow raise, not a convenience:
API 19/20 (KitKat) devices are below the floor of the current AGP/D8 and AndroidX
toolchain, and the `android-core` and `android-integration` modules have to be real
Android library modules under Gradle, which inherit that floor. The camera, permission
and threading code this fork maintains has no API-19-only path, so nothing is lost
functionally. Devices at API 19/20 remain served by the upstream published APK.
This is recorded in `docs/maint/MIGRATION.md` as a breaking support change.

**core version.** The Gradle build compiles `:core` from this repository's sources
(3.5.5-SNAPSHOT) instead of the Maven build's pinned core 3.3.3 binary. The pin existed
because the old `android-maven-plugin`/`dx` pipeline could not desugar core's Java 8
constructs; D8 does. A scan of `core/src/main/java` found no API-24+ platform calls
(lambdas and method references are desugared; `StandardCharsets` is API 19). This is a
real behaviour change for the app and is listed in `MIGRATION.md`.

---

## 3. Call chain as it stands

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

### Open — confirmed, not yet fixed

| ID | Sev | Ev | Finding |
| --- | --- | --- | --- |
| A-06 | high | S | **Scoped storage: history CSV export is broken.** `HistoryManager.java:299` writes to `new File(Environment.getExternalStorageDirectory(), "BarcodeScanner")`. Since API 29 an app cannot create that directory; the write fails and `HistoryActivity` shows the "unmount USB" dialog, which describes a condition that no longer exists. Needs app-specific storage plus a `FileProvider` content URI for the share Intent. |
| A-07 | high | S | **Scoped storage: share-barcode-as-image is broken.** `EncodeActivity.java:140` has the same pattern, so sharing a generated barcode image fails on API 29+. Same fix shape as A-06. |
| A-08 | high | S | **Wi-Fi result handler is a no-op on Android 10+.** `WifiConfigManager` drives `WifiManager.addNetwork`/`enableNetwork`; since API 29 those return failure for non-system apps. Scanning a `WIFI:` barcode appears to do nothing. The modern path is `WifiNetworkSuggestion` (API 29+) or the `ACTION_WIFI_ADD_NETWORKS` settings intent (API 30+). Permissions are deliberately still declared so the behaviour change is explicit, not silent. |
| A-09 | medium | S | **Cleartext HTTP endpoints are blocked by default.** `SearchBookContentsActivity.java:177,179`, `BookResultInfoRetriever.java:99`, `BrowseBookListener.java:54` and the `maps.google`/product-search launches in `ResultHandler.java:411,417,423` all use `http://`. Since targetSdk 28 cleartext is off by default, so the in-app book search request fails outright and the external `VIEW` intents hand a cleartext URL to the browser. The three `http://` `BROWSABLE` intent filters in the manifest (zxing.appspot.com, google.com/m/products/scan) have the same shape. |
| A-10 | medium | S | **Package visibility (Android 11).** `LoadPackagesAsyncTask`/`AppPickerActivity` enumerate installed packages, and `CaptureActivityHandler` resolves a browser by package name. Without a `<queries>` element, targetSdk 30+ sees a filtered view, so the "share an app" picker is largely empty and the Chrome/AOSP-browser special case never matches. |
| A-11 | medium | S | **Decode-thread JPEG work on every successful scan.** `DecodeHandler.bundleThumbnail` renders a thumbnail, allocates an `ARGB_8888` `Bitmap`, JPEG-compresses it at quality 50 into a `ByteArrayOutputStream`, ships the bytes through a `Bundle`, and `CaptureActivityHandler` then `decodeByteArray`s it and makes a **second** mutable `ARGB_8888` copy. A compress/decompress round trip to move a bitmap between two threads in the same process. Only on the success path, so it costs first-scan latency rather than throughput — must be measured (L2/L3) before it is touched. |
| A-12 | medium | S | **Ambient-light torch control fights the autofocus manager.** `CameraManager.setTorch` tears down and rebuilds `AutoFocusManager` on every torch toggle, and `AmbientLightManager` can toggle on every sensor callback. Needs a hysteresis/rate check; behaviour on a device with a jittery light sensor is **D**. |
| A-13 | low | S | `getFramingRectInPreview()` maps the framing rect from screen to preview coordinates by independent x/y ratios (`CameraManager.java:245-248`). When the preview aspect ratio and the screen aspect ratio differ — which `CameraConfigurationManager` explicitly allows — the scan region does not correspond to what the viewfinder draws. Needs a fixed-frame L2 test per resolution before changing. |
| A-14 | low | S | `Looper.myLooper().quit()` in `DecodeHandler` (`DecodeHandler.java:57`) dereferences a nullable return; harmless in practice because it runs on the decode thread, but it is an unchecked assumption. |
| A-15 | info | S | `PreviewCallback.previewHandler` is written on the main thread and read on the camera thread without synchronization. The one-shot callback pattern makes a stale read benign today, but it is not guaranteed by the memory model and would become a real hazard under any continuous-callback change. |

### Needs device verification (D)

| ID | Question |
| --- | --- |
| D-01 | Behaviour on a device with no camera at all, and with the camera held by another app (`CameraService` returns busy). |
| D-02 | Torch/autofocus on devices that reject `FOCUS_MODE_CONTINUOUS_PICTURE` or torch parameters — the safe-mode re-flatten path in `CameraManager.openDriver` is untested here. |
| D-03 | Front-camera path (`Intents.Scan.CAMERA_ID`), including mirroring. |
| D-04 | Configuration change / Activity recreation under "don't keep activities". |
| D-05 | API 21 (the new minSdk floor) and Android 15/16 devices. Not available here. |

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
- Explicit-package calls: `IntentIntegrator` sets the package to
  `com.google.zxing.client.android`. **This fork's development applicationId is
  `com.google.zxing.client.android.community`, so explicit calls aimed at the upstream
  package will not reach it.** See `MIGRATION.md`.

---

## 6. What was measured, and what was not

Measured on the audit device at B0: install, launch, CAMERA grant and denial paths,
one live UPC-A decode, and the 100-cycle open/close stability loop
(`tools/maint/stability-loop.sh`, results in `docs/maint/reports/`).

Not measured yet, and therefore not claimed: camera-ready latency, first-scan latency,
decode time distributions, success/timeout rates by format, CPU, memory, GC, allocation
or power. The benchmark harness that has to produce those numbers is specified in
`docs/maint/BENCHMARKS.md`; until it has run on a device, this fork makes **no**
performance claims of any kind.
