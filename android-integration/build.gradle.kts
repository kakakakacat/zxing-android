// The Intent integration helper third-party apps embed to call the scanner.
plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "io.github.kakakakacat.zxing.android.integration"
  compileSdk = 36

  defaultConfig {
    minSdk = 23
  }

  sourceSets {
    getByName("main") {
      // This module is Kotlin; src/main/kotlin is picked up by AGP's built-in Kotlin
      // support without being named. The empty java srcDir is kept deliberately, because
      // the default would otherwise sweep in directories this module does not have.
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

dependencies {
  testImplementation(libs.junit)
}

// Kotlin compiles against the same bytecode level the Java sources do; a mismatch is a
// link error at assembly time rather than a warning.
kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

