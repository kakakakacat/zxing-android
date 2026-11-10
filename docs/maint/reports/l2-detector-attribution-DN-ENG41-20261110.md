# L2 — which reader the detection half is / 检测那一半到底是哪个 reader

**Run**: DN-ENG41 (Android 14, arm64-v8a, Qualcomm), clocks locked, `5492da41c`
plus the uncommitted attribution seam.
**Date**: 2026-11-10. **Envelopes**: `l2-groups-5492da41c-DN-ENG41-{native,java}.json` and
`l2-stages-5492da41c-DN-ENG41-{native,java}-20261110.json`. The stage split was re-taken
in this session rather than read from 2026-11-09's file, because the sum check in §3 only
means something if both sides come from the same session. The 11-09 envelopes are kept
beside them.
**Protocol**: `-Pl2.matrix=full -Pl2.throttleWait=0`, four runs in one session,
`backend` recorded by `recordL2RunnerArgs` in each envelope.

---

## 1. The question

§10.3c attributed an all-format miss to thresholding (5.5–7.8% native, 8.9–15.3% Java)
and detection (the rest). That retired hand-written NEON for the binarizer and left the
large half unexplained. "Detection" is six readers in a loop, and a 15.310 ms FHD miss
could be one of them or all six evenly — those call for completely different work.

§10.3c 把全码制 MISS 拆成了阈值化和检测两半，检测占 90–93%，但「检测」是六个 reader 的循环。
15.310 ms 可能集中在一个 reader，也可能六个平摊，这两种情况要做的事完全不同。

## 2. Method

Neither reader loop can be timed from inside: zxing-cpp's `MultiFormatReader` keeps
`_readers` private and this fork's Java one keeps `readers` private. `ReaderGroup` reaches
the decomposition from outside instead — a format set per reader, so that a decode
restricted to it constructs that reader and no other. Both decoders build the same six, in
the same order: 1D, QR, DataMatrix, Aztec, PDF417, MaxiCode.

两个 reader 循环都没法从内部计时（`_readers` / `readers` 都是私有的）。`ReaderGroup` 从外部达到
同样的分解：每个 reader 一个格式集合，限制到该集合的解码只会构造那一个 reader。

Three constraints that are part of the method, not footnotes:

* **A group is a whole symbology and never less.** The readers branch on their own format
  set — `QRReader` skips Micro-QR and rMQR detection unless they are in it, `ODReader` and
  `MultiFormatOneDReader` build one sub-reader per linear symbology. A narrower group
  would time a reader doing *less* work than it does in the all-format run, and the
  attribution would understate it.
* **A group is intersected with the decoder's own formats, and refused when that is
  empty.** Timing the 1D reader under a QR-only scanner would attribute work that
  configuration never performs. `NativeStageSplitTest#aGroupOutsideTheConfiguredFormatsIsRefused`
  pins this; refusing is the only answer that cannot be mistaken for a cheap reader.
* **PDF417 covers two readers on the native side.** zxing-cpp builds `Pdf417::Reader` and
  `MicroPdf417::Reader` separately, and no format set selects the first without the second.
  Reported together rather than split, because a split would describe a decode nobody runs.

`ReaderOptions::hasAnyFormat` turned out to be declared under `ZXING_INTERNAL`, which this
build does not define. Its body was inlined over the public `operator&` rather than start
defining a library-internal macro for one predicate.

## 3. What the method had to pass first

All three of these are checks the measurement makes on itself, and all three were stated
before the numbers were read.

**(1) The six sum to `detect` on a miss.** Both loops run every reader to the end when the
frame holds nothing.

| | VGA | HD | FHD |
| --- | --- | --- | --- |
| native, six summed ÷ `detect` | 97.9% | 99.0% | 97.8% |
| Java, six summed ÷ `detect` | 99.5% | 99.8% | 99.7% |

**(2) The loop stops at the first hit, in the order §10.3c predicted from source.** A hit
charges nothing to the readers behind the one that found it, so a *prefix* of the order
should reproduce `detect` while all six should overshoot badly.

| | prefix | native | Java |
| --- | --- | --- | --- |
| 1D hit | `oned` | 98.1–98.2% | 88.3–92.8% |
| QR hit | `oned + qrcode` | 97.5–99.7% | 99.0–99.6% |
| either | all six | 223–11604% | 189–50459% |

The reader order was read out of `MultiFormatReader.cpp:45` and `MultiFormatReader.java:119`
and used to predict group membership in §10.3c. It is now measured.

**(3) The same reader reached two independent ways.** The `qrcode` group under `DEFAULT`
against the whole `detect` under `QR_ONLY` — a different class, a different run, a
different configuration.

| | VGA | HD | FHD |
| --- | --- | --- | --- |
| native, miss | 100.5% | 101.0% | 100.2% |
| native, QR hit | 99.8% | 100.5% | 100.3% |
| Java, miss | 99.9% | 98.6% | 99.9% |
| Java, QR hit | 100.0% | 100.1% | 100.2% |

## 4. The result

All-format miss — the frame a live preview decodes thousands of — medians in ms, and as a
share of that backend's own `detect`.

### Native

| reader | VGA | share | HD | share | FHD | share |
| --- | --- | --- | --- | --- | --- | --- |
| 1D | 0.474 | 13.3% | 0.895 | 10.1% | 1.331 | 8.7% |
| QR | 0.883 | 24.7% | 2.560 | 28.7% | 5.892 | **38.5%** |
| DataMatrix | 0.102 | 2.9% | 0.199 | 2.2% | 0.275 | 1.8% |
| Aztec | 0.816 | 22.8% | 2.488 | 27.9% | 2.821 | 18.4% |
| PDF417 | 1.182 | 33.1% | 2.628 | 29.5% | 4.614 | 30.1% |
| MaxiCode | 0.042 | 1.2% | 0.043 | 0.5% | 0.045 | 0.3% |
| **six summed** | 3.500 | 97.9% | 8.813 | 99.0% | 14.977 | 97.8% |
| **`detect`** | 3.576 | 100% | 8.905 | 100% | 15.310 | 100% |

### Java

| reader | VGA | share | HD | share | FHD | share |
| --- | --- | --- | --- | --- | --- | --- |
| 1D | 9.064 | 45.7% | 14.917 | 35.0% | 19.398 | 28.2% |
| QR | 1.071 | 5.4% | 3.199 | 7.5% | 11.474 | 16.7% |
| DataMatrix | 0.020 | 0.1% | 0.030 | 0.1% | 0.039 | 0.1% |
| Aztec | 0.083 | 0.4% | 0.127 | 0.3% | 0.168 | 0.2% |
| PDF417 | 9.306 | 46.9% | 24.050 | 56.4% | 37.158 | **54.0%** |
| MaxiCode | 0.185 | 0.9% | 0.222 | 0.5% | 0.291 | 0.4% |
| **six summed** | 19.729 | 99.5% | 42.546 | 99.8% | 68.528 | 99.7% |
| **`detect`** | 19.834 | 100% | 42.648 | 100% | 68.754 | 100% |

### Per reader, across backends (all-format miss, Java ÷ native)

| reader | VGA | HD | FHD |
| --- | --- | --- | --- |
| 1D | 19.1× | 16.7× | 14.6× |
| QR | 1.2× | 1.2× | 1.9× |
| DataMatrix | **0.2×** | **0.2×** | **0.1×** |
| Aztec | **0.1×** | **0.1×** | **0.1×** |
| PDF417 | 7.9× | 9.2× | 8.1× |
| MaxiCode | 4.4× | 5.2× | 6.5× |
| whole `detect` | 5.5× | 4.8× | 4.5× |

## 5. What this says

**The two backends spend their detection in different places.** On Java, PDF417 and the 1D
reader are 82% of an FHD miss. On native they are 39%, and QR is the largest single reader
at 38.5%. A conclusion drawn on one backend does not transfer.

**两种后端的检测时间花在完全不同的地方。** Java 上 PDF417 + 1D 占 FHD MISS 的 82%；原生上这两个
只占 39%，最大的是 QR（38.5%）。在一种后端上得出的结论不能搬到另一种。

**Two native readers are slower than their Java counterparts, and only those two.**
DataMatrix and Aztec together are 20–30% of a native all-format miss (3.095 ms of 15.310
at FHD, 2.687 ms of 8.905 at HD) against 0.3–0.5% of a Java one (0.207 ms of 68.754 at
FHD). Every other reader is 1.9× to 14.6× faster on native. **This is the clearest
optimisation target this project has found**: a fifth of the native miss is two detectors
that the Java backend dismisses for essentially nothing, which means the work is not
inherent to the frame.

**原生有两个 reader 比 Java 的还慢，而且只有这两个。** DataMatrix 和 Aztec 合起来占原生全码制
MISS 的 20–30%，而在 Java 上只占 0.3–0.5%。其余每个 reader 原生都快 1.9–14.6 倍。这是目前找到的
最清楚的优化目标：原生 MISS 的五分之一花在两个 Java 后端几乎不花钱就打发掉的检测器上——说明这些
工作不是帧本身要求的。

**Restricting formats is a larger lever than any code change measured so far, and it is
already shipped.** `ScannerOptions` can already narrow the format set. On native, dropping
to QR-only takes an FHD miss's detection from 15.310 ms to 5.878 ms; on Java, from 68.754
to 11.482. Those were measurable before, but it was not known which reader was being paid
for.

## 6. What this does not settle

* **Why** zxing-cpp's DataMatrix and Aztec detectors do real work on a blank frame where
  ZXing Java's bail out. The size of the gap is measured; the cause is not. That is the
  next cut, and it is a reading of `DMDetector`/`AZDetector` against their Java
  counterparts rather than another benchmark.
* **Hit frames beyond the two synthetic ones.** The groups are attributed on a miss, which
  is the dominant frame; a hit stops at the first reader and so attributes nothing past it.
* **Non-Qualcomm, 16 KB page, and low-end devices** (D-06). One device, one ABI.
* **Whether the Aztec share's non-monotonic scaling** (0.816 VGA → 2.488 HD → 2.821 FHD,
  native) is a detector whose work is bounded by structure rather than pixels, or an
  artefact of these three generated frames. Not investigated.

## 7. Run-level events, recorded rather than smoothed over

* **The first attempt died 5.8 s into its first case.** `ActivityManager` logged
  `Force stopping io…benchmark.test appid=10226 user=-1: deletePackageX`, then
  `Killing … due to deletePackageX`, then `Crash of app … running instrumentation`. The
  test process was killed because the package was being uninstalled — there was no Java
  exception (the JUnit XML carries an empty `<failure></failure>`) and no tombstone (the
  newest on the device is an unrelated Bluetooth HAL abort from 2026-10-31). **Who
  requested the uninstall is not established.** The identical command then completed 72 of
  72 cases, and the run reported here is that one. Recorded because a reader deciding
  whether to trust these numbers should know the first attempt did not survive, and
  because a cause I did not establish is worse written down than left open.
* **`THROTTLE_WAIT_DISABLED` and `NO_SUSTAINED_PERFORMANCE_MODE`** are in all four
  envelopes. This device sits permanently at thermal status 3; `-Pl2.throttleWait=0` is the
  repo's own documented response and is justified by a committed measurement.
* **Clocks were already locked from the 2026-11-09 session and the device was never
  rebooted**, so these numbers and §10.3c's are at the same lock.

## 8. A free reproducibility result

The stage split was re-run here, so §10.3c's figures have a second measurement a day
apart, same commit, same lock. All-format miss, 2026-11-09 → 2026-11-10:

| | native binarize | native detect | Java binarize | Java detect |
| --- | --- | --- | --- | --- |
| VGA | −0.91% | +0.55% | +0.03% | +0.26% |
| HD | −1.15% | +0.45% | −1.60% | −0.07% |
| FHD | −0.90% | +1.06% | −0.46% | +0.07% |

Every figure is inside ±1.6%, against §10.6's 3% effect floor. §10.3c's conclusions stand
on a second session, and the day-to-day noise on this device with clocks locked is now
bounded rather than assumed.
