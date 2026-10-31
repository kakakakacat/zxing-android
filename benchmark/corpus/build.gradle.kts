// The parts of L1 that both runners must share: the manifest, the classification rules
// and the result envelope.
//
// L1 runs twice now -- on the host JVM against :core, and on a device against the
// decoder backends behind the :scanner seam. The two are only comparable if the samples,
// the scoring and the file format are literally the same code, so they live here rather
// than being written out a second time. Nothing in this module may depend on Android or
// on a decoder: it is consumed by a java-library and by an androidTest APK.
plugins {
  `java-library`
}

java {
  // Consumed by :benchmark-android, so it has to compile into an Android test APK.
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  testImplementation(libs.junit)
}
