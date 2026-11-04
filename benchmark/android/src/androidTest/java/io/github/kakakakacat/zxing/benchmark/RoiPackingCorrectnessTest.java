package io.github.kakakakacat.zxing.benchmark;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import org.junit.Test;

/** Byte-level contract tests for the test-only Java/JNI ROI packers. */
public final class RoiPackingCorrectnessTest {
  @Test public void javaAndNativeMatchAcrossLayoutsAndPreserveGuards() {
    for (int pixel : new int[] {1, 2}) {
      int frameWidth = 9, frameHeight = 7, row = frameWidth * pixel + 5;
      int position = 4, limit = row * (frameHeight - 1)
          + (frameWidth - 1) * pixel + 1;
      ByteBuffer input = ByteBuffer.allocateDirect(limit + 11);
      for (int i = 0; i < input.capacity(); i++) input.put(i, (byte) (i * 17 + 3));
      byte[] original = new byte[input.capacity()];
      for (int i = 0; i < original.length; i++) original[i] = input.get(i);
      input.position(position);
      input.limit(limit);
      ByteBuffer source = input.asReadOnlyBuffer();
      source.position(position);
      source.limit(limit);
      byte[] javaOut = guarded(5 + 5 * 5 + 12);
      byte[] nativeOut = javaOut.clone();

      assertTrue(RoiPacking.packJava(source, javaOut, 5, frameWidth, frameHeight,
          2, 1, 5, 5, row, pixel));
      assertTrue(RoiPacking.packNative(source, nativeOut, 5, frameWidth, frameHeight,
          2, 1, 5, 5, row, pixel));
      assertArrayEquals(javaOut, nativeOut);
      for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
        int sourceIndex = (1 + y) * row + (2 + x) * pixel;
        assertEquals(original[sourceIndex], javaOut[5 + y * 5 + x]);
      }
      ByteBuffer unchanged = input.duplicate();
      unchanged.clear();
      for (int i = 0; i < input.capacity(); i++) assertEquals(original[i], unchanged.get(i));
      assertGuards(javaOut, 5, 12, (byte) 0x5a);
      assertGuards(nativeOut, 5, 12, (byte) 0x5a);
      assertEquals(position, source.position());
      assertEquals(limit, source.limit());
    }
  }

  @Test public void oddCropAndLastRowWithoutTrailingPaddingAreValid() {
    int row = 11, base = 2;
    int length = row * 2 + 5; // Last row has no complete trailing padding.
    ByteBuffer src = ByteBuffer.allocateDirect(length);
    for (int i = 0; i < length; i++) src.put(i, (byte) i);
    byte[] out = new byte[15];
    src.position(base);
    src.limit(length);
    assertTrue(RoiPacking.packJava(src, out, 0, 5, 3, 1, 0, 3, 3, row, 1));
    byte[] nativeOut = new byte[15];
    assertTrue(RoiPacking.packNative(src, nativeOut, 0, 5, 3, 1, 0, 3, 3, row, 1));
    assertArrayEquals(out, nativeOut);
  }

  @Test public void rejectsInvalidInputsWithoutChangingDestinationOrBufferState() {
    ByteBuffer src = ByteBuffer.allocateDirect(32);
    src.position(3);
    src.limit(21);
    byte[] out = guarded(24);
    byte[] before = out.clone();
    assertFalse(RoiPacking.packJava(src, out, 0, 4, 2, 0, 0, 4, 2, 3, 1)); // row overrun
    assertFalse(RoiPacking.packNative(src, out, 0, 4, 2, 0, 0, 4, 2, 3, 1));
    assertFalse(RoiPacking.packJava(src, out, out.length - 2, 4, 2, 0, 0, 4, 2, 8, 1));
    assertFalse(RoiPacking.packNative(src, out, out.length - 2, 4, 2, 0, 0, 4, 2, 8, 1));
    assertFalse(RoiPacking.packJava(src, out, 0, Integer.MAX_VALUE, 2, 0, 0,
        Integer.MAX_VALUE, 1, 8, 2));
    assertFalse(RoiPacking.packNative(src, out, 0, Integer.MAX_VALUE, 2, 0, 0,
        Integer.MAX_VALUE, 1, 8, 2));
    assertArrayEquals(before, out);
    assertEquals(3, src.position());
    assertEquals(21, src.limit());
  }

  private static byte[] guarded(int size) {
    byte[] out = new byte[size];
    java.util.Arrays.fill(out, (byte) 0x5a);
    return out;
  }

  private static void assertGuards(byte[] actual, int offset, int suffix, byte value) {
    for (int i = 0; i < offset; i++) assertEquals(value, actual[i]);
    for (int i = actual.length - suffix; i < actual.length; i++) assertEquals(value, actual[i]);
  }
}
