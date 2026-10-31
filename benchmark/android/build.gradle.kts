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
    // Which decoder backend DecoderBackendBenchmark measures: "java" or "native".
  // One run per backend, two result files, then compare.py. See BENCHMARKS.md.
  testInstrumentationRunnerArguments["l2.backend"] =
    (project.findProperty("l2.backend") as String?) ?: "java"

  testInstrumentationRunnerArguments["l2.matrix"] =
      (project.findProperty("l2.matrix") as String?) ?: "ci"

    // -Pl2.throttleWait=0 stops Microbenchmark waiting for a cool-down between
    // benchmarks. On a device that is permanently above its skin threshold the wait
    // buys nothing — measured on DN-ENG41: 270s slept per benchmark, SEVERE before and
    // SEVERE after — but it is still a deviation from the default protocol, so it is
    // never silent. The argument is recorded by recordL2RunnerArgs below and turns into
    // a THROTTLE_WAIT_DISABLED warning in the result file.
    //
    // It switches off the *waiting*, not the throttling: the device stays throttled and
    // the numbers are real numbers for a throttled device. Raising the thermal
    // thresholds would be the other thing, and §6 rules it out.
    (project.findProperty("l2.throttleWait") as String?)?.let { seconds ->
      testInstrumentationRunnerArguments[
        "androidx.benchmark.thermalThrottle.sleepDurationSeconds"] = seconds
    }

    // -Pl2.emulator=true lets a run happen on an emulator at all: Microbenchmark
    // otherwise refuses outright. Suppressing the error only makes it *run* — §6 still
    // says an emulator result is shape, never magnitude, l2_report.py still raises
    // EMULATOR from the build fingerprint, and compare.py treats that as invalidating
    // and will not let it be waived. So an emulator run is a harness check and an
    // order-of-magnitude tripwire; it can never produce a verdict.
    if ((project.findProperty("l2.emulator") as String?)?.toBoolean() == true) {
      testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }
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

// What protocol a run was made under is part of its result, not something a reader should
// have to reconstruct from whoever typed the command. The arguments are written out here
// and l2_report.py folds them into the result file.
val l2RunnerArgs = android.defaultConfig.testInstrumentationRunnerArguments.toSortedMap()
val recordL2RunnerArgs = tasks.register("recordL2RunnerArgs") {
  val outFile = layout.buildDirectory.file("l2-runner-args.json")
  outputs.file(outFile)
  val args = l2RunnerArgs
  doLast {
    outFile.get().asFile.writeText(
      args.entries.joinToString(",\n", "{\n", "\n}\n") {
        "  \"${it.key}\": \"${it.value}\""
      }
    )
  }
}
tasks.matching { it.name.startsWith("connected") }.configureEach {
  dependsOn(recordL2RunnerArgs)
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
