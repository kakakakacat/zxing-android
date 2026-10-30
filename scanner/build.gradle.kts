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
