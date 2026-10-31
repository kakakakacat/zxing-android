# Benchmarks — how performance is measured here

This fork makes no performance claims. This document is the standard a claim has to meet
before it can be made, and the specification of the harness that produces the evidence.

It exists before the harness on purpose. A measurement plan written after the numbers are
in is a rationalisation of them.

---

## 1. What this document is for

Three jobs:

1. **Define the layers.** Four of them, L1 to L4, each answering a different question. A
   number from one layer never answers another layer's question.
2. **Fix the method.** Dataset, device, warmup, repetition count, statistics and result
   schema are pinned here, so two runs months apart are comparable.
3. **Set the gates.** §10.6 says when a comparison is allowed to say *faster*, when it must
   say *slower*, and — the important one — when it must say **inconclusive**.

---

## 2. The rules of evidence

These are binding on the README, the changelog, commit messages and any issue reply.

- **No claim without a result file.** A performance statement must cite a committed result
  set: layer, run id, commit, dataset hash, device, configuration.
- **A baseline and a candidate, or nothing.** "Faster" is a comparison. A single run is not
  a comparison.
- **Host JVM numbers are not Android numbers.** L1 timings come from a desktop JVM with a
  different JIT, a different memory system and no thermal limit. They are a diagnostic for
  spotting a decoder regression early. They may never be quoted as an Android result.
- **Removing work is not a measured speedup.** Where this fork deletes obviously wasteful
  code, that is described as simpler code until a layer measures it.
- **Swapping an implementation is not evidence.** Replacing the Java decoder with a native
  one is a change, not a result; §10.6 decides what it did.
- **`inconclusive` is a legitimate and expected outcome.** The comparison tool must emit it
  rather than round a noisy result towards the answer the author wanted.
- **Device and thermal state travel with the number.** A figure without a device is not a
  figure.

---

## 3. Vocabulary

| Term | Meaning here |
| --- | --- |
| **Frame** | One preview buffer: 8-bit luminance plane, a width, a height and a stride |
| **Crop** | The framing rectangle in *camera* coordinates, from `PreviewCoordinateMapper` |
| **Decode attempt** | One pass of `MultiFormatReader.decode` over one `BinaryBitmap` |
| **Hit** | A decode attempt that returns a `Result` |
| **Miss** | A decode attempt that throws `NotFoundException` — the common case on live preview |
| **Configuration** | A named hint set: `default`, `qr_only`, `oned_only`, `try_harder` |
| **B0** | The baseline commit a comparison is measured against; today `50e6e3860` |
| **Dataset hash** | `dataset_hash` in `benchmark/dataset/manifest.json`, over every sample's SHA-256 |

Hit and miss timings are reported separately and never pooled. A miss costs more than a
hit — the reader exhausts every enabled format before giving up — so a dataset's hit/miss
ratio silently sets any pooled average.

---

## 4. The four layers

| Layer | Question | Harness | Where it runs | State |
| --- | --- | --- | --- | --- |
| **L1** | Does the decoder still decode what it used to, and roughly how expensive is a decode? | `:benchmark-jvm`, `zxingbench.L1Runner` | host JVM | **built** |
| **L2** | How long does one frame take through the real Android frame path, stage by stage? | `:benchmark-android`, AndroidX Microbenchmark | device / emulator | **specified, §10** |
| **L3** | How long from launch to first successful scan, with a real camera? | Macrobenchmark + a scripted scene | physical device | specified, §11 |
| **L4** | What does sustained scanning cost in CPU, memory, GC and heat? | instrumented soak run | physical device | specified, §12 |

L1 is correctness-first and timing-second. L2 is the one that gates optimisation work.

---

## 5. The dataset

`benchmark/dataset/manifest.json`, built by `benchmark/dataset/build_manifest.py` from the
Apache-2.0 blackbox corpus that ships in `core/src/test/resources/blackbox`.

- 1168 samples, each an independent source image — transformations of one image are never
  counted as several samples.
- Per sample: `sample_id`, `path`, `sha256`, `expected_format`, `expected_text`, `width`,
  `height`, `stride`, `rotation`, `scene_tags`, `license`.
- `dataset_hash` covers all of it. **Two result sets with different `dataset_hash` values
  are not comparable**; the comparison tool refuses them (§13).
- CI rebuilds the manifest and fails on any diff, so the dataset cannot drift silently.

Negative samples (`falsepositives-*`) carry no expected text and are scored as
`CORRECT_NO_RESULT` / `FALSE_POSITIVE`.

**Two known dataset defects, open:** 13 samples report `INCORRECT` with the expected format
but different text — upstream passes a per-case charset the manifest does not record yet —
and 2 negatives decode as EAN_8 / UPC_E. Both are unexamined. Until they are settled, the
correctness totals are a regression tripwire, not a verdict on the decoder.

---

## 6. Devices and environment

A result is tied to the machine that produced it.

| Role | Machine | Notes |
| --- | --- | --- |
| Reference device | **DN-ENG41**, Android 14 (API 34), arm64, no GMS | the only physical device available; `userdebug`, `adb root` works |
| Emulator, Android 15 | AVD `zxing_api35`, Pixel 7, arm64 | API 35 |
| Emulator, Android 16 | AVD `zxing_api36`, Pixel 7, arm64 | API 36 |
| Host | macOS 27.2 arm64, JDK 25 (JBR), Gradle toolchain 17 | L1 only |

Rules:

- **L2 and above must name a device.** An emulator result is labelled as one and never
  compared against a physical-device result.
- **Emulator timings are not used at all.** They run on the host's cores with no thermal
  model, and they distort the ratio between stages, not merely the magnitude — see §10.7
  step 4. `compare.py` treats `EMULATOR` as invalidating and will not let it be waived.
  An emulator is for correctness tests here, not for timings.
- Before a device run: screen on, charger behaviour recorded, airplane mode on, no other
  foreground app, and `adb shell cmd thermalservice override-status 0` is *not* used — a
  masked thermal state produces numbers the product will never see.
- **Lock the clocks first: `./gradlew :benchmark-android:lockClocks`.** It needs root,
  which DN-ENG41 has. Without it every result carries `UNLOCKED_CLOCKS` and §10.6 blocks
  on it. `unlockClocks` restores the device by rebooting it, so a phone left locked will
  feel slow until it is rebooted.
- **`adb shell svc power stayon true` next.** Microbenchmark's `IsolationActivity` stops
  making progress when the screen goes off, and it does so silently: the run neither fails
  nor finishes, the process simply stops accumulating CPU time. Observed on DN-ENG41,
  which locked seven minutes into a run. If a run appears to hang, compare fields 14 and
  15 of `/proc/<pid>/stat` a few seconds apart before suspecting the benchmark.
- Microbenchmark's own warnings (debuggable, emulator, unlocked clocks, thermal throttle)
  are recorded in the result file rather than suppressed.
- **One device and one Android version is the current, stated limitation.** Nothing here
  generalises to low-end or non-Qualcomm hardware.

---

## 7. Method

- **Warmup until stable, then measure.** L2 delegates this to AndroidX Microbenchmark,
  which warms up adaptively; L1 uses an explicit `--warmup` (default 3) and `--measure`
  (default 10) per sample.
- **Minimum 50 measured iterations** per L2 case. Fewer is reported as inconclusive
  regardless of the spread.
- **Report the median and the p90, never the mean.** Decode time is long-tailed; a mean
  tracks the tail and hides the typical frame.
- **Report a 95% confidence interval on the median**, by bootstrap over the measured
  iterations (10 000 resamples, fixed seed, recorded in the result file).
- **Per-stage allocations and GC count** are recorded alongside time. A stage that got
  faster by allocating a frame-sized buffer per frame has not got faster.
- **Each configuration is its own run.** Results from `default` and `qr_only` are never
  pooled or averaged together.

---

## 8. The result-file schema

Every layer writes one JSON file per run. Common envelope, so one comparison tool reads
all of them:

```json
{
  "layer": "L2",
  "run_id": "b0-DN-ENG41-20261030",
  "commit": "50e6e3860",
  "config": "DEFAULT",
  "dataset_hash": null,
  "frames": { "hd/hit_qr": "f70e4a4c…", "…": "…" },
  "device": { "label": "DN-ENG41", "api": 34, "emulator": false, "cpu_locked": false },
  "warnings": ["…conditions §10.6 turns into `inconclusive`…"],
  "bootstrap": { "resamples": 10000, "seed": 20261030 },
  "cases": [ { "name": "…", "stage": "…", "resolution": "hd", "variant": "hit_qr",
               "config": "default", "decodes": true,
               "ns_p50": 0, "ns_p90": 0, "ns_ci95_low": 0, "ns_ci95_high": 0,
               "iterations": 0, "alloc_count_p50": 0 } ]
}
```

L2 replays generated frames rather than the L1 corpus, so its `dataset_hash` is null and
`frames` carries the comparability key instead (§10.4). Allocations are reported as a
**count**, not bytes: that is what the platform's own counter gives, and converting it to
bytes would be arithmetic on an assumption. A measurement that is absent is `null`, never
zero — "no allocation metric was collected" and "it allocated nothing" are different
claims.

L1's existing file is this envelope with a `samples` array and `status_counts` instead of
`cases`; it also carries `note`, which states that host timings are diagnostic only.

Result sets are committed under `docs/maint/reports/` with the run id in the filename.

---

## 9. L1 — corpus correctness and host decode timing

**Built.** `./gradlew :benchmark-jvm:run --args="--manifest benchmark/dataset/manifest.json
--out build/l1.json --config default --warmup 3 --measure 10"`.

Each image is decoded to a luminance plane once, up front; the timed section covers the
binarizer and the reader only, so file I/O and PNG decompression are not counted. The
decoder is `:core` — the same code the AAR ships, so an L1 number and an app number refer
to the same decoder.

Latest full run, all 1168 samples, `default`:

| Status | Count |
| --- | --- |
| CORRECT | 853 |
| INCORRECT | 13 |
| NOT_FOUND | 254 |
| CORRECT_NO_RESULT | 46 |
| FALSE_POSITIVE | 2 |

`NOT_FOUND` is dominated by upstream's deliberately hard images, which upstream's own
blackbox tests also allow to fail; the number is a tripwire against *change*, not a target.

**L1 timings are host-JVM diagnostics. They are not Android results.**

---

## 10. L2 — the Android frame-pipeline benchmark

Not built yet. This section is its specification, and it is the next piece of work.

Why it comes before any optimisation: the native-decoder work cannot be judged without a
frame-pipeline baseline on a device. Building the harness afterwards would be writing the
scoreboard after the game.

### 10.1 What it measures, and what it does not

**Measures:** the cost of turning one preview buffer into a decode outcome, on the device's
own ART, stage by stage, with allocations.

**Does not measure:** camera open latency, autofocus, exposure convergence, preview frame
rate, or anything a user would call "how fast it scans". Those are L3. An L2 improvement
is not a scanning improvement until L3 says so.

### 10.2 Module

`:benchmark-android` at `benchmark/android`. **Built.** Never published, never shipped.

- `androidx.benchmark:benchmark-junit4` 1.5.0 and the `androidx.benchmark` Gradle plugin;
  the benchmarks are instrumentation tests against `:scanner` and `:core`.
- `minSdk` 21, matching the library, so the benchmark cannot measure a path the shipped
  AAR does not take on an old device.
- `testBuildType = "release"` with R8 on: the code under measurement is the code a consumer
  gets. Microbenchmark numbers from a debuggable build are not numbers.
- No camera, no printed barcode, no steady hand: `FrameFactory` generates the frames
  (§10.4), so a run is deterministic and can fail a change rather than drift.

| File | What it is |
| --- | --- |
| `ScanGeometry` | the pinned viewing scenario and stage 1's call into `PreviewCoordinateMapper` |
| `FrameFactory` | generates the nine replay frames and their SHA-256 |
| `FramePipeline` | the five stages, calling what `DecoderThread` calls, how it calls it |
| `L2Config` | the three hint sets, and which variants each one can decode at all |
| `FrameGoldens` | reads the committed frame hashes; one file, three readers |
| `FramePipelineBenchmark` | the benchmark itself, parameterised over the matrix |
| `FrameIntegrityTest` | the device-side gate in front of the benchmark |
| `FrameFactoryTest`, `FrameGoldensTest` | the host tests §10.4 requires |

How to run it:

```sh
# the CI slice: 1280x720, default hints, all three variants
./gradlew :benchmark-android:connectedReleaseAndroidTest

# the full 3 x 3 x 3 matrix, for a baseline
./gradlew :benchmark-android:connectedReleaseAndroidTest -Pl2.matrix=full

# the host tests alone — no device needed
./gradlew :benchmark-android:testReleaseUnitTest
```

`FrameIntegrityTest` runs before the benchmark on purpose. It re-generates all nine frames
on the device, checks them against the committed goldens, and checks that every hit frame
still hits and every miss frame still misses. A miss frame that quietly started decoding
would turn the most expensive case in the matrix into the cheapest, and every comparison
after it would be wrong in the flattering direction.

### 10.3 The pipeline, stage by stage

Mirrors the live path — `PreviewCallback` → `CameraManager.buildLuminanceSource` →
`DecoderThread` — with the camera replaced by a fixed buffer. Each stage is timed as its
own benchmark case, and the whole path is timed as one more:

| Stage | Case name | What it covers |
| --- | --- | --- |
| 1 | `crop` | framing rectangle through `PreviewCoordinateMapper`, display→camera rotation |
| 2 | `luminanceSource` | `PlanarYUVLuminanceSource` construction over the cropped region |
| 3 | `binarize` | `HybridBinarizer` producing the black matrix |
| 4 | `decode` | `MultiFormatReader.decode`, hit and miss reported separately |
| 5 | `endToEnd` | stages 1–4 as the decode thread actually runs them |

`endToEnd` is the headline number. The per-stage cases exist so a regression can be
attributed instead of guessed at.

### 10.4 Fixed replay inputs

Three resolutions, because the cost is dominated by pixel count and the preview size varies
by device:

| Input | Size | Why |
| --- | --- | --- |
| `vga` | 640 × 480 | the small preview size the camera layer still selects on low-end devices |
| `hd` | 1280 × 720 | the common modern preview size |
| `fhd` | 1920 × 1080 | upper bound; where a full-frame copy hurts most |

Each resolution is replayed in three variants, so the hit/miss split is explicit:

- **`hit_qr`** — a QR code inside the framing rectangle, decodes
- **`hit_1d`** — a UPC-A/EAN-13 barcode inside the framing rectangle, decodes
- **`miss`** — no barcode present; the reader exhausts every enabled format

Frames are **generated, not committed**. Nine raw luminance planes at these resolutions is
about ten megabytes of binary in the repository, and a committed blob is no more
reproducible than a tested generator — only harder to review. `FrameFactory` fills the
plane with a fixed xorshift64\* noise band, encodes the barcode with this fork's own writer
(`ZX-L2-QR`, UPC-A `012345678905`) and draws it into the crop rectangle with a quiet zone,
so a hit frame decodes through the *real* crop rather than only when the whole frame is
read. The PRNG is written out in that file rather than taken from `java.util.Random`, so
the byte stream is defined by this fork and cannot move under a JDK or ART update.

The comparability key is `benchmark/android/goldens/frames.golden.json`: the committed
SHA-256 of all nine frames, plus the geometry they were built against. One file, three
readers — the host unit test, the on-device run and the report converter.
**A result set whose frame hashes differ from the baseline's is not comparable**, and both
the host test and `FrameIntegrityTest` on the device fail outright rather than let such a
run produce a number. Regenerating the goldens is a deliberate commit that says why, and
it invalidates every baseline taken before it.

The crop that geometry produces, for the record:

| Input | Frame | Crop in frame coordinates | Crop size |
| --- | --- | --- | --- |
| `vga` | 640 × 480 | `[107, 151, 533, 329]` | 426 × 178 |
| `hd` | 1280 × 720 | `[213, 227, 1067, 493]` | 854 × 266 |
| `fhd` | 1920 × 1080 | `[320, 340, 1600, 740]` | 1280 × 400 |

The scenario behind those numbers is pinned in `ScanGeometry`: a 1080 × 2400 portrait
display, a tall viewfinder band at `[340, 400, 740, 2000]`, and a display-to-camera
rotation of **90**. That is the Android 16 large-screen portrait case — the one `A-13`
fixed — so the crop goes through the rotation path rather than the identity path that only
ever worked by accident, and a regression in it shows up here instead of on a user's large
screen. A tall band on screen rotates into a wide band in frame space, which is the only
shape a 95-module 1D symbol can be measured in.

Stride is set to width for the Camera1 path. When Phase 5 feeds `ImageProxy`
`YUV_420_888` in directly, a `stride != width` variant is added here and baselined before
that path is enabled by default.

### 10.5 Configurations

Run and reported separately, never pooled:

1. `default` — every format the reader supports, no `TRY_HARDER`; what the app ships.
2. `qr_only` — `POSSIBLE_FORMATS = [QR_CODE]`; the common library-consumer case.
3. `oned_only_upca` — a single specified 1D format, the cheapest realistic hint set.

3 resolutions × 3 variants × 3 configurations × 5 stages is the full matrix, selected with
`-Pl2.matrix=full`. CI runs the `hd` + `default` slice, which is the default.

The cross product is deliberately complete, so it contains cases that cannot decode —
`hit_qr` under `oned_only_upca`, for instance. Those are legitimate: they measure a miss,
and an expensive one. `L2Config#canDecode` names them, and the result file records
`decodes` per case, so nothing in a report has to be inferred from a case name.

### 10.6 Gates — pass, fail, inconclusive

A comparison of a candidate against a baseline. Both sides must share `dataset_hash`,
frame hashes, device, API level and configuration, or the verdict is `inconclusive`
immediately and the tool says which field differed.

Per case, on the median, using the 95% intervals from §7:

| Verdict | Condition |
| --- | --- |
| **pass** (improvement) | candidate CI upper bound < baseline CI lower bound **and** the median improves by at least the effect floor |
| **fail** (regression) | candidate CI lower bound > baseline CI upper bound **and** the median worsens by at least the effect floor |
| **inconclusive** | anything else |

"Anything else" explicitly includes all of these, each reported with its reason:

- the intervals overlap;
- the medians differ by less than the effect floor for a case of that size — below the
  measured noise floor on the reference device, so a difference is not resolvable;
- either side has fewer than 50 measured iterations;
- either side carries a Microbenchmark warning that invalidates timing (debuggable build,
  unlocked clocks, thermal throttling detected), except where the read-through below
  applies;
- either side is an emulator run and the other is not;
- `alloc_count_p50` grew by more than that same floor while time improved — a time win
  bought with allocations is not a win until L4 says the GC cost is absorbed.

A run as a whole is `fail` if any case fails, `pass` only if at least one case passes and
none fails, otherwise `inconclusive`.

#### Reading through a standing warning

A warning may describe a **standing condition of the device** rather than something the
operator left switched off. On DN-ENG41 three do: `THERMAL_THROTTLE`,
`THROTTLE_WAIT_DISABLED` and `NO_SUSTAINED_PERFORMANCE_MODE`. The phone sits above its
40.0 °C skin threshold at rest, so the cool-down wait cannot succeed and switching it off
changes nothing but the clock; and the hardware has no sustained performance mode to
enable. None of this is avoidable from here — §13.2 has the 45-minute cooling measurement
that closed the last cheap route, and raising the vendor threshold is ruled out by §6.

**Such a warning is read through, and the comparison proceeds, when all of:**

1. the warning is one of the three named above;
2. **both sides carry exactly the same set of them.** One run hot and the other cold is a
   difference *between the runs*, not a shared condition, and still blocks;
3. the device is one whose effect floor was measured under those same conditions.
   DN-ENG41 is, and it is the only one. Adding another device to that list means
   measuring its floor in that state first.

`UNLOCKED_CLOCKS` is deliberately **not** on the list. It is avoidable — `adb root` plus
`lockClocks` — and it moves the operating point rather than describing it.

**Why this is admissible — measured, not argued.** With the clocks pinned, the thermal
governor does not move them. Evidence:
`reports/thermal-vs-locked-clocks-DN-ENG41-20261031.log` — a 5 min 09 s run at thermal
status SEVERE with `vendor.thermal-engine` running, and the kernel's own `time_in_state`
counters reset immediately beforehand:

| | locked at | ticks there | ticks at every other frequency |
| --- | --- | --- | --- |
| big cores | 1344000 kHz | 33425 (334.25 s) | **0** |
| little cores | 1190400 kHz | 33438 (334.38 s) | **0** |

Zero ticks at any other frequency, *including every frequency below the locked one*,
which is where a mitigation would have put it. 175 samples at 1 Hz agree, and
`scaling_max_freq` was never clamped either, so no cooling device lowered the policy
ceiling. `lockClocks` pins the big cores at 56% of their maximum, so the operating point
already sits below anything the governor would reduce it to: there is nothing left for it
to take away.

Note that `lockClocks` *attempts* to stop `vendor.thermal-engine` and **this device
refuses** — so these numbers are taken with thermal management fully running. Nothing
here suppresses it, and §6 forbids doing so.

The second, weaker argument still holds and is independent: the effect floor below was
not chosen against a quiet device, it was measured by running the full matrix twice on
this phone *in this state*, so whatever the throttling costs in run-to-run spread is
already inside the 3% and 10% numbers the gate uses. And both sides pay it equally, which
is the condition that matters for a comparison.

**What it does not license.** The frequency measurement covers **CPU frequency only**:
the memory bus could not be locked on this device, and one 5-minute run is not a soak —
a sustained effect is exactly what L4 (§12) exists to find. It is not a claim that these
timings are what a cool device would produce; they are slower, and §6's rule that
absolute numbers belong to their operating point is unchanged. It does not travel to
another device, another protocol, or a re-measured floor. And it is recorded, not silent: `compare.py` lists the read-through
warnings in `verdict.json` and names them at the top of every Markdown report, so a number
read under this clause says so wherever it is quoted.

**Checked against the two full-matrix runs.** Under this clause run 1 and run 2 of
`edef9d0a0` — identical code, identical protocol — clear the warning gate and then come
back `inconclusive` on all 135 cases, which is the correct answer for two runs of the same
binary. The clause removes a block; it does not manufacture a verdict.

#### The effect floor, measured

No longer a placeholder. The full matrix was run twice on DN-ENG41 under one protocol
with nothing changed between the runs, so everything the two runs disagreed about is
noise. 135 cases, `benchmark/tools/noise_floor.py`, commit `edef9d0a0`:

| Band | Cases | p50 | p90 | p95 | max |
| --- | --- | --- | --- | --- | --- |
| median ≥ 100 µs | 69 | 0.15% | 1.27% | 1.66% | **2.51%** |
| median < 100 µs | 66 | 1.25% | 3.53% | 5.25% | **8.44%** |

Per stage, which shows where the noise actually lives:

| Stage | typical median | p50 | p90 | max |
| --- | --- | --- | --- | --- |
| `crop` | 195 ns | 1.47% | 2.76% | 3.04% |
| `luminanceSource` | 160 ns | 2.49% | 6.00% | 8.44% |
| `binarize` | 4.9 ms | 0.27% | 1.66% | 2.24% |
| `decode` | 1.3 ms | 0.14% | 1.14% | 1.45% |
| `endToEnd` | 2.9 ms | 0.18% | 0.97% | 2.51% |

**The floor therefore depends on the size of the case**, because one global floor is wrong
in both directions: at 5% it would have hidden a real 3% regression in `decode`, and it
would still have called noise a result in `luminanceSource`.

| Case | Floor |
| --- | --- |
| median ≥ 100 µs | **3%** |
| median < 100 µs | **10%** |

Each floor sits above the worst its own band was observed to do, with a little margin.
From a single pair of runs the maximum is a weak estimate of the tail, so the conservative
reading is the right one.

Two things this does not mean. It is not a claim that the device is quiet — the noise
lives almost entirely in the two stages that cost a few hundred nanoseconds and contribute
nothing to a frame, and a 10% floor on a 160 ns stage is harmless because nobody will ever
optimise it. And it is specific to this device and this protocol: re-measure after any
change to either, which is two 40-minute runs and one command.

### 10.7 The B0 baseline

**A correction to this document's own plan.** Earlier revisions said the L2 baseline is
taken at B0, `50e6e3860`. It cannot be: the harness did not exist at B0, and a benchmark
cannot be run on a commit that does not contain it. The baseline is therefore taken at the
first commit where `:benchmark-android` exists, and that commit id is what the result file
records. This is not a loophole — between B0 and here the decode path changed only by the
namespace rename and the crop-mapping fix, both of which are in the result anyway — but a
baseline that claims a commit it was not run on is exactly the kind of tidy fiction this
document exists to prevent.

Before any Phase 5 work:

1. Run the full matrix (`-Pl2.matrix=full`) on **DN-ENG41**.
2. Commit the result sets under `docs/maint/reports/` as
   `l2-<commit>-DN-ENG41-<config>.json`.
3. ~~Record the measured run-to-run spread that replaces the 5% floor~~ — **done**, see
   §10.6. Two full-matrix runs on 2026-10-30/31, clocks locked, cool-down wait disabled;
   the device was at thermal status SEVERE throughout, which is its normal state.
4. Run **`FrameIntegrityTest` only** on the API 35 and API 36 emulators — not the
   benchmark.

   An earlier revision of this document said to repeat the `hd`/`default` slice on both
   emulators as a labelled secondary result. That was wrong, and the position is left
   empty on purpose rather than by oversight. An emulator runs on the host's cores, and
   the damage is not only to the magnitude: `binarize` is memory-bandwidth bound and
   `decode` is branch-heavy, so a host with several times the memory bandwidth distorts
   the ratio *between* stages — and which stage dominates is the entire reason §10.3
   breaks the pipeline up. A number whose shape is wrong is not "shape, not magnitude";
   it is just wrong. Host CPU scaling and whatever else is running on the machine finish
   the job.

   What is worth running there is the correctness gate. The golden frame hashes are the
   comparability key for every L2 result, and they were generated and verified on one
   device's ART. If a different ART produced different bytes, the goldens would be
   device-dependent and the whole comparability premise would fail. That is unlikely —
   `xorshift64*` is `long` arithmetic the JLS pins down, the writer is pure Java and
   SHA-256 is standard — so this is cheap insurance rather than a necessity. It costs
   seconds:

   ```sh
   ANDROID_SERIAL=emulator-5554 ./gradlew :benchmark-android:connectedReleaseAndroidTest \
       -Pl2.emulator=true --tests '*FrameIntegrityTest'
   ```

Nothing in Phase 5 may claim a speedup until step 1–3 exist.

---

## 11. L3 — app and camera, on a real device

Not built. Macrobenchmark, physical device only, because it measures the camera.

Targets: cold-start to preview-visible, preview-visible to camera-ready, and
camera-ready to first successful scan against a printed barcode in a fixed scene (fixed
distance, fixed lighting, documented in the result file with a photograph).

The scene is the hard part and the reason L3 is behind L2: an unscripted scene makes the
operator's hand the dominant variable. Until the scene is fixed and documented, L3 numbers
are not admissible under §2.

---

## 12. L4 — sustained scanning

Not built. A soak run: continuous scanning for a fixed duration on a physical device,
recording CPU time, RSS, GC count and pause time, allocation rate, battery drain and
thermal status over time.

Its purpose is to catch what a per-frame median hides — a leak, an allocation treadmill, or
a win that evaporates once the device throttles. A native decoder that is faster cold and
slower hot has not improved the product, and only L4 can say so.

The existing 100-cycle open/close stability loop
(`docs/maint/reports/stability-100-b0-DN-ENG41.log`) is a crash check, not an L4 run.

---

## 13. The comparison tool

Two pieces. `benchmark/tools/l2_report.py` **is built**; `benchmark/tools/compare.py` is not.

### 13.1 `l2_report.py` — Microbenchmark output into the §8 envelope

Microbenchmark's own JSON is a fine record of a run and a poor basis for a comparison: it
carries per-repeat samples but no interval, and it knows nothing about which frames were
measured. The converter computes the median, p90 and the bootstrap interval of §7 from
those samples, attaches the committed frame hashes and the device, and records every
condition §10.6 turns into `inconclusive` — unlocked clocks, no sustained performance
mode, an emulator fingerprint, thermal throttling, fewer than 50 repeats — rather than
suppressing them. It invents nothing: a field it cannot fill from the input is `null`.

```sh
python3 -I benchmark/tools/l2_report.py \
    --input <path>/…-benchmarkData.json \
    --goldens benchmark/android/goldens/frames.golden.json \
    --run-id b0-DN-ENG41-20261030 --commit 50e6e3860 --device-label DN-ENG41 \
    --out docs/maint/reports/l2-b0-DN-ENG41-default.json
```

Its tests are `benchmark/tools/test_l2_report.py` (`python3 -I` to run).

### 13.2 `compare.py` — the verdict

**Built**, with `benchmark/tools/test_compare.py` covering every gate of §10.6, including
each reason a comparison must come back `inconclusive`.

```sh
python3 -I benchmark/tools/compare.py \
    --baseline docs/maint/reports/l2-<commit>-DN-ENG41-default.json \
    --candidate build/l2-candidate.json \
    --out-dir build/benchmark/compare
```

It writes `verdict.json`, `cases.csv` and `report.md`, and exits **0** `pass`, **1**
`fail`, **2** `inconclusive`, **3** the two result sets could not be compared at all —
different frames, device, API level or configuration, each named in the output.

CI has no step for it yet; one is added when there is a baseline to compare against, so
that a pull request touching the decode path carries a verdict instead of an opinion.

#### Warnings that block, and the one switch

§10.6 makes a run inconclusive when it carries a warning that invalidates timing.

**`UNLOCKED_CLOCKS` no longer applies to DN-ENG41.** It is a `userdebug` build and `adb
root` works, so the benchmark plugin's `lockClocks` task pins the cores — big cores to
1344 of 2400 MHz, little cores to 1190 of 1900 MHz — and Microbenchmark reports
`cpuLocked: true`. This is what made the measured floor in §10.6 possible: the same
`decode[HD_HIT_QR]` case read 7.18 ms unlocked and 12.98 ms locked, and locking buys
reproducibility rather than speed. Both sides of a comparison pay the same price.
`unlockClocks` restores the device by rebooting it.

**`THERMAL_THROTTLE` and `THROTTLE_WAIT_DISABLED` do apply, and §10.6 now reads through
them** under the clause written there — both sides must carry the same ones, on a device
whose floor was measured in that state. The measurement that settled it follows.
DN-ENG41's skin throttling threshold is 40.0 °C, and throughout the benchmarking
session the skin sensors read around 46 °C and never came down. So either the harness
waits — 270 s before every benchmark, 67 of the first baseline's 72 minutes, and it never
cools — or the wait is switched off and the result says so. Either way a warning is
carried, and every comparison against these baselines is `inconclusive` by default.

**Measured, after two wrong guesses.** This section first asserted the device is at
SEVERE at rest and never leaves it, inferred from a sensor reading that looked pinned.
It was then corrected to say that reading was a runtime anomaly which a reboot cleared.
Both were guesses; here is the measurement.

Rebooted, idle, on AC, one sample a minute for 45 minutes
(`reports/thermal-cooldown-DN-ENG41-20261031.log`): `Thermal Status: 3` for all 45
samples. One skin sensor reported 47.207 every single time — so the pinning is real and
reproducible, and it re-pins at a different value after a reboot. The other decayed
normally (42.383 → 41.511 → 40.988 → 40.813 → 40.348 → 40.232 → 40.116 → 40.0) and then
sat on 40.0, the threshold itself, and stopped. Incidentally that explains the earlier
40.116: it is a point on this cooling curve, so the pinned sensor had frozen at a real
value rather than reporting a configured constant.

**So passive cooling over adb does not clear the threshold.** The device unplugged and
physically cooled is the one route left that might, and it needs hands.

**That is what §10.6's read-through clause responds to**, and it is an amendment to the
rule written down, not a switch flipped. The thermal state is a standing condition of
this device; the effect floor was measured inside it; both sides of a comparison pay it
equally. `NO_SUSTAINED_PERFORMANCE_MODE` joins it for the same reason — the hardware has
no such mode to enable, and locking the clocks is the stronger thing done in its place.

The tool now separates three kinds of warning rather than quietly softening the rule:

- **Invalidating** — `DEBUGGABLE`, `EMULATOR`. These cannot be waived at all.
- **Standing** — `THERMAL_THROTTLE`, `THROTTLE_WAIT_DISABLED`,
  `NO_SUSTAINED_PERFORMANCE_MODE`. Read through under §10.6 when both sides carry the
  same set and the device is on `READ_THROUGH_DEVICES`, which today is DN-ENG41 alone.
  Listed in `verdict.json` as `read_through_warnings` and named at the top of the report.
- **Noise** — `UNLOCKED_CLOCKS`, and any standing warning outside the clause above.
  Blocking by default. `--accept-noise-warnings` switches that off deliberately, and the
  waiver is recorded in `verdict.json` and printed at the top of the Markdown report, so a
  number read under a suspended rule says so wherever it is quoted.

Of the three ways out listed when this section was written, all three are now resolved:
the clocks are locked, the effect floor is measured rather than guessed (§10.6), and the
thermal state is handled by the read-through clause rather than by a waiver. **Raising the
vendor thermal thresholds was never on that list** — §6 rules it out, because it would
produce numbers the shipped product never delivers and nothing in the result file would
show it.

With the floor measured at 3% for the cases that matter, L2 on this hardware resolves
rather more than "large changes only". What remains unavailable is a baseline from a cool
device; the numbers here belong to a warm one, and §6's rule that absolute numbers belong
to their operating point is why that is written down rather than averaged away.

- Reads two result files of the same layer.
- Refuses mismatched `dataset_hash`, frame hashes, device, API level or configuration, with
  the mismatching field named.
- Emits a grouped CSV, a JSON verdict file and a Markdown report: per case, baseline and
  candidate median, p90, 95% interval, delta, allocation delta, and a verdict.
- Exit code: 0 `pass`, 1 `fail`, 2 `inconclusive`. A tool that cannot distinguish "no
  regression" from "could not tell" is worse than no tool.
- Its tests cover each gate in §10.6, including every `inconclusive` reason, with
  synthetic result files.

CI has no step for it yet; one is added when it exists, so that pull requests touching the
decode path carry a verdict instead of an opinion.

---

## 14. What is not measured today

Stated plainly, because the absence is the current state of the evidence:

- **No Android timing of any kind exists.** No decode time, no frame-path time, no
  camera-ready latency, no first-scan latency, no CPU, memory, GC, allocation or power
  figure.
- L1 is host-only and diagnostic.
- One physical device, Android 14, no GMS. No low-end device, no non-Qualcomm device, no
  physical Android 15 or 16 device.
- The two dataset defects in §5 are unresolved.

Until §10.7 exists, the honest answer to "is this fork faster?" is: **unknown, and nobody
has measured it.**
