# Same Java decoder stage / 同一 Java 解码器阶段

This stage feeds both ROI packers into the same Java decoder; only ROI packing changes. Each backend now has **three independent Gradle instrumentation processes**, each with 50 AndroidX timing samples for all nine cases. The table reports the median of the three process medians. The final column shows the observed per-round relative change (positive means JNI→C++ was faster). Raw AndroidX JSON and test XML are retained locally under the ignored `benchmark/android/build/roi-results/decode-r01-*` through `decode-r03-*` directories. All six runs reported 9 tests, 0 failures, 0 skipped; each case had 50 samples.

本阶段两种 ROI 输出均交给同一个 Java decoder，唯一变化是 ROI packer。每种 backend 现有 **三个独立 Gradle instrumentation 进程**，每个进程的九个 case 各采集 50 个 AndroidX 样本。表中数值为三个进程中位数的中位数；末列是每轮观察到的相对变化（正值表示 JNI→C++ 更快）。AndroidX 原始 JSON 和测试 XML 保存在本机忽略跟踪目录 `benchmark/android/build/roi-results/decode-r01-*` 至 `decode-r03-*`。六次运行均为 9 项测试、0 失败、0 跳过；每个 case 有 50 个样本。

| Case | Java pack + decode (ms) | JNI→C++ pack + Java decode (ms) | Median change | Per-round range |
|---|---:|---:|---:|---:|
| VGA HIT QR | 4.974 | 4.958 | 0.30% | 0.20% to 0.32% |
| VGA HIT 1D | 0.069 | 0.057 | 17.44% | 16.70% to 18.28% |
| VGA MISS | 11.997 | 11.977 | 0.04% | -0.09% to 0.26% |
| HD HIT QR | 9.630 | 9.602 | 0.18% | 0.11% to 0.29% |
| HD HIT 1D | 0.101 | 0.087 | 14.00% | 13.88% to 14.20% |
| HD MISS | 26.634 | 26.608 | 0.08% | -0.08% to 0.10% |
| FHD HIT QR | 16.664 | 16.715 | -0.19% | -0.31% to 0.14% |
| FHD HIT 1D | 0.176 | 0.138 | 20.91% | 12.85% to 21.22% |
| FHD MISS | 44.653 | 44.600 | 0.12% | -0.34% to 0.41% |

The three rounds show a repeatable 1D improvement: 16.70–18.28% at VGA, 13.88–14.20% at HD, and 12.85–21.22% at FHD. QR and miss cases are within roughly 0.5% of Java and do not show a consistent direction. Absolute timings changed substantially between round 1 and rounds 2–3 (about 1.8×), so device state dominates cross-round absolute time. The packer effect is consistent for 1D, but this remains a single-device experiment with only three independent processes per backend; it does not establish a production live-camera or overall scanner speedup. The JNI→C++ path is test-only and is not a production option.

三轮数据中，一维码改善方向稳定：VGA 为 16.70–18.28%，HD 为 13.88–14.20%，FHD 为 12.85–21.22%。QR 和 miss case 与 Java 相差约 0.5% 以内，且方向不一致。第 1 轮与第 2、3 轮的绝对耗时差异显著（约 1.8 倍），说明跨轮设备状态对绝对计时影响很大。一维码 packer 效果方向一致，但这仍是单设备、每种 backend 仅三个独立进程的实验，不能证明生产实拍链路或 scanner 整体提速。JNI→C++ 路径仅用于测试，不是生产选项。

The device was DN-ENG41, Android 14/API 34, arm64-v8a. The instrumentation process was 64-bit. Runs used `-Pl2.throttleWait=0`; AndroidX cooldown waiting was disabled and this remains a thermal-state limitation. No production code, public release, or default backend was changed.

设备为 DN-ENG41、Android 14/API 34、arm64-v8a；instrumentation 进程为 64 位。运行使用 `-Pl2.throttleWait=0`，关闭了 AndroidX 冷却等待，这是温控方面的限制。没有修改生产代码、发布公共版本或更改默认 backend。
