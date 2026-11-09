# L2 comparison

**Verdict: fail**

| | |
| --- | --- |
| Baseline | `java-full-DN-ENG41-20261109c` (099d1153a) |
| Candidate | `native-full-DN-ENG41-20261109c` (099d1153a) |
| Device | DN-ENG41 |
| Configuration | mixed |
| Decoder backend | **java → native** |
| Effect floor | 3% at or above 100 µs, 10% below |

Read through under section 10.6: NO_SUSTAINED_PERFORMANCE_MODE, THROTTLE_WAIT_DISABLED. Both runs carry the same standing warning on a device whose effect floor was measured under it, so it is a shared condition rather than a difference between them.

| Case | Baseline p50 | Candidate p50 | Δ | Alloc Δ | Verdict | Why |
| --- | --- | --- | --- | --- | --- | --- |
| `endToEnd[FHD_HIT_1D_DEFAULT]` | 89.1 µs | 96.0 µs | +7.7% | -10.3% | inconclusive | the medians differ by 7.7%, below the 10% floor measured for cases of this size: not resolvable on this device |
| `endToEnd[FHD_HIT_1D_ONED_ONLY_UPCA]` | 89.1 µs | 85.0 µs | -4.6% | -10.3% | inconclusive | the medians differ by -4.6%, below the 10% floor measured for cases of this size: not resolvable on this device |
| `endToEnd[FHD_HIT_1D_QR_ONLY]` | 17.62 ms | 4.67 ms | -73.5% | -95.0% | pass | -73.5% faster, intervals disjoint |
| `endToEnd[FHD_HIT_QR_DEFAULT]` | 31.70 ms | 6.73 ms | -78.8% | -97.5% | pass | -78.8% faster, intervals disjoint |
| `endToEnd[FHD_HIT_QR_ONED_ONLY_UPCA]` | 1.74 ms | 189.2 µs | -89.1% | -99.3% | pass | -89.1% faster, intervals disjoint |
| `endToEnd[FHD_HIT_QR_QR_ONLY]` | 18.80 ms | 6.00 ms | -68.1% | -88.7% | pass | -68.1% faster, intervals disjoint |
| `endToEnd[FHD_MISS_DEFAULT]` | 81.42 ms | 16.84 ms | -79.3% | -99.8% | pass | -79.3% faster, intervals disjoint |
| `endToEnd[FHD_MISS_ONED_ONLY_UPCA]` | 1.20 ms | 262.1 µs | -78.2% | -99.1% | pass | -78.2% faster, intervals disjoint |
| `endToEnd[FHD_MISS_QR_ONLY]` | 23.82 ms | 7.28 ms | -69.4% | -96.5% | pass | -69.4% faster, intervals disjoint |
| `endToEnd[HD_HIT_1D_DEFAULT]` | 75.8 µs | 91.1 µs | +20.1% | -10.3% | fail | 20.1% slower, intervals disjoint |
| `endToEnd[HD_HIT_1D_ONED_ONLY_UPCA]` | 73.8 µs | 80.1 µs | +8.5% | -10.3% | inconclusive | the medians differ by 8.5%, below the 10% floor measured for cases of this size: not resolvable on this device |
| `endToEnd[HD_HIT_1D_QR_ONLY]` | 8.27 ms | 1.90 ms | -77.0% | -94.8% | pass | -77.0% faster, intervals disjoint |
| `endToEnd[HD_HIT_QR_DEFAULT]` | 18.11 ms | 3.14 ms | -82.6% | -96.6% | pass | -82.6% faster, intervals disjoint |
| `endToEnd[HD_HIT_QR_ONED_ONLY_UPCA]` | 1.31 ms | 133.5 µs | -89.8% | -98.8% | pass | -89.8% faster, intervals disjoint |
| `endToEnd[HD_HIT_QR_QR_ONLY]` | 9.02 ms | 2.69 ms | -70.2% | -84.3% | pass | -70.2% faster, intervals disjoint |
| `endToEnd[HD_MISS_DEFAULT]` | 48.52 ms | 9.54 ms | -80.3% | -99.8% | pass | -80.3% faster, intervals disjoint |
| `endToEnd[HD_MISS_ONED_ONLY_UPCA]` | 1.15 ms | 181.2 µs | -84.3% | -99.3% | pass | -84.3% faster, intervals disjoint |
| `endToEnd[HD_MISS_QR_ONLY]` | 9.14 ms | 3.20 ms | -65.0% | -91.8% | pass | -65.0% faster, intervals disjoint |
| `endToEnd[VGA_HIT_1D_DEFAULT]` | 61.7 µs | 87.4 µs | +41.5% | -10.3% | fail | 41.5% slower, intervals disjoint |
| `endToEnd[VGA_HIT_1D_ONED_ONLY_UPCA]` | 61.4 µs | 75.9 µs | +23.7% | -10.3% | fail | 23.7% slower, intervals disjoint |
| `endToEnd[VGA_HIT_1D_QR_ONLY]` | 2.37 ms | 600.5 µs | -74.6% | -89.5% | pass | -74.6% faster, intervals disjoint |
| `endToEnd[VGA_HIT_QR_DEFAULT]` | 9.23 ms | 1.24 ms | -86.6% | -95.5% | pass | -86.6% faster, intervals disjoint |
| `endToEnd[VGA_HIT_QR_ONED_ONLY_UPCA]` | 890.5 µs | 76.6 µs | -91.4% | -98.2% | pass | -91.4% faster, intervals disjoint |
| `endToEnd[VGA_HIT_QR_QR_ONLY]` | 2.94 ms | 976.0 µs | -66.8% | -78.8% | pass | -66.8% faster, intervals disjoint |
| `endToEnd[VGA_MISS_DEFAULT]` | 21.79 ms | 3.80 ms | -82.6% | -99.7% | pass | -82.6% faster, intervals disjoint |
| `endToEnd[VGA_MISS_ONED_ONLY_UPCA]` | 873.8 µs | 100.2 µs | -88.5% | -99.0% | pass | -88.5% faster, intervals disjoint |
| `endToEnd[VGA_MISS_QR_ONLY]` | 3.13 ms | 1.11 ms | -64.4% | -89.5% | pass | -64.4% faster, intervals disjoint |
