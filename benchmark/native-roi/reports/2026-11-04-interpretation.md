# Interpretation / 结果解读

Evidence / 数据：[summary / 汇总](2026-11-04-host.md) · [raw samples / 原始样本](2026-11-04-host.json)

This is a single shared Intel Xeon x86_64 Linux host, pinned to one logical CPU, with
uncontrolled frequency and thermal/co-tenant load. C++ Clang 17.0.6 and Rust 1.75's
LLVM 17.0.6 were matched at the LLVM version level. Java was OpenJDK HotSpot 17.0.20.
Three fresh processes per backend, nine cases, 21 calibrated batch samples per case,
500 ms warmup per case; all outputs were checked. This is exploratory implementation
comparison, not a language-wide or Android/ART performance verdict.
本次为一台共享 Intel Xeon x86_64 Linux 宿主，绑定一个逻辑核，未锁频，温控及其他任务负载不可控。
Clang 与 Rust 的 LLVM 版本均为 17.0.6，Java 为 HotSpot 17.0.20。每个 backend 三个独立进程，
每进程九组输入、每组 21 批样本及 500 ms 预热，并校验完整输出。结果仅比较本次实现，不代表语言整体或 Android/ART。

## Findings / 发现

1. **There is no demonstrated benefit from replacing the current C++ backend with Rust.**
   Native packed-row cases were close in several configurations: FHD was 46.278 µs for
   C++ and 46.657 µs for Rust. In padded FHD the apparent Rust advantage (47.929 vs
   52.689 µs) came with overlapping process-median ranges: C++ 44.829–52.798, Rust
   42.640–49.846 µs. Avoid treating those small aggregate differences as a stable ranking.
   Native stride-2 Rust was slower in this implementation: HD 169.712 vs C++ 129.636 µs.
   The safe-indexing vs pointer code can compile differently; no assembly/counter study
   was performed, so the exact cause is unestablished.

2. **For the common stride-1 copy, a faster native kernel does not justify crossing JNI.**
   HD padded input took Java 12.916 µs, JNI→C++ 38.908 µs, JNI→Rust 38.943 µs.
   The out-of-band JNI array probe reported a copy in every recorded JNI case on this
   HotSpot VM. This identifies an extra-copy mechanism, not an exact decomposition of
   all overhead. Do not subtract the native-driver table from Java/JNI rows to claim
   exact JNI cost. The production direct native Y-plane path can avoid packing entirely.

3. **Stride-2 packing is an ARM experiment candidate, not a reason for a rewrite.**
   HD Java took 357.680 µs, JNI→C++ 147.677 µs and JNI→Rust 210.049 µs.
   FHD results followed the same direction, but process spread remained material:
   JNI→C++ 363.354–436.050 µs; JNI→Rust 513.135–668.829 µs.
   First determine whether real target phones expose stride 2 and spend meaningful
   time in packing; a rare path can have little effect on camera-to-result latency.

1. **没有证据支持把现有 C++ 后端替换为 Rust。** FHD 紧密行原生调用为 C++ 46.278 µs、Rust 46.657 µs。
   FHD padding 的 Rust 汇总值更小，但进程中位数范围重叠，不能当作稳定排名；步幅 2 的本次 Rust 实现则更慢。
   安全索引和指针循环可能生成不同代码，尚未做汇编及硬件计数分析，不能确定具体原因。
2. **常见步幅 1 场景，搬到 native 后的完整 JNI 调用更慢。** HD padding：Java 12.916 µs、JNI→C++
   38.908 µs、JNI→Rust 38.943 µs。该 HotSpot 的 JNI 数组探针均返回副本；它说明存在复制机制，不等于
   全部差值都来自 JNI 调用本身。现有 direct native Y 平面路径还可以直接绕过打包。
3. **步幅 2 是后续 ARM 小实验候选。** HD：Java 357.680 µs、JNI→C++ 147.677 µs、JNI→Rust 210.049 µs。
   先确认目标真机是否使用该布局、打包是否占据实际扫码时间；稀少路径变快可能对总延迟影响很小。

## Decision / 决定

Keep production code and defaults unchanged. Retain Kotlin/Java orchestration and the
existing zxing-cpp engine. No Rust runtime, new JNI bridge or experimental packer enters
any shipped AAR. Do not add these host figures to the README Android performance chart.

The next justified measurement is the **same stride-2 packer on ARM32/ARM64 Android,
through identical Java-array JNI semantics**, alongside the existing Java path and direct
native Y-plane path. Gate any migration on end-to-end camera latency, recognition/timeouts,
allocated bytes/GC, CPU and sustained power/temperature—not this isolated kernel alone.

生产代码与默认策略保持不变：保留 Kotlin/Java 调度与 zxing-cpp。实验 Rust、JNI 桥及打包函数不进入任何 AAR，
桌面结果不加入 README 的 Android 性能图。下一步有依据的测试是：ARM32/ARM64 Android 上以相同 Java 数组 JNI
接口测步幅 2，同时对比 Java 与现有直接 native Y 平面路径；是否迁移应由整链路延迟、识别/超时、分配/GC、CPU
及持续功耗/温度共同决定。
