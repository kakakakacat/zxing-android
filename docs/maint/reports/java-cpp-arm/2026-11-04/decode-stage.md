# Same Java decoder stage / 同一 Java 解码器阶段

This stage uses the same Java decoder for both inputs. The only changed step is ROI packing: Java packer versus JNI→C++ packer. Each case has 50 AndroidX timing samples, but only **one independent process per backend** was collected. Treat this as a first-run observation, not a significance result.

本阶段两种输入都交给同一个 Java 解码器；唯一变化是 ROI 打包器（Java 或 JNI→C++）。每个 case 有 50 个 AndroidX 计时样本，但每种 backend 仅采集了 **一个独立进程**，因此只作首轮观察，不作为显著性结论。

| Case | Java pack + Java decode (ns) | JNI→C++ pack + Java decode (ns) | Observed change |
|---|---:|---:|---:|
| VGA hit QR | 8,911,240 | 8,893,698 | −0.20% |
| VGA hit 1D | 118,192 | 96,592 | −18.27% |
| VGA miss | 21,494,212 | 21,484,655 | −0.04% |
| HD hit QR | 17,249,141 | 17,230,927 | −0.11% |
| HD hit 1D | 178,303 | 153,547 | −13.89% |
| HD miss | 47,769,062 | 47,730,742 | −0.08% |
| FHD hit QR | 29,987,005 | 29,946,389 | −0.14% |
| FHD hit 1D | 292,993 | 255,335 | −12.85% |
| FHD miss | 80,088,802 | 79,994,270 | −0.12% |

The QR and miss cases show that total decode time dominates ROI packing. The 1D rows show a larger first-run difference, but one process per side cannot establish repeatability. A production native-direct Y-plane decoder may bypass the packing operation entirely; this experiment does not establish a production decode speedup.

QR 與 miss case 表明總解碼時間主要由解碼本身決定。1D case 的首輪差異較大，但兩側各一個進程不足以確認可重複性。生產環境的 native-direct Y-plane decoder 可能完全跳過此打包步驟；此實驗不能證明生產解碼提速。

Raw AndroidX JSON and test XML are retained locally under the ignored `benchmark/android/build/roi-results/decode-r01-*` directories. The tracked ROI pack report contains the repeated-process analysis. Neither stage contains a live camera test.

AndroidX 原始 JSON 與測試 XML 保存在本機忽略跟踪的 `benchmark/android/build/roi-results/decode-r01-*` 目錄。已跟踪的 ROI 打包報告包含多進程統計分析。兩階段均未進行實時相機測試。
