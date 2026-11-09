# Handoff — working state

Operational notes that do not belong in the README: where things stand, what is in
flight, and the environment quirks that will otherwise be rediscovered the hard way.

**Where documentation goes, changed by the user on 2026-11-03.** `README.md` is now a
short bilingual landing page and nothing else: what this project is, one table of
improvements over upstream, one performance chart with its conditions, what is next, and
links out. Everything that used to live there — the integration example, the build
instructions, the support range, the phase-by-phase status list, the namespace rationale,
the known-problems table — moved to `docs/maint/PROJECT_DETAILS.md`, which carries the
same content in 简体中文 and English. **Write new detail there, in both languages, and
keep the README short.** A change only reaches the README when it changes what the
project *is* or moves one of the headline numbers.

Last updated at commit `ed0b063ea` on branch `maint/android`, **which is pushed**.

---

## Where the work is

- Repository: <https://github.com/kakakakacat/zxing-android> (fork of `zxing/zxing`)
- Default branch: **`maint/android`** — all work lands here; `master` is untouched upstream
- Remotes: `origin` = the fork, `upstream` = `zxing/zxing`
- Local checkout: `/Users/dm/zxing-android`
- Upstream baseline (U0): `56b8a9a7b53392097891463023a26c406b422225`
- B0 (first commit that builds and runs): `50e6e3860`

### Commits so far, oldest first

| Commit | What |
| --- | --- |
| `0b9a70d1f` | Gradle build for the Android modules |
| `26faa5ce1` | Manifest: `exported`, dead permissions, DSL-owned attrs |
| `70ea967f9` | `switch` over `R.id` → `if/else` |
| `50e6e3860` | Runtime CAMERA permission flow |
| `16c44927d` | Drop non-Android modules and the Maven build |
| `30bda4529` | Extract the `:scanner` AAR; app refactored onto it |
| `597a2235c` | Publishing pipeline, README, migration guide |
| `3a5620764` | Android 15/16 edge-to-edge fix |
| `db915d799` | Viewfinder → frame coordinate mapping |
| `76ef9d61c` | Record the 15/16 findings, tick the status list |
| `849921bfa` | Move everything out of `com.google.zxing` |
| `de2472846` | This handoff note |
| `e75a14d14` | `BENCHMARKS.md`: the measurement standard, written before the harness |
| `59711c522` | `:benchmark-android`, the L2 frame-pipeline benchmark |
| `9b7f7a50c` | `compare.py`, the pass / fail / inconclusive gate |
| `40a9ceb77` | Record the protocol a run used; drop emulator timings |
| `edef9d0a0` | First L2 result set: DN-ENG41, hd/default |
| `7da14bb76` | `noise_floor.py` |
| `2e91e8a41` | Full L2 matrix, run 1 of 2 |
| `df9a7904c` | Full matrix run 2, and the measured effect floor |
| `15522cb28` | This handoff brought up to date with the L2 work |
| `3e52a33d3` | Correct the claim that the device is permanently throttled |
| `a4963668a` | Measure the thermal state instead of guessing at it twice |
| `38137301f` | §10.6 reads through a standing device warning |
| `7ac3da651` | Thermal throttling never moves a pinned clock |
| `e0e486cf1` | L1 scored nine good decodes as misreads; fixed |
| `3bbcb94f7` | Narrow a claim about extended Code 39 |
| `bebabb402` | `setFormats` reaches the running decoder |
| `e5bd68aa9` | `YPlaneLuminanceSource`: a CameraX luma plane, no frame copy |
| `cdbdaab10` | The `FrameDecoder` seam |
| `60e69f57d` | zxing-cpp backend, both ABIs, 16 KB alignment verified |
| `1f3ac5752` | NEON checked rather than assumed |
| `b43ec34bf` | Backend selection for the benchmark |
| `94d3e4732` | Backend names resolve instead of silently skipping |
| `602d368c4` | Native backend configured to the Java effort level |
| `d780d3fa7` | **The first measured A/B of the two decoders** |
| `ebe1708b4` | **zxing-cpp is the default**, contract preserved |
| `e1ebfc2df` | L1's scoring rules moved where two runners can share them |
| `4cdff45b7` | **L1 on a device**, and the accuracy answer it produced |
| `2ecd06428` | Native results rendered the way this library always has |
| `4d4bdf232` | TRY_HARDER mapped onto the native effort flags |
| `49bd3beb3` | **The format-name bug that cost 90 corpus reads** |
| `9b79318f9` | Kotlin: `:android-integration`, plus `tools/maint/api-diff.sh` |
| `59b01b55c` | Kotlin: `:scanner` leaf types; one was dead code |
| `d050d1d0b` | Kotlin: `Intents`, every constant still a compile-time constant |
| `df6d38919` | Kotlin: the decoder seam, JNI symbols intact |
| `fc0cf7323` | Kotlin: the camera stack; **the stability loop was checking nothing** |
| `39fdc78e0` | **`:scanner` is Kotlin end to end**, with a Java compatibility test |
| `8dce7b212` | Kotlin: `:app` wired up, first 6 of 58 files |
| `d38748272` | Kotlin: the `result` package, 20 files |
| `4555376cc` | Kotlin: `history`, with the screen opened on a device |
| `edf8051da` | Kotlin: `book`, and A-09 reproduced on the way |
| `ff25f7d9b` | Kotlin: `share`, driven end to end |
| `6f6e6010a` | Kotlin: `encode`, with the rendered QR decoded back |
| `9e0b5ba5a` | Kotlin: the root package except `CaptureActivity` |
| `efa0a2dea` | **`:app` is Kotlin end to end**, and two classes moved out of `:scanner` |
| `aac6682fb` | **The release would have published an unresolvable AAR**; fixed and guarded |
| `d48de88f3` | **A-09 fixed**: https everywhere this app builds a URL, both schemes where it matches one |
| `a85e87079` | **A-10 fixed**: `<queries>` declared, and the picker asks for launcher activities |
| `c80ac0496` | **A-06, A-07 fixed**: exports go through a `FileProvider`, not the SD card root |
| `173541d91` | **A-08 fixed**: a scanned network is added by the route the platform allows |
| `69b3c7062` | **A-12 fixed**: a floor on torch toggles, because the torch moves the sensor |
| `2038009ba` | **A-16 fixed**, and with it **all of Phase 3** |
| `a19900119` | Phase 3 closed out in this file |
| `29a5c1e7d` | **Phase 6's decision record**, and the three-flavor spike it is built on |
| `e8071855d` | **A second physical device**, Android 15 — and the Phase 6 headline it overturned |
| `2e9e5ce16` | `minSdk` 21 → 23, which Phase 6 needed |
| `a90619e9a` | **CameraX adopted in `:scanner`**, behind `ScannerOptions.CameraBackend` |
| `40b6423ed` | What the adoption verified, and why CameraX is still not the default |
| `b8aeeeae9` | A frame posted behind `MSG_QUIT` was dropped rather than closed |
| `2d26c1221` | The R8 release build runs CameraX, checked on a device |
| `f4a01c025` | **L3 built and baselined** — and the Camera1 main-thread block it found |
| `7d032431b` | **A-19**: predictive back had silently disabled both back handlers |
| `e451c13fe` | **L4 harness** — soak, leak slope, GC from ART's log |
| `32e8904b3` | **L4 baseline**: CameraX costs ~60% more CPU to keep scanning |
| `4e33b4263` | The L3 and L4 findings folded back into the camera decision |
| `5da9aa797` | **The CI comparison step** — CI judges a measured result, it does not take one |
| `0e2b34992` | `:app` shipped unstripped `.so`: 35.5 MB APK against 4.7 MB |
| `81e925e3b` | **Phase 6 verified on Android 15**, and the torch defect it found |
| `812244527` | Handoff points at its own commit |
| `9af04bbf0` | What the Android 15 run left on the second device; a `private.sh` trap bug |
| `1388b3f87` | CodeQL's scan was green and its upload was refused; permissions and triggers fixed |
| `65fc7bf57` | **A real barcode read off a screen**, both camera backends; the `ó` was FNC4 |
| `343158d5f` | `EncodeActivity` opened any URI a caller named, and crashed on most of them |
| `14722d942` | Why CodeQL alert #1 is dismissed, and the code-scanning state |
| `ea0f853d6` | **The AAR taken all the way to a consumer**: resolved, minified, and it scans |
| `0a43c6972` | **0.1.0 cut in the changelog**, and its notes extracted before the publish |
| `cd1f8f16b` | The release state, recorded |
| `d759c47eb` | **v0.1.0 released**, and checked from the published side |

Nothing is uncommitted. All three workflows were green on `6d4ab48b1`: Build
`41468529666`, Instrumentation `41468529072`, and the new **L2 comparison** `41468529824`
— which validated the baseline manifest against the real committed files on a clean
checkout and reported, correctly, that no candidate was submitted. The two commits after
it carry the Android 15 work; CI's verdict on them is at the top of this file's git log,
not here, because this line has been wrong before. Nothing is released; no tag has been
pushed.

---

## Verified, with the evidence

| What | Result | Where |
| --- | --- | --- |
| `:core:test` | 602 tests, 0 failures | local |
| L1 corpus, both backends, DN-ENG41 | java 862/4/254/46/2, zxing-cpp 890/3/227/48/0 | `reports/l1-49bd3beb3-*` |
| L1 at TRY_HARDER, both backends | java 921 correct, zxing-cpp 960 | `reports/l1-49bd3beb3-*-tryharder.json` |
| L1 device harness against the host run | same five totals through the same decoder | §9.1 |
| `:scanner:testDebugUnitTest` | 69 tests: 9 coordinate-mapping, 6 hint-construction, 18 luma-plane, 8 decoder-seam, 6 backend-selection, 10 native-rendering, 3 Kotlin class-literal mapping, 9 Java source compatibility | local + CI |
| `:scanner:connectedDebugAndroidTest` | 21 tests: 35 fixed frames over 16 formats, a live format-switch test, the default-backend checks and the native backend, 0 failures | Android 14 device, Android 15 device, **and** CI emulators API 23/34/35/36 on `2e9e5ce16` (run `41423012347`) |
| 16 KB page alignment | `0x4000` on both shipped ABIs, enforced by `:scanner:verifyNativeAlignment` | local + CI |
| App install / launch / preview | works | Android 14 device, API 35 + 36 emulators |
| CAMERA grant / deny / permanently-denied | works, no request loop | Android 14 device |
| 100× open-close stability loop | 0 crashes, 0 camera errors | `reports/stability-100-b0-DN-ENG41.log`, and twice more after the Kotlin conversion: `reports/stability-100-kotlin-camera-*`, `reports/stability-100-kotlin-scanner-*` |
| `:android-integration:testDebugUnitTest` | 5 tests, written in Java so javac has to accept the Kotlin API | local + CI |
| L1 over all 1168 samples | 862 correct / 4 incorrect / 254 not-found / 46 correct-no-result / 2 false-positive | local |
| `:benchmark-jvm:test` | 9 tests on the L1 classification rules | local + CI |
| Published AAR + jar | zero `com/google` entries | local `publishToMavenLocal` |
| Implicit `com.google.zxing.client.android.SCAN` | still resolves to the app | Android 14 device |
| `:benchmark-android:testReleaseUnitTest` | 10 host tests: frame determinism, goldens, decodability | local + CI |
| `benchmark/tools/` tests | 64 tests across `l2_report`, `compare`, `noise_floor` | local + CI |
| L2 hd/default slice | 15 cases, 50 repeats, clocks locked | DN-ENG41, 72 min |
| L2 full matrix ×2 | 135 cases each, identical protocol | DN-ENG41, 40 min each |
| L2 backend A/B | 27 cases per backend, java vs zxing-cpp, 19 pass / 8 fail | DN-ENG41, `reports/l2-ab-602d368c4-*` |
| `FrameIntegrityTest` | 2 tests, 0 failures, golden hashes identical | API 34 device **and** API 35 + 36 emulators |
| `:app` Kotlin conversion, screen by screen | history, settings, help, share, app picker, encode, book search all driven by hand; 0 fatal exceptions | DN-ENG41 |
| A camera-to-result scan of a generated QR | decoded, result screen, history row, history replay | API 36 emulator, virtual-scene poster |
| `EncodeActivity`'s own QR, decoded back with `:core` | `QR_CODE  Kotlin encode check`, and `mailto:` for the email type | DN-ENG41 screenshot |
| `:scanner` API after the two classes moved out | only `DecodeFormatManager` and `DecodeHintManager` leave; nothing else changes | `tools/maint/api-diff.sh :scanner` |
| A-09: the book-search request after the https fix | reaches the network; `ConnectException` on :443 instead of okhttp's cleartext rejection | DN-ENG41, `reports/a09-cleartext-ab-DN-ENG41-20261102.log` |
| A-09: `isBookSearchUrl` on https / http / a bare ISBN | URL branch, URL branch, ISBN branch; the pre-fix build reads the https URL as an ISBN | DN-ENG41, same log |
| The four `BROWSABLE` deep links after adding https | all four resolve to `CaptureActivity`, and implicit `SCAN` still does | DN-ENG41 |
| A-10: the app picker, before and after | 79 rows → 49; 64 unlabelled vendor services gone, 34 real apps gained, 15 common | DN-ENG41, `reports/a10-package-visibility-DN-ENG41-20261102.log` |
| A-10: what the app can see through intent queries | 0 packages without `<queries>`, 21 with it, `com.android.chrome` among them | API 36 emulator, same log |
| `tools/maint/scrape-app-picker.sh` | reproduces both picker runs byte-identically, and was watched failing when the tap misses | DN-ENG41 |
| A-06: history CSV export | before, "SD card is not accessible"; after, the file is written and the Intent carries the grant | DN-ENG41, `reports/a06-a07-scoped-storage-DN-ENG41-20261102.log` |
| A-07: share the barcode image | the print service opened the `content://` URI and rendered the QR; the exported PNG decodes to its own contents | DN-ENG41, same log |
| The export sweep | a file dated a month earlier is deleted, a recent one kept | DN-ENG41, same log |
| A-08: scanning a WPA `WIFI:` barcode | before, `Unable to add network`; after, the system "Save this network?" prompt and the SSID in `cmd wifi list-networks` | API 36 emulator, `reports/a08-wifi-DN-ENG41-api36-20261102.log` |
| A-08: a WEP barcode | reported, not silently dropped | API 36 emulator, `reports/a08-wep-unsupported-api36.png` |
| A-12: six seconds of a flapping light sensor | 6 flash-mode changes before, 1 after, reproduced three times | API 36 emulator, `reports/a12-torch-flapping-api36-20261102.log` |
| `:scanner:testDebugUnitTest` after A-12 | 77 tests, 0 failures; 4 fail with the interval check deleted | local |
| `:scanner:connectedDebugAndroidTest` after A-12 | 21 tests, 0 failures | DN-ENG41 **and** API 35 + 36 emulators |
| 100× open-close stability loop after A-12 | 0 fatal exceptions, 0 camera errors | DN-ENG41, `reports/stability-100-a12-DN-ENG41-20261102.log` |
| A-16: the viewfinder at 1600x2560 | 1000×675 (26% of the height) → 675×1200; 1920×1080 unchanged at 1200×675 | API 36 emulator, `reports/a16-large-screen-portrait-api36-20261102.log` |
| A-16: the status line in a portrait window | under the frame at y 1892, not at the window's bottom; unchanged on a landscape phone | API 36 emulator **and** DN-ENG41 |
| A-16: the result screen in a portrait window | stacks, and the scanned text is on screen | API 36 emulator, `reports/a16-portrait-result-after.png` |
| `:scanner:testDebugUnitTest` after A-16 | 84 tests, 0 failures | local |
| 100× open-close stability loop after A-16 | 0 fatal exceptions | DN-ENG41, `reports/stability-100-a16-DN-ENG41-20261102.log` |
| Phase 6 spike: release APK, R8 on, two ABIs | 1,608,303 B; CameraX +558,174; without `camera-view` +207,382 | local, `reports/camera-spike-apk-size-20261102.log` |
| Phase 6 spike: CameraX's one-camera retry | 5,987 ms → 114 ms with the back-camera limiter; no change on the two-camera phone | API 36 emulator + DN-ENG41, `reports/camera-spike-camerax-validator-api36-20261102.log` |
| `YPlaneLuminanceSource` against a real `ImageProxy` | QR decoded, `rowStride=1920 pixelStride=1`, no copy | API 36 emulator, `reports/camera-spike-decode-api36-20261102.log` |
| `:scanner:connectedDebugAndroidTest` on **real Android 15** | 21 tests, 0 failures | Mix5 (API 35, SM8550), `reports/instrumentation-21-mix5-api35-20261102.log` |
| Phase 6 spike on a **second** physical device, 15 runs per flavor, 1920x1080 both sides | `onCreate`→first frame: camera1 742 ms, camerax 741 ms, cameraxlean 745 ms — **no difference** | Mix5, `reports/camera-spike-*-mix5-20261102.log` |
| Phase 6 spike re-measured on DN-ENG41 with the aspect ratio pinned | camera1 955 ms, camerax 682 ms, cameraxlean 665 ms, one session | DN-ENG41, `reports/camera-spike-*-DN-ENG41-20261102.log` |
| **Phase 6 adoption**: CameraX in `:scanner`, preview + viewfinder rectangle | drawn, and in the same place as the Camera1 path | DN-ENG41, `reports/camerax-adoption-viewfinder-{camerax,camera1}-DN-ENG41-20261102.png` |
| Phase 6 adoption: a real decode through `ScannerView` on CameraX | QR_CODE, with thumbnail and result points on the result screen | API 36 emulator, `reports/camerax-adoption-decode-api36-20261102.png` |
| Phase 6 adoption: 100× open-close on CameraX | 0 fatals, 0 camera errors — **after** the second run; the first found a crash | DN-ENG41 and API 36 emulator, `reports/stability-100-camerax-*-20261102.log` |
| Phase 6 adoption: 21 instrumentation tests on CameraX's tree | 21 tests, 0 failures | DN-ENG41, `reports/instrumentation-21-camerax-DN-ENG41-20261102.xml` |
| Phase 6 adoption: what CameraX costs `:app` | `res/` **+0 entries**, `classes.dex` +930,544 B, CameraX's own `.so` +65,332 B over two ABIs | local, `reports/camerax-adoption-apk-size-20261102.log` |
| Phase 6 adoption: the **R8 release** build on CameraX | preview and viewfinder run; CameraX's own threads present in the process, which is the check, because a silent fallback to Camera1 looks identical | DN-ENG41, `reports/camerax-adoption-release-r8-DN-ENG41-20261102.png` |
| **L3 baseline**, both backends, 12 cold launches × 2 pairs, one session each | CameraX to the window's first frame **−330 ms**; CameraX to a frame at the decoder **+200 ms**. Both reproduced across pairs | DN-ENG41, `reports/l3-*-DN-ENG41-20261102-run{1,2}.json`, read in `reports/l3-verdict-DN-ENG41-20261102.md` |
| **A-19**: back with a result on screen | before: leaves the app. After: returns to the preview. Cause isolated by A/B against `enableOnBackInvokedCallback` | API 36 emulator, `reports/a19-predictive-back-api36-20261102.log` |
| **L4 baseline**, both backends, 12-minute soaks, one session | CPU **74% vs 118%** of one core (camera1 vs camerax); +17 MB PSS, +17 threads; **no leak** in either | DN-ENG41, `reports/l4-*-DN-ENG41-20261102.json`, read in `reports/l4-verdict-DN-ENG41-20261102.md` |
| **Phase 6 on Android 15**, minified build, both backends | preview live, camera open, viewfinder rectangle **identical to the pixel** (laser rows 556-558, x 642-1838) although the stream sizes differ (1920x1080 vs 2400x1080); decode thread 0.71 s CPU per 10 s | mix5 API 35, `reports/camerax-android15-mix5-20261103.md` |
| **100x open/close on Android 15**, each backend | 0 fatals, 0 camera errors, awake at both ends | mix5, `reports/stability-100-{camerax,camera1}-mix5-20261103.log` |
| **21 instrumentation tests on the CameraX tree, Android 15** | 21/21, including the 12 native-decoder ones | mix5, `reports/instrumentation-21-camerax-mix5-20261103.log` |
| **The torch**, first test anywhere | `FrontLightMode.ON` set `flash.mode=TORCH` on Camera1 and **`OFF` on CameraX**; `AUTO` set `TORCH` on CameraX, which is what isolated the cause. After the fix both read `TORCH`, preview mean luminance 35.2 and 34.4 against 2.0 broken | mix5, `reports/torch-frontlight-on-mix5-20261103.png` |

**All four layers now have numbers, and they are baselines, not claims.** The only
comparison anyone has drawn from them is the decoder A/B (§10.6c) and the two camera
readings above; nothing has been optimised. L1 timings remain host-JVM diagnostics and
must never be quoted as an Android result. **First-scan latency is still unmeasured** —
L3 covers launch, camera open and first frame, and the scan target needs a fixed,
photographed scene that does not exist. Power is unmeasured too: DN-ENG41 on charge never
moves its battery counters.

---

## Environment

- Host: macOS 27.2 arm64, JDK 25 (Android Studio JBR); Gradle toolchain targets JDK 17
- Android SDK at `$ANDROID_HOME` = `/Users/dm/Library/Android/sdk`, platform 36, build-tools 36.0.0
- Physical device: **DN-ENG41, Android 14 (API 34), serial `T3515NNFBC000055`, no GMS**
  — the no-GMS verification point, and where every L2 number in this repository comes from
- Second physical device: **Xiaomi 25010PN30C "mix5", Android 15 (API 35), SM8550**, on the
  user's home LAN and reached through a root agent rather than adb. See "The second device"
  below. It is the Android 15 verification point; it is **not** an L2 device, because
  nothing here locks its clocks and it is reached over a link that drops
- Third physical device: **DN-ENG81, Android 14 (API 34), serial `ZT812U128GB254233100034`,
  1200x1920** — not a test target. It is the **panel**: it displays a generated symbol for
  DN-ENG41 to read off its screen, which is what closed the "no barcode has ever been in
  front of a phone" gap. `tools/maint/scan-off-screen.sh` drives both ends
- Emulators created for this work: AVDs `zxing_api35` (Android 15) and `zxing_api36`
  (Android 16), Pixel 7 profile, arm64. Start with
  `emulator -avd zxing_api36 -no-snapshot -no-audio -camera-back virtualscene`
- No Android **16** physical device is available, and neither device has 16 KB pages, so
  the alignment the AAR is built and checked for still has no hardware behind it. The README
  says so.

### The second device, and how to reach it

**Xiaomi 25010PN30C (`xuanyuan`), Android 15 / API 35, HyperOS `OS2.0.207.0.VOACNXM`,
SM8550, arm64-v8a, five cameras, `getconf PAGE_SIZE` = 4096.** The first Android 15
hardware this project has been able to touch.

**It is not reached over adb, and no ADB listener is opened on it.** It runs a root agent
that exposes a shell and a file transfer over a private control channel; APKs go in through
a push endpoint and commands through an exec endpoint. **The hostnames, addresses and exact
invocation are deliberately not in this file** — this repository is public. They are in
`docs/maint/private/second-device.md.enc`, encrypted; see "Encrypted notes" below.

What is worth recording here, because none of it is a secret and all of it will be hit
again by anyone driving an Android device over a non-adb channel:

1. **Put the command in a file, never inline.** That exec path evaluates the string twice,
   so a `$f` in a shell loop arrives already expanded to nothing. Build the request body
   with a quoted heredoc and `curl --data-binary @file`, and put anything with variables
   into a script on the phone — which is what `tools/maint/camera-spike-run-ondevice.sh`
   is for.
2. **`cmd appops set <pkg> CAMERA allow` as well as `pm grant`.** HyperOS revokes the
   runtime grant again once an app opens the camera without a user-visible prompt, and
   **CameraX's response to that is a silent 700 ms retry loop, forever** — no exception, no
   error callback, just no frames. Fifteen runs reported nothing at all before the logcat
   line `cannot open camera 0 without camera permission` explained it.
3. **`svc power stayon true` before any loop.** The screen times out mid-run and a paused
   activity stops getting frames; it reads as a backend failure.
4. **`wm dismiss-keyguard` after `input keyevent KEYCODE_WAKEUP`.** An activity started
   under the lockscreen does not resume.
5. **`{"live":false}` from the control channel is idle, not down.** The client on the
   router creates a session lazily on the first real request and releases it after a
   minute so the phone's own web UI can take over. A previous session read that reply as
   "unreachable" and recorded Android 15 as unverified for a whole day's work. Send a
   real command before concluding anything.
6. **`svc power stayon true` does nothing unless the phone is plugged in**, and this one
   usually is not — `AC powered: false`, battery 100%, discharging. That flag is
   `STAY_ON_WHILE_PLUGGED_IN` and nothing more. A loop stays awake because its own
   `input keyevent` resets the idle timer; anything that does not generate input needs
   `settings put system screen_off_timeout` instead. Check `mWakefulness`, do not assume
   it — `tools/maint/stability-loop-ondevice.sh` now reports it at both ends, because a
   run that cycles a stopped activity looks exactly like a clean run.
7. **`cmd appops set <pkg> CAMERA allow` has to be repeated after every install**, not
   once per device. A `pm install -r -g` leaves `Uid mode: CAMERA: ignore` and the camera
   never opens: resumed activity, live process, `Active Camera Clients: []`, nothing in
   the log.
8. **The link is slow and drops.** Roughly 24 KB/s on a bad stretch, and one 16 MB transfer
   died halfway. Strip the APK to the device's ABI and re-sign it (`zip -d <apk>
   'lib/x86_64/*' 'lib/armeabi-v7a/*'` then `apksigner sign --ks ~/.android/debug.keystore`)
   — that halved the instrumentation APK from 12 MB to 6.3 MB — keep SSH alive with
   `-o ServerAliveInterval=15`, and retry rather than assume a failure is real.

Nothing was installed on that phone beyond test APKs; the ADB listener opened during this
work was closed again and the firewall rules that scoped it were removed.

### Encrypted notes

`docs/maint/private/` holds the few things that must not be public but must still travel
with the repository: hostnames, private addresses and the exact commands that reach the
second device. Each file is encrypted at rest and committed as ciphertext.

```sh
tools/maint/private.sh decrypt docs/maint/private/second-device.md.enc   # prints plaintext
tools/maint/private.sh edit    docs/maint/private/second-device.md.enc   # decrypt, $EDITOR, re-encrypt
tools/maint/private.sh check                                             # before committing
```

**The passphrase is not in this repository and must not be**, including in CI. It is in the
macOS Keychain under the service name `zxing-android-private`, which the script reads
automatically; `security find-generic-password -s zxing-android-private -w` prints it, which
is how it gets onto a second machine. A GitHub Actions secret would not work for this:
secrets cannot be read back by a person, so a second laptop cloning the repo still could not
decrypt, and routing plaintext out through a workflow artifact would publish it — artifacts
of a public repository are downloadable by anyone. **No CI job needs these files.**

Three things stop a mistake, because an ignore rule alone only helps someone who does not
type `-f`: plaintext under `docs/maint/private/` is git-ignored; `private.sh check` fails on
a staged plaintext or on a `.enc` that is not actually encrypted; and `build.yml` runs that
check on every push. The check was watched failing on purpose before it was committed.

`docs/maint/private/README.md` documents the scheme and is public on purpose.

### Traps, and the runbook for each

1. **DroidLens global Gradle init script.** `~/.gradle/init.d/droidlens.gradle` injects a
   third-party SDK into debug builds of any project listed in
   `~/.gradle/droidlens/projects.txt`. It registered this project once and broke the
   build with `minSdkVersion 21 cannot be smaller than version 24 declared in library
   kakacat.android.helper:runtime-network`. The user has since disabled auto-attach. If
   it ever comes back, the symptom is that error or an unexpected SDK inside the APK;
   check `unzip -l app-debug.apk | grep kakacat`.
2. **Benchmarking DN-ENG41 needs three commands first**, in this order:
   `adb root` (it is a `userdebug` build and root works),
   `./gradlew :benchmark-android:lockClocks`, then `adb shell svc power stayon true`.
   Without the lock every result carries `UNLOCKED_CLOCKS` and §10.6 blocks on it.
   **`unlockClocks` restores the device by rebooting it** — a phone left locked runs at
   1344 of 2400 MHz and will feel slow until someone reboots it.
3. **The device was at thermal status SEVERE for the whole benchmarking session, and
   the cool-down wait never completed.** Its skin throttling threshold is 40.0 °C. Over
   several hours of benchmarking the skin sensors read ~46 °C and never came down, so
   Microbenchmark slept the full 270 s before every benchmark and ran anyway — 67 of the
   first baseline's 72 minutes. `-Pl2.throttleWait=0` switches the waiting off, not the
   throttling, and the result records `THROTTLE_WAIT_DISABLED`. Full matrix: 11 hours
   with the wait, 40 minutes without, same thermal state either way.

   **Tested, and on AC power at rest it does not clear the threshold.** Evidence:
   `reports/thermal-cooldown-DN-ENG41-20261031.log` — rebooted, idle, plugged in, one
   sample a minute for 45 minutes. `Thermal Status: 3` for all 45 samples.

   Two things that log settles, because both were guessed at wrongly in between:

   - **One skin sensor pins and stops updating.** It read 47.207 for all 45 samples —
     one distinct value in 45 minutes. Before the reboot it had been pinned at 40.116.
     So this is reproducible behaviour and not a one-off, and it is not a configured
     constant either: 40.116 turns out to be a point on the *other* sensor's cooling
     curve, so the pinned sensor had frozen at a real value it passed through.
   - **The other sensor decays normally and floors at exactly 40.0** — 42.383, 41.511,
     40.988, 40.813, 40.348, 40.232, 40.116, 40.0 — and then sits on the threshold
     value itself and stops.

   **But with the clocks locked it does not matter.** The thermal governor never moves a
   pinned clock on this device — 100% residency at the locked frequency across a 5-minute
   run at SEVERE, zero ticks anywhere else:
   `reports/thermal-vs-locked-clocks-DN-ENG41-20261031.log`. §10.6 reads the warning
   through on that basis. Note that `lockClocks` tries to stop `vendor.thermal-engine`
   and this device refuses, so that measurement was taken with throttling fully active.

   What is still untested is the device physically unplugged and actively cooled, which
   needs hands. Everything reachable over adb has been tried.
4. **A Microbenchmark run stalls when the screen goes off.** `IsolationActivity` stops
   making progress and says nothing: no failure, no timeout, the process just stops using
   CPU. DN-ENG41 locked seven minutes into the first L2 run and sat there. Run
   `adb shell svc power stayon true` before any device benchmark; to tell a stall from
   slow work, watch fields 14/15 of `/proc/<pid>/stat`.
5. **`third_party/zxing-cpp` is a git submodule.** A fresh clone without
   `git submodule update --init --recursive` fails at CMake configure time; the
   `CMakeLists.txt` says so in the error rather than leaving a missing-header pile. All
   four workflows check out with `submodules: recursive`.
6. **The native build needs the pinned NDK**, `29.0.14206865`, set in
   `gradle/libs.versions.toml` and installed by CI. It is pinned because "whichever NDK
   was lying around" is not something a decoder comparison can hold constant.
7. **Kotlin has no plugin and no version of its own.** AGP 9 compiles Kotlin itself:
   drop a `.kt` file into `src/main/kotlin` and `compileDebugKotlin` appears, with no
   `plugins {}` entry anywhere. Adding `org.jetbrains.kotlin.android` on top of that does
   not merely duplicate work, it **fails the build** — "Cannot add extension with name
   'kotlin', as there is an extension already registered with that name" — and asking for
   it by version fails earlier still, because the plugin is already on the classpath at an
   unknown version. So there is no `kotlin` entry in `gradle/libs.versions.toml` and there
   must not be one. The compiler is whatever AGP ships: **AGP 9.4.1 → Kotlin 2.2.10**,
   readable as the `kotlin-stdlib` on any module's compile classpath. Pinning AGP pins
   Kotlin. A module that compiles Kotlin needs one thing added by hand, a `kotlin { }`
   block setting `jvmTarget` to match `compileOptions`; AGP fails the compile task on a
   mismatch rather than warning, so this is checked rather than assumed.
8. **A module that packages native code but does not build any still needs
   `ndkVersion`.** `:app` had none, so AGP could not find `llvm-strip` and packaged every
   `.so` **unstripped**: `arm64-v8a/libzxingnative.so` at 18.6 MB against 1.8 MB stripped,
   and the whole APK at 35.5 MB against 4.7 MB. It announces this once, at **info** level
   — `Unable to strip the following libraries, packaging them as they are` — and nothing
   fails. Found only because a 35 MB APK will not cross a 20 KB/s link in a reasonable
   time. The AAR was never affected; `:scanner` sets `ndkVersion`. If an APK is
   implausibly large, run `./gradlew :<module>:strip<Variant>DebugSymbols --info` and read
   for "missing strip tool for ABI".
9. **Emulator `wm size` changes persist.** The Android 16 large-screen reproduction ran
   `wm size 1600x2560 && wm density 240`. Both were reset afterwards, but if an emulator
   behaves oddly, `adb shell wm size reset && adb shell wm density reset`.
10. **`am start --grant-read-uri-permission` grants the intent's data and ClipData, not
    an extra.** Three attempts to hand `EncodeActivity` a readable `EXTRA_STREAM` through
    MediaStore all came back `SecurityException` and looked exactly like the new URI guard
    misfiring. Put the URI in `-d` as well as `--eu`, or drive a provider that grants on
    its own terms — `content://com.android.contacts/contacts/as_vcard/<lookup>` does, and
    a contact can be created from the shell with `content insert`.
11. **An `ó` out of a Code 128 is not a decoder bug.** U+00F1..U+00F4 are the FNC1..FNC4
    escapes on both sides of this library, and they mean opposite things: `Code128Writer`
    reads them in its *input* as "emit an FNC here", while the readers emit them for
    *decoded* FNC content — `ó` is an FNC4-shifted character, extended ASCII. So
    `encode("óóóó1234óóabózz")` decodes back to `1234abzz`, and a scan that returns a
    string full of `ó` is reading a symbol that genuinely carries FNC4. This cost a
    morning: such a scan was reported as CameraX corrupting frames, and both camera
    backends return it identically, 3/3 each
    (`reports/real-barcode-decode-DN-ENG41-20261103.md`). Check the content before
    blaming the transport.

---

## Decisions already made — do not relitigate without a reason

- **`minSdk` 21** (up from upstream 19). Rationale in `AUDIT.md` §2.
- **`compileSdk` / `targetSdk` 36**, AGP 9.4.1, Gradle 9.7.1, all pinned.
- **Gradle is the only build.** The Maven poms are gone; they could not build anything
  Android on a JDK from 9 onward.
- **Only the Android side is maintained.** `javase`, `zxingorg` and the appspot site were
  removed at the user's instruction.
- **This fork maintains its own decoder.** `core/` is forked, renamed into
  `io.github.kakakakacat.zxing`, and published. Nothing at runtime depends on an upstream
  ZXing artifact. Upstream fixes come in through
  `tools/maint/sync-core-from-upstream.sh`.
- **`core/` stays Java, and is not deleted now that native is the default.** The reasoning
  is in `decisions/0002-core-language-and-upstream-sync.md`: it is the `api` surface of the
  AAR, the only decoder on ABIs with no native build, and the only implementation of result
  parsing and encoding. Converting it to Kotlin would cost ~35 upstream fixes a year, most
  of them hardening on an attacker-controlled path, and buy nothing else.
- **Sync `core/` from upstream quarterly, and on any upstream release.** Run
  `tools/maint/sync-core-from-upstream.sh`; it records the upstream SHA in the commit
  message. As of 2026-11-04 this fork is **0 commits behind** `upstream/master`
  (`56b8a9a7b`, 2026-10-28) and 149 ahead. A sync that finds nothing is cheap; a sync never
  run is how a vendored fork becomes a permanent one.
- **Namespace:** everything is `io.github.kakakakacat.zxing.**`; `applicationId` is
  `io.github.kakakakacat.zxing.android`. Publishing `com.google.zxing.*` class names from
  a second groupId is a duplicate-class build failure for consumers.
- **The Intent action strings are frozen** on `com.google.zxing.client.android.*`. They
  name a protocol, not a class. The rename tooling only touches `package`/`import` lines
  so this cannot be undone by accident.
- **Emulator timings are not used.** An emulator runs on the host's cores and distorts
  the ratio *between* stages, not merely the magnitude — `binarize` is memory-bandwidth
  bound, `decode` is branch-heavy. `compare.py` treats `EMULATOR` as invalidating and
  will not let it be waived. Emulators run the correctness gate here, nothing else.
  Rationale in `BENCHMARKS.md` §10.7 step 4.
- **A standing device warning is read through, not waived** (§10.6). On a device whose
  effect floor was measured under it, and when both sides of a comparison carry exactly
  the same set, `THERMAL_THROTTLE`, `THROTTLE_WAIT_DISABLED` and
  `NO_SUSTAINED_PERFORMANCE_MODE` no longer block — measured: with the clocks pinned the
  thermal governor never moves them, the floor already contains whatever they cost, and
  both sides pay it equally. `UNLOCKED_CLOCKS` is excluded because it is
  avoidable. Every read-through is named in `verdict.json` and in the report header.
- **The effect floor is measured, not chosen**: 3% for cases at or above 100 µs, 10%
  below. From two full-matrix runs of identical code. Re-measure after any change to the
  device or the protocol; it is two 40-minute runs and one command.
- **Vendor thermal thresholds are never raised.** It would make the warnings disappear
  and produce numbers the shipped product never delivers, with nothing in the result file
  to show it. `BENCHMARKS.md` §6. Locking clocks is the opposite kind of change: it pins
  a sustainable operating point and makes the numbers *slower*.
- **Kotlin has no package-private, and the conversion accepts the widening.** A
  package-private Java class becomes `internal`, which the Kotlin compiler enforces for
  Kotlin callers and the JVM does not enforce at all: in bytecode it is public. That
  asymmetry has a sting in it across a module boundary — see rule 9 under item 6, and
  the two classes it moved out of this module. So
  `JavaFrameDecoder`, `NativeFrameDecoder` and the rest of the module's internals are
  reachable from a consumer's Java code where they were not before. The alternatives were
  worse — leaving them in Java forever, or promoting them to real public API — and the
  classes are absent from the README, from `MIGRATION.md` and from every published
  example. Three details that follow from it:
  - Members those classes expose to Java **inside** this module (the unit tests, the
    benchmark module) must be plain `public`, not `internal`: an `internal` *function* is
    name-mangled (`foo$scanner`) and Java cannot call it, while an `internal` *class* is
    not. So the mangling protects nothing here and only breaks the callers.
  - Kotlin emits `access$...` bridges, public and static, wherever a class touches its
    companion's private members. They are unavoidable and they are not API.
  - `tools/maint/api-diff.sh` prints all of this, so the widening is a thing that was
    read and accepted per commit rather than discovered later.
- **Camera1 is still the default, but it is no longer the only backend.** Phase 6 adopted
  CameraX alongside it; `ScannerOptions.cameraBackend` chooses. The paragraph below is the
  original note and the reasoning it records still applies to why the Camera1 code is still
  here at all.
- A Camera2/CameraX decision record is Phase 6, and must not be
  presented as a performance improvement on its own.

---

## Start here if you are new to this file

**Two P0 items from the 2026-11-03 review are done, `3785004b1` and `13c3e4221`.**

**P1 §6, embedding, is done too.** Four things, and the fourth was only found because the
test host refused to be fooled.

- **`unbindAll()` is gone from the CameraX backend.** It takes down every use case on the
  process-wide provider, a host application's own preview and capture included. A library
  has no business doing that, and nothing in a standalone scanner app would ever notice.
- **`ProcessCameraProvider.configureInstance` is opt-in** — process-wide, once per process,
  and it was telling CameraX that only the back camera exists on behalf of whatever app
  embedded this. `ScannerOptions.setCameraXProcessConfiguration(true)`; this fork's own app
  sets it, so the 5.9 s one-camera validation saving is kept where it was measured.
- **The scan rectangle comes from the view's size, not the display's.** `ViewfinderView`
  draws it in the view's own coordinates, so the two were the same rectangle only for a
  full-screen scanner.
- **`resume()` on a view already on screen never opened the camera.** The surface callback
  was added inside `resume()` and only when no surface existed yet, and `SurfaceHolder`
  does not replay `surfaceCreated` for a callback added later. A tab, a reused dialog or a
  "scan" button all produce that order. Silent: no error, no camera, no log.

**And the lesson from how that one surfaced.** The first run of the four new embedding
tests passed three and failed one. The three were passing without a camera ever
opening — `capabilities()` and `isBound()` both answer perfectly well for a scanner that
never started. Making the helper *fail* rather than skip when the camera neither opens nor
reports an error turned three green into three red, and the shared cause was the surface
callback. **A test that tolerates "nothing happened" is a test that reports nothing
happened as success.**


**`Barcode::setReaderOptions` is private, and only `ZXing::ReadBarcodes` is its friend.**
That matters to anything that drives `MultiFormatReader` itself rather than going through
`ReadBarcodes` — which the decoder now does, to stop rebuilding the readers on every
frame. The only thing those options carry into a result is the `TextMode`, and a result
without them answers `text()` with zxing-cpp's default **HRI**, which replaces every
non-graphical character with a stand-in. So the symptom would not have been a crash or a
miss: it would have been every GS1 separator and every control character quietly coming
back as `<GS>`, on the exact payloads this fork spent §9.1 getting right. The text mode is
passed explicitly at the JNI boundary now, so no path depends on that state.
`aGs1SeparatorSurvivesInTheText` is the test that would have caught it, and did run.

**The paired run is the measurement; the committed result from last session is not.**
Comparing the reader-reuse change against the previous session's committed native result
made every 2D case look 4–5% *slower*. Re-running the unchanged code in the same session
reproduced those same elevated numbers, so it was session drift. On this device a
before/after taken across two sessions is not a before/after — and the trap is that the
drift was small enough to read as a real regression rather than as noise.

**The JNI bridge was handing modified UTF-8's function standard UTF-8.** Every string the
native backend returned went through `NewStringUTF`, which takes *modified* UTF-8, and
zxing-cpp produces the standard kind. The two differ in exactly the two places a payload
is most likely to land: U+0000 is one byte rather than two, and a supplementary-plane
character is one four-byte sequence rather than a surrogate pair of three-byte ones. This
file never asserted what the runtime did with that, and now it does not have to: the
conversion is explicit, validated, and `NewString` is called with a length. An embedded
NUL stops truncating and an emoji arrives as itself, checked on a device against the Java
backend on the same frames.

**Mapping the result metadata found something worth knowing.** zxing-cpp knows the ECI
modifier offset and then does not apply it — `Barcode::symbologyIdentifier()` calls
`SymbologyIdentifier::toString()` with `hasECI` defaulted to false — so a QR carrying an
ECI came back `]Q1` where ISO/IEC 15424 and this fork's own `DecodedBitStreamParser` both
say `]Q2`. It was found only because the new test makes the two backends compare their
metadata on one symbol. Corrected for QR, where the offset is 1 and the base modifiers
agree; for a symbology whose offset zxing-cpp does not expose there is now no identifier
rather than a wrong one. **If a backend looks like it disagrees about a standard field,
check whether the other one is simply not applying a rule it knows.**

**The decode thread could hang the main thread for ever, and two configurations reached
it.** The decoder was built inside the `DecodeHandler` constructor and the latch counted
down after it, so an initialisation that threw never reached the notification —
`DecoderBackend.NATIVE` on an ABI with no native build, or a format selection zxing-cpp
cannot be asked for, both throw there. `getHandler()` had no timeout. The thread now has
four states and announces both terminal ones; `ScannerView` is *told* the decoder is up
rather than blocking on it, and the 500 ms `join` in teardown is gone (neither backend's
`close()` can pull a buffer out from under a decode in flight — a Camera1 frame is a heap
array, a CameraX `ImageProxy` keeps its reader open until closed).

**The decoder is injectable now**, through `DecoderThread.DecoderFactory`, which is the
only way those states can be produced on demand. Nine lifecycle tests drive them. **Two of
those tests passed for the wrong reason first time**: their fake frame returned a null
source, which the decode thread answers as a miss without ever calling the decoder, so
"the decoder threw" was never tested. A fake that is too convenient tests the fake.

Evidence for both: 44/44 `:scanner` instrumentation tests on DN-ENG41, the same 44 again
with CheckJNI on (framework restarted, "CheckJNI is ON" in logcat, no warning of any
kind), 100 open/close cycles with no fatal, and a real CODE_128 read off DN-ENG81's screen
3/3 on each camera backend. **Not** verified: that no main-thread wait remains, by trace.
The blocking calls are gone by construction and no systrace was taken.

**`scan-off-screen.sh` broke the moment publishing started writing javadoc jars** into
`core/build/libs` — its `head -1` picked `core-*-javadoc.jar` and the helper would not
compile. Fixed there; worth remembering for any other script that globs that directory.

**CI had not run on any of this until 2026-11-02, and now it has.** The remote sat at
`de2472846` from 2026-10-30 while 70 commits accumulated locally — the whole benchmark
toolchain, the native decoder, the Kotlin conversion, all of Phase 3, Phase 6 and the
minSdk bump. Every "local + CI" in the table below was therefore a claim about a CI run
that predated the thing it described. They are now true: run `41423012309` (Build) and
`41423012347` (Instrumentation) are green on `2e9e5ce16`, **first try, nothing broken** —
API 23/34/35/36 each 21 tests, 0 failures, 0 skipped, the API 23 leg being the new one the
minSdk bump created.

The lesson is not "it was fine". It is that nobody could have known it was fine, for three
days of work, and the file said otherwise. **Push before writing "CI" in this table.**

**There is a second physical device now, and it is an Android 15 one.** A Xiaomi
25010PN30C (`xuanyuan`, API 35, SM8550, five cameras, 4 KB pages), reached through a private
root-agent control channel rather than adb — **no ADB listener is opened on it**; APKs go in
through a push endpoint and commands through an exec endpoint. The traps that device has are
in "The second device" below; how to reach it is in the encrypted notes beside it.

The first thing it bought: **the 21 `:scanner` instrumentation tests pass on real Android 15
hardware**, closing a README item open since Phase 2
(`reports/instrumentation-21-mix5-api35-20261102.log`).

The second thing it bought was a correction.

**Phase 6 has a decision record, and it has been accepted and carried out.**
`docs/maint/decisions/0001-camera-backend.md` compares Camera1, Camera2 and CameraX and
recommends CameraX, through `benchmark/camera-spike`, which builds the same application
three times over three camera stacks so that a delta belongs to the stack and nothing else.

**Its first version led with latency and that was wrong.** On DN-ENG41 CameraX reaches a
decodable frame 273 ms (29%) sooner; on the Mix5 the two are **1 ms apart over 15 cold
launches each**, ranges almost fully overlapping. So the recommendation now rests on
maintenance and deprecation — 1,285 lines of Camera1 quirk handling against a library that
ships a per-device quirk database — and not on speed. CameraX costs +558 KB, of which
351 KB is `camera-view` alone and avoidable.

**Two devices disagreed about the headline of a record written from one.** That is the
same rule `BENCHMARKS.md` already applies to L2 and it applies to spikes too.

**That question is answered: the user chose minSdk 23**, and the bump is in
(`:scanner`, `:android-integration`, `:app`, both benchmark modules, and the CI
instrumentation matrix, whose API 21 leg became unrunnable). Android 5.0 and 5.1 are
dropped; nothing has been released, so no consumer is being moved off a version they had.
Three code paths the bump made unreachable went with it — the install-time-permission
branches in `ScannerView` and `CaptureActivity`, and the version check around the
browser-bookmark button in `ShareActivity`, which Android 6.0 had already made useless.
`api-diff.sh :scanner` reports no Java-visible API difference, and the 21 instrumentation
tests still pass on DN-ENG41.

**Phase 6 adoption is done, and Phase 5 item 8 with it.** `:scanner` carries both camera
stacks now: `ScannerOptions.CameraBackend` picks one, the same shape `setDecoderBackend`
already had, and `CAMERA1` is still the default value. The CameraX path binds
`Preview` + `ImageAnalysis`, draws into the `SurfaceView` `ScannerView` already owns rather
than taking `camera-view`, limits CameraX to the back camera before it initialises, and
feeds `YPlaneLuminanceSource` the luma plane in place — so item 8's "no full-frame copy"
is finally the path the library actually runs, and not a class with synthetic tests.

Verified on DN-ENG41 and the API 36 emulator: preview, the viewfinder rectangle landing in
the same place as on Camera1, a real QR decoded with its thumbnail and result points, 100
open/close cycles and the 21 instrumentation tests. `reports/camerax-adoption-*`.

**The default has not moved, and what was holding it is gone.** Android 15 was verified
on 2026-11-03, and on the same day **a real barcode was decoded off a real camera through
`ScannerView`, on both backends** — the check this paragraph used to name as missing. A
third device turned up (**DN-ENG81**, 1200x1920) and a phone can read a symbol off its
screen, so it needed no hands after all: `tools/maint/scan-off-screen.sh` is the harness
and `reports/real-barcode-decode-DN-ENG41-20261103.md` is the result. `CODE_128` 6/6 and
`EAN_13` on each backend, with `ScannerTrace`'s `zxing.first.scan` span firing for the
first time in this repository's history.

**So nothing factual is left — only the decision**, and the one measurement arguing
against it is L4's ~60% more CPU to keep scanning. That is a trade-off for the user to
weigh, not a gap to close.

**The 100-cycle loop earned its keep.** Its first CameraX run crashed the app in `onPause`:
pausing while `ProcessCameraProvider.getInstance` was still resolving left the backend's
`LifecycleRegistry` at `INITIALIZED`, and `LifecycleRegistry` throws rather than go straight
from there to `DESTROYED`. One cycle in a hundred, a fatal, and a single launch would never
have shown it. Fixed by stepping through `CREATED`.

Four things the spike turned up that are worth knowing whatever is decided:

- **CameraX spends five seconds retrying when a device has only one camera.** It verifies
  that both lens facings exist. `setAvailableCamerasLimiter(DEFAULT_BACK_CAMERA)` removes it:
  5,987 ms to 114 ms on a one-camera emulator, and no change at all on the two-camera phone.
- **`YPlaneLuminanceSource` now has a real `ImageProxy` behind it.** Phase 5 item 8 built it
  for CameraX and had only ever fed it synthetic planes; it decoded a real QR out of a real
  camera buffer, no copy, on both CameraX flavors.
- **DN-ENG41's back camera reports no flash unit and auto-focus OFF only**, from
  `dumpsys media.camera`. A-12's torch control and `AutoFocusManager` are therefore both
  unexercised on it. The Mix5 has five cameras and can test both.
- **CameraX answers a revoked CAMERA permission with a silent 700 ms retry loop that never
  ends and never reports.** Fifteen Mix5 runs produced no result and no exception before the
  logcat line `cannot open camera 0 without camera permission` explained it. Whatever
  replaces `ScannerView` has to keep reporting `ScannerError.PERMISSION_DENIED` rather than
  inherit that silence.

**Phase 3 is closed: all seven carried-forward defects are fixed**, each with its own
commit and its own before/after on a device or an emulator. See "Phase 3" below; the
three lessons worth carrying past the individual fixes are named at the top of it.

**The Kotlin conversion is finished, Phase 5 item 6.**
`:android-integration`, `:scanner` and `:app` have no Java source left; the only Java in
the tree is `:core` (synced from upstream, stays Java on purpose) and the `:scanner`
tests that are written in Java deliberately, so javac has to accept the Kotlin API. Item
6 below carries the eight rules the conversion settled and the one trap that only showed
up at the very end. Nothing was half-converted: every commit builds, lints and runs on a
device or an emulator.

**And a warning that generalises past Kotlin.** `tools/maint/stability-loop.sh` had been
reporting "100 cycles, 0 crashes" while starting nothing at all. It still named
`com.google.zxing.client.android.CaptureActivity`, which moved at `849921bfa`; every
`am start` failed into `/dev/null`. **A verification tool that is not itself verified is
worse than no tool** — it was fixed, hardened to fail when the activity does not come
up, and the fix checked by running it with the old name. Looking for more of the same
turned up **a release-blocking defect in `release.yml`**, now fixed and guarded — see
"Release and distribution" below. Treat a clean result from an unexercised script as unproven until
you have watched it fail on purpose.

**The native decoder ships by default, and both halves of that decision are now
measured and agree.** zxing-cpp is 56–82% faster on 2D and on all-format misses (§10.6c)
*and* reads 30 more of the 1168 corpus samples than the Java decoder, with fewer wrong
answers and no false positives (§9.1). Nothing open here; it is recorded so the next
person does not re-litigate it.

**What that took, and the lesson in it.** The first corpus run said the opposite — 862 →
762 correct — and both halves of that deficit turned out to be bugs on *this* side of the
seam, not properties of zxing-cpp:

> 1. **Rendering** (`2ecd06428`). 61 samples where the right symbol came back spelled
>    zxing-cpp's way: UPC-E expanded, Codabar with its guards, control characters
>    escaped, GS1 parentheses dropped.
> 2. **Format names** (`49bd3beb3`). zxing-cpp returns the *variant* it read, never the
>    symbology you asked for, and an unmapped name was silently reported as "nothing
>    found". That alone cost 90 reads — every RSS-14 sample and all the stacked
>    RSS-Expanded ones — and this file described it as a DataBar capability gap in
>    zxing-cpp for a day. An unmapped name is now logged.

**So: a decoded symbol thrown away by a name table looks exactly like a decoder that
cannot read that symbology.** If a backend ever looks weak at one format, check the
mapping before believing it.

**What is left against zxing-cpp**, small and real: PDF417 reads 50 of 56 against the
Java decoder's 56, and UPC-E 37 of 55 against 40. Both are ordinary misses, not names.

**And one thing that bites anyone measuring here:** host L1 and device L1 are not
interchangeable. 231 of the 1168 samples reach the decoder as different pixels, because
`ImageIO` gamma-encodes greyscale PNGs and ignores transparency where Android does
neither. Device against device, always.

Everything else is below.

## The third device, and why it is not three gaps closed

A Samsung **SM-S948B** — Android 16 / API 36, SM8850, arm64-v8a — was reachable for about
an hour over a cloud ADB tunnel (`rdb`) on 2026-11-03. It produced **21/21 `:scanner`
instrumentation tests on real Android 16 hardware**, including the 12 native-decoder ones,
which is the first Android 16 claim here not made on an emulator. Then the tunnel became
unusable and the run was abandoned. `reports/android16-SM-S948B-20261103.md`.

**It is Qualcomm and its `PAGE_SIZE` is 4096**, so it closed neither the non-Qualcomm gap
nor the 16 KB page gap, whatever "Android 16 flagship" suggests. `id -u` was 2000, so no
root, which rules out L2/L3/L4 by rule and not just by inconvenience — §10.6 turns an
unlocked-clock L2 run into `inconclusive`.

**The trap worth keeping:** `adb connect` does **not** repair a transport that has gone
`offline` — it answers `already connected` and leaves the dead one in place. A retry loop
built on it tried the same broken connection five times per step and reported a working
device as unreachable, failing all 34 steps. `adb disconnect` *then* `adb connect`.
`tools/maint/android16-checks.sh` does that now, and eight consecutive probes passed where
zero had.

## What is left

Ordered as the user asked for it. `docs/maint/PROJECT_DETAILS.md` carries the same list as
tickboxes, in both languages — it used to be the README's, and moved on 2026-11-03.

### Phase 4 — all four layers now exist and are baselined

**L4 is built and baselined**, and its headline is the one the Phase 6 decision record
explicitly listed as unmeasured: **CameraX costs about 60% more CPU to keep scanning** —
118% of one core against Camera1's 74%, ranges 103–130 against 64–88, same device, same
session, same decode path. Also +17 MB PSS, +12 MB native heap and +17 threads, at an
identical Java heap and slightly *fewer* GCs, so it is not memory pressure and not GC.
Neither backend leaks over twelve minutes and neither degrades across the run.

Read with L3's two findings, the backends were not simply better and worse: **Camera1 was
cheaper to run and janked the launch by ~400 ms of blocked main thread; CameraX launched
smoothly and cost more to keep running.** That is three independent measurements pointing
the same way about CameraX's frame path on this hardware — and **the half that favoured
CameraX is gone**: the block was fixed on 2026-11-03 and Camera1 now reaches a drawn window
first. What is left of the comparison all points one way.

`reports/l4-verdict-DN-ENG41-20261102.md` has the whole of it. Two conditions it records
rather than hides: this device sits at thermal status 3 permanently, so every L4 number
from it is a throttled number; and on charge the battery never moves, so drain is not
measured.

**Re-run 2026-11-03 to answer the power question, and the CPU finding reproduced:** 78.8%
of one core against 123.6%, **+57%**, with PSS and threads landing where the baseline put
them — so the Camera1 async-open change did not move steady-state cost, which is what a
startup change should look like. **Power could not be resolved and that is the result**:
DN-ENG41 has no charging switch anywhere under `/sys/class/power_supply`, so the fuel gauge
is flat by construction, and the USB-input figure gives +15 mW with a 95% interval of
[−272, +308] mW — wide enough to contain the 150–250 mW the extra CPU ought to cost. A
bound, not a measurement. `reports/l4-power-DN-ENG41-20261103.md`.

**Two traps that cost two full soaks each, now refused by the harness rather than
documented.** A second device was still showing a Code 128 from an earlier test, so the
scanner decoded it in the first second and sat on the result screen for twelve minutes —
CPU 3.4% against 74%, and the result file still said `scene: "miss"`, because the scene was
declared on the command line and never checked. And `InactivityTimer` finishes
CaptureActivity after five minutes **on battery** (on charge it is cancelled, which is why
no soak had hit it), leaving a cached process that `pidof` still answers for. `l4-soak.sh`
now checks the result view before and after, and the focused window at the end. Also: that
device's unfiltered logcat was **717 MB in twelve minutes** of `AppsFilter` spam, inside a
measurement of its own power draw — the stream is `logcat --pid` now.

### Phase 4 — L1 and L2

**L3 is built and baselined.** `benchmark/l3` (Macrobenchmark), `tools/maint/l3-run.sh`,
`benchmark/tools/l3_report.py` with 18 unit tests, and a two-pair baseline on DN-ENG41 for
both camera backends. `BENCHMARKS.md` §11 has the whole of it; the reading is
`reports/l3-verdict-DN-ENG41-20261102.md`. Three things from it are worth knowing without
opening either file:

- **The shipping Camera1 path blocked the main thread for about 400 ms at startup, and
  that is fixed.** `ScannerView.surfaceCreated` runs on the main thread and
  `Camera1Backend.open` called `openDriver` synchronously from it; it showed up as CameraX
  reaching the window's first frame ~330 ms sooner, across both baseline pairs with
  non-overlapping intervals. The open now runs on a serial worker. Re-measured with
  unchanged CameraX as the control, the within-session difference reverses: **+349 / +319 ms
  before, −360 / −291 ms after**. Two things had to move with it and are the part worth
  remembering — every `CameraManager` accessor is `@Synchronized`, so main-thread
  `stopPreview` / `closeDriver` / `isOpen` would have waited the open out on that monitor
  instead, and `ViewfinderView.onDraw` reads the scan rectangle through the same monitor, so
  the camera is handed over in `onCameraReady` now rather than in `resume()`. The worker has
  **no Looper on purpose**: `android.hardware.Camera` delivers preview and auto-focus
  callbacks to the Looper of whichever thread called `Camera.open()`, so a `HandlerThread`
  would have moved them silently. `reports/l3-async-open-DN-ENG41-20261103.md`.
- **L3 disagrees with the Phase 6 spike about CameraX's latency, in the other direction.**
  The spike has CameraX 273 ms *faster* to a decodable frame on this device; L3 has it
  ~200 ms *slower* from asking for a camera to a frame at the decoder, and ~330 ms faster
  to a drawn window. Different intervals, not necessarily contradictory — the spike's
  single number contains both effects and they have opposite signs. Also 1.4.2 against
  1.6.2, and a harness activity against the whole app. **Three readings now disagree about
  this, which is why the camera decision does not rest on latency.**
- **Launch → first frame end to end is still not measurable**, and the reason is a real
  constraint rather than an oversight: the camera spans start at `surfaceCreated`, which on
  Camera1 is before the window's first frame, so they overlap the startup metric by an
  unknown amount and must not be added. Closing it needs `ScannerView` to tell its host
  when frames start, which is a published-API decision and is left open.

**What L3 cost to build is itself worth reading**, because every one of its three traps is
the same shape this file keeps collecting — a harness that reports a number for something
that did not happen:

- **A run claiming CameraX silently measured Camera1, twice.** AGP's install step
  uninstalls first and takes the app's `shared_prefs` with it, so a preference written
  before Gradle is gone by the time the benchmark starts; and `chown` did nothing because
  `dumpsys package` prints no `userId=` line on DN-ENG41, so the uid parsed out of it was
  empty. Both were caught only because `ScannerView` emits `zxing.backend.<name>` into the
  trace and `l3_report.py` **refuses** a result whose trace disagrees with the run. Build
  that check first, not last.
- **A run that produced no result came back `BUILD SUCCESSFUL`**, with a failed APK install
  printed as a stack trace in the middle of the log. `am instrument` has the same habit —
  it reports a failed test and exits 0. The script now fails when no `benchmarkData.json`
  exists, whatever any exit code says.
- **`AndroidBenchmarkRunner` is Microbenchmark's**, and a Macrobenchmark module configured
  with it starts instrumentation, reports `tests="0"` and succeeds.

L2 is built, baselined and gated. The method, the layers and the gates are in
`docs/maint/BENCHMARKS.md`; section numbers below point into it.

Done: the `:benchmark-android` module (five stages), the nine generated replay frames with
their committed hashes, the three hint sets, `l2_report.py`, `compare.py`,
`noise_floor.py`, a committed baseline, and a measured effect floor replacing the 5%
guess.

**Results, in `docs/maint/reports/`:**

| File | What |
| --- | --- |
| `l2-9b7f7a50c-DN-ENG41-default-ci.json` | hd/default slice, full cool-down wait |
| `l2-edef9d0a0-DN-ENG41-full-run1.json` | full 135-case matrix |
| `l2-edef9d0a0-DN-ENG41-full-run2.json` | the same again, for the noise floor |
| `l2-noise-floor-edef9d0a0-DN-ENG41.json` | the spread the floor came from |

**Phase 4 is now closed.** L1, L2, L3 and L4 all exist, are baselined, and the last item
on the list — the CI comparison step — is in.

**How it answers "CI cannot produce a trustworthy timing", which was the open question.**
It does not try to. No workflow runs a benchmark; `build.yml` only assembles the APKs.
What `.github/workflows/l2-compare.yml` does is the half that was being skipped: an
operator measures on the reference device and commits the result file to
`docs/maint/reports/candidates/`, and `benchmark/tools/ci_compare.py` picks the baseline,
runs the §10.6 gates and puts the verdict in the job summary. §13.3 is the whole of it.

Three things in it are decisions rather than plumbing, and are the parts worth not
reversing by accident:

- **The baseline is selected on device, config *and decoder backend*.** compare.py
  deliberately does not guard the backend, because across backends it is answering §10.6b's
  A/B question. Selecting on it is what keeps the A/B and the regression check apart —
  without it, the commit that moved the default to zxing-cpp would have had every later
  candidate judged against a Java baseline, and the 56–82% win reported as a pass on a
  change that did not cause it. `docs/maint/reports/l2-baselines.json` is the manifest,
  and it is validated rather than trusted (exit 4, and a test that reads the real file).
- **A baseline that records no backend must assert one, with a reason.** The `default` and
  `full` baselines predate the field. A null that matched everything would silently judge a
  native candidate against a Java run.
- **`inconclusive` does not fail the build; "could not be judged" does.** Two runs of the
  same binary are inconclusive and should be, so blocking on it would teach people to
  waive it. A candidate with no matching baseline is a different thing — nobody can read
  it — and it outranks `fail` when several candidates disagree.

All five exit codes were watched coming out of the real tool against the real manifest
before the workflow was committed (`reports/ci-compare-gate-checked-20261102.log`) —
which is the same rule this file keeps arriving at: a check nobody has seen fail is a
check nobody knows the shape of.

An empty candidate directory is not a pass: when a pull request touches the decode path
and carries no result, the summary says so and lists the files. A notice and not a
failure, because most changes under those paths are not performance changes and a gate
that cries wolf gets ignored.

**The thermal question is settled, by amending the rule.** Every result from DN-ENG41
carries `THERMAL_THROTTLE` or `THROTTLE_WAIT_DISABLED`, because the phone sits above its
40 °C skin threshold at rest — letting it cool on AC for 45 minutes does not clear it
(trap 3, with the log). Of the three ways out, the chosen one is **§10.6's read-through
clause**: such a warning does not block when both sides carry exactly the same set of them
and the device's effect floor was measured under those conditions. DN-ENG41 qualifies and
is the only device that does.

`NO_SUSTAINED_PERFORMANCE_MODE` is read through on the same grounds — the hardware has no
such mode to enable, and locking the clocks is the stronger thing done in its place. This
was found while checking the clause against the real files: a thermal-only amendment would
have left the baselines blocked anyway.

`UNLOCKED_CLOCKS` is deliberately excluded: it is avoidable and it moves the operating
point rather than describing it.

**Checked, not assumed, twice over.**

1. Run 1 against run 2 of `edef9d0a0` — identical code — now clears the warning gate and
   returns `inconclusive` on all 135 cases, which is the right answer for two runs of the
   same binary. The clause removes a block; it does not manufacture a verdict.
2. **With the clocks pinned, thermal throttling does not move them at all.** A 5 min 09 s
   run at status SEVERE with `vendor.thermal-engine` running put 100% of both clusters'
   time at the locked frequency and **zero ticks at any other frequency**, below it
   included, by the kernel's own `time_in_state` counters. `lockClocks` pins the big
   cores at 56% of maximum, so the operating point is already below anything the governor
   would reduce it to. Evidence:
   `reports/thermal-vs-locked-clocks-DN-ENG41-20261031.log`.

   Worth knowing: `lockClocks` *tries* to stop `vendor.thermal-engine` and this device
   refuses, so these numbers are taken with thermal management fully running. That is the
   right outcome — §6 forbids suppressing it.

   Covers CPU frequency only: the memory bus could not be locked, and one 5-minute run is
   not a soak. A sustained effect is what L4 is for.

**What this does not buy.** These timings belong to a warm device and are slower than a
cool one's. The physically-unplugged-and-cooled run is still untried and still needs
hands; it is now a refinement rather than a blocker. Raising the vendor thermal thresholds
remains ruled out — see the decisions above.

**A second finding for Phase 5, from L1 rather than L2.** Two corpus samples
(`ean13-4/01`, `rss14-2/11`) are misread as UPC-E by an all-formats reader and read
correctly by upstream's format-restricted blackbox run. So restricting formats is not only
the 67× speedup below — on this corpus it is also a *correctness* improvement. Both
arguments point at item 9; the ordering is still the user's call.

**One finding worth carrying into Phase 5.** The full matrix shows the cost of a frame is
dominated by how many formats the reader is asked to try, far more than by resolution.
`endToEnd` on a miss frame — which is what live preview mostly does:

| | all formats | QR only | one 1D format |
| --- | --- | --- | --- |
| 640×480 | 22.2 ms | 3.0 ms | 0.88 ms |
| 1280×720 | 48.4 ms | 8.7 ms | 1.16 ms |
| 1920×1080 | 81.0 ms | 23.7 ms | 1.21 ms |

Up to **67×** between the first and last column. This is a property of the current
decoder, measured, not the effect of any change. It bears on the ordering of Phase 5:
item 9 (an API to restrict formats per scan) currently sits *behind* the native decoder,
and these are the only numbers anyone has for either. Whether zxing-cpp beats 67× on the
path that dominates live preview is precisely what the A/B in item 10 has to answer.
**The ordering has not been changed** — that is a decision, not a conclusion from data.

### Phase 5 — Kotlin and the native decoder

The user's stated direction (route 1 of the screenshot they sent): zxing-cpp + ARM NEON.

6. Convert the maintained Android code to Kotlin module by module, keeping the Java API
   source-compatible.

   **Done.** Every maintained module is Kotlin; `:core` stays Java on purpose.

   | Module | State |
   | --- | --- |
   | `:android-integration` | Kotlin, 0 Java (`9b79318f9`) |
   | `:scanner` — the published AAR | Kotlin, 0 Java in `main`, 25 files (`39fdc78e0`) |
   | `:app` | Kotlin, 0 Java, 58 files (`8dce7b212` … `efa0a2dea`) |
   | `:core` | **stays Java on purpose** — synced from upstream by `sync-core-from-upstream.sh` |

   Java still in the tree, all of it on purpose:

   - `:core`, synced from upstream.
   - Eleven test files in `:scanner` and one in `:android-integration`.
     `JavaSourceCompatibilityTest` and its neighbours are Java **deliberately** — they
     exist so javac has to accept the Kotlin API, and rewriting them in Kotlin would
     delete the check. Do not convert them.
   - The benchmark modules (`benchmark/jvm`, `benchmark/corpus`, `benchmark/android`),
     which item 6 never covered. They are harnesses, not shipped code, and nothing has
     been decided about converting them.

   **`:app` converted package by package**, each commit building, linting, installing and
   being driven by hand: `result` (20 files) → `history` → `book` → `share` → `encode` →
   the root package → `CaptureActivity`. It was a **pure refactor**, decided with the
   user: none of the six open Phase 3 defects was fixed on the way, and each is now
   marked with a comment naming it (A-06 in `HistoryManager.saveHistory`, A-07 in
   `EncodeActivity.share`, A-08 in `WifiConfigManager`, A-09 in `BrowseBookListener` and
   `SearchBookContentsActivity`, A-10 in `LoadPackagesAsyncTask`). They are still open.

   **The trap that only showed up on the last file, and the ninth rule.** `internal` is
   public in bytecode, so Java in *another module* can still call it as long as the
   package name matches — which is how `:app`'s Java `CaptureActivity` kept calling
   `:scanner`'s `internal` `DecodeFormatManager` and `DecodeHintManager` for six commits.
   The moment `CaptureActivity` became Kotlin the compiler enforced the visibility and the
   build broke. **So the widening note below cuts both ways: `internal` protects nothing
   from Java and everything from Kotlin.** Of the two ways out — widen the published AAR's
   API, or move the classes — the user chose the move: nothing in `:scanner` used either
   class, so both now live in `:app` next to their only caller, and
   `tools/maint/api-diff.sh :scanner` shows the two classes leaving the AAR and nothing
   else changing.

   **What is not covered by tests, and was checked by hand instead.** `:app` still has no
   automated tests; the build file points its test source sets at paths that do not exist
   so the legacy `src/` layout cannot be picked up by accident. What each commit did
   instead is in its message. The high points:

   - **A real scan, end to end**, on the Android 16 emulator: `-virtualscene-poster` plus
     the SDK's `Walk_to_image_room` macro put a generated QR code in front of the virtual
     camera, the app decoded it, and the result screen rendered with its thumbnail,
     result points, format, type and buttons. History then showed the scan, and tapping
     that row replayed it through `onActivityResult`. This is the recipe to reuse:
     `adb emu virtualscene-image wall <png>` swaps the poster on a running emulator, and
     the macros live in `$ANDROID_HOME/emulator/resources/macros/`.
   - **The encoder was checked by decoding its own output**: the QR `EncodeActivity` drew
     was screenshotted and decoded with this fork's `:core`, and read back exactly.
   - **The regex literals in the vCard and MECARD encoders were compared**, decoded
     through each language's escape rules, Java against Kotlin, rather than eyeballed. `$`
     needs escaping in Kotlin and a backslash pair does not change, so these are the
     easiest place in the whole conversion to introduce a silent bug.
   - **Settings' one piece of real logic was exercised**: unchecking formats until one is
     left leaves that row checked and disabled.

   What is still unexercised, and would be the first thing to drive by hand after any
   change here: the SCAN-intent and `zxing://` link replies through
   `handleDecodeExternally`, bulk mode, the contact encoders, the Wi-Fi handler (A-08
   means it cannot work anyway) and the bookmark picker (disabled since Android 6).

   ### How to carry on — the rules this conversion settled

   Read the Kotlin trap in the Environment section first. Then:

   1. **Work in clusters, not files.** A package-private Java class becomes `internal`,
      and the Java that still calls it has to keep compiling, so a class converts
      together with whatever it cannot be separated from.
   2. **Members that Java inside the module calls stay plain `public`, never
      `internal`.** An `internal` *function* is name-mangled (`foo$scanner`) and javac
      cannot call it; an `internal` *class* is not mangled and is public in bytecode
      anyway. `internal` on a member breaks the caller and protects nothing.
   3. **Every companion member needs an explicit `private`.** A `const val` inside a
      `private companion object` is still emitted as a **public static field** on the
      enclosing class. This leaked four private constants before the API diff caught it.
   4. **`@JvmSuppressWildcards` on every generic parameter.** Kotlin's `Collection` and
      `Map` are `out`, so `Collection<BarcodeFormat>` reaches Java as
      `Collection<? extends BarcodeFormat>`. A Java *call* still compiles; a Java
      *override* does not.
   5. **Run `tools/maint/api-diff.sh :<module> HEAD` before every commit** and read it.
      Expected noise: `INSTANCE`/`Companion` fields, `final` on static methods,
      `access$` bridges, and package-private widening. Anything else is a finding.
   6. **A Kotlin class literal is not the Java one.** `Boolean::class.java` is
      `boolean.class`; the boxed class needs `javaObjectType`. `DecodeHintValueTypesTest`
      pins the four that `DecodeHintManager` depends on. The test stays in `:scanner`
      although `DecodeHintManager` moved to `:app`: what it actually pins is
      `DecodeHintType.getValueType()` in `:core`, and `:app` has no test source set to
      move it into.
   7. **Static `native` methods need `@JvmStatic private external fun` in the
      companion**, and only that combination. Anything else moves the JNI symbol to
      `…$Companion` and nothing binds.
   8. **Nullability on an overridden Java callback follows the Java original.** A
      non-null Kotlin parameter compiles to a null check that throws;
      `Camera.PreviewCallback.onPreviewFrame` has been seen receiving null.
   9. **`internal` across modules holds only until the caller is Kotlin too.** Java in
      another module can call an `internal` class whose package name matches, because it
      is public in bytecode; Kotlin cannot, at all. So a module boundary that compiles
      today can break on the commit that converts the caller — which is exactly what
      happened to `DecodeFormatManager` and `DecodeHintManager`. When it happens, decide
      deliberately between widening the published API and moving the class to its caller,
      and record which.

   ### What guards this now

   - `tools/maint/api-diff.sh <module> <ref>` — builds the module's release AAR at a
     revision and at the working tree and diffs `javap`, members sorted. It found the
     `getMoreExtras()` widening, 28 mangled `ScannerOptions.Builder` accessors, the
     leaked private constants, and a companion-mangling mistake a hand-read had missed.
   - `JavaSourceCompatibilityTest` in **both** `:android-integration` (5 tests) and
     `:scanner` (9 tests), written in Java deliberately, most of it compiled rather than
     run. Both verified to fail when the thing they guard is reverted.
   - `DecodeHintValueTypesTest` in `:scanner`, 3 tests.
   - CI runs `:android-integration:testDebugUnitTest` next to the scanner's unit tests.
7. `zxing-cpp` backend behind the existing decoder seam, CMake building `arm64-v8a` and
   `armeabi-v7a`, shipped in the same AAR. **16 KB page alignment is mandatory** for
   Android 15+.

   **In progress. The seam exists now** — it did not before, despite this item saying
   "the existing decoder seam". `DecoderThread` constructed a `MultiFormatReader`
   directly, so there was nothing for a second backend to sit behind and nothing for
   item 10 to switch between. `FrameDecoder` is that seam and `JavaFrameDecoder` is the
   shipping implementation; 8 contract tests pin the behaviour a second backend has to
   match, and all 35 existing tests still pass unchanged through it.

   **The seam takes a `LuminanceSource`, not a `BinaryBitmap`**, which is a decision
   worth not reversing by accident: binarization belongs to a decoder, it is the most
   expensive single stage at roughly 4.9 ms on a 1280x720 frame, and zxing-cpp has its
   own. Handing a backend the output of `HybridBinarizer` would compare two halves of one
   decoder instead of two decoders.

   **The native backend now exists and passes on a device.** zxing-cpp v3.1.1 is a git
   submodule at `third_party/zxing-cpp`, built by CMake into `libzxingnative.so` for
   `arm64-v8a` and `armeabi-v7a` and packaged in the AAR; readers only, so the writers
   and their bundled libzint are left out. `NativeFrameDecoder` is the `FrameDecoder`
   implementation, 13 instrumentation tests cover it, and it agrees with the Java backend
   on QR, Code 128, EAN-13, Data Matrix, Aztec and PDF417 frames.

   **It is not wired in as a default and must not be**, until item 10's A/B exists.

   **NEON is checked, and "zxing-cpp + NEON" turned out to mean something other than the
   plan implies.** zxing-cpp has **no NEON intrinsics at all** — no `arm_neon.h`, no
   hand-written kernel. It relies on the auto-vectorizer, and tests `__ARM_NEON` in one
   place only (`GlobalHistogramBinarizer.cpp:140`) to decide whether packing a strided
   column before thresholding is worth it. So there is no "turn NEON on" switch to find;
   there are two conditions, and both now hold and are checked:

   - `__ARM_NEON` is defined. The C++ **fails to compile** on an ARM ABI without it, and
     the device reports it too: `abi=arm64-v8a;neon=1;zxingcpp=3.1.1`. Note NDK 29
     **refuses** `ANDROID_ARM_NEON=FALSE` outright ("Disabling Neon is no longer
     supported"), so the guard cannot fire that way; it was verified to have teeth
     through the route still open, a stray `-mfpu=vfpv3-d16`.
   - The auto-vectorizer did emit SIMD on both shipped ABIs: 7089 vector-register
     instructions on arm64-v8a, and on armeabi-v7a 1281 q-register instructions plus
     3033 `vld1`/`vst1`. Evidence and method:
     `reports/neon-audit-zxingcpp-v3.1.1-ndk29.log`.

   **That is a census, not a speed claim**, and it does not say *which* functions were
   vectorised — the count is library-wide. No armeabi-v7a device exists here, so that
   ABI is built and inspected but has never been run.

   **Item 7 is complete.** The backend selection item 10 needed is in
   (`FrameDecoders`, `DecoderBackendBenchmark`, `-Pl2.backend`), and the comparison has
   been run — see item 10.

   **Left deliberately undone:** the native backend is not wired in as anyone's default,
   and must not be until the UPC-A rendering question under item 10 is decided.
8. Feed CameraX `ImageProxy` `YUV_420_888` straight in: honour `rowStride`/`pixelStride`,
   crop to the scan region, no full-frame copy, and do not release a buffer while an
   async decode still holds it.

   **Half done, and the half that is done is the half that does not need a camera.**
   `YPlaneLuminanceSource` (in `:scanner`, `…android.camera`) reads a luma plane in
   place: strides honoured, crop applied, only the scan rectangle ever copied, buffer
   position never touched so the camera's buffer is not disturbed. It takes a
   `ByteBuffer` and three integers rather than an `ImageProxy`, so the AAR gains no
   CameraX dependency while the camera-stack question is open. 18 unit tests, including
   four that decode a real QR code through the actual reader out of a padded and
   interleaved plane; verified to fail (10 of 18) if the stride handling is broken.

   **What is left is the camera stack itself**, which is Phase 6's decision, not a
   performance change to slip in here. `ScannerView` still runs Camera1 and still uses
   `PlanarYUVLuminanceSource`, so **nothing in the shipping app uses this yet and no
   speedup has been measured**. Avoiding a full-frame copy per frame is a structural
   fact about the code, not a number.

   The buffer-lifetime half of the item is handled by documenting the ownership rule and
   giving callers `detach()` — one scan-rectangle copy — for when the image must be
   released before the decode finishes. It cannot be enforced from here until something
   in this repo owns the `ImageProxy`.
9. ~~API to enable/disable formats per scan so the native reader does less work.~~
   **Done** (`ScannerView.setFormats` now reaches the live decoder; it had silently done
   nothing on a running scanner). Deliberately taken before item 7: it is hours rather
   than days, it is backed by two measurements already in hand, and item 10's A/B is only
   fair if both decoders can be asked for the same format set. The native work is
   unaffected — this is the seam it will be compared across.
10. **A/B the native backend against the Java one on the fixed dataset, then decide the
    default.** **The comparison now exists** (`BENCHMARKS.md` §10.6c, files
    `reports/l2-ab-602d368c4-DN-ENG41-*`): DN-ENG41, clocks locked, 27 cases per backend,
    one run each. Verdict `fail` — **19 pass, 8 fail** — and the 8 are one explainable
    class, not noise.

    - zxing-cpp is **56–82% faster on everything 2D and on all-format misses**, which is
      the path a scanner spends almost every frame on: `FHD_MISS_DEFAULT` 81.3 ms →
      18.7 ms.
    - The Java decoder wins **1D hits** by 5–20×. Absolute gap: **1.7 ms, once**, against
      tens of milliseconds saved on every miss frame before it.

      **The reason first recorded here was wrong, and both real ones are now fixed.** It
      said zxing-cpp binarizes the whole frame first while `HybridBinarizer` is lazy. It
      does not: zxing-cpp's `HybridBinarizer::getPatternRow` delegates to
      `GlobalHistogramBinarizer`, so its 1D path thresholds one row at a time exactly as
      the Java one does. What the 5–20× actually was: a full copy of the ROI before the
      JNI call, and a `MultiFormatReader` — with every format reader the options ask for —
      constructed on every single frame inside `ZXing::ReadBarcode`. Both are gone
      (2026-11-09). **A mechanism nobody measured is a guess with a citation**; this one
      survived in this file because it was plausible and the number it explained was real.
    - **Where the 1D hit stands now**, paired against Java in one session on DN-ENG41:
      VGA +44.5%, HD +21.9%, FHD no longer resolvable, and every other case in the matrix
      63–91% the other way. Still fixed-cost dominated — the native 1D hit barely moves
      between VGA and FHD — so what is left to find is more fixed cost, not pixel work.
      `reports/l2-native-cached-reader-DN-ENG41-20261109.md`.

    **Decided: zxing-cpp is now the default** (`ebe1708b4`), with Java as the fallback
    where there is no native build for the ABI. `ScannerOptions.setDecoderBackend(JAVA)`
    restores the old decoder.

    The compatibility questions were handled rather than shipped as surprises:

    - **UPC-A is converted back** to the 12-digit `UPC_A` this library has always
      returned, so no host's string changes. GS1 prefix 0 is reserved for UPC-A so the
      conversion is exact. The raw rendering stays reachable for measurement.
    - **Result points of a found symbol are carried back** as the symbol's four corners,
      so thumbnails are unaffected.
    - **Lost, and documented:** the viewfinder's *animated candidate* points, which the
      Java reader emits while searching through `NEED_RESULT_POINT_CALLBACK`. zxing-cpp
      has no streaming equivalent. Also lost: every decode hint other than the format
      selection. A caller needing either should select the Java backend.

    **Accuracy is now measured too, and item 10 is closed** (§9.1). The native backend
    reads **890** of the corpus against the Java decoder's 862, with 3 wrong answers
    against 4 and no false positives against 2; at TRY_HARDER it is 960 against 921. The
    two defects the first run exposed are fixed — see the top of this file, because the
    second one is the more useful lesson in this repository.

### Phase 3 — carried-forward defects (independent; can be picked up any time)

**All of them are now fixed** — A-06, A-07, A-08, A-09, A-10, A-12 and A-16 — each with
its own commit, its own before/after on a device or an emulator, and its own file in
`reports/`. They survived the Kotlin conversion untouched by decision and were taken one
at a time afterwards. Nothing from this phase is open.

Three things generalise past the individual fixes and are written out below: *widen what
you match, narrow what you emit* (A-09), *a grant that reaches the destination app is not
the same as a grant that reaches the share sheet* (A-06/A-07), and *the measurement was
wrong three times before it was right* (A-10). The rest is per defect.

**A-09 is fixed**, and the one-command repro is now the check that it stays fixed:

```
adb shell "am start -a com.google.zxing.client.android.SEARCH_BOOK_CONTENTS \
  -e ISBN 9780140328721 -e QUERY fox"
```

then press the search button. What used to happen: `java.io.IOException: Cleartext HTTP
traffic to www.google.com not permitted`, thrown inside okhttp's `CleartextURLFilter`
before anything left the device. What happens now: a real socket to
`www.google.com:443`.

**On this network that connection is refused**, so the success path still cannot be seen
here — DN-ENG41 reaches `223.5.5.5` fine and cannot reach `www.google.com` at all. The
endpoint itself was checked from the host instead and still answers over https with the
same JSON the activity parses. So the fix is verified by the failure changing, by an A/B
on one file at a time on the device, and by the server's own response; it is **not**
verified by watching results render in the app. Both halves are in
`reports/a09-cleartext-ab-DN-ENG41-20261102.log`.

The second half of A-09 was not in the audit entry and is the more interesting one:
`LocaleManager.isBookSearchUrl` tested for `http://` only, so a **scanned** https book
URL — which is every book URL in the field — was read as a literal ISBN, and the
volume-id branch in `SearchBookContentsActivity` and `BrowseBookListener` could not be
reached at all. Matchers that run against scanned data were widened to accept both
schemes; URLs this app *builds* were switched to https outright. That distinction is the
rule to keep: **widen what you match, narrow what you emit.**

Cleartext itself stays off. A scanned `http://` URL is therefore not fetched for
supplemental info (title, shortener unwrapping); it fails as an ordinary `IOException`,
the extra line is simply absent, and the link still opens in the browser under the
browser's own policy. `TitleRetriever` and `URIResultInfoRetriever` say so in place.

**A-10 is fixed, and the measurement was wrong three times before it was right.** The
`<queries>` element declares the two intents this app genuinely asks about — MAIN/LAUNCHER
for the picker, VIEW on http(s) for `launchProductQuery` — instead of
`QUERY_ALL_PACKAGES`, which is the permission for apps that need the whole list and which
this app does not need. `LoadPackagesAsyncTask` asks `queryIntentActivities` for launcher
activities rather than `getInstalledApplications`, because **an intent query is the only
kind the platform can match a `<queries>` declaration against** — that is the part worth
carrying forward. `getInstalledApplications` has no intent in it, so no declaration can
widen it, and it keeps returning the handful of packages that were visible anyway.

On DN-ENG41 the picker went from 79 rows to 49, and the drop is the fix working: 64 of
the 79 were unlabelled vendor services with no launcher entry, visible only because such
system packages are forceQueryable, and 34 real installed apps appeared that the app
could not see at all before. Only 15 rows were common to both. Tapping one of the 34 now
produces its `market://details?id=` barcode.

**And the warning in it.** Three different measurement bugs each returned a plausible
number: a fling that skips screens between dumps (32 instead of 79), two scrapes
overwriting one temp file (a 35-row list that was neither run), and `tr '>' '>\n'`, which
does **not** split a line — tr pads the shorter set, it cannot expand one character into
two — so the dump stayed one line, the bounds match returned the root node's, and the
script tapped the middle of the screen and scraped the previous screen's seven labels and
called it an app list. `tools/maint/scrape-app-picker.sh` is the corrected scraper; it
refuses to run unless `app_picker_list_item_label` is on screen, and both numbers above
were reproduced with it, byte-identical.

**Half verified, and which half.** DN-ENG41 has no browser — nothing on it answers VIEW
https — so `launchProductQuery`'s Chrome special case cannot be exercised there. On the
API 36 emulator, `dumpsys package queries` shows this app seeing 0 packages through
intent queries without the declaration and 21 with it, `com.android.chrome` among them,
which is the set `resolveActivity` reads. The product-search flow itself was not driven
end to end: it needs a product barcode scanned after a `PRODUCT_SEARCH_LINK` or
`zxing://` launch, and the virtual-scene camera cannot be aimed at the poster from the
command line — `adb emu` has `virtualscene-image` but no macro command.

**A-06 and A-07 are fixed, and they were one fix.** Both wrote into
`Environment.getExternalStorageDirectory()/BarcodeScanner` and shared a `file://` URI —
the first forbidden since Android 10, the second since targetSdk 24. `ExportFiles` writes
to the app's own cache under `exports/` and returns a `content://` URI from a
`FileProvider` whose `res/xml/file_paths.xml` exposes that one subtree and nothing else.
Exports are swept after 24 hours, checked by planting a file dated a month earlier and
watching it go. `msg_unmount_usb` — "Sorry, the SD card is not accessible" — is gone from
all 34 locales along with the condition it described; `msg_export_failed` replaces it.
This adds `androidx.core` to `:app`, for `FileProvider`; the published AAR is untouched.

**The A/B found a bug in the first version of the fix, which is the part worth keeping.**
Setting `EXTRA_STREAM` plus `FLAG_GRANT_READ_URI_PERMISSION` and letting `startActivity`
build the ClipData is the usual recipe, and the app the user picks can indeed read the
file — the system print service rendered the QR in its print preview. But the **share
sheet** could not:

> `Permission Denial: opening provider androidx.core.content.FileProvider from
> ProcessRecord{com.android.intentresolver}`, and a broken thumbnail.

`Intent.createChooser` copies the target's ClipData and flags **at the moment the chooser
is built**, and `startActivity` builds that ClipData afterwards, so the chooser is handed
nothing. `ExportFiles.grantRead` sets the ClipData itself; after that the chooser Intent
carries `flg=0x1` and the clip, and the denial count over the same run is zero. **A grant
that reaches the destination app is not the same as a grant that reaches the share
sheet**, and only one of the two is visible in a screenshot.

**What is not verified:** a mail app actually holding the CSV attachment. DN-ENG41 has no
app that accepts `text/csv`; the API 36 emulator offers Drive and Gmail with no permission
denial, but neither has an account on it. The unobserved step is the same provider call
that A-07's print preview does observe.

**A-08 is fixed, by route rather than by patch.** `WifiNetworkAdder` chooses what the
platform allows: Android 11+ hands a `WifiNetworkSuggestion` to settings through
`ACTION_WIFI_ADD_NETWORKS`, which prompts the user and saves the network for real;
Android 10 registers it with `addNetworkSuggestions`, which is weaker — the platform
decides when to use it — but is all that version has; below that the old
`WifiConfiguration` path still works, which is why `WifiConfigManager` is still here.
`minSdk` is 21, so that branch is reachable.

Two things changed besides the route. **WEP cannot be expressed as a network suggestion
at all**, so a WEP barcode is now reported rather than silently dropped. And the toast
follows the outcome instead of announcing a connection in advance: "requesting connection
to network" was half of what made this defect invisible, because it said the same thing
whether the network had been added or `addNetwork` had just returned -1.

**Driven as a real scan on the Android 16 emulator**, which is the recipe to reuse:
generate the `WIFI:` QR with this fork's `:core`, `adb emu virtualscene-image wall <png>`,
and let the app decode it off the camera. The code must be small enough on the poster to
fit inside the viewfinder — 600 px on an 800 px canvas is clipped by the frame and never
decodes, 420 px on 1400 px works. Before: `Unable to add network "A08TestNet"` and
nothing else. After: the system's "Save this network?" dialog, and after Save the SSID
appears in `adb shell cmd wifi list-networks` as `wpa2-psk`.

**Not verified:** the Android 10 and pre-Android-10 routes, because no such device or
emulator exists here, and the `ActivityNotFoundException` fallback, because
`ACTION_WIFI_ADD_NETWORKS` resolves on both machines available.

**Also found, and left alone:** replaying a history row does not leave the result on
screen. `CaptureActivity.onResume` resets the status view after `onActivityResult` has
handled the replay, so the result flashes and the preview comes back. It is why A-08 had
to be driven on the emulator rather than from a seeded history row on DN-ENG41. Not part
of A-08 and not in the audit; worth a look.

**A-12 is fixed, and the interesting part is which oscillation actually matters.** The
lux thresholds are 45 and 450 and were already far apart, so a *steady* scene never
oscillated. The loop that bites is the torch's own light: switching it on raises the
measured lux, which can push the reading past the bright threshold, which switches it off,
which drops the reading back under the dark one. **No threshold can see that happening**,
because the sensor cannot tell the torch's light from the room's. So the fix is a floor on
how often the state may flip — three seconds — not a wider dead band.

`TorchPolicy` holds the rule, away from the camera and the sensor, which is the only
reason it is a class of its own: it can be unit-tested, and it has 8 tests that were
watched failing (4 of them) when the interval check is deleted. `AmbientLightManager` now
calls `setTorch` only when the answer changes, so a steady scene does not touch the camera
at all — the old code called it five times a second, each call reading the camera's
parameters.

**Measured on the API 36 emulator**, whose light sensor can be driven (`adb emu sensor set
light`) where a physical one cannot: six seconds of a sensor flapping across both
thresholds cost **6** flash-mode changes before and **1** after, and every one of the 6
had torn down and rebuilt the `AutoFocusManager`. `tools/maint/flap-light-sensor.sh` is
that measurement, and it reproduces both numbers. One caveat worth knowing: the emulator's
camera reports no flash modes, so only the "on" direction ever reaches the log.

**Left alone deliberately:** the teardown and rebuild inside `CameraManager.setTorch`.
`AutoFocusManager.stop()` sets a `stopped` flag it never clears, so the object genuinely
cannot be restarted, and making it restartable means reasoning about an in-flight
`AutoFocusTask` resurrecting a stopped manager. It is now bounded by the rate check, and
the camera stack is Phase 6's decision.


**A-16 is fixed in the sense that is available.** The orientation cannot be forced back —
Android 16 ignores `screenOrientation` on a large screen and that is the end of it — so
the capture UI was made to work in the portrait window it gets. Three parts, each
measured at 1600x2560 @240dpi on the API 36 emulator:

1. **The viewfinder's size caps follow the window's long and short axes** instead of x and
   y. The numbers 1200 and 675 are five eighths of 1920 and of 1080, the long and short
   sides of a landscape phone, and writing them as a *width* cap and a *height* cap is the
   same thing right up until the window is portrait. There the frame came out 1000x675:
   62% of the width and **26%** of the height, a shallow band. It is now 675x1200. **A
   landscape window is bit-for-bit unchanged**, because there the long axis *is* x, and
   `FramingRectSizerTest` pins that as hard as it pins the portrait case.
2. **The status line sits under the frame** rather than at the bottom of a 2560px window,
   a thousand pixels from where the user is looking.
3. **The result row stacks** instead of putting the thumbnail beside the scanned text.

Two mistakes on the way, both of which returned a plausible-looking screen:

- **The frame is a camera property and does not exist yet** when the layout is inflated,
  when `onResume` runs, or at the layout pass that follows — the surface has not been
  created and the driver has not been opened. Two versions of the status-line fix read a
  null rectangle and did nothing, silently. It retries from a pre-draw listener now and
  removes the listener as soon as it succeeds.
- **Stacking the result row lost the scanned text off the bottom of the screen.** The
  metadata column is `fill_parent` tall, which centres it beside the text in a *row* and
  eats the whole height in a *column*. Format, type and time were on screen and the URL
  was not.

Done in `CaptureActivity` rather than a `layout-port/capture.xml`, because a second copy
of that file is precisely how A-18 happened; `layout-ldpi/capture.xml` has the two new ids
added for the same reason. Evidence, with before/after screenshots:
`reports/a16-large-screen-portrait-api36-20261102.log`.

**Still not covered:** no physical large-screen device exists here. The window sizes and
the layout are the same thing on a real tablet; the camera on one is not.

### Phase 2 — predictive back, and a trap in how it is tested

**A-19 is fixed.** From API 33 the back key stops reaching `onKeyDown` once an app opts
into `OnBackInvokedDispatcher`, and at targetSdk 36 that opt-in is the default. Both
activities' back handling had therefore stopped running — silently, because the system's
default back does something plausible. With a scan result on screen, back left the app.

The fix registers an `OnBackInvokedCallback` **only while back should be intercepted**,
rather than registering once and deciding inside it. That is the point of predictive back:
while nothing is registered the system knows back will leave and can draw the real preview
animation for it. `lastResult` now changes in exactly one place so the registration cannot
drift out of step with it.

**The trap, and it will catch the next person too: `adb shell input keyevent KEYCODE_BACK`
is not a back press under predictive back.** An injected key event reaches both the
dispatcher and the legacy path, so a handler runs *and* the activity finishes. After the
fix, the injected key still appeared to leave the app while a log line proved the callback
had run. Only a real edge-swipe gesture behaves correctly, and the *pre-fix* reproduction
was re-run with a gesture before any of this was believed. A fix verified with the wrong
input is not verified.

### Phase 6 — the camera backend

The decision record is `docs/maint/decisions/0001-camera-backend.md`; the summary and the
one thing still open are at the top of this file. What is left here:

- ~~**The minSdk question**~~ — **answered: 23**, done.
- ~~**Adoption**~~ — done. `CameraXBackend` beside `Camera1Backend`, both behind
  `ScannerCameraBackend`, chosen by `ScannerOptions.cameraBackend`.
- ~~**Verification**~~ — done on DN-ENG41 and the API 36 emulator,
  `reports/camerax-adoption-verification-20261102.md`, and **re-done on Android 15** on
  2026-11-03 against the minified build, `reports/camerax-android15-mix5-20261103.md`.
- ~~**A real decode off real camera hardware**~~ — **done, 2026-11-03**, on both backends:
  `reports/real-barcode-decode-DN-ENG41-20261103.md`. A second Android device displays a
  symbol and the phone reads it off that screen, which is why this never needed the hands
  it was waiting for.
- **Making CameraX the default**, still the only Phase 6 item open — and now purely a
  decision. Two phones, two Android versions, an R8 build and a real symbol all agree; the
  viewfinder rectangle lands in the same place to the pixel on both backends; 21/21
  instrumentation tests pass on the Android 15 one. The only thing pointing the other way
  is L4's ~60% more CPU to keep scanning, which is a trade-off to weigh rather than a gap
  to close.

  **The Android 15 run earned its keep by finding a defect**: `FrontLightMode.ON` was a
  silent no-op on the CameraX backend, because the rule lived inside the Camera1 parameter
  configuration. `AUTO` worked, because `AmbientLightManager` goes through `ScannerCamera`.
  No crash, no log line, nothing to see — and untestable on DN-ENG41, which reports no
  flash unit. Fixed in `ScannerView`'s open callback, where both backends pass, and
  re-verified on both. **A setting that silently does nothing is the failure mode a second
  device exists to catch.**

### How the two backends fit together

Worth reading before changing either, because the seams were chosen to keep the Camera1
path literally unchanged rather than to be elegant:

- `ScannerCamera` is public and tiny — framing rectangles and the torch. `ViewfinderView`
  and `AmbientLightManager` take one, so neither knows which stack is running.
- `ScannerCameraBackend` is internal and adds open / close / request-a-frame. `open` is
  **asynchronous for both**: Camera1 opens synchronously and still answers through the
  callback, because writing one caller against the slower of the two is the only way one
  caller can drive both.
- `Camera1Backend` wraps `CameraManager` and owns nothing. `CameraManager` is still the
  whole Camera1 stack and still public API; what moved into the wrapper is the open-failure
  classification that used to sit in `ScannerView`, which stopped belonging there the moment
  there were two stacks to classify failures from.
- `ScanFrame` carries one frame **and its ownership** to the decode thread. Camera1 hands
  over a `byte[]` it has finished with and `close()` does nothing; CameraX hands over an
  `ImageProxy` holding a slot in a fixed-size queue, which `YPlaneLuminanceSource` reads in
  place. The decode thread closes it in a `finally`. **A frame nobody closes stops the
  camera, with no exception anywhere** — that failure mode is the reason the interface
  exists at all rather than the backends posting their own payloads.
- Frames are **pulled, not pushed**. `requestFrame` arms exactly one delivery; anything the
  analyzer receives while nothing is waiting is closed on the spot.

### Two traps this adoption walked into

- **The viewfinder drew nothing at first.** `ViewfinderView.onDraw` returns early when the
  framing rectangle is null, and the laser animation reschedules itself *from inside the
  draw it never got to do* — so one early return is permanent. Camera1 has geometry the
  moment `openDriver` returns; CameraX, as first written, had none until the first frame
  arrived. Fixed by reading `ImageAnalysis.resolutionInfo` straight after
  `bindToLifecycle`, which settles the resolution and rotation there. Anything else that
  makes geometry late will reproduce this exactly.
- **`setFixedSize` on the preview surface is deliberate.** It sizes the surface buffer to
  what the camera is actually producing; the `SurfaceView` still fills the scanner, so the
  preview is scaled to the view. That is what the Camera1 path always did, and
  `PreviewCoordinateMapper` is written against precisely that assumption — which is why the
  rectangle maps identically on both backends and why `PreviewView`'s scale/crop transform
  was never needed here.

`benchmark/camera-spike` stays in the tree whatever is decided — it is the only thing that
can re-run any of those numbers. CI assembles all three flavors on every push so it cannot
rot unnoticed, but CI cannot *run* it: it needs a camera.

Reproducing the measurements:

```
./gradlew :camera-spike:installCamera1Release :camera-spike:installCameraxRelease \
          :camera-spike:installCameraxleanRelease
tools/maint/camera-spike-run.sh camera1 15 <serial>
BACK_CAMERA_ONLY=1 tools/maint/camera-spike-run.sh cameraxlean 15 <serial>
tools/maint/camera-spike-size.sh
```

**One trap in the harness itself, and it is the kind this file keeps collecting.** The runner
*streams* logcat to a file for the duration of each launch rather than reading the buffer
back afterwards. On DN-ENG41 the main buffer is busy enough that a line is gone within a few
seconds, and the first version of the script — which polled `logcat -d` — found the result
line and lost the decode line on every single run. That looked exactly like a decode that
never happened, and the first reading of it was that `YPlaneLuminanceSource` had thrown
against a real `ImageProxy`. It had not; the reader was too late.

### Code scanning

CodeQL runs on every push to `maint/android` and on the weekly schedule, and it is green.
Getting there took two things worth knowing:

- **A green scan is not a green workflow.** The only run this fork ever had was red
  because the repository's default `GITHUB_TOKEN` is read-only and the inherited workflow
  declared no `permissions`: the analysis finished and the *upload* was refused with
  `Resource not accessible by integration`. Fixed at `1388b3f87`, which also repointed
  triggers that were still filtered to `master` — in this fork an untouched branch, so
  nothing on `maint/android` had ever been scanned on a push.
- **There is one dismissed alert and it must not be re-dismissed blindly.** #1,
  `java/android/unsafe-content-uri-resolution` on `QRCodeEncoder`, was real and is fixed
  at `343158d5f`. The query kept it open afterwards because it models only the
  path-prefix form of validation, so it is dismissed as a false positive. That dismissal
  is justified by `rejectUnsafeStream` existing — **if that guard is ever relaxed, reopen
  it.** `reports/encode-stream-uri-guard-DN-ENG41-20261103.md` has the reasoning and the
  four device cases.

### Release and distribution

- **Fixed: tagging `v0.1.0` would have published an AAR nobody can resolve.** Found on
  2026-10-31 at the `849921bfa` rename audit, fixed once the Kotlin work was out of the
  way.

  `:scanner` declares `api(project(":core"))`, so its published POM carries a compile
  dependency on `io.github.kakakakacat.zxing:zxing-core:<version>`. `release.yml`
  published **only** `:scanner`, so that dependency would not exist in GitHub Packages
  and every consumer's resolution would fail. The step now runs
  `:core:publishMavenPublicationToGitHubPackagesRepository` alongside it.

  The comment above that step is what hid this: it said `:core` was deliberately not
  published because "its classes are `com.google.zxing.*`, so a second artifact carrying
  them would collide with upstream". They have not been `com.google.zxing.*` since
  `849921bfa` — avoiding exactly that collision is *why* the rename happened — and
  `PUBLISHING.md` has documented both artifacts as published the whole time. The comment
  now says why both have to go together.

  **Checked both ways, against a real resolver**, because a publish step that is never
  run until a tag is exactly the kind of tooling this file warns about:

  - With only `:scanner` in `~/.m2`, a throwaway Gradle project asking for
    `zxing-android-scanner` fails with `Could not find
    io.github.kakakakacat.zxing:zxing-core`. With `:core` published too, it resolves the
    AAR, the jar and `kotlin-stdlib`.
  - `tools/maint/check_release_publishes.py` now guards it structurally: it reads the
    publish tasks out of `release.yml`, generates exactly those POMs, and fails if any
    dependency under this project's own group is not itself being published. It was
    watched failing on purpose — delete the `:core` line and it names the missing
    artifact — and `build.yml` runs it on every push.

- **The AAR has been taken all the way to a consumer and works**, 2026-11-03:
  published to `~/.m2`, resolved by a throwaway Gradle build that knows nothing about this
  repository, compiled from both Java and Kotlin against the documented example (then in
  the README, now in `PROJECT_DETAILS.md`),
  minified by R8 with only the default rules, installed, and used to decode a real
  `CODE_128` off a camera — `SCANNED CODE_128 AAR-CONSUMER-OK`. Both shipped ABIs report
  `LOAD align = 0x4000`, so 16 KB alignment is in the ELF rather than assumed.
  `reports/aar-deliverable-check-20261103.md`. **Nothing about the artifact is blocking a
  release; what is left is the changelog and the tag.**
- **v0.1.0 is released, 2026-11-03.** `[Unreleased]` became `## [0.1.0] - 2026-11-03`
  (its duplicate `### Added`/`### Changed`/`### Fixed` subsections merged into one of
  each, and the day's security fix added as `### Security`), the tag was pushed, and
  `release.yml` went green on the first run, every step: build, checksums, the
  resolvability check, the notes, the publish, the Release.

  **Checked afterwards rather than trusted:** both packages read `0.1.0` in GitHub
  Packages; the Release carries the AAR, the debug APK and `SHA256SUMS.txt`, and both
  sums recompute to the published values from the downloaded files; the released AAR has
  the expected 13 entries and `LOAD align = 0x4000` on every `LOAD` segment of both ABIs.

  **Checked later the same day, with a token that carries `read:packages`:** resolving
  the artifact out of GitHub Packages, which had been the one open item. With
  `mavenLocal()` removed and the module cache for the group deleted, a consumer project
  resolved both artifacts from `maven.pkg.github.com`, R8'd, installed on DN-ENG41 and
  read a Code 128 off DN-ENG81's screen. The AAR Gradle fetched is sha256 `3b2f4392…`,
  the published `SHA256SUMS.txt` value. Without such a token the registry answers 401 —
  GitHub requires auth for Maven packages even on a public repository, exactly as
  `PUBLISHING.md` says, which is why JitPack stays the route to hand to someone else.
  `gh auth refresh -h github.com -s read:packages` is how to get one; a fine-grained PAT
  does **not** work against the Maven registry, it has to be classic or the gh OAuth
  token.
- **`release.yml` extracted the release notes *after* publishing**, so a missing
  changelog section would have failed the release with the version already taken in
  GitHub Packages, which cannot be replaced. Fixed at `0a43c6972`: the extraction now
  runs beside the resolvability check, before anything is pushed.
- **The next release is the same three steps.** Put the changes under a fresh
  `## [Unreleased]`, rename it to the version with its date, tag `vX.Y.Z`;
  `.github/workflows/release.yml` does the rest. `changelog_section.py` exits non-zero on
  a version with no section, by design, and that check now runs *before* the publish.
- **Maven Central is not set up.** It needs a Central Portal account with the
  `io.github.kakakakacat` namespace verified, a published GPG key, and a plugin that
  speaks the Central Portal bundle API. Details in `PUBLISHING.md`.

### Known gaps in the testing itself

- Two physical devices now, Android 14 and Android 15, both Qualcomm and both 4 KB pages.
  No real-camera verification on Android 16, nothing non-Qualcomm, nothing low-end with a
  modern OS, and no 16 KB page hardware. **Every L2 number is still from DN-ENG41 alone**,
  at one pinned operating point, in one thermal state — the Mix5 is reached over a link that
  drops and nothing locks its clocks, so it cannot produce an L-layer timing.
- ~~**No barcode has ever been put in front of either phone.**~~ **Closed 2026-11-03**:
  DN-ENG41 reads symbols off **DN-ENG81**'s screen on both camera backends, and
  `tools/maint/scan-off-screen.sh` makes it repeatable. What a panel does not cover is
  paper, angles, poor light, movement and auto-focus — it is the easy case on purpose. The
  Mix5 and Android 16 still have nothing in front of them.
- **Auto-focus on the CameraX path is untested anywhere.** DN-ENG41 offers auto-focus OFF
  only, and a screen at a fixed distance exercises focus on neither backend, so the
  2026-11-03 decodes do not touch this. The Mix5 can do it but has not been pointed at
  anything.
- **The two devices disagreed about the Phase 6 latency result**, which is the clearest
  demonstration in this repository of why a one-device number is not a result. The spike
  numbers also drift between sessions on DN-ENG41 — its camera1 median read 810 ms and
  955 ms the same afternoon with nothing changed — so only compare flavors measured in the
  same session.
- The L2 effect floor comes from a single pair of runs. More pairs would sharpen the
  tail estimate; the current floors take the conservative reading instead.
- ~~13 samples report `INCORRECT` in L1…~~ **Settled.** It was not a charset gap — every
  sidecar involved is ASCII, and that guess was wrong. Nine of the thirteen were a bug in
  the L1 harness itself (`classify()` stripped a trailing newline from the expectation but
  not from the decoded text) and are now `CORRECT`; the corpus reads 862/4/254/46/2. The
  four that remain are understood and real, and the two false positives sit inside
  upstream's own tolerance of four. `BENCHMARKS.md` §9 has the case-by-case table.
