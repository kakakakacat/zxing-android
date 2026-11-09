# A Code 39 the native backend could not read / 原生 backend 读不出的 Code 39

A correctness defect in the shipping default decoder, found on 2026-11-09 while
looking for what else the native 1D path spends time on. It is not a timing
problem, and the measurement below exists only to show that fixing it costs
nothing.

这是出厂默认解码器的一个正确性缺陷，2026-11-09 在排查原生一维码路径还有哪些开销时发现。
它不是性能问题；下面的测量只用来说明修复它不付出代价。

## The defect / 缺陷

In zxing-cpp, **Code 32** (Italian pharmacode) and **PZN** are *variants of
Code 39*, and `Code39Reader` reports one of them whenever the decoded **content**
of an ordinary Code 39 symbol happens to parse as one — checked before Code 39
itself, and for Code 32 the text is rewritten to the decoded pharmacode. Neither
has an equivalent in this fork, so `formatFromNativeName` dropped the result and
the frame was reported as holding nothing. Because the native decoder is
configured for a single symbol, the dropped variant also ended the search.

在 zxing-cpp 中，**Code 32**（意大利药品码）和 **PZN** 是 *Code 39 的变体*，`Code39Reader`
只要普通 Code 39 符号解出的**内容**恰好能解析成其中之一，就会报那个变体——判断在 Code 39 之前，
且 Code 32 还会把文本改写成解出的药品码。本 fork 两者都没有对应格式，于是 `formatFromNativeName`
丢弃结果，整帧被报告为空。又因为原生解码器被配置为只找一个符号，被丢弃的变体同时终结了搜索。

**So an ordinary Code 39 symbol was unreadable by the default backend while the
Java backend read it.** Content-triggered, so one specific barcode fails forever
while the next one works. On DN-ENG41, all six six-character payloads that are
valid Code 32, and one valid PZN:

**结果是：一个普通的 Code 39 符号，默认 backend 读不出来，而 Java backend 读得出。**
由于由内容触发，某一个具体条码会永远失败，旁边那个却正常。DN-ENG41 上，六个合法 Code 32
的六字符 payload 和一个合法 PZN：

| Payload | Native, before | Native, after | Java |
| --- | --- | --- | --- |
| `5J2451` | miss | `CODE_39:5J2451` | `CODE_39:5J2451` |
| `NWTNT4` | miss | `CODE_39:NWTNT4` | `CODE_39:NWTNT4` |
| `4NX7JF` | miss | `CODE_39:4NX7JF` | `CODE_39:4NX7JF` |
| `P65HG1` | miss | `CODE_39:P65HG1` | `CODE_39:P65HG1` |
| `8F92NF` | miss | `CODE_39:8F92NF` | `CODE_39:8F92NF` |
| `F0KDXU` | miss | `CODE_39:F0KDXU` | `CODE_39:F0KDXU` |
| `-10000001` (PZN) | miss | `CODE_39:-10000001` | `CODE_39:-10000001` |
| `ABC123` | `CODE_39:ABC123` | `CODE_39:ABC123` | `CODE_39:ABC123` |

**This is not a corner case.** About **9.3%** of six-character Code 39 contents
drawn from Code 32's own alphabet are valid Code 32 (20,000 random samples).

**这不是边角情况。** 在 Code 32 自己的字母表上，六字符 Code 39 内容中约有 **9.3%** 是合法
Code 32（20,000 次随机采样）。

## Two things that were wrong about the first fix / 第一次修复想错的两点

**Restricting the format set does not exclude a variant.** `operator<=` reads as
"same symbology, and the requested one is the base variant", so asking for
`Code39` still enables `Code32` and `PZN` — and asking for `PDF417` enables
`MicroPDF417`, `QRCode` enables Micro QR and rMQR, `DataBar` enables DataBar
Limited. Measured, not assumed: restricting to `CODE_39` missed those payloads
too. Only asking for the **specific variants** — `Code39Std,Code39Ext` — makes
`hasFormat(Code32)` and `hasFormat(PZN)` false.

**限制格式集并不能排除变体。** `operator<=` 的语义是「同 symbology，且被请求的是基础变体」，
所以请求 `Code39` 仍会启用 `Code32` 和 `PZN`——同理请求 `PDF417` 会启用 `MicroPDF417`，
`QRCode` 会启用 Micro QR 和 rMQR，`DataBar` 会启用 DataBar Limited。这是实测而非推断：
限定为 `CODE_39` 时那些 payload 同样 miss。只有请求**具体变体** `Code39Std,Code39Ext`
才能让 `hasFormat(Code32)` 和 `hasFormat(PZN)` 为假。

**What is asked for and what comes back are different vocabularies.** The first
attempt changed only the request table, and plain Code 39 started missing too —
the reverse table is built from it, so the spelling `Code 39` that a result
actually carries disappeared with the edit. The two tables are now explicitly
decoupled, and a comment says so.

**请求用的名字和返回的名字是两套词汇。** 第一次只改了请求表，结果连普通 Code 39 也开始 MISS——
反查表是从它构建的，返回结果真正携带的拼写 `Code 39` 随这次编辑一起消失了。现在两张表显式解耦，
并有注释说明。

## The fix / 修复

1. Code 39 is asked for as its two nameable variants, and `Code 39` is
   registered in the result table explicitly.
2. **"No format selection" now asks for every format this fork can name**,
   instead of sending the empty string that means *all* to zxing-cpp. All
   includes symbologies and variants with no equivalent here — Telepen, DX Film
   Edge, Code 32, PZN, DataBar Limited, MicroPDF417, Micro QR, rMQR, Aztec Rune
   — whose results can only ever be dropped, and whose readers need not be built.

1. Code 39 按它的两个可命名变体请求，`Code 39` 在结果表中显式注册。
2. **「未指定格式」现在请求本 fork 能命名的全部格式**，不再发送对 zxing-cpp 意为「所有格式」
   的空串。「所有」包含本 fork 没有对应物的 symbology 和变体——Telepen、DX Film Edge、
   Code 32、PZN、DataBar Limited、MicroPDF417、Micro QR、rMQR、Aztec Rune——它们的结果
   只会被丢弃，它们的 reader 也不必构造。

## What it cost / 代价

**L1, the 1168-sample corpus**, native backend at the default effort on
DN-ENG41: 890 correct, 3 incorrect, 227 not found, 48 correctly empty — compared
sample by sample against the committed baseline, `verdict: same`, **0
regressions and 0 improvements**. The corpus holds no symbol of this class, so
the fix is free there and the gain is in what the corpus does not cover.

**L1 语料 1168 张**，DN-ENG41 原生 backend 默认力度：890 正确、3 错误、227 未找到、
48 正确为空——与已提交基线逐样本比较，`verdict: same`，**0 回归、0 改善**。语料中没有这一类
符号，所以修复在语料上是免费的，收益落在语料覆盖不到的地方。

**L2**, paired in one session on DN-ENG41 with clocks locked, 27 cases, 50
samples each: **pass** — 2 cases pass, 25 inconclusive, **none slower**. The two
are all-format QR hits, where the 1D readers run over 15 rows in both directions
before the QR reader is reached, so two fewer of them is measurable:
`HD_HIT_QR_DEFAULT` 3.32 ms → 3.19 ms (−4.0%) and `VGA_HIT_QR_DEFAULT` 1.29 ms →
1.24 ms (−3.4%), intervals disjoint. The all-format 1D hits moved −3.2% to −3.6%,
below the 10% floor these sub-100 µs cases carry on this device, so they are not
resolvable here.

**L2**，DN-ENG41 锁频同场次配对，27 项、每项 50 个样本：**pass** —— 2 项通过、25 项不可分辨、
**无一变慢**。通过的两项是全码制 QR 命中——一维 reader 要在两个方向上扫 15 行才轮到 QR reader，
所以少两个是能测出来的：`HD_HIT_QR_DEFAULT` 3.32 ms → 3.19 ms（−4.0%）、
`VGA_HIT_QR_DEFAULT` 1.29 ms → 1.24 ms（−3.4%），置信区间不重叠。全码制一维码命中变化
−3.2% 至 −3.6%，低于这台设备对百微秒以下用例的 10% 门槛，在此不可分辨。

`:scanner` instrumentation: **59/59 pass**, including two new regression tests —
`aCode39WhoseContentParsesAsAnotherVariantIsStillACode39` over all seven payloads
in both format configurations, and
`noFormatSelectionAsksForTheNameableFormatsRatherThanAllOfThem`, which rebuilds
the expected request list from the per-format spellings so the two tables cannot
drift apart again.

`:scanner` instrumentation：**59/59 通过**，含两项新回归测试——
`aCode39WhoseContentParsesAsAnotherVariantIsStillACode39` 覆盖全部七个 payload 的两种
格式配置，以及 `noFormatSelectionAsksForTheNameableFormatsRatherThanAllOfThem`，
它从逐格式拼写重建期望的请求列表，防止两张表再次走偏。

## Still open / 仍未解决

The variants that remain enabled because they are sub-variants of a symbology
this fork does name — DataBar Limited under DataBar, MicroPDF417 under PDF417,
Micro QR and rMQR under QRCode, Aztec Rune under Aztec. None of them aliases an
ordinary symbol the way Code 32 aliases a Code 39: they are distinct symbologies,
so a QR symbol is not read as a Micro QR. They cost reader construction and can
still swallow a frame that genuinely holds one of them, which is the honest
answer anyway. Excluding them would mean requesting variant lists for four more
formats, and that is not worth doing without a symbol of each to test against.

仍被启用的那些变体，是因为它们是本 fork 能命名的 symbology 的子变体——DataBar 下的
DataBar Limited、PDF417 下的 MicroPDF417、QRCode 下的 Micro QR 与 rMQR、Aztec 下的
Aztec Rune。它们都不像 Code 32 别名 Code 39 那样会别名一个普通符号：它们是独立 symbology，
QR 符号不会被读成 Micro QR。代价是 reader 构造，以及确实拿到这类符号时会吞掉一帧——而那种情况下
「读不出」本来就是诚实答案。要排除它们需要为另外四个格式也写变体列表，在没有各自的样本可测之前
不值得做。

Data / 数据：
[L2 cases CSV](l2-nameable-formats-DN-ENG41-20261109-cases.csv),
[L2 verdict JSON](l2-nameable-formats-DN-ENG41-20261109-verdict.json),
[L2 baseline](l2-nameable-formats-baseline-DN-ENG41-20261109.json),
[L2 candidate](l2-nameable-formats-candidate-DN-ENG41-20261109.json),
[L1 result](l1-75be15d45-DN-ENG41-native-nameable-formats.json).
Raw AndroidX output / AndroidX 原始输出：
[baseline](l2-nameable-formats-baseline-DN-ENG41-20261109-androidx.json),
[candidate](l2-nameable-formats-candidate-DN-ENG41-20261109-androidx.json).
