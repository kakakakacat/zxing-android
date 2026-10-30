// zxing-android-scanner: the reusable barcode scanning pipeline, published as an AAR.
//
// Everything an app needs to put a working scanner on screen — camera ownership,
// the decode thread, the viewfinder overlay and the Intent contract — without
// dragging in the Barcode Scanner app's history, sharing or preferences UI.
plugins {
  alias(libs.plugins.android.library)
  `maven-publish`
}

android {
  namespace = "com.google.zxing.client.android.scanner"
  compileSdk = 36

  defaultConfig {
    minSdk = 21
    consumerProguardFiles("consumer-rules.pro")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures {
    buildConfig = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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

dependencies {
  // `api`: callers handle com.google.zxing.Result and BarcodeFormat directly.
  api(project(":core"))

  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
}

publishing {
  publications {
    register<MavenPublication>("release") {
      groupId = "io.github.kakakakacat.zxing"
      artifactId = "zxing-android-scanner"
      version = project.findProperty("scannerVersion")?.toString() ?: "0.1.0"
      afterEvaluate { from(components["release"]) }
      pom {
        name.set("ZXing Android Scanner")
        description.set(
          "Barcode scanning pipeline for Android, maintained as a community fork of the " +
            "ZXing Android client. Not an official ZXing release."
        )
        licenses {
          license {
            name.set("The Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
          }
        }
      }
    }
  }
}
