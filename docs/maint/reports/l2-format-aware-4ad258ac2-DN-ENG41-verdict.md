# L2 comparison

**Verdict: fail**

This is a 27-case L2 comparison of Java and `FORMAT_AWARE` on DN-ENG41 (Android 14/API 34, arm64-v8a), one fresh instrumentation process per backend and 50 measured samples per case. The CPU clocks were locked. Both runs carry the same standing warnings: the AndroidX cooldown wait was disabled, and this device has no sustained performance mode. The memory bus could not be locked and vendor thermal services could not be stopped.

The result is **15 pass, 3 fail, 9 inconclusive**. All three failures are default all-format 1D hits. `FORMAT_AWARE` sends a request with no explicit format list through AUTO, so those cases still use native and retain the regression: +481.9% at VGA, +970.2% at HD and +1883.8% at FHD versus Java. For the explicitly UPC-A-only 1D hits, it selects Java; those timings differ from the Java baseline by −2.4% or less, below the measured floor for these sub-100 µs cases. This confirms the opt-in route avoids the native penalty for a constrained 1D request, while leaving the default all-format 1D regression unresolved. No production default was changed.

The converted Java result is [l2-format-aware-4ad258ac2-DN-ENG41-java.json](l2-format-aware-4ad258ac2-DN-ENG41-java.json), the candidate is [l2-format-aware-4ad258ac2-DN-ENG41-format-aware.json](l2-format-aware-4ad258ac2-DN-ENG41-format-aware.json), and per-case data is in [l2-format-aware-4ad258ac2-DN-ENG41-cases.csv](l2-format-aware-4ad258ac2-DN-ENG41-cases.csv).

| | |
| --- | --- |
| Baseline | `java-locked-full-DN-ENG41-20261105` (4ad258ac2) |
| Candidate | `format-aware-locked-full-DN-ENG41-20261105` (4ad258ac2) |
| Device | DN-ENG41 |
| Configuration | mixed |
| Decoder backend | **java → format-aware** |
| Effect floor | 3% at or above 100 µs, 10% below |

Read through under section 10.6: NO_SUSTAINED_PERFORMANCE_MODE, THROTTLE_WAIT_DISABLED. Both runs carry the same standing warning on a device whose effect floor was measured under it, so it is a shared condition rather than a difference between them.

| Case | Baseline p50 | Candidate p50 | Δ | Alloc Δ | Verdict | Why |
| --- | --- | --- | --- | --- | --- | --- |
| `endToEnd[FHD_HIT_1D_DEFAULT]` | 95.0 µs | 1.88 ms | +1883.8% | -13.5% | fail | 1883.8% slower, intervals disjoint |
| `endToEnd[FHD_HIT_1D_ONED_ONLY_UPCA]` | 92.8 µs | 92.2 µs | -0.7% | +0.0% | inconclusive | the medians differ by -0.7%, below the 10% floor measured for cases of this size: not resolvable on this device |
| `endToEnd[FHD_HIT_1D_QR_ONLY]` | 17.59 ms | 6.58 ms | -62.6% | -95.9% | pass | -62.6% faster, intervals disjoint |
| `endToEnd[FHD_HIT_QR_DEFAULT]` | 31.51 ms | 8.99 ms | -71.5% | -97.5% | pass | -71.5% faster, intervals disjoint |
| `endToEnd[FHD_HIT_QR_ONED_ONLY_UPCA]` | 1.74 ms | 1.74 ms | +0.2% | +0.0% | inconclusive | the medians differ by 0.2%, below the 3% floor measured for cases of this size: not resolvable on this device |
| `endToEnd[FHD_HIT_QR_QR_ONLY]` | 18.98 ms | 8.04 ms | -57.6% | -89.0% | pass | -57.6% faster, intervals disjoint |
| `endToEnd[FHD_MISS_DEFAULT]` | 81.42 ms | 18.67 ms | -77.1% | -99.9% | pass | -77.1% faster, intervals disjoint |
| `endToEnd[FHD_MISS_ONED_ONLY_UPCA]` | 1.20 ms | 1.21 ms | +0.4% | +0.0% | inconclusive | the medians differ by 0.4%, below the 3% floor measured for cases of this size: not resolvable on this device |
| `endToEnd[FHD_MISS_QR_ONLY]` | 23.78 ms | 9.25 ms | -61.1% | -97.3% | pass | -61.1% faster, intervals disjoint |
| `endToEnd[HD_HIT_1D_DEFAULT]` | 81.2 µs | 869.3 µs | +970.2% | -13.6% | fail | 970.2% slower, intervals disjoint |
| `endToEnd[HD_HIT_1D_ONED_ONLY_UPCA]` | 80.0 µs | 79.4 µs | -0.7% | +0.0% | inconclusive | the medians differ by -0.7%, below the 10% floor measured for cases of this size: not resolvable on this device |
| `endToEnd[HD_HIT_1D_QR_ONLY]` | 8.26 ms | 2.68 ms | -67.5% | -96.0% | pass | -67.5% faster, intervals disjoint |
| `endToEnd[HD_HIT_QR_DEFAULT]` | 18.07 ms | 4.14 ms | -77.1% | -96.7% | pass | -77.1% faster, intervals disjoint |
| `endToEnd[HD_HIT_QR_ONED_ONLY_UPCA]` | 1.31 ms | 1.31 ms | +0.1% | -0.0% | inconclusive | the medians differ by 0.1%, below the 3% floor measured for cases of this size: not resolvable on this device |
| `endToEnd[HD_HIT_QR_QR_ONLY]` | 9.02 ms | 3.60 ms | -60.2% | -84.8% | pass | -60.2% faster, intervals disjoint |
| `endToEnd[HD_MISS_DEFAULT]` | 48.54 ms | 10.44 ms | -78.5% | -99.9% | pass | -78.5% faster, intervals disjoint |
| `endToEnd[HD_MISS_ONED_ONLY_UPCA]` | 1.16 ms | 1.16 ms | +0.2% | -0.0% | inconclusive | the medians differ by 0.2%, below the 3% floor measured for cases of this size: not resolvable on this device |
| `endToEnd[HD_MISS_QR_ONLY]` | 9.14 ms | 4.03 ms | -55.9% | -93.5% | pass | -55.9% faster, intervals disjoint |
| `endToEnd[VGA_HIT_1D_DEFAULT]` | 67.9 µs | 395.1 µs | +481.9% | -13.7% | fail | 481.9% slower, intervals disjoint |
| `endToEnd[VGA_HIT_1D_ONED_ONLY_UPCA]` | 67.9 µs | 66.3 µs | -2.4% | -0.0% | inconclusive | the medians differ by -2.4%, below the 10% floor measured for cases of this size: not resolvable on this device |
| `endToEnd[VGA_HIT_1D_QR_ONLY]` | 2.37 ms | 869.1 µs | -63.3% | -92.0% | pass | -63.3% faster, intervals disjoint |
| `endToEnd[VGA_HIT_QR_DEFAULT]` | 9.18 ms | 1.63 ms | -82.2% | -95.7% | pass | -82.2% faster, intervals disjoint |
| `endToEnd[VGA_HIT_QR_ONED_ONLY_UPCA]` | 893.3 µs | 896.4 µs | +0.3% | -0.0% | inconclusive | the medians differ by 0.3%, below the 3% floor measured for cases of this size: not resolvable on this device |
| `endToEnd[VGA_HIT_QR_QR_ONLY]` | 2.89 ms | 1.30 ms | -54.8% | -79.7% | pass | -54.8% faster, intervals disjoint |
| `endToEnd[VGA_MISS_DEFAULT]` | 21.79 ms | 4.13 ms | -81.1% | -99.8% | pass | -81.1% faster, intervals disjoint |
| `endToEnd[VGA_MISS_ONED_ONLY_UPCA]` | 877.8 µs | 882.1 µs | +0.5% | -0.0% | inconclusive | the medians differ by 0.5%, below the 3% floor measured for cases of this size: not resolvable on this device |
| `endToEnd[VGA_MISS_QR_ONLY]` | 3.15 ms | 1.39 ms | -55.7% | -92.0% | pass | -55.7% faster, intervals disjoint |
