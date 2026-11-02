# Migration guide

Everything in this fork that behaves differently from upstream `zxing/zxing`, and why.
Baseline for comparison is upstream commit `56b8a9a7`.

## Identity — read this first

| | Upstream | This fork |
| --- | --- | --- |
| Java packages | `com.google.zxing.**` | `io.github.kakakakacat.zxing.**` |
| Application ID | `com.google.zxing.client.android` | `io.github.kakakakacat.zxing.android` |
| Signing key | The ZXing maintainers' | **Not held.** Debug key only |
| Maven coordinates | `com.google.zxing:core`, `:android-integration` | `io.github.kakakakacat.zxing:zxing-core`, `:zxing-android-scanner` |

### The package rename

Every class this project ships moved out of `com.google.zxing`, the decoder included.
Two reasons, in order of weight:

1. **Duplicate classes.** Publishing `com.google.zxing.BarcodeFormat` from a second
   groupId breaks any consumer that also resolves upstream core — directly or through
   another barcode library — with `Duplicate class ... found in modules`. That is not a
   theoretical conflict; it is a build failure on a classpath nobody fully controls.
2. **Identity.** `com.google.zxing` is the upstream project's namespace. This fork has
   no claim on it.

What it costs you: `Result`, `BarcodeFormat`, `DecodeHintType` and the rest are now
`io.github.kakakakacat.zxing.*`. They are not the same types as upstream's, so a result
cannot be handed straight to other ZXing-based code — convert at the boundary. For a
port, replacing `com.google.zxing.` with `io.github.kakakakacat.zxing.` on import lines
is usually the whole migration.

What it does **not** cost you: the Intent contract. `com.google.zxing.client.android.SCAN`,
`.ENCODE`, `.SEARCH_BOOK_CONTENTS`, `.SHARE` and all the extra keys are unchanged. Those
are strings; they name a protocol, not a class.

`tools/maint/sync-core-from-upstream.sh` re-applies this namespace to freshly pulled
upstream core sources, so tracking upstream stays a reviewable diff instead of a merge
that conflicts on every package line.

The application ID is a **development default**, chosen so this build cannot pose as the
published Barcode Scanner. Consequences:

- **Explicit-package Intent calls do not reach this app.** Callers that want this build
  must target `io.github.kakakakacat.zxing.android`, or drop the explicit package and
  let the implicit `com.google.zxing.client.android.SCAN` action resolve. This fork's
  `IntentIntegrator` lists both apps in `TARGET_ALL_KNOWN`, and adds
  `TARGET_MAINTENANCE_FORK_ONLY` for targeting this build alone.
- Both apps can be installed side by side, and both will then offer to handle the same
  actions. The user gets a disambiguation dialog. That is a real cost of the rename and
  is not hidden here.
- This APK **cannot** be installed over the official app as an update. Different key,
  different package.

A maintainer who publishes their own build should set `applicationId`, the display name
and the Maven coordinates to their own identity in `android/build.gradle.kts` and
`scanner/build.gradle.kts`.

## Support range

- **`minSdk` 19 → 21 → 23.** The second step was taken deliberately on 2026-11-02 so that
  the project can track current CameraX, which requires 23 from 1.5.0 onward; the last
  CameraX release supporting 21 was 1.4.2, on 2025-03-26. The reasoning, and the
  measurements behind it, are in
  [the camera-backend decision record](decisions/0001-camera-backend.md). **This drops
  Android 5.0 and 5.1 (API 21/22)**. Nothing has been released yet, so no consumer is
  being moved off a version they already had. Three code paths went with it, all of them already dead on
  any device the project tests on: the install-time-permission branches in `ScannerView`
  and `CaptureActivity`, and the version check around the browser-bookmark button in
  `ShareActivity` — Android 6.0 removed `READ_HISTORY_BOOKMARKS`, so with minSdk 23 there
  is no version left on which that button could work, and it is now simply disabled.

  For the first step, 19 → 21: API 19/20 are below the floor of the current AGP/D8 and
  AndroidX toolchain, and the Gradle library modules inherit that floor. No functionality
  was lost there either — nothing in the camera, permission or threading code had an
  API-19-only path. Devices on API 19/20 are still served by the upstream published APK.
- **`targetSdk` 22 → 36.** This is what makes the runtime permission model, the API 31+
  `exported` rules, scoped storage and package visibility apply at all. Several of the
  fixes below exist because of it.

## Build

- **Gradle replaces Maven.** The Android modules could not be built by Maven on any JDK
  from 9 onward. Gradle (9.7.1) + AGP 9.4.1 is now the only build. The upstream poms are
  gone along with the modules they built.
- **`javase`, `zxingorg` and `zxing.appspot.com` are removed.** This fork maintains the
  Android side only. Anyone needing the desktop or web pieces should use upstream.
- **`core` is built from source, not pinned to 3.3.3.** Upstream's `android/pom.xml`
  pinned `com.google.zxing:core` to 3.3.3 as "the last compatible version", because the
  old `dx` pipeline could not desugar core's Java 8 constructs. D8 can. The app therefore
  gets current core, including seven years of decoder fixes. This is the single largest
  behavioural change in the fork.
- **`android-core` is gone.** Its one class, `CameraConfigurationUtils`, moved into
  `:scanner`.

## API

### New: the `:scanner` AAR

The scanning pipeline used to be private to the app and read `SharedPreferences` from
inside the camera and decode classes. It is now a library with an explicit configuration
object. Nothing was removed from the app's own API to make this happen, but the following
app-internal classes no longer exist, having been replaced by library equivalents:

| Removed | Replacement |
| --- | --- |
| `CaptureActivityHandler` | `ScannerView` owns the state machine |
| `DecodeThread`, `DecodeHandler` | `DecoderThread` inside `:scanner` |
| `FrontLightMode.readPref(SharedPreferences)` | `FrontLightMode.parse(String)` + `ScannerOptions` |
| `ShareActivity` hardcoding the upstream package | targets `getPackageName()`, so the share flow reaches this app's encoder |
| `AutoFocusManager(Context, Camera)` | `AutoFocusManager(Camera, boolean)` |
| `CameraManager(Context)` | `CameraManager(Context, ScannerOptions)` |
| `AmbientLightManager(Context)` | `AmbientLightManager(Context, FrontLightMode)` |
| `AmbientLightManager.start(CameraManager)` | `start(ScannerCamera)` — either camera backend satisfies it |
| `ViewfinderView.setCameraManager(CameraManager)` | `setCamera(ScannerCamera)`, for the same reason |
| `CameraManager.requestPreviewFrame` delivering `byte[]` with the size in `arg1`/`arg2` | delivers a cropped frame object; the crop belongs to the backend, and a CameraX frame is not a `byte[]` |

These were package-private or effectively internal upstream; none of them were part of a
published artifact.

### Unchanged: the Intent contract

Actions, input extras, result extras and cancel behaviour are preserved exactly. The full
list is in [AUDIT.md](AUDIT.md) §5. `android-integration`'s `IntentIntegrator` and
`IntentResult` are untouched apart from the package-name caveat above.

## Behaviour

### Fixed

- **The CAMERA permission is now requested.** Upstream never asked, so on a first run the
  camera service refused and the app showed *"Sorry, the Android camera encountered a
  problem. You may need to restart the device."* and exited. It now asks, and on refusal
  distinguishes "not now" from "don't ask again", offering the system settings page in
  the latter case.
- **The launcher icon works.** `CaptureActivity` had `exported="false"` with a
  MAIN/LAUNCHER filter, which on API 31+ means the launcher cannot start it. Same for the
  `ENCODE`, `SEARCH_BOOK_CONTENTS` and `SHARE` activities, which were unreachable from
  other apps.
- **Camera failures are distinguished.** `NO_CAMERA`, `CAMERA_UNAVAILABLE` (held by
  another app), `PREVIEW_FAILED` and `PERMISSION_DENIED` each get their own message
  instead of one "restart your device" dialog.
- **Results from a finished session are dropped.** Each scanning session carries an id and
  a late decode is discarded. Upstream relied on `removeMessages()` after a 500 ms join,
  which could still deliver a previous session's result into a freshly resumed screen.

### Changed

- **The result thumbnail no longer makes a JPEG round trip.** Upstream compressed it to
  quality-50 JPEG to pass it between two threads *in the same process*, then decoded it
  and made a second mutable copy. It is now built mutable once and handed over directly.
  The thumbnail is therefore no longer JPEG-degraded. This is **not** claimed as a
  measured speedup; see A-11 in [AUDIT.md](AUDIT.md).
- **Permissions dropped from the manifest:** `FLASHLIGHT` (not grantable since API 23),
  `com.android.browser.permission.READ_HISTORY_BOOKMARKS` (provider removed in Android 6),
  `WRITE_EXTERNAL_STORAGE` (a no-op since API 29). The Wi-Fi permissions are deliberately
  kept even though the Wi-Fi handler is broken on Android 10+, so the breakage stays
  visible as an audit item rather than being quietly dropped.

### Known broken, not yet fixed

Carried forward from upstream and tracked in [AUDIT.md](AUDIT.md):

- History CSV export and share-as-image write to the legacy external storage root and
  fail under scoped storage (A-06, A-07).
- The Wi-Fi result handler cannot add networks on Android 10+ (A-08).
- Book search and several outbound links use cleartext `http://`, blocked by default from
  targetSdk 28 (A-09).
- The app-share picker and the browser special case are affected by Android 11 package
  visibility (A-10).
