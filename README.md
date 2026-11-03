# ZXing Android

[简体中文](#简体中文) · [English](#english)

## 简体中文

基于 [ZXing](https://github.com/zxing/zxing) 的独立 Android 社区维护项目，持续改进扫码应用与可复用扫描库。

### 长期目标

- **持续适配 Android**：跟进系统演进，改善相机、权限和生命周期兼容。
- **更快、更省资源**：优化 Java / 原生解码与图像处理，降低延迟、CPU、内存和耗电。
- **更稳定、更准确**：修复缺陷，减少误识别，改善困难场景下的识别表现。
- **便于集成、保持离线**：提供清晰稳定的 API，支持无 GMS 设备。
- **用数据验证改进**：持续建设 benchmark、回归样本和多设备测试，公开性能收益与回退。

### 性能对比

![Java ZXing 与原生解码后端的性能对比，含一维码退化场景](docs/maint/assets/decoder-performance.zh-CN.svg)

**本分支内部 Java ZXing 与 zxing-cpp 解码后端的对比，不是原版 App 与当前 App 的整体提速。**

本轮无码帧耗时降低 **77–81%**，二维码命中耗时降低 **71.9–82.5%**；居中一维码命中则是 Java 更快。完整 27 项测试为 **19 项通过 / 8 项失败**。

测试条件：DN-ENG41、Android 14、锁频、长期偏热状态；提交 `602d368c4`，每后端一轮，相同生成帧及匹配处理选项。图中展示全部 9 个默认全码制场景，各面板量程不同。结果不代表其他设备、后续版本、真实首扫或续航表现。

[测试方法](docs/maint/BENCHMARKS.md#106c-the-first-backend-ab-measured) · [完整结果](docs/maint/reports/l2-ab-602d368c4-DN-ENG41-verdict.md) · [原始数据](docs/maint/reports/l2-ab-602d368c4-DN-ENG41-cases.csv)

[集成与构建说明、历史进度](docs/maint/PROJECT_DETAILS.md#简体中文) · [发布与获取](docs/maint/PUBLISHING.md)

---

## English

An independent Android community maintenance project based on [ZXing](https://github.com/zxing/zxing), improving the scanner app and reusable scanning library.

### Long-term goals

- **Keep pace with Android**: improve camera, permission and lifecycle compatibility as the platform evolves.
- **Faster, more efficient scanning**: optimise Java / native decoding and frame processing to reduce latency, CPU, memory and power use.
- **Greater reliability and accuracy**: fix defects, reduce misreads and improve detection in difficult scenes.
- **Easy integration, fully offline**: provide clear, stable APIs and support devices without GMS.
- **Evidence-driven improvements**: maintain benchmarks, regression datasets and multi-device tests, publishing both gains and regressions.

### Performance comparison

![Java ZXing vs native decoder performance, including 1D regressions](docs/maint/assets/decoder-performance.svg)

**This compares the Java ZXing and zxing-cpp decoder backends within this fork, not the original app against the current app.**

In this run, time drops **77–81% on no-barcode frames** and **71.9–82.5% on QR hits**; Java wins on centered 1D hits. The full 27-case matrix reports **19 pass / 8 fail**.

Conditions: DN-ENG41, Android 14, locked clocks and standing warm thermal conditions; commit `602d368c4`, one run per backend, identical generated frames and matched effort options. The chart shows all nine default/all-format cases, with different scales per panel. Results do not establish performance on other devices, later versions, real-camera first scans or battery life.

[Method](docs/maint/BENCHMARKS.md#106c-the-first-backend-ab-measured) · [Full results](docs/maint/reports/l2-ab-602d368c4-DN-ENG41-verdict.md) · [Raw data](docs/maint/reports/l2-ab-602d368c4-DN-ENG41-cases.csv)

[Integration, building and historical progress](docs/maint/PROJECT_DETAILS.md#english) · [Releases and distribution](docs/maint/PUBLISHING.md)

---

独立维护，非 ZXing 官方项目。保留原版权，采用 [Apache-2.0](LICENSE) 许可证。  
Independently maintained, not an official ZXing project. Original copyrights retained; licensed under [Apache-2.0](LICENSE).
