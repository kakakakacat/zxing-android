# Where a frame's time goes: thresholding or detection / 一帧的耗时去向：阈值化还是检测

Measured 2026-11-09 on DN-ENG41 (Android 14/API 34, arm64-v8a) at commit `5492da41c`
with CPU clocks locked. Four runs in one session, each in its own fresh Gradle
instrumentation process, full 27-case matrix: the stage split and the end-to-end
case, for each of the two decoder backends. AndroidX cooldown was disabled and the
device has no sustained performance mode; all four runs carry the same two standing
warnings. Result files: `l2-stages-5492da41c-DN-ENG41-{native,java}.json`,
`l2-e2e-5492da41c-DN-ENG41-{native,java}.json`.

2026-11-09 在 DN-ENG41（Android 14/API 34，arm64-v8a）锁频测量，commit `5492da41c`。同一场次
四轮，每轮独立 Gradle instrumentation 进程，27 项完整矩阵：两种解码后端各跑一轮阶段切分和一轮端到端。
AndroidX 等待降温关闭、设备不支持持续性能模式；四轮带有相同的两条常驻提示。结果文件见上。

## The question / 要回答的问题

§10.6c measured an FHD all-format miss at 18.677 ms on the native backend and left it
there. A miss under every format is what a scanner does for almost every frame of a
session, so that number is the one worth attacking — but nothing said whether it was
mostly the binarizer or mostly the detectors. Hand-written NEON for the binarizer, a
different binarizer, `-O3`, ThinLTO and a smaller analysis resolution are five different
decisions depending on the answer, and all five were guesses.

§10.6c 把原生后端的 FHD 全码制未命中测到 18.677 ms 就停在那里。全码制未命中是一次扫描会话里几乎每
一帧的实际工作，所以这个数字最值得攻，但没有任何依据说明它主要是二值化还是主要是检测器。手写 NEON
二值化、换 binarizer、`-O3`、ThinLTO、降低分析分辨率——这五个决定取决于那个答案，而五个都只是猜测。

`DecoderStageBenchmark` splits it, for either backend. Method, and the three things the
split is not, in BENCHMARKS.md §10.3b.

`DecoderStageBenchmark` 把它切开，两种后端通用。方法及该切分**不**代表的三件事见 BENCHMARKS.md §10.3b。

## The answer: detection dominates, on both backends / 答案：两种后端都是检测占主导

All-format miss, the frame a session actually spends its time on. Medians, in ms.

全码制未命中，即会话真正耗时所在的那种帧。中位数，单位 ms。

| | native binarize | native detect | binarize share | Java binarize | Java detect | binarize share |
| --- | --- | --- | --- | --- | --- | --- |
| VGA | 0.209 | 3.556 | **5.5%** | 1.947 | 19.783 | **8.9%** |
| HD | 0.614 | 8.865 | **6.4%** | 5.754 | 42.677 | **11.9%** |
| FHD | 1.315 | 15.150 | **7.8%** | 12.407 | 68.709 | **15.3%** |

**Thresholding is 5.5–7.8% of a native all-format miss and 8.9–15.3% of a Java one.**
Reducing it to zero would not move either backend much, so **hand-written NEON for the
binarizer is not the lever** — which is what the standing idea was, on the strength of a
disassembly showing `HybridBinarizer::getBlackMatrix` vectorised only to `.8b` with a
`uminv`/`umaxv` reduction per 8×8 block. That reading of the instructions was right and
the conclusion drawn from it was wrong: the code is suboptimal and it sits on 8% of the
path.

**阈值化占原生全码制未命中的 5.5–7.8%，占 Java 的 8.9–15.3%。** 即便压到零，两种后端都不会有多大变化，
所以**手写 NEON 优化 binarizer 不是杠杆**——而这正是此前基于反汇编得出的设想：`HybridBinarizer::
getBlackMatrix` 只向量化到 `.8b`，且每个 8×8 块付一次 `uminv`/`umaxv` 横向归约。对指令的判读没错，
由它得出的结论错了：那段代码确实次优，但它位于只占 8% 的路径上。

Thresholding also scales almost exactly with pixel count on the native side — 0.209 →
0.614 → 1.315 ms for 1× → 2.93× → 6.75× the pixels, about 0.68 ns per pixel — and is
flat across variant and config at a given resolution (0.208–0.209 ms at VGA for all nine).
It has to be: it is the same frame through the same binarizer. That invariance is the
split's own internal check and it holds.

原生侧的阈值化几乎严格随像素数线性：像素数 1× → 2.93× → 6.75× 对应 0.209 → 0.614 → 1.315 ms，
约 0.68 ns/像素；且在同一分辨率下跨 variant 与 config 保持不变（VGA 九个 case 全是 0.208–0.209 ms）。
这是必然的——同一帧、同一 binarizer。该不变性是切分自身的内部检验，并且成立。

## zxing-cpp wins both halves, and wins thresholding harder / zxing-cpp 两半都赢，阈值化赢得更多

Same all-format miss frames, each backend's own thresholding against the other's.

同样的全码制未命中帧，两种后端各自的阈值化互相对比。

| | binarize, Java ÷ native | detect, Java ÷ native |
| --- | --- | --- |
| VGA | **9.3×** | 5.6× |
| HD | **9.4×** | 4.8× |
| FHD | **9.4×** | 4.5× |

This is not the same algorithm implemented twice — each decoder brings its own
thresholding, which is why §10.6b says there is no shared stage to hold constant. It is
each backend's own answer to the same problem, on identical frames, and the native one is
about nine times cheaper at it. The detection half, which is where the time actually goes,
it wins by 4.5–5.6×.

这不是同一个算法实现了两遍——每种解码器带自己的阈值化，这正是 §10.6b 说「没有可以对齐的共享阶段」的
原因。这是两种后端对同一问题各自的答案，输入帧完全相同，原生一侧的阈值化便宜约九倍。而真正耗时所在的
检测那一半，原生快 4.5–5.6 倍。

One asymmetry worth recording: the native binarizer's cost does not depend on frame
content (0.208–0.209 ms across all nine VGA cases) while the Java one does (1.578–1.947 ms
across the same nine, a 23% spread). Not pursued here.

一处值得记录的不对称：原生 binarizer 的耗时与帧内容无关（VGA 九个 case 全在 0.208–0.209 ms），
Java 的则有关（同样九个 case 在 1.578–1.947 ms，相差 23%）。本轮未深究。

## The split adds up, and where it does not is the finding / 切分能对上，对不上的地方本身就是结论

`binarize + detect` against the end-to-end case for the same frame, same commit, same
session. Two groups, with nothing in between.

`binarize + detect` 对同一帧、同一 commit、同一场次的端到端 case。结果分成两组，中间没有过渡。

| Group | Cases | native | Java |
| --- | --- | --- | --- |
| A 2D reader consults the matrix | 15 of 27 | 92–99% | 96–101% |
| No 2D reader ever asks for one | 12 of 27 | 286–1588% | 292–12214% |

Group A is the split working: the two halves account for essentially the whole decode, and
the 0–8% residual is the costs §10.3b names as excluded — the crop, the luminance source,
the JNI transition, the array pin, and `detect` reading a matrix that is warm in cache.

A 组是切分在正常工作：两半几乎覆盖了整个解码，0–8% 的残差正是 §10.3b 列为排除在外的那些开销——取景框
裁剪、亮度源、JNI 跨越、数组 pin，以及 `detect` 读到的矩阵在 cache 中是热的。

Group B is not noise and not a broken harness. It is exactly the twelve cases where no 2D
reader ever requests a matrix, and membership was predicted from the source before it was
seen in the data: only the 2D readers call `getBitMatrix`, while `ODReader` thresholds a
row at a time through `getPatternRow` — and **with `tryHarder` off, which is what this
fork ships, `MultiFormatReader` puts the 1D reader ahead of every 2D one**
(`MultiFormatReader.cpp:45` on the native side, `MultiFormatReader.java:119` on the Java
side; both move it behind when `tryHarder` is on). So a 1D symbol ends the read before
anything asks for a matrix, and a 1D-only format set has no 2D reader to ask. For those
twelve frames `binarize` measures work the decode does not do, and **the halves must not be
added.**

B 组不是噪声，也不是 harness 坏了。它恰好就是那十二个「没有任何 2D reader 请求过矩阵」的 case，而且归属
是先从源码推出、后在数据里看到的：只有 2D reader 会调 `getBitMatrix`，`ODReader` 通过 `getPatternRow`
逐行阈值化——而**在 `tryHarder` 关闭（本 fork 的出货默认）时，`MultiFormatReader` 把 1D reader 排在
所有 2D reader 之前**（原生侧 `MultiFormatReader.cpp:45`，Java 侧 `MultiFormatReader.java:119`；
两者在 `tryHarder` 开启时都把它挪到最后）。于是一维码在任何东西索要矩阵之前就结束了这次 read，而仅一维
的码制集合根本没有 2D reader 去索要。对这十二帧，`binarize` 测的是解码并不做的工作，**两半不能相加。**

The clearest single case: at HD, `hit_1d_default` is 0.612 ms of thresholding against
0.047 ms of detection, for a frame whose whole decode measures 0.092 ms — a decode
cheaper than the matrix it supposedly built. On the Java backend the same case reads
4.767 ms against 0.064 ms for a 0.075 ms decode.

最清楚的单个 case：HD 下 `hit_1d_default` 是 0.612 ms 阈值化对 0.047 ms 检测，而这一帧的完整解码
只有 0.092 ms——解码比它号称构建的矩阵还便宜。Java 后端同一 case 是 4.767 ms 对 0.064 ms，
解码 0.075 ms。

**This is also a large part of why `TRY_HARDER` costs what it does.** Turning it on moves
the 1D reader behind the 2D ones, so a 1D hit stops short-circuiting and starts paying for
a matrix it never reads. That is a per-frame cost on top of the extra search, and it was
not previously attributed.

**这也是 `TRY_HARDER` 之所以那么贵的很大一部分原因。** 开启后 1D reader 被挪到 2D 之后，一维码命中
不再短路，开始为一个它从不读取的矩阵付费。这是叠加在额外搜索之上的每帧固定开销，此前未被归因。

## Reproducibility / 重复性

The native stage run was taken twice at this commit, in two separate sessions a few
minutes apart, the first of them while an unrelated host compile was running. All-format
miss, the two sessions:

原生阶段切分在本 commit 上测了两次，分属相隔几分钟的两个独立场次，其中第一次是在主机并行执行一次无关
编译时进行的。全码制未命中，两个场次对比：

| | binarize | detect |
| --- | --- | --- |
| VGA | −0.05% | −0.07% |
| HD | −0.11% | −0.04% |
| FHD | −0.08% | −0.27% |

Inside the §10.6 effect floor of 3% for cases above 100 µs by two orders of magnitude, and
it settles the question the host compile raised: it had no measurable effect. The first
session's envelope is not committed — it was converted with a stale protocol record (see
below) and a result file that can describe a different run than the one that produced it
is worse than no result file.

比 §10.6 对 100 µs 以上 case 规定的 3% 效应下限低两个数量级，也解决了那次并行编译引出的问题：它没有
可测量的影响。第一场次的信封未提交——它是用一份过期的协议记录转换的（见下），而一份可能描述成另一轮的
结果文件，比没有结果文件更糟。

## A protocol trap, re-entered through a different door / 一个协议陷阱，从另一个入口重入

The first pass of all four runs passed the backend as
`-Pandroid.testInstrumentationRunnerArguments.l2.backend=native`. That reaches the
instrumentation, so the measurements were genuinely the native backend — confirmed by
magnitude, since HD all-format miss came back at 9.5 ms against the Java backend's 48.5 ms
— but it does **not** enter `android.defaultConfig.testInstrumentationRunnerArguments`, so
`recordL2RunnerArgs` wrote the default and the envelope said `backend: java`,
`l2.matrix: ci`. The intended property is `-Pl2.backend`, which sets the instrumentation
argument and gets recorded.

四轮的第一遍是用 `-Pandroid.testInstrumentationRunnerArguments.l2.backend=native` 传后端的。它能
到达 instrumentation，所以测出来的确实是原生后端——由量级确认：HD 全码制未命中为 9.5 ms，而 Java
后端是 48.5 ms——但它**不进** `android.defaultConfig.testInstrumentationRunnerArguments`，于是
`recordL2RunnerArgs` 写入的是默认值，信封里成了 `backend: java`、`l2.matrix: ci`。正确的属性是
`-Pl2.backend`，它既设置 instrumentation 参数又会被记录。

**This repository has already fixed this bug once**, from the inside: the comment at
`benchmark/android/build.gradle.kts:151` describes a run with `-Pl2.backend=native` being
recorded as java because a leftover file was considered up to date, and the fix was to
make the arguments an input of the task. That fix is correct and still works. What failed
here was not the task but a route around it, from the command line. All four runs were
re-taken with the correct property rather than having the field corrected by hand.

**这个 bug 本仓库已经从内部修过一次**：`benchmark/android/build.gradle.kts:151` 的注释记录了一次
`-Pl2.backend=native` 的运行被记成 java，原因是遗留文件被当作最新，修法是把参数作为任务的输入。那个
修法是对的，至今有效。这次失效的不是该任务，而是从命令行绕过它的一条路径。四轮全部按正确属性重跑，
而不是手工修正那个字段。

## What this does not settle / 本轮未解决的问题

- **One device, one operating point, one thermal state.** DN-ENG41 sits at thermal status
  3 permanently, so every figure here is a throttled figure. Nothing generalises to
  low-end or non-Qualcomm hardware, and `armeabi-v7a` has still never been run anywhere.
- **Where inside detection the time goes is still unattributed.** This split stops at two
  halves. The detectors are now 90–93% of a native all-format miss and the next question
  is which of them, which this harness does not answer.
- **Nothing here is a claim about first-scan latency**, which remains unmeasured: this is
  decoder CPU replay cost on fixed frames, with no camera delivery, pacing or autofocus.

- **单设备、单工作点、单热状态。** DN-ENG41 常驻 thermal status 3，因此此处每个数字都是降频数字。
  不能外推到低端或非高通硬件，`armeabi-v7a` 至今未在任何设备上运行过。
- **检测内部的耗时去向仍未归因。** 本切分只到两半为止。检测器现在占原生全码制未命中的 90–93%，
  下一个问题是其中哪一个，而本 harness 回答不了。
- **此处没有任何关于首次扫码延迟的结论**，那一项仍未测量：这是固定帧上的解码器 CPU 回放成本，
  不含相机送帧、取帧节奏和对焦。
