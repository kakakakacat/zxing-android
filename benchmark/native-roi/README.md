# ROI packing language experiment / ROI 打包语言对照实验

A deliberately narrow experiment based on `YPlaneLuminanceSource.copyMatrixTo`: copy a
cropped, strided luminance plane to a packed ROI. This is not a barcode decoder rewrite.
The scanner, native default backend, AARs and public API do not depend on this directory.
基于 `YPlaneLuminanceSource.copyMatrixTo` 的小范围实验：将带步幅的亮度平面裁剪并打包为连续 ROI。
本实验不重写条码解码器；扫描库、默认 native 后端、AAR 及公开 API 均不依赖本目录。

Recorded exploratory run / 已记录的预实验：
[2026-11-04 summary / 汇总](reports/2026-11-04-host.md) ·
[raw samples / 原始样本](reports/2026-11-04-host.json) ·
[interpretation / 解读与下一步](reports/2026-11-04-interpretation.md)

## Questions / 问题

1. How do these equivalent C++ and Rust kernels perform behind the same C ABI driver?
2. Does the benefit, if any, survive a Java call through the same JNI bridge and byte-array API?
3. How do they compare with the existing Java direct-buffer bulk-row/scalar-stride approach?

1. 相同 C ABI 驱动下，等价的 C++ 与 Rust 打包函数表现如何？
2. 如果函数有收益，经过相同 JNI 桥和 Java 字节数组接口后是否仍然存在？
3. 与现有 Java direct buffer 按行批量读取/跨像素步幅读取相比如何？

## Protocol / 方法

- Three frame resolutions, each with packed rows, padded rows and pixel stride 2: nine cases.
  The centered ROI is 5/8 of frame width/height, a test choice rather than a live-camera crop claim.
- Fixed deterministic pixels and expected ROI hashes. C++ and Rust validate all geometry before
  writing. Source/destination allocations must be valid and nonoverlapping, as declared in `roi.h`.
- C++ uses restrict pointers, memcpy for stride 1 and a scalar indexing loop otherwise. Rust
  constructs slices at its unsafe FFI boundary, uses copy_from_slice and safe indexing inside.
  These implementations need not compile identically; this is not proof of a language-wide ranking.
- Native calls use the same dynamically resolved C ABI function pointer. Java uses a separate
  process for each backend; both native backends use the identical C++ JNI bridge. Its ordinary
  GetByteArrayElements/ReleaseByteArrayElements access may allocate and copy. No critical pinning.
- Work buffers are allocated outside timing. JNI VM array access and Java duplicate cursors stay
  inside timing. Fixture loading, correctness comparisons and process startup stay outside timing.
- Java's pack method is not inlined into the batch loop, preventing whole-batch copy elimination;
  normal optimization inside the method remains enabled. No such directive is applied to scanner code.
- Warmup, calibrated batches, multiple fresh processes, rotated backend process order. Raw batch
  samples and per-process medians are retained. Summary = median of process medians, not pooled samples.
- C++/Rust native timings exclude JNI; the Java/JNI timings include it. Never subtract cross-table
  timings to claim an exact JNI cost because the drivers, destinations and runtimes differ.

- 三种帧分辨率，各测紧密行、padding 行、像素步幅 2，共九种；中心 ROI 为帧宽高的 5/8，属于实验选择。
- 固定像素与 ROI 哈希，写入前校验几何参数；源、目标必须是声明长度内有效且不重叠的真实分配。
- C++ 使用 restrict 指针、memcpy/索引循环；Rust 在 unsafe FFI 边界建立切片，内部使用批量复制和安全索引。
  两者未必生成同样的机器码，因此只能比较具体实现，不能据此排名语言。
- 原生调用共用相同 C ABI 函数指针驱动；Java 各 backend 独立进程，两种 native 共用一个 JNI 桥，保留可能复制
  或分配的普通数组访问；不使用 critical pinning。
- 工作缓冲区在计时外分配；JNI 数组访问及 Java 游标复制计时。加载、正确性比较及进程启动不计时。
- 禁止 Java 打包方法内联到批循环，避免整个批次被合并消除；方法内部正常优化，不修改生产 scanner 的编译策略。
- 预热、自动批大小、多个独立进程、轮换 backend 顺序；保存原始批样本与进程中位数，汇总取进程中位数的中位数。
- 原生表不含 JNI，Java/JNI 表包含 JNI；驱动、目标分配和运行时不同，不能跨表相减声称精确 JNI 开销。

## Run / 运行

First driver: **x86_64 Linux**, C++17 compiler, Rust with standard library and a complete JDK 17.
No Cargo dependencies, Android SDK, NDK or Gradle are required. Clang 17 + Rust 1.75 are the
reference toolchain family; exact versions and flags are recorded in each result. `CXX`/`RUSTC`
select executables. A full JDK provides `jni.h` under `$JAVA_HOME/include`.
首版驱动限定 **x86_64 Linux**，需要 C++17 编译器、带标准库的 Rust、完整 JDK 17，不依赖 Cargo 包、
Android SDK、NDK 或 Gradle。参考工具链为 Clang 17/Rust 1.75，实际版本及参数会记录；可用 CXX/RUSTC
指定可执行文件，完整 JDK 的 JAVA_HOME/include 应包含 jni.h。

```sh
# Verify bytes, guards, source immutability and rejected geometry; discard timing.
# 校验输出、哨兵、源数据不变及非法几何参数，丢弃计时数据。
CXX=clang++-17 python3 benchmark/native-roi/run.py --verify-only

# Exploratory timing. Optionally --cpu <allowed-logical-cpu>; affinity is not clock locking.
# 探索计时，可选 --cpu <允许使用的逻辑核>；绑核不等于锁频。
CXX=clang++-17 python3 benchmark/native-roi/run.py \
  --rounds 3 --samples 21 --warmup-ms 500 --batch-ms 10 \
  --out benchmark/native-roi/build/result.json

python3 -m unittest discover -s benchmark/native-roi -p 'test_*.py'
```

Outputs include JSON (versions, flags, hashes, process order, batch sizes and raw samples)
and a bilingual Markdown summary. Build products and regenerated fixtures stay in ignored `build/`.
CI runs correctness only and publishes no timing verdict from shared runners.
输出 JSON（版本、参数、哈希、进程顺序、批大小及原始样本）与双语 Markdown；构建与生成数据位于被忽略的 build/。
CI 仅验证正确性，不用共享 runner 的计时给出性能结论。

## Interpretation / 解读

- HotSpot is not ART; x86_64 is not ARM32/ARM64. Shared-host CPU frequency, thermal state,
  co-tenants and scheduling are uncontrolled. Process median ranges describe variability,
  not confidence intervals. More rounds or matched compilers do not remove these limits.
- The already-available native direct Y-plane path can bypass ROI packing. A faster pack
  function may have zero effect on that path. No camera delivery, recognition, decoder,
  startup, power or total-app performance is measured here.
- Use this result to decide which ARM/JNI experiment to run next, not to change shipping
  defaults or the README performance chart. Android follow-up must keep Java array semantics,
  inputs, correctness, ABI and compiler options controlled and measure real camera P50/P95.

- HotSpot 不等于 ART，x86_64 不等于 ARM32/ARM64；共享宿主频率、温控、其他任务及调度不可控。
  进程中位数范围仅描述波动，不是置信区间；增加轮次或匹配编译器也不能消除这些限制。
- 现有直接 native Y 平面路径可以绕过 ROI 打包；该函数变快可能对这条路径完全没有影响。
  本实验不测相机送帧、识别率、解码、启动、功耗或整机性能。
- 结果用于决定下一步 ARM/JNI 实验，不用于修改默认策略或 README 性能图；后续 Android 实验需控制
  Java 数组接口、输入、正确性、ABI 和编译参数，并测真机相机链路 P50/P95。
