# Handoff — working state

Operational notes that do not belong in the README: where things stand, what is in
flight, and the environment quirks that will otherwise be rediscovered the hard way.

Written at commit `849921bfa` on branch `maint/android`.

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

Nothing is uncommitted. Nothing is released; no tag has been pushed.

---

## Verified, with the evidence

| What | Result | Where |
| --- | --- | --- |
| `:core:test` | 602 tests, 0 failures | local |
| `:scanner:testDebugUnitTest` | 9 coordinate-mapping tests | local + CI |
| `:scanner:connectedDebugAndroidTest` | 35 fixed frames, 16 formats, 0 failures | Android 14 device **and** CI emulators API 21/34/35/36 |
| App install / launch / preview | works | Android 14 device, API 35 + 36 emulators |
| CAMERA grant / deny / permanently-denied | works, no request loop | Android 14 device |
| 100× open-close stability loop | 0 crashes, 0 camera errors | `reports/stability-100-b0-DN-ENG41.log` |
| L1 over all 1168 samples | 853 correct / 13 incorrect / 254 not-found / 46 correct-no-result / 2 false-positive | local |
| Published AAR + jar | zero `com/google` entries | local `publishToMavenLocal` |
| Implicit `com.google.zxing.client.android.SCAN` | still resolves to the app | Android 14 device |

**No performance numbers exist.** L1 timings are host-JVM diagnostics only and must never
be quoted as an Android result.

---

## Environment

- Host: macOS 27.2 arm64, JDK 25 (Android Studio JBR); Gradle toolchain targets JDK 17
- Android SDK at `$ANDROID_HOME` = `/Users/dm/Library/Android/sdk`, platform 36, build-tools 36.0.0
- Physical device: **DN-ENG41, Android 14 (API 34), serial `T3515NNFBC000055`, no GMS**
  — the only real device available, and the only no-GMS verification point
- Emulators created for this work: AVDs `zxing_api35` (Android 15) and `zxing_api36`
  (Android 16), Pixel 7 profile, arm64. Start with
  `emulator -avd zxing_api36 -no-snapshot -no-audio -camera-back virtualscene`
- No Android 15/16 **physical** device is available. Anything needing a real camera on
  those versions stays unverified, and the README says so.

### Three traps

1. **DroidLens global Gradle init script.** `~/.gradle/init.d/droidlens.gradle` injects a
   third-party SDK into debug builds of any project listed in
   `~/.gradle/droidlens/projects.txt`. It registered this project once and broke the
   build with `minSdkVersion 21 cannot be smaller than version 24 declared in library
   kakacat.android.helper:runtime-network`. The user has since disabled auto-attach. If
   it ever comes back, the symptom is that error or an unexpected SDK inside the APK;
   check `unzip -l app-debug.apk | grep kakacat`.
2. **A Microbenchmark run stalls when the screen goes off.** `IsolationActivity` stops
   making progress and says nothing: no failure, no timeout, the process just stops using
   CPU. DN-ENG41 locked seven minutes into the first L2 run and sat there. Run
   `adb shell svc power stayon true` before any device benchmark; to tell a stall from
   slow work, watch fields 14/15 of `/proc/<pid>/stat`.
3. **Emulator `wm size` changes persist.** The Android 16 large-screen reproduction ran
   `wm size 1600x2560 && wm density 240`. Both were reset afterwards, but if an emulator
   behaves oddly, `adb shell wm size reset && adb shell wm density reset`.

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
- **Namespace:** everything is `io.github.kakakakacat.zxing.**`; `applicationId` is
  `io.github.kakakakacat.zxing.android`. Publishing `com.google.zxing.*` class names from
  a second groupId is a duplicate-class build failure for consumers.
- **The Intent action strings are frozen** on `com.google.zxing.client.android.*`. They
  name a protocol, not a class. The rename tooling only touches `package`/`import` lines
  so this cannot be undone by accident.
- **Camera1 stays for now.** A Camera2/CameraX decision record is Phase 6, and must not be
  presented as a performance improvement on its own.

---

## What is left

Ordered as the user asked for it. The README carries the same list as tickboxes.

### Next up — Phase 4, L2 before any optimisation

The method, the layers and the gates are now written down in `docs/maint/BENCHMARKS.md`;
the section numbers quoted below point into it.

The point of doing this first: the native-decoder work in Phase 5 cannot be judged
without a frame-pipeline baseline. Building it after the optimisation would be writing
the scoreboard after the game.

1. ~~`:benchmark-android` module using **AndroidX Microbenchmark**~~ — **done.** Five
   stages (crop, luminance source, binarize, decode, end to end) measured on the device's
   own ART.
2. ~~Fixed replay inputs at 640×480, 1280×720 and 1920×1080~~ — **done**, and generated
   rather than committed; `benchmark/android/goldens/frames.golden.json` holds their
   SHA-256 values and is the comparability key. Both the host test and the on-device
   `FrameIntegrityTest` fail a run whose frames do not match it.
3. ~~Configurations: default, QR-only, one specified 1D format~~ — **done**, reported
   separately. The matrix is the full cross product, so it includes cases that cannot
   decode; those measure a miss and are labelled as such.
4. **Commit a baseline result set for every configuration.** Note the correction in
   `BENCHMARKS.md` §10.7: the baseline cannot be taken at B0, because the harness does not
   exist at B0. It is taken at the first commit that contains the harness, and the result
   file records that commit.
5. `benchmark/tools/compare.py` + its own unit tests: reads two result sets, emits
   grouped CSV/JSON and a Markdown report, and returns **pass / fail / inconclusive**
   against the gates in `BENCHMARKS.md` §10.6. It must say `inconclusive` rather than
   `pass` when the data cannot support a conclusion. CI currently has no step for it;
   add one when it exists. The input side is already built:
   `benchmark/tools/l2_report.py` turns a Microbenchmark run into the §8 envelope, with
   the bootstrap intervals and the inconclusive-reason warnings already computed.

### Phase 5 — Kotlin and the native decoder

The user's stated direction (route 1 of the screenshot they sent): zxing-cpp + ARM NEON.

6. Convert the maintained Android code to Kotlin module by module, keeping the Java API
   source-compatible.
7. `zxing-cpp` backend behind the existing decoder seam, CMake building `arm64-v8a` and
   `armeabi-v7a`, shipped in the same AAR. **16 KB page alignment is mandatory** for
   Android 15+.
8. Feed CameraX `ImageProxy` `YUV_420_888` straight in: honour `rowStride`/`pixelStride`,
   crop to the scan region, no full-frame copy, and do not release a buffer while an
   async decode still holds it.
9. API to enable/disable formats per scan so the native reader does less work.
10. **A/B the native backend against the Java one on the fixed dataset, then decide the
    default.** No speedup may be claimed before that comparison exists. "Swapped the
    decoder" is not evidence.

### Phase 3 — carried-forward defects (independent; can be picked up any time)

- **A-06 / A-07** History CSV export and share-as-image write to the legacy external
  storage root; broken under scoped storage since Android 10. Needs app-specific storage
  plus a `FileProvider`.
- **A-08** Wi-Fi result handler cannot add networks on Android 10+. Modern path is
  `WifiNetworkSuggestion` or `ACTION_WIFI_ADD_NETWORKS`.
- **A-09** Book search and several outbound links use cleartext `http://`, blocked from
  targetSdk 28.
- **A-10** App-share picker and the browser special case are hit by Android 11 package
  visibility; needs a `<queries>` element.
- **A-12** Ambient-light torch control rebuilds the autofocus manager on every toggle.
- **A-16** Android 16 ignores `screenOrientation` on large screens, so the
  landscape-designed capture UI runs portrait. The crop mapping is fixed; the **layout**
  and the framing-rect shape still assume landscape.

### Release and distribution

- Nothing has been released. Tagging `v0.1.0` triggers `.github/workflows/release.yml`,
  which builds, checksums, publishes to GitHub Packages and cuts a GitHub Release whose
  body comes from the `CHANGELOG.md` section for that version. Move the `[Unreleased]`
  section to `[0.1.0]` first, or the release script exits non-zero by design.
- **Maven Central is not set up.** It needs a Central Portal account with the
  `io.github.kakakakacat` namespace verified, a published GPG key, and a plugin that
  speaks the Central Portal bundle API. Details in `PUBLISHING.md`.

### Known gaps in the testing itself

- One physical device only, and it is Android 14. No real-camera verification on
  Android 15 or 16, and none on a low-end or a non-Qualcomm device.
- 13 samples report `INCORRECT` in L1 with the decoded format matching the expected one,
  which means the text differs. Upstream's blackbox tests pass a per-case charset that
  the dataset manifest does not yet record, so these are most likely a dataset-fidelity
  gap rather than decoder bugs — **unconfirmed**, and worth settling before anyone reads
  the correctness numbers as a decoder verdict.
- 2 samples in `falsepositives-2/` decode as EAN_8 / UPC_E. Also unexamined.
