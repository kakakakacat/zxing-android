# Handoff — working state

Operational notes that do not belong in the README: where things stand, what is in
flight, and the environment quirks that will otherwise be rediscovered the hard way.

Last updated at commit `efa0a2dea` on branch `maint/android`.

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

Nothing is uncommitted. Nothing is released; no tag has been pushed.

---

## Verified, with the evidence

| What | Result | Where |
| --- | --- | --- |
| `:core:test` | 602 tests, 0 failures | local |
| L1 corpus, both backends, DN-ENG41 | java 862/4/254/46/2, zxing-cpp 890/3/227/48/0 | `reports/l1-49bd3beb3-*` |
| L1 at TRY_HARDER, both backends | java 921 correct, zxing-cpp 960 | `reports/l1-49bd3beb3-*-tryharder.json` |
| L1 device harness against the host run | same five totals through the same decoder | §9.1 |
| `:scanner:testDebugUnitTest` | 69 tests: 9 coordinate-mapping, 6 hint-construction, 18 luma-plane, 8 decoder-seam, 6 backend-selection, 10 native-rendering, 3 Kotlin class-literal mapping, 9 Java source compatibility | local + CI |
| `:scanner:connectedDebugAndroidTest` | 21 tests: 35 fixed frames over 16 formats, a live format-switch test, the default-backend checks and the native backend, 0 failures | Android 14 device **and** CI emulators API 21/34/35/36 |
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
8. **Emulator `wm size` changes persist.** The Android 16 large-screen reproduction ran
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
- **Camera1 stays for now.** A Camera2/CameraX decision record is Phase 6, and must not be
  presented as a performance improvement on its own.

---

## Start here if you are new to this file

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
turned up **a release-blocking defect in `release.yml`** — see "Release and
distribution" below. Treat a clean result from an unexercised script as unproven until
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

   The only Java left in the tree is `:core` and eleven test files in `:scanner`. Those
   are Java **deliberately**: `JavaSourceCompatibilityTest` and its neighbours exist so
   javac has to accept the Kotlin API, and rewriting them in Kotlin would delete the
   check. Do not convert them.

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
    - The Java decoder wins **1D hits** by 5–20×, because `HybridBinarizer` is lazy and
      the 1D readers scan rows outward from the middle, so a centred 1D hit binarizes a
      few rows. zxing-cpp binarizes the whole frame first. Absolute gap: **1.7 ms, once**,
      against tens of milliseconds saved on every miss frame before it.

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

All of these survived the Kotlin conversion untouched, by decision. The five that live in
`:app` now carry a comment naming their number, so `grep -rn 'Defect A-' android/src`
lands on the exact lines: A-06 in `HistoryManager.saveHistory`, A-07 in
`EncodeActivity.share`, A-09 in `BrowseBookListener` and `SearchBookContentsActivity`,
A-10 in `LoadPackagesAsyncTask`. A-08's `WifiConfigManager` carries the whole deprecated
`WifiConfiguration` API in a `@Suppress` that names the defect instead. A-12 and A-16 are
not in `:app` at all — A-12 is `AmbientLightManager` in `:scanner`, A-16 is the capture
layout — and neither carries a comment yet.

**A-09 is reproducible in one command**, which is the shortest way to see one of these
fail for real:

```
adb shell "am start -a com.google.zxing.client.android.SEARCH_BOOK_CONTENTS \
  -e ISBN 9780596009205 -e QUERY barcode"
```

then press the search button: `java.io.IOException: Cleartext HTTP traffic to
www.google.com not permitted`, and the UI says "Sorry, the search encountered a problem."

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

- **Tagging `v0.1.0` today would publish an AAR nobody can resolve.** Found on
  2026-10-31 while checking which tooling had gone stale at the `849921bfa` rename; not
  fixed, because it was outside the Kotlin work in flight.

  `:scanner` declares `api(project(":core"))`, so its published POM carries a compile
  dependency on `io.github.kakakakacat.zxing:zxing-core:<version>` — confirmed by reading
  the POM that `publishToMavenLocal` produces. `release.yml` publishes **only**
  `:scanner:publishReleasePublicationToGitHubPackagesRepository`, so that dependency
  would not exist in GitHub Packages and every consumer's resolution would fail.

  `:core` is already set up to publish (`core/build.gradle.kts` registers a `maven`
  publication as `zxing-core` with the same group and version), so the fix is to run its
  publish task in the same step.

  The comment above that step is also wrong and is what hid this: it says `:core` is
  deliberately not published because "its classes are `com.google.zxing.*`, so a second
  artifact carrying them would collide with upstream". They have not been
  `com.google.zxing.*` since `849921bfa` — avoiding exactly that collision is *why* the
  rename happened, and this file's own evidence table records "Published AAR + jar, zero
  `com/google` entries". The reasoning was left behind by the rename and the step was
  never revisited.

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
- ~~13 samples report `INCORRECT` in L1…~~ **Settled.** It was not a charset gap — every
  sidecar involved is ASCII, and that guess was wrong. Nine of the thirteen were a bug in
  the L1 harness itself (`classify()` stripped a trailing newline from the expectation but
  not from the decoded text) and are now `CORRECT`; the corpus reads 862/4/254/46/2. The
  four that remain are understood and real, and the two false positives sit inside
  upstream's own tolerance of four. `BENCHMARKS.md` §9 has the case-by-case table.
