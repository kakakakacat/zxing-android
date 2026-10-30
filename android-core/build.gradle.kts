// Camera parameter helpers shared by the app; upstream ships this as a plain jar
// built against android.jar, which Gradle models as an Android library.
plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.google.zxing.client.android.camera.core"
  compileSdk = 36

  defaultConfig {
    minSdk = 21
  }

  sourceSets {
    getByName("main") {
      java.setSrcDirs(listOf("src/main/java"))
      manifest.srcFile("src/main/AndroidManifest.xml")
      res.setSrcDirs(emptyList<String>())
      resources.setSrcDirs(emptyList<String>())
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  lint {
    abortOnError = false
  }
}
