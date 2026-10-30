// Barcode Scanner app, built from the upstream legacy source layout (src/, res/, assets/).
plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "com.google.zxing.client.android"
  compileSdk = 36

  defaultConfig {
    // Development default for this maintenance fork. It is deliberately NOT the
    // upstream application id: this build is not signed with the upstream key and
    // must not present itself as an update to the published Barcode Scanner.
    // See docs/maint/MIGRATION.md.
    applicationId = "com.google.zxing.client.android.community"
    minSdk = 21
    targetSdk = 36
    versionCode = 10800
    versionName = "4.7.9-maint.1"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  sourceSets {
    getByName("main") {
      java.setSrcDirs(listOf("src"))
      res.setSrcDirs(listOf("res"))
      assets.setSrcDirs(listOf("assets"))
      manifest.srcFile("AndroidManifest.xml")
      resources.setSrcDirs(emptyList<String>())
    }
    getByName("androidTest") {
      java.setSrcDirs(listOf("src-androidTest/java"))
      res.setSrcDirs(emptyList<String>())
      manifest.srcFile("src-androidTest/AndroidManifest.xml")
    }
    getByName("test") {
      java.setSrcDirs(listOf("src-test/java"))
    }
  }

  buildTypes {
    getByName("debug") {
      isMinifyEnabled = false
    }
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // No production signing config is checked in. `assembleRelease` produces an
      // unsigned APK unless the maintainer supplies one.
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = true
  }

  packaging {
    resources.excludes += setOf("META-INF/*")
  }

  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
}

dependencies {
  // :scanner brings :core along as an api dependency.
  implementation(project(":scanner"))
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

  testImplementation(libs.junit)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
}
