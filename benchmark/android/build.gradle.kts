// L2: the Android frame-pipeline benchmark. Specified in docs/maint/BENCHMARKS.md §10.
//
// This module is never published and never shipped. It exists so that a frame-path
// change can be compared against a committed baseline on a named device, instead of
// being argued about.
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.androidx.benchmark)
}

android {
  namespace = "io.github.kakakakacat.zxing.benchmark"
  compileSdk = 36

  defaultConfig {
    // The library's floor, so the benchmark cannot accidentally measure a path that
    // the shipped AAR does not take on an old device.
    minSdk = 21
    testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

    // BENCHMARKS.md §10.5: CI runs the hd/default slice; the full matrix is a manual
    // device run. -Pl2.matrix=full flips it.
    testInstrumentationRunnerArguments["l2.matrix"] =
      (project.findProperty("l2.matrix") as String?) ?: "ci"
  }

  // Microbenchmark numbers from a debuggable build are not numbers. Measuring against
  // the release variant is the whole point; the plugin signs the release androidTest
  // APK with the debug key so it can still be installed.
  testBuildType = "release"

  buildTypes {
    debug {
      // Present only so the variant graph resolves. Nothing is measured here.
    }
    release {
      isDefault = true
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "benchmark-proguard-rules.pro"
      )
    }
  }

  // The committed frame hashes (§10.4) are one file with three readers: the host unit
  // test, the instrumentation run and benchmark/tools/l2_report.py. Shipping it as an
  // androidTest asset is how the device run gets to the same copy.
  sourceSets {
    getByName("androidTest") {
      assets.srcDir("goldens")
    }
  }

  buildFeatures {
    buildConfig = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  lint {
    abortOnError = false
  }
}

// The host unit test reads the golden file straight off disk; the device run reads the
// asset. Same bytes either way.
tasks.withType<Test>().configureEach {
  systemProperty("l2.goldenFile", file("goldens/frames.golden.json").absolutePath)
}

dependencies {
  // The same decoder and the same frame path the AAR ships, so an L2 number and an app
  // number refer to the same code. :core is pulled in transitively as an `api`
  // dependency of :scanner, but it is named here as well because the benchmark calls
  // the reader directly.
  implementation(project(":scanner"))
  implementation(project(":core"))

  // Frame generation is plain Java and is unit tested on the host (§10.4), so the
  // generated frames can be proved deterministic without a device.
  testImplementation(libs.junit)

  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.benchmark.junit4)
}
