// zxing-android-scanner: the reusable barcode scanning pipeline, published as an AAR.
//
// Everything an app needs to put a working scanner on screen — camera ownership,
// the decode thread, the viewfinder overlay and the Intent contract — without
// dragging in the Barcode Scanner app's history, sharing or preferences UI.
plugins {
  alias(libs.plugins.android.library)
  `maven-publish`
}

group = "io.github.kakakakacat.zxing"
version = (project.findProperty("scannerVersion") as String?) ?: "0.1.0"

android {
  namespace = "io.github.kakakakacat.zxing.android.scanner"
  compileSdk = 36

  // Pinned, like every other toolchain version here: the native decoder is compared
  // against the Java one, and "which compiler built it" is part of what a comparison has
  // to hold constant (BENCHMARKS.md section 6).
  ndkVersion = libs.versions.ndk.get()

  defaultConfig {
    minSdk = 21
    consumerProguardFiles("consumer-rules.pro")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    externalNativeBuild {
      cmake {
        // The two ABIs that exist on real Android hardware. x86_64 would only serve
        // emulators, and BENCHMARKS.md rules emulator timings out anyway.
        abiFilters += listOf("arm64-v8a", "armeabi-v7a")
      }
    }
    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  packaging {
    jniLibs {
      // Page-aligned and uncompressed in the APK. Legacy packaging would compress the
      // library and defeat the 16 KB alignment it was built with.
      useLegacyPackaging = false
    }
  }

  buildFeatures {
    buildConfig = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildTypes {
    getByName("debug") {
      // x86_64 exists only so CI's emulator can run the native correctness tests; a CI
      // runner is x86_64 and would otherwise skip every one of them. It is added to the
      // debug variant alone, so the published AAR still carries the two ABIs that exist
      // on real hardware and nothing else. Emulator *timings* remain inadmissible either
      // way -- see BENCHMARKS.md §10.7.
      externalNativeBuild {
        cmake {
          abiFilters += "x86_64"
        }
      }
      ndk {
        abiFilters += "x86_64"
      }
    }
  }

  publishing {
    singleVariant("release") {
      withSourcesJar()
    }
  }

  lint {
    abortOnError = false
  }
}

// Kotlin compiles against the same bytecode level the Java sources do; AGP fails the
// compile task on a mismatch rather than warning. AGP 9 supplies the compiler itself, so
// there is no Kotlin plugin and no Kotlin version here -- see docs/maint/HANDOFF.md.
kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

dependencies {
  // `api`: callers handle Result and BarcodeFormat directly, so the decoder has to be
  // on their compile classpath.
  //
  // This fork maintains its own decoder rather than depending on upstream's released
  // artifact. That is only safe because core was moved out of the com.google.zxing
  // namespace: two artifacts publishing identical fully-qualified class names would
  // hand consumers a "Duplicate class com.google.zxing.BarcodeFormat" build failure the
  // moment anything else on their classpath pulled upstream core.
  api(project(":core"))

  testImplementation(libs.junit)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
}

publishing {
  repositories {
    // GitHub Packages. Works with the token CI already has; consumers authenticate
    // with any GitHub token that has read:packages. See docs/maint/PUBLISHING.md.
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/kakakakacat/zxing-android")
      credentials {
        username = (project.findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR")
        password = (project.findProperty("gpr.key") as String?) ?: System.getenv("GITHUB_TOKEN")
      }
    }
  }
  publications {
    register<MavenPublication>("release") {
      artifactId = "zxing-android-scanner"
      afterEvaluate { from(components["release"]) }
      pom {
        name.set("ZXing Android Scanner")
        description.set(
          "Barcode scanning pipeline for Android, maintained as a community fork of the " +
            "ZXing Android client. Not an official ZXing release."
        )
        url.set("https://github.com/kakakakacat/zxing-android")
        licenses {
          license {
            name.set("The Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
          }
        }
        scm {
          url.set("https://github.com/kakakakacat/zxing-android")
          connection.set("scm:git:https://github.com/kakakakacat/zxing-android.git")
        }
      }
    }
  }
}

// 16 KB page alignment is mandatory on Android 15+ for 64-bit devices: a library built
// with the old 4 KB assumption simply fails to load there. The CMake linker flag states
// the intent; this proves it on the artifact that actually ships, because the failure
// mode is an app that will not start on hardware nobody here has.
//
// NDK r28+ defaults to 16 KB, so this will usually pass without the flag. That is exactly
// why it is checked: a default is not a guarantee across a toolchain upgrade, and nothing
// else in the build would notice it changing.
val nativeLibsDir = layout.buildDirectory.dir("intermediates/merged_native_libs")
// Resolved from the SDK location and the pinned NDK version rather than from the Android
// extension, which does not expose the NDK directory to a plain task here.
val sdkDir = providers.provider {
  // Read by hand: `java` in a Kotlin build script is Gradle's own extension, so
  // java.util.Properties is not reachable by that name here.
  val fromLocalProperties = rootProject.file("local.properties").takeIf { it.exists() }
      ?.readLines()
      ?.firstOrNull { it.trimStart().startsWith("sdk.dir=") }
      ?.substringAfter("sdk.dir=")
      ?.trim()
      ?.replace("\\:", ":")
      ?.replace("\\\\", "\\")
  val path = fromLocalProperties
      ?: System.getenv("ANDROID_HOME")
      ?: System.getenv("ANDROID_SDK_ROOT")
  path?.let { File(it) }
}
val ndkVersionForCheck = libs.versions.ndk.get()
val readelfPath = sdkDir.map { sdk ->
  File(sdk, "ndk/$ndkVersionForCheck/toolchains/llvm/prebuilt").listFiles()
      ?.firstOrNull { it.isDirectory }
      ?.resolve("bin/llvm-readelf")
}

val verifyNativeAlignment = tasks.register("verifyNativeAlignment") {
  group = "verification"
  description = "Fails if any packaged .so has a LOAD segment aligned below 16 KB."

  val readelf = readelfPath
  val libsDir = nativeLibsDir

  doLast {
    val tool = readelf.orNull
    check(tool != null && tool.exists()) { "llvm-readelf not found in the pinned NDK" }

    val libraries = libsDir.get().asFile.walkTopDown().filter { it.name.endsWith(".so") }.toList()
    check(libraries.isNotEmpty()) {
      "no .so found under ${libsDir.get().asFile}; the native build did not run"
    }

    val required = 16384L
    val problems = mutableListOf<String>()
    libraries.forEach { library ->
      val output = providers.exec {
        commandLine(tool.absolutePath, "-l", library.absolutePath)
      }.standardOutput.asText.get()

      val alignments = output.lineSequence()
          .filter { it.trimStart().startsWith("LOAD") }
          .mapNotNull { it.trim().split(Regex("\\s+")).lastOrNull() }
          .mapNotNull { value ->
            if (value.startsWith("0x")) value.removePrefix("0x").toLongOrNull(16) else value.toLongOrNull()
          }
          .toList()

      if (alignments.isEmpty()) {
        problems += "${library.name}: no LOAD segment found, so alignment could not be read"
      }
      val tooSmall = alignments.filter { it < required }.distinct()
      tooSmall.forEach { alignment ->
        problems += "${library.parentFile.name}/${library.name}: LOAD aligned to $alignment, needs $required"
      }
      if (tooSmall.isEmpty() && alignments.isNotEmpty()) {
        logger.lifecycle(
            "16 KB alignment OK: ${library.parentFile.name}/${library.name} ${alignments.distinct()}")
      }
    }
    check(problems.isEmpty()) {
      "16 KB page alignment check failed:\n  " + problems.joinToString("\n  ")
    }
  }
}

tasks.matching { it.name == "mergeReleaseNativeLibs" }.configureEach {
  finalizedBy(verifyNativeAlignment)
}
