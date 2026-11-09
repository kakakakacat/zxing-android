# Native decoder: reusing the readers / 原生解码器复用 reader：L2 结果

Measured 2026-11-09 on DN-ENG41 (Android 14/API 34, arm64-v8a) with CPU clocks
locked. Three runs in one session, each in its own fresh Gradle instrumentation
process, full 27-case matrix, 50 measured samples per case: the native backend
before the change, the native backend after it, and the Java backend as the
cross-backend reference. The only difference between the first two is that
`NativeDecoder` now keeps its `MultiFormatReader` instead of letting
`ZXing::ReadBarcode` build one per frame. AndroidX cooldown was disabled, the
memory bus could not be locked and this device has no sustained performance
mode; all three runs carry the same standing warnings.

2026-11-09 在 DN-ENG41（Android 14/API 34，arm64-v8a）锁频测量。同一场次三轮，每轮独立 Gradle
instrumentation 进程，27 项完整矩阵，每 case 50 个样本：改动前的原生 backend、改动后的原生 backend，
以及作为跨 backend 参照的 Java backend。前两轮唯一的差别是 `NativeDecoder` 现在保留它的
`MultiFormatReader`，不再让 `ZXing::ReadBarcode` 每帧重建一个。AndroidX 等待降温关闭、
内存总线锁不上、设备不支持持续性能模式；三轮带有相同的常驻提示。

## What changed, and why it was worth looking for / 改了什么，以及为什么值得查

`ZXing::ReadBarcode` constructs a `MultiFormatReader` — and with it every format
reader the options ask for — on every call. That cost does not depend on the
frame, and the already-committed 2026-11-09 array-ROI run had measured it
without naming it: the all-format 1D hit was about 18 µs more expensive than the
UPC-A-only one at VGA, HD and FHD alike, and the only work that differs between
those two cases is the construction of readers that never run. The native 1D hit
was also nearly resolution-independent — 110 µs at VGA against 119 µs at FHD for
six times the pixels — which is the signature of a fixed per-call cost rather
than per-pixel work.

`ZXing::ReadBarcode` 每次调用都会构造一个 `MultiFormatReader`，连带 options 要求的每一个 format
reader。这笔开销与帧无关，而已提交的 2026-11-09 array-ROI 那一轮其实已经量到了它，只是没点名：全码制一维码
命中在 VGA、HD、FHD 三档都比仅 UPC-A 的贵约 18 µs，而这两个 case 唯一的差别就是那些根本没机会运行的
reader 的构造。原生一维码命中也几乎与分辨率无关——VGA 110 µs、FHD 119 µs，像素数差六倍——这是固定
每调用开销的特征，不是逐像素工作的特征。

The readers are now built once and rebuilt only when an option changes. Only the
configuration this decoder actually fixes takes that path: a dense greyscale
plane, no downscale pyramid, no inversion pass, not `isPure`, no character set.
Anything else falls back to `ZXing::ReadBarcode` unchanged.

reader 现在只构造一次，仅在 option 变化时重建。只有本 decoder 真正固定的配置走这条路径：稠密灰度平面、
不降采样、不反色、非 `isPure`、未设字符集。其余情况一律原样回退 `ZXing::ReadBarcode`。

## Native before and after / 原生前后对比

**Verdict: pass** — 11 cases pass, 16 inconclusive, 0 fail.

**判定：pass** —— 11 项通过，16 项不可分辨，0 项失败。

| Case | Reader per frame | Reader reused | Change |
| --- | ---: | ---: | ---: |
| VGA 1D hit, all formats | 107.4 µs | 91.8 µs | −14.5% |
| HD 1D hit, all formats | 111.7 µs | 95.9 µs | −14.1% |
| FHD 1D hit, all formats | 116.4 µs | 100.9 µs | −13.3% |
| VGA 1D hit, UPC-A only | 88.8 µs | 77.1 µs | −13.2% |
| HD 1D hit, UPC-A only | 93.1 µs | 81.4 µs | −12.6% |
| FHD 1D hit, UPC-A only | 97.7 µs | 86.3 µs | −11.7% |
| VGA miss, UPC-A only | 109.6 µs | 100.9 µs | −7.9% |
| HD miss, UPC-A only | 191.5 µs | 182.9 µs | −4.5% |
| FHD miss, UPC-A only | 274.5 µs | 264.3 µs | −3.7% |

Every passing case has disjoint intervals. **Every case dominated by 2D work —
QR hits and all-format misses — is inconclusive, within ±1%.** That is the shape
a removed fixed cost has to have: the saving is about 15 µs of wall clock at all
three resolutions, which is a double-digit percentage of a hundred-microsecond
case and invisible in a millisecond one. Allocation counts did not move.

每一项 pass 的置信区间都不重叠。**所有以 2D 工作为主的 case——QR 命中和全码制 miss——都是不可分辨，
差异在 ±1% 以内。** 这正是去掉一项固定开销该有的形状：节省量在三档分辨率上都是约 15 µs 墙上时间，
在百微秒级 case 上是两位数百分比，在毫秒级 case 上看不见。分配计数没有变化。

**The pairing mattered.** Compared against the previous session's committed
native result instead, the 2D cases appeared 4–5% *slower*. The paired baseline
run reproduces those same elevated numbers with the old code, so that was
session drift and not the change. A before/after taken across two sessions on
this device is not a before/after.

**配对本身是关键。** 如果拿上一场次已提交的原生结果作对比，2D case 会显得**慢** 4–5%。
配对基线轮用旧代码原样复现了那些偏高的数字，所以那是场次漂移，不是改动引起的。
在这台设备上跨场次取前后对比，不算前后对比。

## Against the Java backend / 与 Java backend 对比

Same session, same conditions. **Verdict: fail** — 21 pass, 3 fail, 3
inconclusive. A run fails if any case fails, and the three failures are the one
class that has always been this backend's weak spot.

同场次、同条件。**判定：fail** —— 21 项通过，3 项失败，3 项不可分辨。只要有一项失败整轮即失败，
而这三项失败正是这个 backend 一贯的弱项。

| Case | Java | zxing-cpp | Change |
| --- | ---: | ---: | ---: |
| VGA 1D hit, all formats | 63.6 µs | 91.8 µs | +44.5% (fail) |
| HD 1D hit, all formats | 78.7 µs | 95.9 µs | +21.9% (fail) |
| FHD 1D hit, all formats | 92.5 µs | 100.9 µs | +9.1% (inconclusive) |
| VGA 1D hit, UPC-A only | 66.0 µs | 77.1 µs | +16.8% (fail) |
| HD 1D hit, UPC-A only | 79.0 µs | 81.4 µs | +3.0% (inconclusive) |
| FHD 1D hit, UPC-A only | 91.5 µs | 86.3 µs | −5.7% (inconclusive) |
| HD QR hit, all formats | 18.05 ms | 3.43 ms | −81.0% |
| HD miss, all formats | 48.52 ms | 9.97 ms | −79.5% |
| HD miss, UPC-A only | 1.15 ms | 182.9 µs | −84.1% |

**The remaining deficit is 1D hits and nothing else, it is worst at the lowest
resolution, and it is gone by FHD.** The previously recorded figure for the HD
all-format 1D hit was 39%; that number compared against a Java run from a
different session and is superseded by the 21.9% measured here against a paired
one. Every other case in the matrix goes the other way by 63–91%.

**剩下的劣势只在一维码命中，且分辨率越低越明显，到 FHD 已经消失。** 此前记录的 HD 全码制一维码命中
差距为 39%，那是与另一场次的 Java 轮相比得出的，现由本轮配对测得的 21.9% 取代。矩阵中其余每一项都
反向领先 63–91%。

## What this does not say / 本结果不能说明什么

These are generated replay frames on one device. They do not measure real-camera
first-scan time, battery life, or any other device. The 1D-hit case is also the
one a live preview reaches last — a scan spends most of its frames on misses,
where this backend is 63–88% ahead.

这些是单台设备上的生成回放帧，不能推断真实相机首扫时间、续航或其他设备的表现。一维码命中也是实时预览
最后才会走到的那一步——扫码过程绝大多数帧是 miss，而本 backend 在 miss 上领先 63–88%。

Correctness: the 57 `:scanner` instrumentation tests pass on DN-ENG41, including
`aGs1SeparatorSurvivesInTheText` and `theFormatSetCanBeChangedBetweenFrames`,
which cover the two things this change could have broken — see the text-mode
note in `HANDOFF.md`.

正确性：`:scanner` 的 57 项 instrumentation 测试在 DN-ENG41 上全部通过，其中
`aGs1SeparatorSurvivesInTheText` 和 `theFormatSetCanBeChangedBetweenFrames` 正好覆盖了
本次改动可能破坏的两件事——参见 `HANDOFF.md` 中关于 text mode 的记录。

Data / 数据：
[native before/after CSV](l2-native-cached-reader-DN-ENG41-20261109-cases.csv),
[verdict JSON](l2-native-cached-reader-DN-ENG41-20261109-verdict.json),
[vs Java CSV](l2-native-cached-reader-vs-java-DN-ENG41-20261109-cases.csv),
[vs Java verdict JSON](l2-native-cached-reader-vs-java-DN-ENG41-20261109-verdict.json),
[native baseline](l2-native-cached-reader-baseline-DN-ENG41-20261109.json),
[native candidate](l2-native-cached-reader-candidate-DN-ENG41-20261109.json),
[Java](l2-native-cached-reader-java-DN-ENG41-20261109.json).
Raw AndroidX output / AndroidX 原始输出：
[baseline](l2-native-cached-reader-baseline-DN-ENG41-20261109-androidx.json),
[candidate](l2-native-cached-reader-candidate-DN-ENG41-20261109-androidx.json),
[Java](l2-native-cached-reader-java-DN-ENG41-20261109-androidx.json).
