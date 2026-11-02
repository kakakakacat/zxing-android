// Barcode Scanner app, built from the upstream legacy source layout (src/, res/, assets/).
plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "io.github.kakakakacat.zxing.android"
  compileSdk = 36

  defaultConfig {
    // Deliberately not the upstream application id, and not inside the
    // com.google.zxing namespace at all: this build is not signed with the upstream key
    // and must not present itself as the published Barcode Scanner.
    // See docs/maint/MIGRATION.md.
    applicationId = "io.github.kakakakacat.zxing.android"
    minSdk = 23
    targetSdk = 36
    versionCode = 10800
    versionName = "4.7.9-maint.1"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  sourceSets {
    getByName("main") {
      // Kotlin alongside Java in the same legacy directory. AGP's built-in Kotlin finds
      // src/main/kotlin by default, which this layout does not have, so `src` has to be
      // named for both. See docs/maint/HANDOFF.md on Kotlin and AGP 9.
      java.setSrcDirs(listOf("src"))
      kotlin.setSrcDirs(listOf("src"))
      res.setSrcDirs(listOf("res"))
      assets.setSrcDirs(listOf("assets"))
      manifest.srcFile("AndroidManifest.xml")
      resources.setSrcDirs(emptyList<String>())
    }
    // The app has no tests of its own yet; the pipeline is covered by :scanner's
    // unit and instrumentation tests. Point these at paths that do not exist so the
    // legacy layout above cannot accidentally be picked up as test sources.
    getByName("androidTest") {
      java.setSrcDirs(listOf("src-androidTest/java"))
      res.setSrcDirs(emptyList<String>())
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

// Same bytecode level as the Java sources; AGP fails the compile task on a mismatch.
kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

dependencies {
  // :scanner brings :core along as an api dependency.
  implementation(project(":scanner"))
  // FileProvider, for handing an exported file to another app as a content:// URI that
  // carries a read grant. App-only: the published AAR gains nothing from this.
  implementation(libs.androidx.core)
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

  testImplementation(libs.junit)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
}
