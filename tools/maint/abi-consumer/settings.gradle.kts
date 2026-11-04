pluginManagement {
  repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
  repositories { mavenLocal(); google(); mavenCentral() }
  versionCatalogs {
    create("libs") { from(files("../../../gradle/libs.versions.toml")) }
  }
}
rootProject.name = "abi-consumer-check"
