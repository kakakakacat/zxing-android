package io.github.kakakakacat.zxing.benchmark;

import android.os.Process;
import android.util.Log;
import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.platform.app.InstrumentationRegistry;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Test-only local ROI pack benchmark; invoke once per backend and fresh process. */
@RunWith(Parameterized.class)
public final class RoiPackingBenchmark {
  private static final int[][] SIZES = {{640, 480}, {1280, 720}, {1920, 1080}};
  private static final String[] SIZE_NAMES = {"VGA", "HD", "FHD"};
  private static final String[] LAYOUTS = {"packed", "padded", "pixel2"};

  @Rule public final BenchmarkRule benchmarkRule = new BenchmarkRule();
  @Parameterized.Parameter(0) public int sizeIndex;
  @Parameterized.Parameter(1) public int layoutIndex;

  private ByteBuffer source;
  private byte[] destination;
  private int frameWidth, frameHeight, left, top, width, height, row, pixel, dstOffset;
  private String backend;
  private volatile int sink;

  @Parameterized.Parameters(name = "ROI_{0}_{1}")
  public static Collection<Object[]> cases() {
    ArrayList<Object[]> cases = new ArrayList<>();
    for (int size = 0; size < SIZES.length; size++)
      for (int layout = 0; layout < LAYOUTS.length; layout++)
        cases.add(new Object[] {size, layout});
    return cases;
  }

  @Before public void prepare() {
    backend = InstrumentationRegistry.getArguments().getString("roi.backend", "java");
    Assume.assumeTrue(backend.equals("java") || backend.equals("jni_cpp"));
    frameWidth = SIZES[sizeIndex][0];
    frameHeight = SIZES[sizeIndex][1];
    pixel = layoutIndex == 2 ? 2 : 1;
    int padding = layoutIndex == 0 ? 0 : 32;
    row = frameWidth * pixel + padding;
    width = frameWidth * 5 / 8;
    height = frameHeight * 5 / 8;
    left = (frameWidth - width) / 2;
    top = (frameHeight - height) / 2;
    dstOffset = 13;
    source = ByteBuffer.allocateDirect(row * frameHeight);
    for (int y = 0; y < frameHeight; y++) {
      for (int x = 0; x < frameWidth; x++)
        source.put(y * row + x * pixel, (byte) ((x * 13 + y * 7 + 17) & 255));
    }
    destination = new byte[dstOffset + width * height + 19];
    byte[] expected = new byte[width * height];
    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
      expected[y * width + x] = (byte) (((left + x) * 13 + (top + y) * 7 + 17) & 255);
    boolean arrayCopied = RoiPacking.nativeArrayCopyProbe(destination);
    Log.i("RoiPackingBenchmark", "backend=" + backend + " case=" + SIZE_NAMES[sizeIndex]
        + "/" + LAYOUTS[layoutIndex] + " process64Bit=" + Process.is64Bit()
        + " abi=" + android.os.Build.SUPPORTED_ABIS[0]
        + " sourceSha256=" + sha256(source) + " expectedSha256=" + sha256(expected)
        + " jniArrayCopyProbe=" + arrayCopied);
  }

  private static String sha256(ByteBuffer input) {
    ByteBuffer bytes = input.duplicate();
    bytes.clear();
    byte[] contents = new byte[bytes.remaining()];
    bytes.get(contents);
    return sha256(contents);
  }

  private static String sha256(byte[] input) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
      StringBuilder value = new StringBuilder(64);
      for (byte b : digest) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
      return value.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  @Test public void pack() {
    BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) {
      boolean packed = backend.equals("java")
          ? RoiPacking.packJava(source, destination, dstOffset, frameWidth, frameHeight,
              left, top, width, height, row, pixel)
          : RoiPacking.packNative(source, destination, dstOffset, frameWidth, frameHeight,
              left, top, width, height, row, pixel);
      if (!packed) throw new AssertionError("valid ROI rejected");
      sink = destination[dstOffset] & 0xff;
    }
  }
}
