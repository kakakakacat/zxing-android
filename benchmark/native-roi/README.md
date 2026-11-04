# Java/C++ ROI packing experiment / Java/C++ ROI 打包实验

A host-side experiment based on `YPlaneLuminanceSource.copyMatrixTo`: copy a cropped,
strided luminance plane to a packed ROI. This does not change the scanner, shipping native
backend, AARs or public API. Host results are not Android performance claims.

基于 `YPlaneLuminanceSource.copyMatrixTo` 的宿主机实验：将带步幅的亮度平面裁剪并打包为连续 ROI。
不改变扫描库、生产 native 后端、AAR 或公开 API。宿主机结果不代表 Android 性能结论。

## Active experiment / 当前实验

The active runner compares the C++ kernel, Java direct-buffer packing, and Java → JNI → C++
packing. It builds and executes only these three cases. Inputs and expected outputs are
deterministic; native correctness checks run before timing. Each backend uses a fresh
process, and process order rotates between rounds. Java uses HotSpot, not ART.

活跃驱动只比较 C++ kernel、Java direct buffer 打包和 Java → JNI → C++ 打包。输入与期望输出固定，计时前
执行 native 正确性检查。每个 backend 独立进程运行，并在多轮间轮换顺序。Java 使用 HotSpot，而非 ART。

The matrix contains VGA, HD and FHD frames, each with packed rows, 32-byte row padding,
and pixel stride 2 with padding. The centered ROI is 5/8 of the frame dimensions. Work
buffers are allocated outside timing; JNI array access and any VM copy remain inside it.
The pure native diagnostic excludes JNI and must not be subtracted from the Java/JNI row
to estimate an exact JNI cost.

矩阵包含 VGA、HD、FHD 三种帧尺寸，各覆盖紧密行、32 字节行 padding、pixel stride=2 且含 padding。ROI 为帧宽高的
5/8。工作缓冲区在计时外分配；JNI 数组访问及 VM 可能执行的复制计入计时。纯 native 诊断不含 JNI，不能与
Java/JNI 结果相减来推算精确 JNI 开销。

## Historical record / 历史记录

The files `roi.rs` and `reports/2026-11-04-host.*` are retained as historical source and
results from the original three-language experiment. Their contents and hashes are not
rewritten. The active runner, its tests and CI do not build, call or rank that historical
backend. Historical measurements are not Android results.

`roi.rs` 与 `reports/2026-11-04-host.*` 保留原三语言实验的历史源码和结果，内容及哈希不重写。活跃驱动、测试和
CI 不会构建、调用或排名历史 backend。历史测量不代表 Android 结果。

## Run / 运行

Requirements: x86_64 Linux, a C++17 compiler, Python 3 and a complete JDK with `jni.h`.
No Android SDK, NDK, Gradle or other language toolchain is needed. `CXX` selects the C++
compiler; exact versions, flags, source hashes and raw batch samples are recorded.

依赖：x86_64 Linux、C++17 编译器、Python 3 和含 `jni.h` 的完整 JDK。不需要 Android SDK、NDK、Gradle 或其他语言工具链。
通过 `CXX` 指定编译器；结果记录精确版本、参数、源码哈希和原始批次样本。

```sh
python3 -m unittest discover -s benchmark/native-roi -p 'test_*.py'
python3 benchmark/native-roi/run.py --verify-only
python3 benchmark/native-roi/run.py \
  --rounds 3 --samples 21 --warmup-ms 500 --batch-ms 10 \
  --out benchmark/native-roi/build/result.json
```

Outputs are local under the ignored `build/` directory. CI runs correctness only; shared
runner timings are not performance evidence.

结果保存在忽略跟踪的本地 `build/` 目录。CI 仅运行正确性检查；共享 runner 的计时不作为性能证据。

The existing native-direct Y-plane path can bypass ROI packing. A faster pack kernel may
therefore have no effect on that path. Decoder latency, camera delivery, recognition,
power and ARM performance require separate Android/device experiments.

现有 native-direct Y 平面路径可以绕过 ROI 打包，因此打包 kernel 变快不一定影响该路径。解码、相机送帧、识别、功耗及
ARM 性能需要独立的 Android/真机实验。
