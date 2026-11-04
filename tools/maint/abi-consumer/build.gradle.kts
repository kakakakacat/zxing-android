import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import java.util.zip.ZipFile

plugins { alias(libs.plugins.android.library) }
android {
  namespace = "io.github.kakakakacat.zxing.abicheck"
  compileSdk = 36
  defaultConfig { minSdk = 23 }
}
val abi = providers.gradleProperty("scannerAbi").get()
val scannerVersion = providers.gradleProperty("scannerVersion").get()
require(abi in listOf("arm64-v8a", "armeabi-v7a"))
dependencies {
  // Keep normal module metadata/dependencies and select just the classified artifact.
  implementation("io.github.kakakakacat.zxing:zxing-android-scanner:$scannerVersion") {
    artifact { classifier = abi; type = "aar"; extension = "aar" }
  }
}
tasks.register("verifyAbiDependency") {
  doLast {
    val runtime = configurations.getByName("releaseRuntimeClasspath")
    val components = runtime.incoming.resolutionResult.allComponents.mapNotNull {
      (it.id as? ModuleComponentIdentifier)?.let { id -> "${id.group}:${id.module}" }
    }.toSet()
    listOf("io.github.kakakakacat.zxing:zxing-core", "androidx.camera:camera-core",
        "androidx.camera:camera-camera2", "androidx.camera:camera-lifecycle").forEach {
      check(it in components) { "Classifier lost transitive dependency: $it" }
    }
    val scannerFiles = runtime.files.filter { it.name.startsWith("zxing-android-scanner-") }
    check(scannerFiles.size == 1 && scannerFiles.single().name.endsWith("-$abi.aar")) {
      "Expected only the selected classifier AAR, got $scannerFiles"
    }
    ZipFile(scannerFiles.single()).use { zip ->
      val native = zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".so") }
          .map { it.name }.toList()
      check(native.isNotEmpty() && native.all { it.startsWith("jni/$abi/") }) {
        "Wrong native payload for $abi: $native"
      }
    }
    logger.lifecycle("$abi classifier and core/CameraX transitive dependencies verified")
  }
}
