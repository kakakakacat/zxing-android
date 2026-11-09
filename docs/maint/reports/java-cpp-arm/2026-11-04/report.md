# Android ROI packing / Android ROI 打包

Commit: `0f08a40c8e6d46d894677ddad6ee4043710bc344` · Dataset SHA-256: `a33845cac1d8a5ed3cd78d91a530028edad56897c4c8bbcd2389caad2c4f8004` · Device: DN-ENG41, API 34, arm64-v8a.
Base: `905a7dae3b1d30467b3f3aa579993352dcc4dbf8` · `third_party/zxing-cpp`: `287c85df6f961c8efbfb5ffd736cd9457b8b890e` (v3.1.1) · 4 Java and 3 JNI→C++ independent processes.
The measurements cover packing only. Each backend was run in fresh processes, with 50 AndroidX measured samples per case. Confidence intervals bootstrap whole-process medians. Raw JSON files remain in the local ignored run directory.
測量範圍僅為 ROI 打包。每個 backend 使用獨立進程，每 case 每進程 50 個 AndroidX 樣本；置信區間以進程中位数为聚类单位 bootstrap。原始 JSON 保留在本机忽略跟踪目录。

## Device and verification / 设备与验证

The device reports Android 14/API 34 and arm64-v8a; the instrumentation process was confirmed 64-bit. CPU clocks were locked, but the device refused the memory-bus lock and stopping vendor thermal services. Thermal status was SEVERE and the protocol disabled cooldown waits (`-Pl2.throttleWait=0`); conclusions apply to this device state. The JNI `GetByteArrayElements` copy probe reported `isCopy=false` on ART.

设备为 Android 14/API 34、arm64-v8a，instrumentation 进程确认为 64 位。CPU 时钟已锁定，但设备拒绝锁定内存总线频率及停止厂商温控服务。温控状态为 SEVERE，协议关闭了冷却等待（`-Pl2.throttleWait=0`）；结论仅适用于此设备状态。ART 上 JNI `GetByteArrayElements` 的复制探测结果为 `isCopy=false`。

Host protocol tests (4), report tests (2), Android unit tests, scanner release AAR assembly, scanner AAR native payload check, and Android ROI correctness instrumentation passed. The ROI JNI library is test-only and is absent from the scanner AAR. The active host runner now covers Java/C++ only; its Linux/x86_64 execution was not run on this macOS host. No public release or default backend change was made.

宿主协议测试（4 项）、报告测试（2 项）、Android 单元测试、scanner release AAR 构建、AAR native payload 检查及 Android ROI correctness instrumentation 均通过。ROI JNI 库仅用于测试，未进入 scanner AAR。active host runner 现仅覆盖 Java/C++；该 Linux/x86_64 runner 未在本 macOS 主机执行。没有发布公共版本，也未更改默认 backend。

Warnings: AndroidX cooldown wait disabled; thermal status remained severe, Memory bus frequency lock and vendor thermal service stop were refused by device. The ROI decision rule is separate from L2: the bootstrap C++−Java interval must exclude zero, and the median effect must exceed the largest observed Java A/A relative deviation.
告警：AndroidX cooldown wait disabled; thermal status remained severe, Memory bus frequency lock and vendor thermal service stop were refused by device。ROI 判定独立于 L2：C++−Java bootstrap 区间须排除零，且中位改善须超过 Java A/A 最大相对偏差。

| Case | Java ns/copy | JNI→C++ ns/copy | Effect | A/A floor | 95% delta CI ns | Result |
|---|---:|---:|---:|---:|---:|---|
| VGA_packed | 81566 | 50003 | 38.7% | 0.3% | [-31798, -31503] | cpp_faster |
| VGA_padded | 81552 | 49763 | 39.0% | 0.1% | [-31873, -31766] | cpp_faster |
| VGA_pixel2 | 3999632 | 233398 | 94.2% | 6.2% | [-4013430, -3765764] | cpp_faster |
| HD_packed | 156545 | 92929 | 40.6% | 0.1% | [-63846, -63469] | cpp_faster |
| HD_padded | 156269 | 93303 | 40.3% | 0.1% | [-63038, -62826] | cpp_faster |
| HD_pixel2 | 11984102 | 715515 | 94.0% | 0.4% | [-11308038, -11215744] | cpp_faster |
| FHD_packed | 398056 | 313139 | 21.3% | 1.2% | [-92531, -76585] | cpp_faster |
| FHD_padded | 412720 | 303707 | 26.4% | 3.0% | [-121647, -92251] | cpp_faster |
| FHD_pixel2 | 27084939 | 1717010 | 93.7% | 6.6% | [-27152704, -25340300] | cpp_faster |

The result does not establish full decode or live camera gains. The production native-direct path can skip this pack operation. Decoder and camera stages are reported separately. The repeated same-Java-decoder stage is in [decode-stage.md](decode-stage.md).
本结果不代表完整解码或实拍链路收益。生产 native-direct 路径可跳过此打包操作；解码与相机阶段须单独报告。同一 Java decoder 的多进程结果见 [decode-stage.md](decode-stage.md)。
