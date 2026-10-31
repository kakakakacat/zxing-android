// L1: decode microbenchmark and correctness runner on the host JVM.
//
// Host numbers are a diagnostic aid only. Any performance claim about Android has to be
// measured on the device's ART; see docs/maint/BENCHMARKS.md.
plugins {
  `java-library`
  application
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

application {
  mainClass.set("zxingbench.L1Runner")
}

dependencies {
  // The same decoder the published AAR ships, so an L1 number and an app number refer
  // to the same core.
  implementation(project(":core"))

  // The manifest, the classification rules and the result envelope, shared with the
  // device runner so the two L1 files can be compared sample by sample.
  implementation(project(":benchmark-corpus"))
}

tasks.named<JavaExec>("run") {
  // Paths in the manifest are repo-relative, so run from the repository root.
  workingDir = rootProject.projectDir
  // Keep the measurement loop off the Gradle daemon's shared JVM.
  jvmArgs("-XX:+UseSerialGC")
}
