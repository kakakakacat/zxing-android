// Phase 6 camera-backend spike. Never published, never shipped, not part of :app.
//
// Two product flavors over one activity: `camera1` compiles in :scanner's shipping Camera1
// stack, `camerax` compiles in CameraX. Everything else about the two APKs is identical, so
//
//   - the difference in time-to-first-frame is the difference between the camera stacks, and
//   - the difference in release APK size is the cost of CameraX, after R8 has run.
//
// Both are questions docs/maint/decisions/0001-camera-backend.md had to answer with a
// measurement rather than a quotation. See that file for the numbers and what they mean.
plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "io.github.kakakakacat.zxing.spike"
  compileSdk = 36
  // Pinned so the debug symbols in :scanner's .so get stripped the way a real release
  // build strips them. Without it AGP has no NDK to strip with and the APK carries an
  // 18 MB unstripped libzxingnative.so, which is not a size anyone ships.
  ndkVersion = libs.versions.ndk.get()

  defaultConfig {
    applicationId = "io.github.kakakakacat.zxing.spike"
    // The library's floor.
    minSdk = 23
    targetSdk = 36
    versionCode = 1
    versionName = "spike"

    // The two ABIs the AAR ships. Without this CameraX would also drag x86 and x86_64
    // copies of its own native libraries in, and the size delta would be measuring an
    // emulator build nobody installs. What those four ABIs cost unfiltered is recorded
    // in the decision record instead.
    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }
  }

  flavorDimensions += "cameraBackend"
  productFlavors {
    create("camera1") {
      dimension = "cameraBackend"
      applicationIdSuffix = ".camera1"
    }
    create("camerax") {
      dimension = "cameraBackend"
      applicationIdSuffix = ".camerax"
    }
    // CameraX without camera-view: same binding, a plain SurfaceView instead of PreviewView.
    // camera-view pulls in AppCompat and camera-video, and a scanner uses neither.
    create("cameraxlean") {
      dimension = "cameraBackend"
      applicationIdSuffix = ".cameraxlean"
    }
  }

  sourceSets {
    getByName("main") { kotlin.setSrcDirs(listOf("src/main/kotlin")) }
    getByName("camera1") { kotlin.setSrcDirs(listOf("src/camera1/kotlin")) }
    // cameraxCommon holds what the two CameraX flavors do identically, so the difference
    // between them is their dependencies and not their code.
    getByName("camerax") {
      kotlin.setSrcDirs(listOf("src/camerax/kotlin", "src/cameraxCommon/kotlin"))
    }
    getByName("cameraxlean") {
      kotlin.setSrcDirs(listOf("src/cameraxlean/kotlin", "src/cameraxCommon/kotlin"))
    }
  }

  buildTypes {
    getByName("debug") {
      isMinifyEnabled = false
    }
    getByName("release") {
      // On, and with the same settings :app uses. An unshrunk APK would overstate the cost
      // of CameraX by whatever R8 would have removed, which is most of the question.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

dependencies {
  implementation(project(":scanner"))
  // In the base, not in the camerax flavor: :app already depends on androidx.core, so
  // charging CameraX for what this app would carry anyway would inflate the size delta.
  implementation(libs.androidx.core)

  listOf("camerax", "cameraxlean").forEach { flavor ->
    add("${flavor}Implementation", libs.camerax.core)
    add("${flavor}Implementation", libs.camerax.camera2)
    add("${flavor}Implementation", libs.camerax.lifecycle)
    // CameraX binds to a LifecycleOwner and this spike's activity is a plain android.app
    // Activity, so the flavors supply one. Counted as part of CameraX's cost: an app that
    // is not already on AndroidX lifecycle pays it to adopt CameraX.
    add("${flavor}Implementation", libs.androidx.lifecycle.runtime)
  }
  // PreviewView, and with it AppCompat and camera-video. The whole point of the lean
  // flavor is what this line costs.
  "cameraxImplementation"(libs.camerax.view)
}
