# Camera1 native array ROI: L2 result / Camera1 原生数组裁剪：L2 结果

Measured 2026-11-09 on DN-ENG41 (Android 14), with CPU clocks locked. Both native
runs used the same working tree based on `a87da2ab1` (`-dirty` in the recorded
metadata), generated replay frames, and AndroidX Microbenchmark. The baseline
passed a copied cropped matrix to JNI; the candidate passed the original Camera1
Y array, crop offset and strides. `FramePipeline.endToEnd` supplies `LumaFrameData`
to both runs, matching the production Camera1 decoder entry point. The decoder
selection alone changed between the two runs. AndroidX cooldown was disabled
and the device has no sustained performance mode; the comparison tool accepts
these as shared standing warnings under the measured effect floor.

2026-11-09 在 DN-ENG41（Android 14）锁频测量。两次原生运行使用基于
`a87da2ab1` 的同一未提交工作树、相同生成回放帧和 AndroidX Microbenchmark。
基线把复制后的裁剪矩阵送入 JNI；候选直接传入 Camera1 原始 Y 数组、裁剪偏移及步幅。
两次 `endToEnd` 均向解码器提供 `LumaFrameData`，与生产 Camera1 入口一致。
只切换了原生解码器的数组裁剪路径。AndroidX 等待降温关闭，设备不支持持续性能模式；
两次运行均有相同提示，对比工具按该设备已测得的效应下限放行。

| Default/all-format end-to-end | Copied native | Array ROI native | Change |
| --- | ---: | ---: | ---: |
| FHD 1D hit | 1.81 ms | 119.0 µs | −93.4% |
| HD 1D hit | 845.2 µs | 114.2 µs | −86.5% |
| VGA 1D hit | 389.8 µs | 110.2 µs | −71.7% |
| HD QR hit | 4.16 ms | 3.25 ms | −21.9% |
| HD miss | 10.44 ms | 9.56 ms | −8.4% |

The native-before/after gate **passed all 27 cases** (0 failed); intervals were
disjoint in every case. A subsequent Java HD/default run measured **81.9 µs**
for the same generated 1D hit, versus **114.2 µs** for the native candidate.
This is a separate run, not a paired Java/native gate: the remaining native
median gap is about **39%**. The optimization removes most of the old 5–20×
regression, but does not establish Java parity for this hit.

原生前后对比 **27 项全部通过**（0 失败），每项置信区间均不重叠。后续另测
Java 的 HD/default 一维码命中为 **81.9 µs**，原生候选为 **114.2 µs**。
这不是配对的 Java/原生门槛对比；原生中位数仍慢约 **39%**。本次消除了历史
5–20 倍退化中的大部分，但不能声称一维码已与 Java 持平。

Instrumented result equivalence compares the copied and array ROI paths on padded,
cropped UPC-A and QR frames, including text, format, raw bytes and metadata.
The device's 57 scanner instrumentation tests passed. These synthetic L2 frames
do not measure real-camera first-scan time, battery life or other devices.

真机回归测试以带填充和裁剪的 UPC-A、QR 帧比较旧、新原生路径的文本、码制、
原始字节和元数据；scanner 的 57 项 instrumentation 测试全部通过。生成帧的
L2 测量不能推断真实相机首扫、续航或其他设备表现。

Data / 数据： [27-case CSV](l2-native-array-roi-DN-ENG41-20261109-cases.csv),
[verdict JSON](l2-native-array-roi-DN-ENG41-20261109-verdict.json),
[native baseline](l2-native-array-roi-baseline-DN-ENG41-20261109.json),
[native candidate](l2-native-array-roi-candidate-DN-ENG41-20261109.json),
[Java HD](l2-native-array-roi-java-hd-DN-ENG41-20261109.json).
Raw AndroidX output / AndroidX 原始输出：
[baseline](l2-native-array-roi-baseline-DN-ENG41-20261109-androidx.json),
[candidate](l2-native-array-roi-candidate-DN-ENG41-20261109-androidx.json),
[Java HD](l2-native-array-roi-java-hd-DN-ENG41-20261109-androidx.json).
