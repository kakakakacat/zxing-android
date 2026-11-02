// Gradle build for the Android side of this maintenance fork.
// Gradle is the only build: :core is the decoder, :scanner is the AAR, :app is the Barcode Scanner app.
pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "zxing-android"

include(":core")
include(":scanner")
include(":android-integration")
include(":app")
include(":benchmark-corpus")
include(":benchmark-jvm")
include(":benchmark-android")
include(":camera-spike")

// Upstream keeps these modules at their historical paths.
project(":core").projectDir = file("core")
project(":scanner").projectDir = file("scanner")
project(":android-integration").projectDir = file("android-integration")
project(":app").projectDir = file("android")
project(":benchmark-corpus").projectDir = file("benchmark/corpus")
project(":benchmark-jvm").projectDir = file("benchmark/jvm")
project(":benchmark-android").projectDir = file("benchmark/android")
// Phase 6 spike, not shipped. See docs/maint/decisions/0001-camera-backend.md.
project(":camera-spike").projectDir = file("benchmark/camera-spike")
