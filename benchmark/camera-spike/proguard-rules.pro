# The spike's own entry point and the JNI-bound native decoder in :scanner. Everything else
# is free to be shrunk -- that is the point of measuring the APK with R8 on.
-keep class io.github.kakakakacat.zxing.spike.SpikeActivity { *; }
-keep class io.github.kakakakacat.zxing.android.scanner.NativeFrameDecoder { *; }
