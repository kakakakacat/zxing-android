// Gradle build for the Android side of this maintenance fork.
// The upstream Maven build (pom.xml) is left untouched and still builds core/javase.
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
include(":android-core")
include(":android-integration")
include(":app")

// Upstream keeps these modules at their historical paths.
project(":core").projectDir = file("core")
project(":android-core").projectDir = file("android-core")
project(":android-integration").projectDir = file("android-integration")
project(":app").projectDir = file("android")
