# L2 comparison

**Verdict: fail**

| | |
| --- | --- |
| Baseline | `ab-java-DN-ENG41-20261031` (602d368c4) |
| Candidate | `ab-native-DN-ENG41-20261031` (602d368c4) |
| Device | DN-ENG41 |
| Configuration | mixed |
| Decoder backend | **java → native** |
| Effect floor | 3% at or above 100 µs, 10% below |

Read through under section 10.6: NO_SUSTAINED_PERFORMANCE_MODE, THROTTLE_WAIT_DISABLED. Both runs carry the same standing warning on a device whose effect floor was measured under it, so it is a shared condition rather than a difference between them.

| Case | Baseline p50 | Candidate p50 | Δ | Alloc Δ | Verdict | Why |
| --- | --- | --- | --- | --- | --- | --- |
| `endToEnd[FHD_HIT_1D_DEFAULT]` | 91.0 µs | 1.81 ms | +1889.4% | -65.2% | fail | 1889.4% slower, intervals disjoint |
| `endToEnd[FHD_HIT_1D_ONED_ONLY_UPCA]` | 91.0 µs | 1.80 ms | +1879.5% | -65.2% | fail | 1879.5% slower, intervals disjoint |
| `endToEnd[FHD_HIT_1D_QR_ONLY]` | 17.62 ms | 6.53 ms | -63.0% | -95.9% | pass | -63.0% faster, intervals disjoint |
| `endToEnd[FHD_HIT_QR_DEFAULT]` | 31.59 ms | 8.87 ms | -71.9% | -99.0% | pass | -71.9% faster, intervals disjoint |
| `endToEnd[FHD_HIT_QR_ONED_ONLY_UPCA]` | 1.74 ms | 1.81 ms | +4.2% | -99.4% | fail | 4.2% slower, intervals disjoint |
| `endToEnd[FHD_HIT_QR_QR_ONLY]` | 18.75 ms | 7.97 ms | -57.5% | -95.3% | pass | -57.5% faster, intervals disjoint |
| `endToEnd[FHD_MISS_DEFAULT]` | 81.33 ms | 18.68 ms | -77.0% | -99.9% | pass | -77.0% faster, intervals disjoint |
| `endToEnd[FHD_MISS_ONED_ONLY_UPCA]` | 1.20 ms | 1.91 ms | +59.4% | -99.3% | fail | 59.4% slower, intervals disjoint |
| `endToEnd[FHD_MISS_QR_ONLY]` | 23.81 ms | 9.25 ms | -61.2% | -97.1% | pass | -61.2% faster, intervals disjoint |
| `endToEnd[HD_HIT_1D_DEFAULT]` | 76.8 µs | 835.8 µs | +988.0% | -65.4% | fail | 988.0% slower, intervals disjoint |
| `endToEnd[HD_HIT_1D_ONED_ONLY_UPCA]` | 79.5 µs | 816.8 µs | +927.2% | -65.4% | fail | 927.2% slower, intervals disjoint |
| `endToEnd[HD_HIT_1D_QR_ONLY]` | 8.19 ms | 2.66 ms | -67.5% | -95.9% | pass | -67.5% faster, intervals disjoint |
| `endToEnd[HD_HIT_QR_DEFAULT]` | 18.04 ms | 4.15 ms | -77.0% | -98.6% | pass | -77.0% faster, intervals disjoint |
| `endToEnd[HD_HIT_QR_ONED_ONLY_UPCA]` | 1.31 ms | 844.1 µs | -35.3% | -99.1% | pass | -35.3% faster, intervals disjoint |
| `endToEnd[HD_HIT_QR_QR_ONLY]` | 9.01 ms | 3.56 ms | -60.6% | -93.6% | pass | -60.6% faster, intervals disjoint |
| `endToEnd[HD_MISS_DEFAULT]` | 48.43 ms | 10.46 ms | -78.4% | -99.9% | pass | -78.4% faster, intervals disjoint |
| `endToEnd[HD_MISS_ONED_ONLY_UPCA]` | 1.15 ms | 894.6 µs | -22.3% | -99.5% | pass | -22.3% faster, intervals disjoint |
| `endToEnd[HD_MISS_QR_ONLY]` | 9.18 ms | 4.03 ms | -56.1% | -93.4% | pass | -56.1% faster, intervals disjoint |
| `endToEnd[VGA_HIT_1D_DEFAULT]` | 65.9 µs | 372.4 µs | +464.8% | -65.4% | fail | 464.8% slower, intervals disjoint |
| `endToEnd[VGA_HIT_1D_ONED_ONLY_UPCA]` | 65.7 µs | 348.8 µs | +430.5% | -65.4% | fail | 430.5% slower, intervals disjoint |
| `endToEnd[VGA_HIT_1D_QR_ONLY]` | 2.36 ms | 869.9 µs | -63.1% | -92.0% | pass | -63.1% faster, intervals disjoint |
| `endToEnd[VGA_HIT_QR_DEFAULT]` | 9.18 ms | 1.61 ms | -82.5% | -98.2% | pass | -82.5% faster, intervals disjoint |
| `endToEnd[VGA_HIT_QR_ONED_ONLY_UPCA]` | 889.4 µs | 345.0 µs | -61.2% | -98.6% | pass | -61.2% faster, intervals disjoint |
| `endToEnd[VGA_HIT_QR_QR_ONLY]` | 2.90 ms | 1.27 ms | -56.3% | -91.5% | pass | -56.3% faster, intervals disjoint |
| `endToEnd[VGA_MISS_DEFAULT]` | 21.73 ms | 4.14 ms | -81.0% | -99.8% | pass | -81.0% faster, intervals disjoint |
| `endToEnd[VGA_MISS_ONED_ONLY_UPCA]` | 871.1 µs | 369.0 µs | -57.6% | -99.2% | pass | -57.6% faster, intervals disjoint |
| `endToEnd[VGA_MISS_QR_ONLY]` | 3.07 ms | 1.39 ms | -54.6% | -92.0% | pass | -54.6% faster, intervals disjoint |
