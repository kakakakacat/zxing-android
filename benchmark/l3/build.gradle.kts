// L3: how long from launch to a camera that is ready, and from there to a frame and to a
// scan, on a real device. Specified in docs/maint/BENCHMARKS.md §11.
//
// Never published, never shipped, and it cannot run on an emulator: it measures a camera.
//
// A Macrobenchmark module is not an app and not a library — it drives :app from outside,
// in a separate process, and reads a system trace. That is also why the thing it measures
// has to be the `benchmark` build type: R8 on, not debuggable, profileable.
plugins {
  // By id, not by alias: the root build file already resolves it, and naming a version
  // twice on one classpath is an error rather than a redundancy.
  id("com.android.test")
}

android {
  namespace = "io.github.kakakakacat.zxing.benchmark.l3"
  compileSdk = 36

  defaultConfig {
    // Higher than the library's floor on purpose. ScannerTrace's asynchronous sections
    // are API 29, so an L3 run below that records the startup metric and none of the
    // camera spans -- a result file with holes in it, which is worse than refusing.
    minSdk = 29
    targetSdk = 36
    // AndroidJUnitRunner, not AndroidBenchmarkRunner: that one belongs to Microbenchmark
    // and :benchmark-android uses it. A Macrobenchmark module drives another process and
    // needs the ordinary runner -- with the benchmark runner here, instrumentation starts,
    // reports `tests="0"` and the build succeeds.
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Which camera backend the app is told to use. The app reads it from the same
    // preference its Settings screen writes, so this measures the shipping path and not
    // a test-only one. "camera1" or "camerax".
    testInstrumentationRunnerArguments["l3.backend"] =
        (project.findProperty("l3.backend") as String?) ?: "camera1"

    // How many cold launches per measurement. The default is deliberately higher than
    // L2's: a camera open is noisier than a decode and there is no way to warm it.
    testInstrumentationRunnerArguments["l3.iterations"] =
        (project.findProperty("l3.iterations") as String?) ?: "10"
  }

  buildTypes {
    create("benchmark") {
      isDebuggable = false
      // A com.android.test module has no signing config of its own, and an unsigned APK
      // fails to install with INSTALL_PARSE_FAILED_NO_CERTIFICATES. :benchmark-android
      // never hits this because the benchmark plugin signs its androidTest APK for it.
      signingConfig = signingConfigs.getByName("debug")
      // :scanner and :core have no `benchmark` variant and do not need one -- they are
      // the same bytes either way. Without this the testedApks graph is resolved with
      // BuildTypeAttr=benchmark all the way down and fails on the first library that has
      // never heard of it.
      matchingFallbacks += listOf("release")
    }
  }

  // The variant of :app to drive. Nothing else is a valid target: a debuggable build is a
  // different program and timing it answers a different question.
  targetProjectPath = ":app"
  experimentalProperties["android.experimental.self-instrumenting"] = true

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  lint {
    abortOnError = false
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

// No runner-args task here, unlike :benchmark-android. Gradle does not run this
// benchmark -- tools/maint/l3-run.sh does, with `am instrument`, because AGP's install
// step wipes the app data the run's camera-backend preference lives in. A record written
// by Gradle could therefore describe a run that never happened, which is the exact failure
// the L2 task was written to prevent. The script writes the record instead.

dependencies {
  implementation(libs.androidx.benchmark.macro.junit4)
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.junit)
}
