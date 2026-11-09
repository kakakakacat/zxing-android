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
| **L3** | How long from launch to a camera that is ready, and from there to a frame and to a scan? | Macrobenchmark + trace spans from `ScannerTrace` | physical device, API 29+ | **built**, §11. Every target but the scan is measured; the scan still needs a scene |
| **L4** | What does sustained scanning cost in CPU, memory, GC and heat? | instrumented soak run | physical device | **built**, §12 |

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

| Status | Count | Was |
| --- | --- | --- |
| CORRECT | 862 | 853 |
| INCORRECT | 4 | 13 |
| NOT_FOUND | 254 | 254 |
| CORRECT_NO_RESULT | 46 | 46 |
| FALSE_POSITIVE | 2 | 2 |

`NOT_FOUND` is dominated by upstream's deliberately hard images, which upstream's own
blackbox tests also allow to fail; the number is a tripwire against *change*, not a target.

**The "Was" column is a harness fix, not a decoder change.** `classify()` stripped a
trailing newline from the *expectation* and compared it against the *raw* decoded text.
Nine QR samples — `qrcode-3/{18,19,20,24,25}` and `qrcode-5/{16,17,18,19}` — carry a
payload that genuinely ends in a newline, so one-sided normalisation guaranteed a
mismatch. Upstream's `AbstractBlackBoxTestCase` compares raw; so does this now, and the
nine decode byte-for-byte identically to their sidecars. No decoder behaviour changed.

**All four remaining `INCORRECT` are understood, and none is a dataset fidelity gap.**
Every sidecar involved is plain ASCII, so the charset difference this document once
suspected is not what is happening:

| Sample | Expected | Decoded | What it is |
| --- | --- | --- | --- |
| `code39-2/1` | `Extended !?*#` | `E+X+T+E+N+D+E+D /A%J/J/C` | Code 39 **extended mode** |
| `code39-2/2` | `12ab` | `12+A+B` | the same |
| `ean13-4/01` | EAN-13 `9780441014989` | UPC-E `17804419` | misread as another symbology |
| `rss14-2/11` | RSS-14 `02001234567893` | UPC-E `02244358` | the same |

The first two are **a real property of any `MultiFormatReader` consumer, this app
included**. Upstream decodes that directory with `new Code39Reader(false, true)`, which
turns on extended mode; `MultiFormatReader` has no way to ask for it, so it returns the
raw Code 39 symbols — measured here as `E+X+T+E+N+D+E+D /A%J/J/C`. `DecoderThread`
constructs the same `MultiFormatReader`, so the app has no extended mode either; that
follows from the reader it uses rather than from a run of the app on this image. Worth
knowing about the product, and not a bug in the harness.

The last two are **genuine misreads that only an all-formats reader can make**. Upstream
restricts `POSSIBLE_FORMATS` to the family under test, so its blackbox run never offers
the decoder the chance to answer UPC-E. L1 deliberately does not restrict formats, because
that is what the app does by default — which is exactly why these two show up here and
nowhere else. They are the strongest argument in the corpus for Phase 5 item 9, a per-scan
format restriction.

**The two `FALSE_POSITIVE` samples are within upstream's own tolerance.**
`falsepositives-2/10` reads as EAN-8 and `/13` as UPC-E. Upstream's
`FalsePositives2BlackBoxTestCase` calls `addTest(4, …)` at each of four rotations, so it
allows **four** false positives per rotation on that directory and we have two. Not a
regression in this fork; that corpus contains images that look enough like barcodes to
fool a reader, on purpose.

**L1 timings are host-JVM diagnostics. They are not Android results.**

---

## 9.1 L1 on a device — the accuracy question the native default was missing

**Built**, and run. `benchmark/tools/l1_device.sh --backend java|native` pushes the corpus
to the device, runs `L1CorpusRun` through the `FrameDecoder` seam and pulls the result
file. Scoring is `zxingbench.corpus.L1Classifier`, the same code that scores the host run.

**It records no timing, deliberately.** It has no clock or thermal protocol, so under §2 it
could not produce an admissible one; the way to keep a number from being quoted is not to
produce it. Speed is L2's question (§10.6c).

**The harness is checked before it is believed.** Every sample's bytes are hashed against
the manifest on the device before it is decoded — a corpus that arrived truncated would
otherwise score as a decoder that misses a lot, which is the one failure mode that looks
exactly like the finding being looked for.

### The host and the device are not interchangeable, and that is the image decoder

Run through the *same* Java decoder, the device and the host return the same five totals —
862 / 4 / 254 / 46 / 2 — but disagree about **six individual samples** (`ean13-2/09`,
`ean13-4/{09,10,14,15}`, `pdf417-3/08`). The file hashes are identical, so it is not the
corpus.

`--luma-hash` on both runners records a hash of the plane handed to the decoder, and
settles it: **231 of 1168 samples arrive at the decoder as different pixels**, all six
disagreements among them. Two causes, both verified by reconstructing the plane from the
PNG by hand and matching the recorded hashes:

- **8-bit greyscale PNGs (38 samples).** `ImageIO` decodes them into a `TYPE_BYTE_GRAY`
  image whose colour space is *linear* grey, and `getRGB()` encodes that to sRGB on the
  way out — the host sees gamma-brightened pixels. Android's `BitmapFactory` returns the
  stored bytes. For `codabar-1/01` the raw plane hashes to `4c4ae0f0…`, the sRGB-encoded
  one to `53fba960…`; the device recorded the first, the host the second.
- **PNGs with transparency (193 samples).** `ImageIO` ignores alpha; Android premultiplies
  into the bitmap and un-premultiplies on read-back, which is not exact below α=255. On
  `aztec-2/01` the host's plane matches "palette RGB, alpha ignored" byte for byte, and the
  device's does not.

The device is seeing what an Android app would see. **Any native-versus-Java comparison has
to be device against device**, which is what the numbers below are; and upstream's own
blackbox suite, which also reads through `ImageIO`, is scoring those 38 greyscale images on
pixels no phone would produce.

### zxing-cpp against zxing-java, 1168 samples, DN-ENG41

All formats enabled, one run per backend per configuration. Correctness is deterministic,
so one run is a result rather than a sample.

**Two bugs had to come out first, and both were on this side of the seam.** The run is
what found them; they are kept here because each one looked exactly like a property of
zxing-cpp until it was traced.

1. **The text a caller receives.** As `ebe1708b4` shipped it
   (`reports/l1-4cdff45b7-DN-ENG41-native.json`) the native backend returned 762 correct
   with **61 INCORRECT** — and not one of the 61 was a misread. The right symbol came
   back rendered by zxing-cpp's conventions: UPC-E expanded to its UPC-A equivalent (37),
   Codabar with its start and stop characters (11), non-graphical characters escaped by
   `TextMode::HRI` (11), GS1 application identifiers without their parentheses (2). Fixed
   in `2ecd06428`.
2. **A format name is not symmetric.** What you ask zxing-cpp for is a symbology; what it
   returns is the variant the symbol turned out to be — its header says "only specific
   variants are returned by the library when reading barcodes". The reverse table was
   built by inverting the request table, so `"DataBar Omni"`, `"DataBar Stacked"`,
   `"DataBar Expanded Stacked"`, `"Compact PDF417"` and `"Code 39 Extended"` matched
   nothing, and an unmapped name is reported as *nothing found*. **That cost 90 reads**:
   all 30 RSS-14 samples and 59 of the 71 RSS-Expanded misses, every one of the latter in
   `rssexpandedstacked-*`. An earlier revision of this section reported it as a DataBar
   capability gap in zxing-cpp. It was ours. Fixed in `49bd3beb3`, which also made an
   unmapped name log once instead of vanishing.

**Where it stands after both** (`reports/l1-49bd3beb3-DN-ENG41-*`):

| Status | zxing-java | zxing-cpp | zxing-java +TRY_HARDER | zxing-cpp +TRY_HARDER |
| --- | --- | --- | --- | --- |
| CORRECT | 862 | **890** | 921 | **960** |
| INCORRECT | 4 | **3** | 15 | **9** |
| NOT_FOUND | 254 | **227** | 184 | **151** |
| CORRECT_NO_RESULT | 46 | 48 | 45 | 48 |
| FALSE_POSITIVE | 2 | **0** | 3 | **0** |

**And a defect this table cannot see, which is the point of writing it down.** The 23
CODE_39 samples contain no symbol whose *content* parses as Code 32 or PZN — variants of
Code 39 in zxing-cpp, reported ahead of it, and nameable by neither this fork nor this
corpus. Every such symbol was decoded by the native backend and then thrown away by the
name table, so an ordinary Code 39 barcode was unreadable by the default decoder while the
Java one read it, and about 9.3% of six-character Code 39 contents over Code 32's alphabet
are in that class. Fixed 2026-11-09; re-running L1 against this baseline afterwards gives
`verdict: same`, **0 regressions and 0 improvements**, which is exactly the problem — a
1168-sample corpus is a strong instrument only for the classes it contains.
`reports/native-nameable-formats-DN-ENG41-20261109.md`.

28 samples regress and 58 improve. Right answers per format, where "right" is CORRECT
plus CORRECT\_NO\_RESULT:

| Format | n | java | cpp | java +TH | cpp +TH |
| --- | --- | --- | --- | --- | --- |
| AZTEC | 39 | 22 | **39** | 22 | **39** |
| DATA_MATRIX | 59 | 39 | **57** | 39 | **57** |
| QR_CODE | 179 | 158 | **165** | 158 | **165** |
| RSS_14 | 30 | 10 | **14** | 14 | **16** |
| CODE_39 | 23 | 21 | **22** | 21 | **23** |
| EAN_13 | 157 | 100 | 98 | 116 | 117 |
| UPC_A | 167 | 77 | 77 | 97 | 98 |
| UPC_E | 55 | **40** | 37 | 46 | 45 |
| CODE_128 | 49 | **45** | 44 | 48 | 48 |
| CODABAR / CODE_93 / EAN_8 / ITF / MAXICODE | 59 | 59 | 59 | 59 | 59 |
| RSS_EXPANDED | 247 | **235** | 228 | **245** | 243 |
| PDF_417 | 56 | **56** | 50 | **56** | 50 |
| negatives | 48 | 46 | **48** | 45 | **48** |
| **total** | **1168** | 908 | **938** | 966 | **1008** |

zxing-cpp is ahead by 30 samples at the matched effort level and by 42 with TRY_HARDER,
it is wrong about fewer of the ones it does read, and it is the only one of the two that
never reads a negative sample. Its wins are 2D — +17 Aztec, +18 Data Matrix, +7 QR. Its
losses are small and spread: **PDF417 50 of 56** is the largest single one, and those six
are ordinary misses spread over all three directories rather than an unmapped variant —
the warning log is silent for the whole run.

### The effort level, checked both ways

Both backends are pinned to the Java reader's effort level (§10.6b), and zxing-cpp
defaults its four effort flags to true, so it is worth knowing what that pinning costs.

- **TRY_HARDER on both backends alike**, now that the hint is mapped (`4d4bdf232`):
  java 908 → 966 right, cpp 938 → 1008. It moves both and changes no conclusion.
- **zxing-cpp at its own full defaults**, all four flags on, Java side unchanged:
  **892 correct**, two more than the matched run, and a false positive it does not
  otherwise produce (`reports/l1-49bd3beb3-DN-ENG41-native-stock-options.json`, taken
  with the four lines flipped locally). Matching the effort level costs it almost nothing
  in detection here, which is what §10.6b assumed and had not checked on a corpus.

### What this says about the default

**Keep it.** zxing-cpp is 56–82% faster on 2D and on all-format misses (§10.6c) *and*
reads 30 more of the 1168 samples with fewer wrong answers and no false positives. The
case for reverting rested on two defects that are now fixed, and nothing in the corpus
argues for it.

Two things a caller should still know, both in the changelog: the viewfinder's animated
candidate points are gone, and PDF417-heavy work reads six fewer of these 56 samples —
`ScannerOptions.setDecoderBackend(JAVA)` is there for either.

**What this does not say.** One corpus, one device, all formats enabled. It says nothing
about speed, and a format with no sample here — DataBar Limited, Micro QR, rMQR — is not
a format this fork can report at all, whichever backend reads it.

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

### 10.6b Comparing two decoder backends

Phase 5 item 10 asks whether the native decoder beats the Java one. That is a comparison
like any other and clears the same gates, with one difference worth stating: **the
backend is the independent variable, so it is the one field that may differ.**

- `DecoderBackendBenchmark` measures a single stage, `endToEnd`, over the same nine
  frames and three hint sets. It has to be the whole decoder: a native backend brings its
  own thresholding, so there is no shared `binarize` to hold constant, and measuring
  anything narrower would compare two halves of one decoder rather than two decoders.
  Crop and luminance source are identical on both sides and together cost under a
  microsecond.
- **The case names carry no backend**, so two runs differing only by backend compare case
  for case. The backend is recorded in the result envelope as `backend` and named at the
  top of every comparison report. It is deliberately **not** in `GUARDED`: refusing to
  compare across it would refuse the comparison this exists for.
- **One run per backend, in its own process.** Running both in one process lets the first
  backend's allocations and cache state colour the second's numbers.

```sh
./gradlew :benchmark-android:connectedReleaseAndroidTest -Pl2.matrix=full \
    -Pl2.backend=java \
    -Pandroid.testInstrumentationRunnerArguments.class=io.github.kakakakacat.zxing.benchmark.DecoderBackendBenchmark
# then the same with -Pl2.backend=native, then compare.py
```

**The two backends are checked for agreement before either is timed.** Every case
verifies that the backend under test returns the same text and format as the Java reader
on that exact frame, and fails the run if it does not. Two decoders that disagree about
what a frame says are not two speeds of the same thing, and a speed comparison between
them would be meaningless.

**Hints other than the format selection are not forwarded to the native backend.** It has
its own options and no mapping from this fork's `DecodeHintType` exists. For `default`,
`qr_only` and `oned_only_upca` this changes nothing, because none of them sets a hint
beyond `POSSIBLE_FORMATS` — but a configuration that did would not be configured alike on
both sides, and the comparison would have to say so.

### 10.6c The first backend A/B, measured

Phase 5 item 10, on DN-ENG41 at commit `602d368c4`, clocks locked, full 27-case matrix
per backend, one run each in its own process. Files:
`reports/l2-ab-602d368c4-DN-ENG41-{java,native}.json`, verdict and per-case table in
`reports/l2-ab-602d368c4-DN-ENG41-verdict.md`.

**Overall verdict: `fail`** — 19 cases pass, 8 fail. A run fails if any case fails
(§10.6), and that is the right headline, because the failures are not noise: they are a
single explainable class.

**zxing-cpp wins everything 2D, and wins the path live preview actually spends its time
on:**

| Case | Java | zxing-cpp | |
| --- | --- | --- | --- |
| `FHD_MISS_DEFAULT` | 81.330 ms | **18.677 ms** | −77.0% |
| `HD_MISS_DEFAULT` | 48.427 ms | **10.458 ms** | −78.4% |
| `VGA_MISS_DEFAULT` | 21.731 ms | **4.139 ms** | −81.0% |
| `HD_HIT_QR_DEFAULT` | 18.043 ms | **4.153 ms** | −77.0% |

A frame that decodes nothing under all formats is what a scanner does for almost every
frame of a session. 17 of the 19 passes are between −54% and −82%.

**The Java decoder wins 1D hits, by a lot, and it is architecture rather than
measurement error:**

| Case | Java | zxing-cpp | |
| --- | --- | --- | --- |
| `FHD_HIT_1D_DEFAULT` | **0.091 ms** | 1.811 ms | +1889% |
| `HD_HIT_1D_DEFAULT` | **0.077 ms** | 0.836 ms | +988% |

In absolute terms the gap is **1.7 ms, once**, on the single frame of a session that
succeeds; the miss path it loses is tens of milliseconds on every frame before that.

**The explanation this section carried was wrong, and it is worth leaving the correction
visible.** It said `HybridBinarizer` is lazy on the Java side — true — while zxing-cpp
binarizes the whole image before scanning. It does not. zxing-cpp's
`HybridBinarizer::getPatternRow` delegates to `GlobalHistogramBinarizer`, so its 1D path
thresholds one row at a time, the same shape as `getBlackRow()`. The gap was two fixed
per-frame costs on this side of the seam: a full copy of the ROI before the JNI call, and
a `MultiFormatReader` — with every format reader the options ask for — constructed inside
`ZXing::ReadBarcode` on every frame. Both were removed on 2026-11-09
(`reports/l2-native-array-roi-DN-ENG41-20261109.md`,
`reports/l2-native-cached-reader-DN-ENG41-20261109.md`), and the HD figure above went
0.836 ms → 0.114 ms → 0.096 ms. Paired against Java in one session it is now +21.9% at
HD, +44.5% at VGA and unresolvable at FHD.

**The tell was in the table all along.** The native 1D hit barely changes between VGA and
FHD — six times the pixels for about 8% more time — which no per-pixel explanation
survives. If a cost does not scale with the frame, stop looking at the pixels.

#### Holding the effort level constant, which the first attempt did not

zxing-cpp defaults `tryHarder`, `tryRotate`, `tryInvert` and `tryDownscale` to **true**:
rotations at 90/180/270, inverted codes, downscaled copies. This fork's Java reader does
none of it. The first run left those defaults alone and so compared one decoder against
another doing several times the work. Matching them is not tuning; it is what confines the
comparison to the decoders.

What that mistake was worth, same frames, stock options versus matched
(`reports/l2-ab-94d3e4732-DN-ENG41-native-stock-options.json`):

| Case | stock | matched | |
| --- | --- | --- | --- |
| `FHD_HIT_1D_DEFAULT` | 40.689 ms | 1.813 ms | 22.4× |
| `FHD_MISS_DEFAULT` | 251.864 ms | 18.683 ms | 13.5× |
| median over 27 cases | | | 1.85× |

Turning them off cost no detection on these frames: all 27 cases still agree with the Java
decoder. Both result sets are kept, because "each decoder out of the box" is a real
question too — it is just not the question item 10 asks.

#### What this does not settle

- **One device, one thermal state, one operating point.** Every §6 caveat applies.
- **Nothing about accuracy.** These frames are clean and generated. A decoder that is
  faster on a clean frame may be worse on a blurred or angled one, and L2 has no such
  frames. The corpus that does is L1, which is host-JVM only.
- **zxing-cpp is not a drop-in**, for a reason that has nothing to do with speed: it
  renders UPC-A as 13 digits and reports it as `EAN_13` unless EAN-13 is excluded
  (§10.6b). Any host switching backends would see its UPC-A text change.

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

**Built.** `benchmark/l3` (Macrobenchmark), driven by `tools/maint/l3-run.sh`, converted by
`benchmark/tools/l3_report.py`. Physical device only, because it measures a camera; the
script refuses an emulator and so does the converter.

### 11.1 The targets, and which of them need a scene

The original specification listed three targets and ruled all three inadmissible until the
scene was fixed. **That was one rule too many, and this section now separates them**, because
only one of the three depends on the scene at all:

| Target | Case name | Needs a scene? |
| --- | --- | --- |
| Cold launch → the window's first frame | `startup_to_initial_display` | no |
| Scanner asks for a camera → backend reports ready | `camera_open_to_ready` | no |
| Camera ready → first frame at the decoder | `ready_to_first_frame` | no |
| Scanner asks for a camera → first frame at the decoder | `open_to_first_frame` | no |
| Camera ready → first **successful scan** | `ready_to_first_scan` | **yes** |

A launch, a camera opening and a frame arriving happen whatever the lens is pointed at. A
*decode* does not. So the first four are admissible under §2 on the usual conditions, and
`ready_to_first_scan` stays inadmissible until the scene is fixed, photographed and recorded
with the result — which no run has done yet. The converter emits that case as `null` with a
`NO_SCENE` warning rather than as a zero, per §8: "nothing was in front of the lens" and
"the scan took no time" are different claims.

The scene is still the hard part, and it is still the reason the scan target is last.

### 11.2 What the spans mean, and which compare across backends

Three of these are intervals only the library can see, so `ScannerView` emits them into the
system trace through `ScannerTrace` and the harness reads them back. They are
**asynchronous** trace sections, which need **API 29**; `l3-run.sh` refuses an older device
rather than produce a result with the camera spans silently missing.

`startup_to_initial_display` is Macrobenchmark's own `timeToInitialDisplayMs`. §11 used to
call this "preview visible" and that name flatters it: the window's first frame holds the
viewfinder and an empty `SurfaceView`, not camera pixels. Camera pixels are
`ready_to_first_frame`, and on the reference device the two are hundreds of milliseconds
apart.

**"Ready" does not mean the same thing on both backends** and a comparison must not pretend
it does: Camera1 reports ready once `startPreview` has returned, CameraX once its use cases
are bound. Those are different moments in a camera's life. What *is* identical on both is
"the scanner asked for a camera" and "a frame reached the decoder", so `open_to_first_frame`
— derived per iteration from the two spans, which are sequential by construction — is the
camera interval to compare. `camera_open_to_ready` and `ready_to_first_frame` are reported
because they localise a difference, not because they compare cleanly.

Summing happens **per iteration and then aggregates**, never by adding two medians, which
would be a statistic of nothing. There is a test for exactly that.

### 11.3 The backend a run measured is read out of the trace, not trusted

The camera backend is chosen by the app's own `preferences_camerax` setting, written before
the run by `l3-run.sh`. It has to be written from the host: the Macrobenchmark target is the
`benchmark` build type, which is deliberately **not debuggable**, so neither `run-as` nor the
instrumentation's own shell can reach the app's `shared_prefs`.

A preference that does not take looks exactly like two backends performing identically. So
`ScannerView` also emits `zxing.backend.camera1` or `zxing.backend.camerax`, the benchmark
collects it, and **`l3_report.py` refuses — not warns about — a result whose trace disagrees
with the backend the run claims.** This is not hypothetical: it fired on the first run that
worked, twice, for two different reasons (AGP's install wiping the app's data, and a
`chown` that silently did nothing because `dumpsys package` prints no `userId=` line on the
reference device).

### 11.4 Running it

```
tools/maint/l3-run.sh <camera1|camerax> [iterations] [serial]
python3 -I benchmark/tools/l3_report.py \
    --input benchmark/l3/build/outputs/l3/zxing-l3/*-benchmarkData.json \
    --runner-args benchmark/l3/build/outputs/l3/l3-runner-args.json \
    --commit <sha> --run-id <backend>-<device>-<date> --device-label <device> \
    --out docs/maint/reports/l3-<backend>-<device>-<date>.json
```

The script needs root adb, to write that preference. It installs both APKs itself and then
runs the benchmark with `am instrument` rather than through Gradle, because **AGP's install
step uninstalls first and takes the app's preferences with it** — anything written before
Gradle runs is gone by the time the benchmark starts.

Two failure modes it checks for, because both were observed: `am instrument` reports a
failed test and still exits 0, and AGP's own runner has been seen letting a failed APK
install through as `BUILD SUCCESSFUL`. A run that produced no `benchmarkData.json` did not
happen, whatever any exit code says, and the script says so.

**Same-session rule, as everywhere else here**: compare two backends only when they were
measured back to back on the same device. The committed baselines obey it.

### 11.5 The baseline, and what it cannot answer

`docs/maint/reports/l3-{camera1,camerax}-DN-ENG41-20261102-run{1,2}.json`, with the reading
in `l3-verdict-DN-ENG41-20261102.md`. Two pairs, 12 cold launches each, clocks locked.

Two findings reproduced across both pairs: CameraX puts the window's first frame on screen
about **330 ms sooner**, and is about **200 ms slower** from asking for a camera to a frame
at the decoder. The first has a cause visible in the code — `Camera1Backend.open` calls
`openDriver` synchronously from `surfaceCreated`, on the main thread, for a measured 400 ms.

**Launch → first frame at the decoder is not derivable from these spans.** `camera_open_to_ready`
begins at `surfaceCreated`, which on the Camera1 path is *before* the window's first frame,
so the startup metric and the camera spans overlap by an unknown amount and must not be
added. `open_to_first_frame` may be summed only because its two halves are sequential by
construction. Closing that gap means the app calling `reportFullyDrawn()` when the scanner
goes live, which needs `ScannerView` to tell its host that frames have started — a published
API decision, left open rather than taken quietly.

---

## 12. L4 — sustained scanning

**Built.** `tools/maint/l4-soak.sh` with its on-device sampler `tools/maint/l4-sample.sh`,
converted by `benchmark/tools/l4_report.py`. Physical device only; the script refuses an
emulator, which has neither a camera nor a thermal envelope.

Its purpose is to catch what a per-frame median hides — a leak, an allocation treadmill, or
a win that evaporates once the device throttles. A native decoder that is faster cold and
slower hot has not improved the product, and only L4 can say so.

The 100-cycle open/close stability loop
(`docs/maint/reports/stability-100-b0-DN-ENG41.log`) remains a crash check, not an L4 run.

### 12.1 What is sampled, and at which of two rates

Two rates, because the expensive sample would otherwise measure the sampler. Every interval
(5 s by default), from `/proc` alone: CPU jiffies, major faults, thread count and RSS. Every
sixth row: `dumpsys meminfo` for Java heap, native heap and total PSS, plus thermal status
and battery. The slow columns are therefore empty on most rows, and the converter counts
only the rows that have them — `n` differs per case on purpose.

The sampler runs **on the device**, not as a loop of `adb shell` calls, for the same reason
the camera spike streams logcat rather than polling it: a per-sample round trip is tens of
milliseconds of jitter on every row, and a stalled link becomes a gap that looks like the
app going quiet.

GC comes from ART's own logcat lines, counted with their pause and total times. **Zero
logged collections is reported as an absent log, not as no GC**, because those are
different claims and only one of them is observable from outside the process.

### 12.2 The three questions it exists to answer

- **Does it leak?** RSS is fitted against time by least squares, and the slope is reported
  in kB/minute **next to the residual spread**, so a slope drawn through noise is visible as
  one. The `RSS_GROWTH` warning fires only when the predicted rise exceeds twice that
  spread, and says in so many words that a soak this short cannot separate a leak from a
  cache filling.
- **Does it allocate on a treadmill?** Collections, bytes freed and pause time from the GC
  lines.
- **Does a win evaporate when the device heats up?** Thermal status is sampled throughout,
  and **every case is also split into first and second half**, so drift over the run is
  visible without anyone having to plot it.

### 12.3 The miss path is the default, and the result file says so

A scanner with nothing in front of it decodes nothing on every frame. That is not a
degenerate workload — §10 measured the all-formats miss as the *most expensive* case, and
it is what live preview actually does almost every frame. So a soak against an empty scene
is the right default, and the result records `scene: "miss"` with a `MISS_PATH` warning
rather than leaving a reader to assume otherwise. A hit-path soak needs the fixed scene
§11 still does not have.

### 12.4 Running it

```
tools/maint/l4-soak.sh <camera1|camerax> [minutes] [serial]
python3 -I benchmark/tools/l4_report.py \
    --samples benchmark/l4/out/samples-<backend>.tsv \
    --logcat benchmark/l4/out/logcat-<backend>.log \
    --backend <backend> --scene miss \
    --commit <sha> --run-id <backend>-<device>-<date> --device-label <device> \
    --device-api <api> --out docs/maint/reports/l4-<backend>-<device>-<date>.json
```

**The baseline**: `docs/maint/reports/l4-{camera1,camerax}-DN-ENG41-20261102.json`, read in
`l4-verdict-DN-ENG41-20261102.md`. Two 12-minute soaks, one session, miss path.

Its headline is the thing §10 could not reach and the Phase 6 record listed as unmeasured:
**CameraX costs about 60% more CPU to keep scanning** — 118% of one core against 74%, with
ranges that do not touch — for +17 MB PSS and +17 threads, at an identical Java heap and
slightly *fewer* GCs. Neither backend leaks over twelve minutes and neither degrades across
the run.

Root adb, for the same preference write L3 needs. The script fails if the app dies during
the soak or is gone at the end — a soak whose process died has flat CPU for the wrong
reason, and that is exactly the shape of a result that looks healthy and is not.

---

## 13. The comparison tool

Three pieces, all built: `benchmark/tools/l2_report.py` turns a run into a result file, `benchmark/tools/compare.py` turns two result files into a verdict, and `benchmark/tools/ci_compare.py` is what CI runs over the committed baselines.

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

CI runs it through `ci_compare.py`; §13.3 is how, and why CI is allowed to judge a
number it is not allowed to take.

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

### 13.3 `ci_compare.py` — the step CI is allowed to run

**CI cannot produce an L2 number.** A shared runner has no locked clocks, no known
thermal state and no device, so a timing taken there is not comparable to anything and §6
forbids keeping it. That is why no workflow runs a benchmark, and why `build.yml` only
*assembles* the L2 APK.

It can still do the other half, which is the half that gets skipped: reading a result
somebody did measure, against the right baseline, under the gates. `.github/workflows/l2-compare.yml`
runs `ci_compare.py` on every push and pull request.

**The flow.** An operator runs the benchmark on the reference device, converts it with
`l2_report.py`, and commits the result to `docs/maint/reports/candidates/`. CI picks the
baseline, runs §10.6, writes the verdict into the job summary and uploads
`verdict.json`, `cases.csv` and `report.md`. The candidate directory's README has the
commands and the rule for taking a candidate away again.

**Which baseline, and why that is a tool's job rather than a flag.** A candidate is
judged against the committed result that shares its **device label, `config` and decoder
backend**, out of `docs/maint/reports/l2-baselines.json`. Device and configuration are
obvious. The backend is in that key although compare.py deliberately does *not* guard it
(§10.6b): across backends compare.py answers "which decoder is faster", which is a real
question and not a regression check. Selecting on the backend is what keeps the two
apart — without it, the day the default moved from Java to zxing-cpp, every candidate
would have been judged against a baseline from the other decoder and the 56–82% win would
have been reported as a pass on a change that did not cause it.

**A null backend does not match everything.** The `default` and `full` baselines predate
both the `FrameDecoder` seam and the `backend` field, so their files record none. The
manifest may still say which decoder produced them — there was one decoder in the tree —
but it has to say so with `backend_asserted` and a `backend_note` giving the reason, which
`ci_compare.py --check` enforces and every report prints. An unexplained null would
quietly match a native candidate.

**The manifest is checked, not trusted.** Every entry must name a file that exists, is
layer L2, and whose device, config and backend are what the entry claims; keys must be
unique and must match their own fields; every entry needs a `note` saying why it is a
baseline. A manifest that selects the wrong file is the one failure mode a comparison tool
must not have, so it is a hard error (exit 4) and `test_ci_compare.py` checks the
committed manifest itself on every push.

**Exit codes and what CI does with them.**

| | | CI |
| --- | --- | --- |
| 0 | `pass`, or no candidate submitted | green |
| 1 | `fail` — slower than baseline past the effect floor | **fails the job** |
| 2 | `inconclusive` | green, with a warning annotation |
| 3 | a candidate could not be judged at all | **fails the job** |
| 4 | the manifest is broken | **fails the job** |

**Each of those five answers was watched coming out of the real tool, against the real
manifest**, before the workflow was committed — a candidate that is its own baseline, the
same file 20% slower, a configuration nothing is baselined on, and a manifest pointing at
a file that is not there:
`reports/ci-compare-gate-checked-20261102.log`. A gate nobody has seen fail is a gate
nobody knows the shape of.

**2 does not fail on purpose.** §10.6 makes `inconclusive` a first-class answer rather
than a soft failure — two runs of the same binary are inconclusive and should be — and
whether an inconclusive result is enough to merge on is a judgement a person makes with
the report in front of them. **3 does fail**, and it is the one worth defending: a result
nobody could judge is the most expensive thing to let pass silently, which is why
`unjudged` outranks `fail` when several candidates disagree.

**An empty candidate directory is not a pass, and the summary says so.** When a pull
request changes `core/src/main/`, `scanner/src/main/`, `benchmark/android/src/main/` or
the zxing-cpp submodule and carries no result at all, the job adds a notice listing the
changed files. Deliberately a notice and not a failure: plenty of changes under those
paths are not performance changes, and a gate that cries wolf on every rename teaches
people to ignore it. What it must not do is stay silent.

**What this still does not give anyone.** The candidate comes from one device, in one
thermal state, at one pinned operating point, run once — every §6 caveat and every caveat
in §10.6c applies unchanged. CI checks that a claim was measured under the protocol and
judged against the right baseline. It does not make one device a population.

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
