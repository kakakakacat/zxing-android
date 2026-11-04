# ARM-specific AARs / ARM 架构专用 AAR

The scanner can distribute three release AARs from the same compiled release. Bytecode,
resources, manifest and consumer ProGuard rules are identical; only JNI directories differ.
扫描库从同一次 release 构建生成三份 AAR；字节码、资源、Manifest 及混淆规则相同，仅 JNI 目录不同。

| Local output / 本地输出 | Maven classifier | Included ABI / 包含 ABI |
| --- | --- | --- |
| `scanner-release.aar` | none / 无 | `arm64-v8a` + `armeabi-v7a` |
| `scanner-release-arm64-v8a.aar` | `arm64-v8a` | ARM 64-bit / ARM 64 位 |
| `scanner-release-armeabi-v7a.aar` | `armeabi-v7a` | ARM 32-bit / ARM 32 位 |

The release AARs do not contain x86/x86_64. The debug variant still includes x86_64 for
emulator correctness tests. This change is available in future releases; existing Maven
versions are immutable and do not gain classifier files retroactively.
release AAR 不包含 x86/x86_64；debug 变体仍提供 x86_64 用于模拟器正确性测试。分类包随后续版本发布，
既有 Maven 版本不可覆盖，不会自动补上分类文件。

## Build / 构建

```sh
./gradlew :scanner:assembleAbiAars
# assembleRelease also builds and verifies all three outputs.
# assembleRelease 同样生成并校验三份输出。
./gradlew :scanner:assembleRelease
```

Files are under `scanner/build/outputs/aar/`. CI uploads all three; release automation
attaches them with versioned names and SHA-256 checksums. Both Maven classifiers belong
to the existing scanner publication, including signatures when Central signing is enabled.
输出位于 `scanner/build/outputs/aar/`。CI 上传三份文件，发布流程附带版本化文件名和 SHA-256 校验值。
两个 Maven classifier 属于现有 scanner publication；启用 Central 签名时一并签名。

## Maven / Maven 集成

Choose exactly one scanner AAR. Use the same published version for all examples below.
Select the classifier with the artifact block so normal transitive dependency resolution
for this fork's core, CameraX and lifecycle is preserved. The universal coordinate stays
available for consumers that support both ARM ABIs.
三份 scanner AAR 只选一份；以下使用同一已发布版本。通过 artifact 块选择 classifier，保留本项目 core、
CameraX、lifecycle 等正常传递依赖。需要同时支持两种 ARM ABI 的应用仍可使用原完整包坐标。

```kotlin
val zxingVersion = "<version>" // A release that includes classifiers / 包含分类包的版本

dependencies {
    implementation("io.github.kakakakacat.zxing:zxing-android-scanner:$zxingVersion") {
        artifact {
            classifier = "arm64-v8a" // For ARM 32-bit use "armeabi-v7a" / ARM 32 位改为此值
            type = "aar"
            extension = "aar"
        }
    }
}
```

For the universal AAR, omit the artifact block. Avoid adding the universal dependency and
an ABI-specific dependency together: their classes are identical. Artifact-only `@aar`
notation disables normal transitive resolution by default; the example above deliberately
uses the regular module dependency. CI publishes to Maven Local and resolves each classifier
from an Android consumer, checking that only the chosen scanner AAR and required core/CameraX
modules are resolved.
使用完整包时省略 artifact 块；不要同时引入完整包和单 ABI 包，两者类相同。`@aar` 形式默认关闭正常
传递依赖解析，因此上例使用普通模块依赖。CI 向 Maven Local 发布后，分别用 Android 消费项目解析两种
classifier，检查仅选中对应 scanner AAR，且 core/CameraX 依赖仍完整。

Reference / 参考：[Gradle artifact selection](https://docs.gradle.org/current/userguide/resolving_specific_artifacts.html).

## Match the app process / 匹配应用进程

Select the ABI of the Android app process, not simply the physical CPU's advertised
bitness. A 64-bit CPU running a 32-bit Android process needs `armeabi-v7a`. Every native
dependency loaded into that process must support the same ABI. Removing the other ABI
reduces library distribution size; it does not change decoding code or establish a speedup.
App Bundles may already deliver native libraries by ABI, so a smaller AAR is not automatically
a smaller per-device Play download.
按应用进程 ABI 选择，不能只看 CPU 标称位数：64 位 CPU 上的 32 位 Android 进程需用 `armeabi-v7a`。
同一进程加载的其他原生依赖也需支持同一 ABI。去掉另一架构减少库分发体积，不改变解码代码，也不代表提速。
App Bundle 本身可能已按 ABI 分发原生库，因此 AAR 变小不等于每台设备的 Play 下载包必然变小。

## Verification / 校验

`verifyAbiAars` compares all common entries byte-for-byte, checks that each split contains
only its intended native ABI, validates ELF class/machine, and retains the release's existing
16 KB native alignment check. Direct Maven publish tasks run the same verification gate.
`verifyAbiAars` 逐字节比较公共文件，检查单 ABI 包中没有其他架构，校验 ELF 位数及机器类型，并保留
release 原有的 16 KB 对齐校验。直接执行 Maven publish 任务也会先运行该检查。
