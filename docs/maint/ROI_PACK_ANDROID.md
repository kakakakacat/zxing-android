# Android Java/C++ ROI experiment / Android Java/C++ ROI 实验

This test-only experiment is separate from the production scanner and its L2 result schema.
`RoiPackingBenchmark` measures one preallocated Java `byte[]` ROI copy; the candidate call
includes Java → JNI → C++ and JNI array access. `RoiPackingDecodeBenchmark` feeds either
packer output to the same Java decoder, so that comparison holds the decoder constant.
Neither test changes a production option or backend.

此实验仅存在于 instrumentation APK，与生产 scanner 及 L2 结果 schema 分离。`RoiPackingBenchmark` 测量写入复用
Java `byte[]` 的 ROI 复制；候选调用包含 Java → JNI → C++ 和 JNI 数组访问。`RoiPackingDecodeBenchmark` 将两种
packer 输出交给同一个 Java decoder，保持解码器一致。实验不改生产选项或默认 backend。

The source is a direct buffer whose coordinates start at buffer index zero, matching
`YPlaneLuminanceSource`; `position` and `limit` are preserved, and no byte at or beyond
`limit` is read. Frame preparation, fixture hashes, expected outputs and JNI array-copy
probing happen outside the timed block. The source and destination are allocated once.

源为 DirectByteBuffer，坐标从 buffer 索引 0 开始，与 `YPlaneLuminanceSource` 一致；调用保持 `position` 和
`limit` 不变，且不读取 `limit` 及之后的数据。帧准备、fixture 哈希、期望输出及 JNI 数组复制探测均在计时区外，源和目标只分配一次。

## Device commands / 设备命令

Select exactly one device with `ANDROID_SERIAL` for every command. Start with the
correctness class. The performance class uses AndroidX adaptive warmup and 50 measured
samples per case. Run each backend in a fresh Gradle instrumentation process and preserve
the AndroidX JSON and test logs before starting the next run.

每条命令都通过 `ANDROID_SERIAL` 指定唯一设备。先运行正确性用例。性能用例使用 AndroidX 自适应预热，每 case 采集 50 个样本。
每个 backend 在独立 Gradle instrumentation 进程中运行，并在下一轮开始前保存 AndroidX JSON 和测试日志。

```sh
ANDROID_SERIAL='<device>' ./gradlew :benchmark-android:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.kakakakacat.zxing.benchmark.RoiPackingCorrectnessTest

ANDROID_SERIAL='<device>' ./gradlew :benchmark-android:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.kakakakacat.zxing.benchmark.RoiPackingBenchmark \
  -Proi.backend=java -Pl2.throttleWait=0

ANDROID_SERIAL='<device>' ./gradlew :benchmark-android:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.kakakakacat.zxing.benchmark.RoiPackingBenchmark \
  -Proi.backend=jni_cpp -Pl2.throttleWait=0

ANDROID_SERIAL='<device>' ./gradlew :benchmark-android:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.kakakakacat.zxing.benchmark.RoiPackingDecodeBenchmark \
  -Proi.decodeBackend=java -Pl2.throttleWait=0

ANDROID_SERIAL='<device>' ./gradlew :benchmark-android:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.kakakakacat.zxing.benchmark.RoiPackingDecodeBenchmark \
  -Proi.decodeBackend=jni_cpp -Pl2.throttleWait=0
```

The argument `l2.throttleWait=0` disables the AndroidX cooldown wait and is recorded as a
warning; it does not suppress thermal detection or alter thresholds. The generated native
library is added only to the `androidTest` source set. Confirm with the scanner AAR
verification before release work.

`l2.throttleWait=0` 关闭 AndroidX 冷却等待并记录为告警，不会关闭温控检测或修改温控阈值。生成的 native 库只加入
`androidTest` source set；发布前应检查 scanner AAR 未包含该库。

`benchmark/tools/roi_android_report.py` consumes one saved JSON per independent process
under `<round>-java/` and `<round>-jni_cpp/`. It rejects incomplete matrices and fewer than
50 measured samples. Its ROI-specific rule bootstraps process medians and requires the
95% interval to exclude zero plus an effect larger than the maximum observed Java A/A
deviation. This is not the L2 end-to-end gate. Keep all run JSON and raw test output local;
they include the device fingerprint and serial.

`benchmark/tools/roi_android_report.py` 读取每个独立进程保存的 JSON，目录名为 `<轮次>-java/` 和 `<轮次>-jni_cpp/`。
它会拒绝不完整矩阵或少于 50 个样本的 case。ROI 独立判定规则以进程中位数做 bootstrap，要求 95% 区间排除零，且效果超过
观测到的 Java A/A 最大偏差；这不是 L2 完整链路门槛。所有原始 JSON 和测试输出保留在本机目录，因为其中含设备 fingerprint 与 serial。
