# The benchmark runs against a minified release build, so the code under measurement is
# the code the AAR consumer gets. Keep what the instrumentation reaches by reflection.
-dontobfuscate

-keep class io.github.kakakakacat.zxing.benchmark.** { *; }
-keep class io.github.kakakakacat.zxing.** { *; }
-keep class io.github.kakakakacat.zxing.android.** { *; }
