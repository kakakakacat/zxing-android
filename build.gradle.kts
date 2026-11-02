plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  // L3's Macrobenchmark module. Resolved here so :benchmark-l3 can apply it by id:
  // AGP is already on the build classpath from the two above, and asking for it again
  // with a version there fails with "already on the classpath with an unknown version".
  alias(libs.plugins.android.test) apply false
}
