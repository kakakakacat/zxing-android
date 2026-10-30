// Gradle view of the upstream `core` module, so the Android app can build against
// current core sources instead of the Maven build's pinned 3.3.3 binary.
// The Maven build (core/pom.xml) remains the module's source of truth for releases.
plugins {
  `java-library`
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

sourceSets {
  main {
    java.setSrcDirs(listOf("src/main/java"))
    resources.setSrcDirs(listOf("src/main/resources"))
  }
  test {
    java.setSrcDirs(listOf("src/test/java"))
    resources.setSrcDirs(listOf("src/test/resources"))
  }
}

dependencies {
  testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
  useJUnit()
}
