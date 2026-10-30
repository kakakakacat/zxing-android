// The Intent integration helper third-party apps embed to call the scanner.
plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "io.github.kakakakacat.zxing.android.integration"
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
