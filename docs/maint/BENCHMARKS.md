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
| Reference device | **DN-ENG41**, Android 14 (API 34), arm64, no GMS | the only physical device available |
| Emulator, Android 15 | AVD `zxing_api35`, Pixel 7, arm64 | API 35 |
| Emulator, Android 16 | AVD `zxing_api36`, Pixel 7, arm64 | API 36 |
| Host | macOS 27.2 arm64, JDK 25 (JBR), Gradle toolchain 17 | L1 only |

Rules:

- **L2 and above must name a device.** An emulator result is labelled as one and never
  compared against a physical-device result.
- **Emulator timings are shape, not magnitude.** They run on the host CPU with no thermal
  model; use them to catch a 10× regression, never to claim a 10% win.
- Before a device run: screen on, charger behaviour recorded, airplane mode on, no other
  foreground app, and `adb shell cmd thermalservice override-status 0` is *not* used — a
  masked thermal state produces numbers the product will never see.
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
  "dataset_hash": "e8d584f3…",
  "device": { "model": "DN-ENG41", "api": 34, "abi": "arm64-v8a", "emulator": false },
  "warnings": ["…microbenchmark warnings, verbatim…"],
  "cases": [ { "name": "…", "stage": "…", "ns_p50": 0, "ns_p90": 0,
               "ns_ci95_low": 0, "ns_ci95_high": 0, "iterations": 0,
               "alloc_bytes": 0, "gc_count": 0 } ]
}
```

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

`:benchmark-android` at `benchmark/android`, added to `settings.gradle.kts`:

- `androidx.benchmark:benchmark-junit4`, the `androidx.benchmark` Gradle plugin, and the
  module built as an instrumentation test target against `:scanner` and `:core`.
- `minSdk` 21, matching the library.
- Release-with-minification build type, as Microbenchmark requires for trustworthy numbers;
  a debuggable run must fail the task, not warn.
- Fixed frames ship as module assets, generated from the dataset manifest, so a run needs
  no camera, no barcode and no steady hand.

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

Frames are generated deterministically from manifest samples: the source image is scaled
into the target resolution at a fixed position, the rest filled with a fixed noise pattern
from a seeded PRNG. Generation is a committed script with its own test, and the generated
frames' SHA-256 values are recorded in the result file. **A result set whose frame hashes
differ from the baseline's is not comparable.**

Stride is set to width for the Camera1 path. When Phase 5 feeds `ImageProxy`
`YUV_420_888` in directly, a `stride != width` variant is added here and baselined before
that path is enabled by default.

### 10.5 Configurations

Run and reported separately, never pooled:

1. `default` — every format the reader supports, no `TRY_HARDER`; what the app ships.
2. `qr_only` — `POSSIBLE_FORMATS = [QR_CODE]`; the common library-consumer case.
3. `oned_only_upca` — a single specified 1D format, the cheapest realistic hint set.

3 resolutions × 3 variants × 3 configurations × 5 stages is the full matrix. CI runs
`hd` + `default` only; the full matrix is a manual device run.

### 10.6 Gates — pass, fail, inconclusive

A comparison of a candidate against a baseline. Both sides must share `dataset_hash`,
frame hashes, device, API level and configuration, or the verdict is `inconclusive`
immediately and the tool says which field differed.

Per case, on the median, using the 95% intervals from §7:

| Verdict | Condition |
| --- | --- |
| **pass** (improvement) | candidate CI upper bound < baseline CI lower bound **and** the median improves by ≥ 5% |
| **fail** (regression) | candidate CI lower bound > baseline CI upper bound **and** the median worsens by ≥ 5% |
| **inconclusive** | anything else |

"Anything else" explicitly includes all of these, each reported with its reason:

- the intervals overlap;
- the medians differ by less than 5% — below this fork's noise floor on the reference
  device, so a difference is not resolvable;
- either side has fewer than 50 measured iterations;
- either side carries a Microbenchmark warning that invalidates timing (debuggable build,
  unlocked clocks, thermal throttling detected);
- either side is an emulator run and the other is not;
- `alloc_bytes` grew by more than 5% while time improved — a time win bought with
  allocations is not a win until L4 says the GC cost is absorbed.

A run as a whole is `fail` if any case fails, `pass` only if at least one case passes and
none fails, otherwise `inconclusive`.

The 5% floor is a placeholder until the first B0 run measures the reference device's
run-to-run spread. **It is replaced by the measured spread, in the same commit that lands
the first baseline**, and the measurement is recorded here.

### 10.7 The B0 baseline

Before any Phase 5 work:

1. Run the full matrix on **DN-ENG41** at B0 (`50e6e3860`).
2. Commit the result sets under `docs/maint/reports/` as `l2-b0-DN-ENG41-<config>.json`.
3. Record here the run date, the device's thermal state and the measured run-to-run spread
   that replaces the 5% floor.
4. Repeat the `hd`/`default` slice on both emulators, labelled as emulator results.

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

`benchmark/tools/compare.py`, with its own unit tests. Not built.

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
