# Publishing the AAR

Two artifacts are published together, both under `io.github.kakakakacat.zxing`:

| Artifact | What it is |
| --- | --- |
| `zxing-android-scanner` (AAR) | The scanning pipeline: `ScannerView`, `ScannerOptions`, camera and decode |
| `zxing-core` (JAR) | The decoder, forked from ZXing core and maintained here |

Both carry `io.github.kakakakacat.zxing.*` classes. Nothing this project publishes is
named `com.google.zxing.*`, so a consumer can have upstream `com.google.zxing:core` on
the same classpath without a duplicate-class failure. The flip side is that the decoder
types are this fork's own and are not interchangeable with upstream's.

> This is a community maintenance fork. It is **not** an official ZXing release and the
> coordinates deliberately do not reuse `com.google.zxing`.

## Releasing

Tag the commit and let CI do it:

```sh
git tag v0.1.0
git push origin v0.1.0
```

`.github/workflows/release.yml` then:

1. builds `:scanner:assembleRelease` and `:app:assembleDebug` at that version,
2. writes `SHA256SUMS.txt` for both artifacts,
3. publishes both Maven artifacts to **GitHub Packages**,
4. uploads both to **Maven Central**, as one signed Portal deployment, when the
   publishing secrets are present,
5. creates a GitHub Release with the AAR, the APK and the checksums attached.

`workflow_dispatch` runs the same thing with a version typed in, for a dry run.

The version comes from the tag (`v0.1.0` → `0.1.0`) and is passed to Gradle as
`-PscannerVersion`. Locally, `./gradlew :core:publishToMavenLocal
:scanner:publishToMavenLocal -PscannerVersion=0.1.0` puts the same artifacts in `~/.m2`.

GitHub Packages needs no secret: the workflow uses the built-in `GITHUB_TOKEN`. Maven
Central needs four, and the step is skipped rather than failed when they are missing, so
a fork can still cut a release:

| Secret | What it is |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token, username half |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token, password half |
| `SIGNING_KEY` | ASCII-armoured **private** GPG key (`gpg --armor --export-secret-keys`) |
| `SIGNING_KEY_PASSWORD` | That key's passphrase |

The Portal deployment is left in `VALIDATED`, not released. Release it by hand at
<https://central.sonatype.com/publishing/deployments>. That gate is deliberate: a version
released to Central can never be replaced or withdrawn.

### Publishing from a laptop

Same four, as Gradle properties in `~/.gradle/gradle.properties` (never in the repo):

```properties
mavenCentralUsername=<token username>
mavenCentralPassword=<token password>
signingInMemoryKey=<armoured private key, newlines stripped>
signingInMemoryKeyPassword=<passphrase>
```

```sh
./gradlew :core:publishToMavenCentral :scanner:publishToMavenCentral -PscannerVersion=0.1.0
```

Both modules in one invocation, always: that is what makes them a single deployment, so
the AAR and the decoder its POM depends on are validated and released together.

## Consuming it

### Maven Central (nothing for the consumer to configure)

The one of the three that costs a consumer nothing — no repository declaration, no
token. The build is wired for it; **no version has gone through it yet**, so until one
does, the two below are what actually resolve.

```kotlin
dependencies {
  implementation("io.github.kakakakacat.zxing:zxing-android-scanner:0.1.0")
}
```

`mavenCentral()` is already in most projects' repository list, and `zxing-core` comes in
as a transitive dependency of the AAR.

### GitHub Packages

Still published, so a consumer already pointing here keeps resolving. New consumers want
Maven Central above. GitHub Packages requires authentication even for public packages, so consumers need a
token with `read:packages`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    maven {
      url = uri("https://maven.pkg.github.com/kakakakacat/zxing-android")
      credentials {
        username = providers.gradleProperty("gpr.user").get()
        password = providers.gradleProperty("gpr.key").get()   // read:packages
      }
    }
  }
}
```

```kotlin
dependencies {
  implementation("io.github.kakakakacat.zxing:zxing-android-scanner:0.1.0")
}
```

### JitPack (no token needed)

JitPack builds straight from a tag, so consumers need no credentials:

```kotlin
repositories { maven { url = uri("https://jitpack.io") } }

dependencies {
  implementation("com.github.kakakakacat.zxing-android:scanner:v0.1.0")
}
```

## How Maven Central is wired

Plain `maven-publish` cannot deploy there: the Central Portal takes a signed bundle
upload over its own API rather than a repository deploy. `com.vanniktech.maven.publish`
speaks it, and is applied in `core/build.gradle.kts` and `scanner/build.gradle.kts`
alongside the existing GitHub Packages repository, which is unchanged.

What the Portal rejects a bundle for, and what satisfies each:

| Requirement | Where it is met |
| --- | --- |
| Verified `io.github.kakakakacat` namespace | Central Portal account, proved by GitHub account ownership |
| GPG signature on every artifact | `signAllPublications()`, key supplied from the environment |

Signing is applied only when a signing key is actually present. It signs every
publication, the GitHub Packages one included, and a signing task without a signatory
fails the build rather than skipping — so applying it unconditionally broke the
GitHub Packages step, which is given no key, and would break any fork that has none.
| sources jar | the plugin's `sourcesJar = true` |
| javadoc jar | the plugin's; empty for `:core`, whose upstream sources do not survive strict doclint |
| POM `name`, `description`, `url`, licence, developer, SCM | the `pom { }` block in each module |


### Exact-commit verification / 同一提交验证

Before any upload, release automation requires successful Build, Instrumentation,
L2 comparison and CodeQL workflow runs for `GITHUB_SHA`. Missing, skipped, cancelled
or failed checks block publishing; a green run on an older commit does not qualify.
Central username, password and signing key must be supplied together. Signature tasks
run before publishing to either Maven repository. No release is triggered by editing code.

任何上传前，发布流程要求 `GITHUB_SHA` 对应的 Build、Instrumentation、L2 comparison 和 CodeQL
工作流成功；缺失、跳过、取消或失败均阻止发布，旧提交的成功记录无效。Central 用户名、密码及签名密钥
必须同时配置；上传任一 Maven 仓库之前先执行签名任务。修改代码本身不会触发版本发布。
