// Gradle view of the upstream `core` module, so the Android app can build against
// current core sources instead of the Maven build's pinned 3.3.3 binary.
// The Maven build (core/pom.xml) remains the module's source of truth for releases.
plugins {
  `java-library`
  `maven-publish`
  alias(libs.plugins.vanniktech.maven.publish)
}

// Published alongside the AAR so consumers resolve a real artifact rather than
// "unspecified". These are this fork's coordinates: upstream's com.google.zxing:core
// remains the artifact to use when you do not want this fork's changes.
group = "io.github.kakakakacat.zxing"
version = (project.findProperty("scannerVersion") as String?) ?: "0.1.0"

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

// GitHub Packages stays: it is where every release before Maven Central went, and a
// consumer already pointing at it should not have the repository disappear underneath
// them. New consumers want Maven Central, which needs no repository and no token.
publishing {
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/kakakakacat/zxing-android")
      credentials {
        username = (project.findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR")
        password = (project.findProperty("gpr.key") as String?) ?: System.getenv("GITHUB_TOKEN")
      }
    }
  }
}

mavenPublishing {
  // The Portal's own API, with the deployment left in VALIDATED for a human to release.
  // Automatic release is deliberately not used: a version published to Central can never
  // be replaced or withdrawn, so the last gate before that is a person looking at it.
  publishToMavenCentral()
  // Central rejects an unsigned bundle. The key lives in the environment, never here --
  // ORG_GRADLE_PROJECT_signingInMemoryKey and friends; see docs/maint/PUBLISHING.md.
  signAllPublications()

  coordinates(group.toString(), "zxing-core", version.toString())

  // The plugin owns the sources jar now; a `java { withSourcesJar() }` of our own would
  // collide with it. The javadoc jar is empty on purpose: Central requires the artifact
  // to exist, and these are upstream's sources, whose javadoc does not survive a strict
  // doclint pass.
  configure(com.vanniktech.maven.publish.JavaLibrary(
    javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty(),
    sourcesJar = true,
  ))

  pom {
    name.set("ZXing core (Android maintenance fork)")
    description.set(
      "Barcode decoding library. A fork of ZXing core, maintained for Android in " +
        "this project and moved to the io.github.kakakakacat.zxing namespace so it " +
        "cannot collide with upstream's com.google.zxing:core on a consumer's " +
        "classpath. Not an official ZXing release."
    )
    url.set("https://github.com/kakakakacat/zxing-android")
    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        distribution.set("repo")
      }
    }
    // Central requires a developer entry. It is the maintainer of this fork, not of ZXing.
    developers {
      developer {
        id.set("kakakakacat")
        name.set("kakakakacat")
        url.set("https://github.com/kakakakacat")
      }
    }
    scm {
      url.set("https://github.com/kakakakacat/zxing-android")
      connection.set("scm:git:https://github.com/kakakakacat/zxing-android.git")
      developerConnection.set("scm:git:ssh://git@github.com/kakakakacat/zxing-android.git")
    }
  }
}
