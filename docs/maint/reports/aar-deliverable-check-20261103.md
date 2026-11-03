# Is the AAR deliverable? Built, published, resolved, minified and scanned

**2026-11-03.** The question was whether what this project would hand someone actually
works for them, rather than whether it builds here. So it was taken the whole way: a
throwaway application that knows nothing about this repository, depends only on the
published coordinates, is minified by R8, and is pointed at a real barcode.

## The artifact

`./gradlew :scanner:assembleRelease` → `scanner-release.aar`, 1.5 MB.

| | |
| --- | --- |
| `classes.jar` | 131 KB |
| `jni/arm64-v8a/libzxingnative.so` | 1.80 MB |
| `jni/armeabi-v7a/libzxingnative.so` | 1.28 MB |
| `res/` | one `values.xml` |
| `proguard.txt` | consumer rules, shipped |

**16 KB page alignment is real, not assumed.** `llvm-readelf --program-headers` reports
`LOAD align = 0x4000` on **every** `LOAD` segment of **both** shipped ABIs. That is the
Android 15+ requirement, and no device here has 16 KB pages to confirm it at runtime, so
the ELF is the evidence.

## Published and resolved, not just built

`:core:publishToMavenLocal` and `:scanner:publishToMavenLocal` put both artifacts in
`~/.m2`. The scanner POM carries `zxing-core` at **compile** scope plus `kotlin-stdlib`,
and the three CameraX artifacts and `lifecycle-runtime` at **runtime** scope — so a
consumer that never asks for CameraX still links, and one that selects it gets the
dependencies without naming them. Publishing `:core` alongside `:scanner` is what
`5da9aa797` fixed and `check_release_publishes.py` now guards; this run is that fix seen
from the consumer's side.

## A consumer that knows nothing about this repository

A separate Gradle build, outside the project, `mavenLocal()` first, one dependency:

```kotlin
implementation("io.github.kakakakacat.zxing:zxing-android-scanner:0.1.0")
```

- **The README's own Java example compiles** against it — `ScannerView` from XML,
  `ScannerOptions.Builder`, `setFormats`, the `Listener`, `resume()`/`pause()`.
- **Kotlin compiles against it too**, in the same module, which is the pair the
  `JavaSourceCompatibilityTest` suite exists to protect.
- **R8 with `isMinifyEnabled = true`** and only the default Android rules: the AAR's own
  `proguard.txt` is what keeps the library working, and it does. The release APK is
  3.5 MB with both ABIs.

## And it scans

Installed on DN-ENG41, pointed at a `CODE_128` on DN-ENG81's screen:

```
I ConsumerCheck: SCANNED CODE_128 AAR-CONSUMER-OK
```

Screenshot of the live preview with the viewfinder rectangle and laser, drawn entirely by
the published AAR in a foreign application:
`aar-consumer-scan-DN-ENG41-20261103.png`.

## Two things the exercise found

- **`FrontLightMode` is in `…android.camera`, not `…android.scanner`**, and the README
  shows no imports, so copying its snippet fails to compile on that one line. A note now
  says where it lives. Everything else in the snippet was accurate, `resume()`/`pause()`
  included — that pair was missed by the person writing the consumer, not by the README.
- **AGP 9 compiles Kotlin itself**, so a consumer must *not* add
  `org.jetbrains.kotlin.android`; it fails with "extension 'kotlin' already registered".
  That is trap 7 and it bites outside this repository too.

## Released, and what the release was checked against

**v0.1.0 went out on 2026-11-03**, `release.yml` green on the first run, every step.
Verified from the published side rather than from the build:

| Check | Result |
| --- | --- |
| Packages in GitHub Packages | `zxing-android-scanner` and `zxing-core`, both `0.1.0` |
| Release assets | the AAR, the debug APK, `SHA256SUMS.txt`, not a draft, not a prerelease |
| Checksums | both recompute to the published values from the downloaded files |
| Released AAR | the same 13 entries, `classes.jar` 131 KB, both ABIs present |
| 16 KB alignment, in the *released* binary | `LOAD align = 0x4000` on every `LOAD`, both ABIs |

### Resolving from GitHub Packages — verified after the fact

This was recorded here as *not verified*: the registry answers 401 without a token
carrying `read:packages`, which GitHub requires for Maven packages even on a public
repository. It was closed the same day with a token that has that scope.

The consumer project below was repointed at
`https://maven.pkg.github.com/kakakakacat/zxing-android`, with `mavenLocal()` **removed**
from the repository list, and the Gradle module cache for `io.github.kakakakacat.zxing`
deleted first — so nothing this machine had built earlier could satisfy the resolve.

| Check | Result |
| --- | --- |
| The four files the build needs | `scanner` POM and AAR, `core` POM and JAR: all 200 |
| Transitive resolve | `zxing-core` arrived through the `scanner` POM, not by being asked for |
| `clean :app:assembleRelease` | BUILD SUCCESSFUL, R8 ran, APK packaged |
| **Is it the released artifact?** | the fetched AAR's sha256 is `3b2f4392…` — the published `SHA256SUMS.txt` value, byte for byte |
| On the device | installed on DN-ENG41, CameraX backend, read a Code 128 off DN-ENG81's screen: `SCANNED CODE_128 AAR-GPR-OK`, 53 times in one run |

The symbol itself was generated **and read back** with the published `zxing-core-0.1.0.jar`
from the Gradle cache, not with a local build, so both published artifacts are exercised.

One practical note for a consumer, found here: a Code 128 drawn 1000 px wide on the panel
did not decode at this working distance — it ran past the framing rectangle. At 700 px it
decoded in under a second. That is framing, not a decoder limit, and it is the kind of
thing a first integration hits.

`PUBLISHING.md` still names JitPack as the token-free route; that remains the easier one
to hand to someone else.

## What was still not done at the time of writing

Nothing about the artifact. The remaining steps are process:

1. **`CHANGELOG.md` still has `[Unreleased]`.** `release.yml` exits non-zero by design
   until it becomes `[0.1.0]`.
2. **Tag `v0.1.0`** to trigger the release workflow, which builds, checksums, publishes
   both artifacts to GitHub Packages and cuts the Release from that changelog section.
3. **Maven Central is not set up** and is not needed for this: GitHub Packages works
   today, Central needs a Portal account, the verified namespace and a published GPG key.
   `PUBLISHING.md` has it.

None of that was done here, because tagging a release is the user's call.
