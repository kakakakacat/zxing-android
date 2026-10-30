# Handoff — working state

Operational notes that do not belong in the README: where things stand, what is in
flight, and the environment quirks that will otherwise be rediscovered the hard way.

Last updated at commit `df9a7904c` on branch `maint/android`.

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
| `:benchmark-android:testReleaseUnitTest` | 10 host tests: frame determinism, goldens, decodability | local + CI |
| `benchmark/tools/` tests | 64 tests across `l2_report`, `compare`, `noise_floor` | local + CI |
| L2 hd/default slice | 15 cases, 50 repeats, clocks locked | DN-ENG41, 72 min |
| L2 full matrix ×2 | 135 cases each, identical protocol | DN-ENG41, 40 min each |
| `FrameIntegrityTest` | 2 tests, 0 failures, golden hashes identical | API 34 device **and** API 35 + 36 emulators |

**L2 numbers now exist. They are a baseline, not a claim** — nothing has been optimised
and nothing compared. L1 timings remain host-JVM diagnostics and must never be quoted as
an Android result. No L3 or L4 number exists, so nothing is known about camera-ready
latency, first-scan latency, CPU, memory, GC or power.

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

### Four traps and a runbook

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

   What is still untested is the device physically unplugged and actively cooled, which
   needs hands. Everything reachable over adb has been tried.
4. **A Microbenchmark run stalls when the screen goes off.** `IsolationActivity` stops
   making progress and says nothing: no failure, no timeout, the process just stops using
   CPU. DN-ENG41 locked seven minutes into the first L2 run and sat there. Run
   `adb shell svc power stayon true` before any device benchmark; to tell a stall from
   slow work, watch fields 14/15 of `/proc/<pid>/stat`.
5. **Emulator `wm size` changes persist.** The Android 16 large-screen reproduction ran
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
- **Emulator timings are not used.** An emulator runs on the host's cores and distorts
  the ratio *between* stages, not merely the magnitude — `binarize` is memory-bandwidth
  bound, `decode` is branch-heavy. `compare.py` treats `EMULATOR` as invalidating and
  will not let it be waived. Emulators run the correctness gate here, nothing else.
  Rationale in `BENCHMARKS.md` §10.7 step 4.
- **The effect floor is measured, not chosen**: 3% for cases at or above 100 µs, 10%
  below. From two full-matrix runs of identical code. Re-measure after any change to the
  device or the protocol; it is two 40-minute runs and one command.
- **Vendor thermal thresholds are never raised.** It would make the warnings disappear
  and produce numbers the shipped product never delivers, with nothing in the result file
  to show it. `BENCHMARKS.md` §6. Locking clocks is the opposite kind of change: it pins
  a sustainable operating point and makes the numbers *slower*.
- **Camera1 stays for now.** A Camera2/CameraX decision record is Phase 6, and must not be
  presented as a performance improvement on its own.

---

## What is left

Ordered as the user asked for it. The README carries the same list as tickboxes.

### Phase 4 — done, except L3 and L4

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

**What is left in Phase 4:**

- **L3** (§11) — Macrobenchmark, physical device, cold start to first successful scan.
  The hard part is the scene, not the harness: an unscripted scene makes the operator's
  hand the dominant variable. Until the scene is fixed and photographed, L3 numbers are
  inadmissible under §2.
- **L4** (§12) — the sustained-scanning soak. This is the layer that would catch a native
  decoder that is faster cold and slower hot.
- **A CI comparison step.** `compare.py` exists and there is now a baseline to compare
  against, but no workflow runs it. CI cannot produce a trustworthy timing on a shared
  runner, so this needs a decision about where candidate runs come from.

**One open question, and it is the user's.** Every result from DN-ENG41 so far carries
either `THERMAL_THROTTLE` or `THROTTLE_WAIT_DISABLED`, because the device was hot for the
whole session. `compare.py` blocks on both, so **every comparison against these baselines
is `inconclusive` by default**.

**The cheap way out was tried and it failed.** Letting the device cool on AC for 45
minutes does not clear the 40 °C skin threshold; it asymptotes on the threshold and stays
at `Thermal Status: 3` (trap 3, with the log). So the remaining options are:

1. **Unplug it and cool it physically**, then run the hd/default slice from cold. This is
   the only untried route that could produce a baseline with no thermal warning at all,
   and it needs hands — nothing reachable over adb is left.
2. **Accept the warning** and read these results with `--accept-noise-warnings`, which
   records the waiver in every output file it writes.
3. **Amend §10.6** to say that on this device the thermal warning may be read through,
   with the reasoning written down.

Raising the vendor thermal thresholds is not one of them — see the decisions above.

Worth weighing against option 1: the measured effect floor is 3% for the cases that
matter, and it was measured *under* this thermal state across two full matrices. So the
throttling is not obviously costing resolution — it is costing the right to call a result
a result. That is an argument for option 3 over spending much effort on option 1, but it
is an argument, not a decision.

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
  Android 15 or 16, and none on a low-end or a non-Qualcomm device. **Every L2 number is
  from that one device**, at one pinned operating point, in one thermal state.
- The L2 effect floor comes from a single pair of runs. More pairs would sharpen the
  tail estimate; the current floors take the conservative reading instead.
- 13 samples report `INCORRECT` in L1 with the decoded format matching the expected one,
  which means the text differs. Upstream's blackbox tests pass a per-case charset that
  the dataset manifest does not yet record, so these are most likely a dataset-fidelity
  gap rather than decoder bugs — **unconfirmed**, and worth settling before anyone reads
  the correctness numbers as a decoder verdict.
- 2 samples in `falsepositives-2/` decode as EAN_8 / UPC_E. Also unexamined.
