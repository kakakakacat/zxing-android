# Publishing the AAR

Two artifacts are published together, both under `io.github.kakakakacat.zxing`:

| Artifact | What it is |
| --- | --- |
| `zxing-android-scanner` (AAR) | The scanning pipeline: `ScannerView`, `ScannerOptions`, camera and decode |
| `zxing-core` (JAR) | This fork's build of ZXing `core`, which the AAR depends on |

`zxing-core` is published because the AAR is built against `core` **from source** in this
repository rather than against upstream's released `com.google.zxing:core`. Without it a
consumer would resolve a dependency that does not exist. If you want upstream core
instead, depend on `com.google.zxing:core` directly and exclude this one.

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
4. creates a GitHub Release with the AAR, the APK and the checksums attached.

`workflow_dispatch` runs the same thing with a version typed in, for a dry run.

The version comes from the tag (`v0.1.0` → `0.1.0`) and is passed to Gradle as
`-PscannerVersion`. Locally, `./gradlew :core:publishToMavenLocal
:scanner:publishToMavenLocal -PscannerVersion=0.1.0` puts the same artifacts in `~/.m2`.

No secret has to be created for this: the workflow uses the built-in `GITHUB_TOKEN`.

## Consuming it

### GitHub Packages

GitHub Packages requires authentication even for public packages, so consumers need a
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

## Maven Central — not done yet

Maven Central is the only one of the three that needs no repository declaration and no
token from consumers, so it is the right end state. It is **not** set up, and this
document does not pretend otherwise. It needs, from the maintainer:

1. a Central Portal account with the `io.github.kakakakacat` namespace verified (GitHub
   account ownership is accepted as proof),
2. a GPG key published to a keyserver, with the private key and passphrase added as
   repository secrets,
3. a publishing plugin that speaks the Central Portal bundle API — plain `maven-publish`
   cannot upload there. `com.vanniktech.maven.publish` or JReleaser both do.

Until those exist, releases go to GitHub Packages and JitPack only.
