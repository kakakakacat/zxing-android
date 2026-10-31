/*
 * Copyright (C) 2026 ZXing Android maintenance fork contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.kakakakacat.zxing.android.camera;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.BinaryBitmap;
import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.MultiFormatReader;
import io.github.kakakakacat.zxing.MultiFormatWriter;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.common.BitMatrix;
import io.github.kakakakacat.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;

import org.junit.Test;

/**
 * Reading a camera luma plane without copying the frame.
 *
 * <p>Every case here is a shape a real {@code YUV_420_888} image can take: padded rows,
 * interleaved luma, a direct buffer with no accessible array, and a buffer whose position
 * someone else has moved. Each of them decodes to garbage rather than failing loudly if
 * it is assumed away, which is why they are pinned here rather than on a device.
 */
public final class YPlaneLuminanceSourceTest {

  private static final int IMAGE_WIDTH = 16;
  private static final int IMAGE_HEIGHT = 8;

  /** A plane whose every sample is a known function of its coordinates. */
  private static byte[] plane(int rowStride, int pixelStride) {
    byte[] data = new byte[rowStride * IMAGE_HEIGHT];
    for (int y = 0; y < IMAGE_HEIGHT; y++) {
      for (int x = 0; x < IMAGE_WIDTH; x++) {
        data[y * rowStride + x * pixelStride] = sample(x, y);
      }
    }
    return data;
  }

  private static byte sample(int x, int y) {
    return (byte) (y * IMAGE_WIDTH + x);
  }

  private static byte[] expectedCrop(int left, int top, int width, int height) {
    byte[] expected = new byte[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        expected[y * width + x] = sample(left + x, top + y);
      }
    }
    return expected;
  }

  @Test
  public void aTightlyPackedPlaneReadsBackExactly() {
    ByteBuffer buffer = ByteBuffer.wrap(plane(IMAGE_WIDTH, 1));
    LuminanceSource source =
        new YPlaneLuminanceSource(buffer, IMAGE_WIDTH, 1, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    assertArrayEquals(expectedCrop(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT), source.getMatrix());
  }

  @Test
  public void rowPaddingIsSkipped() {
    // A row stride wider than the image is the common case on real hardware. Treating
    // the plane as tightly packed shears the image by a few pixels per row.
    int rowStride = IMAGE_WIDTH + 11;
    ByteBuffer buffer = ByteBuffer.wrap(plane(rowStride, 1));
    LuminanceSource source =
        new YPlaneLuminanceSource(buffer, rowStride, 1, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    assertArrayEquals(expectedCrop(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT), source.getMatrix());
  }

  @Test
  public void interleavedLumaIsSkipped() {
    // Legal under YUV_420_888 and produced by some semi-planar layouts.
    int pixelStride = 2;
    int rowStride = IMAGE_WIDTH * pixelStride;
    ByteBuffer buffer = ByteBuffer.wrap(plane(rowStride, pixelStride));
    LuminanceSource source =
        new YPlaneLuminanceSource(buffer, rowStride, pixelStride, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    assertArrayEquals(expectedCrop(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT), source.getMatrix());
  }

  @Test
  public void aCropReadsOnlyTheScanRectangle() {
    int rowStride = IMAGE_WIDTH + 4;
    ByteBuffer buffer = ByteBuffer.wrap(plane(rowStride, 1));
    LuminanceSource source =
        new YPlaneLuminanceSource(buffer, rowStride, 1, 3, 2, 6, 4);
    assertEquals(6, source.getWidth());
    assertEquals(4, source.getHeight());
    assertArrayEquals(expectedCrop(3, 2, 6, 4), source.getMatrix());
  }

  @Test
  public void croppingAgainComposesTheOffsets() {
    ByteBuffer buffer = ByteBuffer.wrap(plane(IMAGE_WIDTH, 1));
    LuminanceSource source =
        new YPlaneLuminanceSource(buffer, IMAGE_WIDTH, 1, 2, 1, 10, 6);
    assertTrue(source.isCropSupported());
    LuminanceSource inner = source.crop(3, 2, 4, 3);
    assertArrayEquals(expectedCrop(5, 3, 4, 3), inner.getMatrix());
  }

  @Test
  public void getRowMatchesTheMatrixRowByRow() {
    int rowStride = IMAGE_WIDTH + 7;
    ByteBuffer buffer = ByteBuffer.wrap(plane(rowStride, 1));
    LuminanceSource source = new YPlaneLuminanceSource(buffer, rowStride, 1, 2, 1, 9, 5);
    byte[] matrix = source.getMatrix();
    byte[] row = null;
    for (int y = 0; y < source.getHeight(); y++) {
      row = source.getRow(y, row);
      for (int x = 0; x < source.getWidth(); x++) {
        assertEquals("row " + y + " column " + x,
                     matrix[y * source.getWidth() + x], row[x]);
      }
    }
  }

  @Test
  public void getRowReusesAnAdequateArrayAndReplacesASmallOne() {
    ByteBuffer buffer = ByteBuffer.wrap(plane(IMAGE_WIDTH, 1));
    LuminanceSource source = new YPlaneLuminanceSource(buffer, IMAGE_WIDTH, 1, 0, 0, 8, 4);
    byte[] big = new byte[64];
    assertTrue("an array at least as wide as the row must be reused",
               big == source.getRow(0, big));
    byte[] small = new byte[2];
    assertTrue("too small an array must not be written past its end",
               small != source.getRow(0, small));
  }

  @Test
  public void aDirectBufferWithNoBackingArrayReadsTheSame() {
    int rowStride = IMAGE_WIDTH + 5;
    ByteBuffer direct = ByteBuffer.allocateDirect(rowStride * IMAGE_HEIGHT);
    direct.put(plane(rowStride, 1));
    direct.rewind();
    LuminanceSource source =
        new YPlaneLuminanceSource(direct, rowStride, 1, 1, 1, 12, 6);
    assertArrayEquals(expectedCrop(1, 1, 12, 6), source.getMatrix());
  }

  @Test
  public void readingDoesNotDisturbTheBuffersPosition() {
    // The camera hands out one buffer; moving its cursor would corrupt anything else
    // reading the same image, and would make a second getMatrix() return different bytes.
    ByteBuffer buffer = ByteBuffer.wrap(plane(IMAGE_WIDTH, 1));
    buffer.position(5);
    LuminanceSource source =
        new YPlaneLuminanceSource(buffer, IMAGE_WIDTH, 1, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    byte[] first = source.getMatrix();
    assertEquals("position must be untouched", 5, buffer.position());
    assertArrayEquals("a second read must return the same pixels", first, source.getMatrix());
  }

  @Test
  public void aDirectBuffersPositionIsAlsoUntouched() {
    ByteBuffer direct = ByteBuffer.allocateDirect(IMAGE_WIDTH * IMAGE_HEIGHT);
    direct.put(plane(IMAGE_WIDTH, 1));
    direct.position(7);
    LuminanceSource source =
        new YPlaneLuminanceSource(direct, IMAGE_WIDTH, 1, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    source.getMatrix();
    assertEquals(7, direct.position());
  }

  @Test
  public void detachGivesTheSamePixelsWithoutTheCamerasBuffer() {
    int rowStride = IMAGE_WIDTH + 3;
    ByteBuffer buffer = ByteBuffer.wrap(plane(rowStride, 1));
    YPlaneLuminanceSource source =
        new YPlaneLuminanceSource(buffer, rowStride, 1, 2, 1, 8, 5);
    PlanarYUVLuminanceSource detached = source.detach();
    assertEquals(8, detached.getWidth());
    assertEquals(5, detached.getHeight());
    assertArrayEquals(source.getMatrix(), detached.getMatrix());
  }

  @Test
  public void aRectangleRunningPastThePlaneIsRejectedAtConstruction() {
    ByteBuffer buffer = ByteBuffer.wrap(plane(IMAGE_WIDTH, 1));
    try {
      new YPlaneLuminanceSource(buffer, IMAGE_WIDTH, 1, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT + 1);
      fail("a crop taller than the plane must not be accepted");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("past the end"));
    }
  }

  @Test
  public void anEmptyOrNegativeRectangleIsRejected() {
    ByteBuffer buffer = ByteBuffer.wrap(plane(IMAGE_WIDTH, 1));
    try {
      new YPlaneLuminanceSource(buffer, IMAGE_WIDTH, 1, 0, 0, 0, 4);
      fail("an empty rectangle must not be accepted");
    } catch (IllegalArgumentException expected) {
      // expected
    }
    try {
      new YPlaneLuminanceSource(buffer, IMAGE_WIDTH, 1, -1, 0, 4, 4);
      fail("a rectangle starting outside the image must not be accepted");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  /**
   * A real QR code, laid into a plane with the awkward shapes a camera can hand over,
   * decoded through the actual reader. Pixel equality above says the bytes are right;
   * this says the decoder can still read them.
   */
  private static void decodesThroughTheRealReader(int rowStride, int pixelStride,
                                                  int margin) throws Exception {
    String payload = "ZX-YPLANE-" + rowStride + "-" + pixelStride;
    int size = 120;
    BitMatrix matrix = new MultiFormatWriter()
        .encode(payload, BarcodeFormat.QR_CODE, size, size);

    int imageWidth = size + 2 * margin;
    int imageHeight = size + 2 * margin;
    int stride = Math.max(rowStride, imageWidth * pixelStride);
    byte[] plane = new byte[stride * imageHeight];
    // Fill with white, including the padding, so a shear would be visible rather than
    // hidden behind zeros.
    java.util.Arrays.fill(plane, (byte) 0xFF);
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        plane[(y + margin) * stride + (x + margin) * pixelStride] =
            matrix.get(x, y) ? (byte) 0x00 : (byte) 0xFF;
      }
    }

    LuminanceSource source = new YPlaneLuminanceSource(
        ByteBuffer.wrap(plane), stride, pixelStride, margin, margin, size, size);
    Result result = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)));
    assertEquals(payload, result.getText());
  }

  @Test
  public void aQrCodeInATightlyPackedPlaneDecodes() throws Exception {
    decodesThroughTheRealReader(0, 1, 8);
  }

  @Test
  public void aQrCodeInAPaddedPlaneDecodes() throws Exception {
    decodesThroughTheRealReader(0, 1, 8);
    decodesThroughTheRealReader(200, 1, 8);
  }

  @Test
  public void aQrCodeInAnInterleavedPlaneDecodes() throws Exception {
    decodesThroughTheRealReader(0, 2, 8);
  }

  @Test
  public void aQrCodeInAPaddedInterleavedPlaneDecodes() throws Exception {
    decodesThroughTheRealReader(400, 2, 8);
  }

  @Test
  public void itAgreesWithPlanarYuvLuminanceSourceOnATightlyPackedPlane() {
    // The Camera1 path builds a PlanarYUVLuminanceSource over the same bytes. Where both
    // can describe the same frame they must produce identical pixels, or swapping camera
    // stacks would quietly change what the decoder sees.
    byte[] data = plane(IMAGE_WIDTH, 1);
    LuminanceSource planar =
        new PlanarYUVLuminanceSource(data, IMAGE_WIDTH, IMAGE_HEIGHT, 3, 2, 7, 4, false);
    LuminanceSource yPlane =
        new YPlaneLuminanceSource(ByteBuffer.wrap(data), IMAGE_WIDTH, 1, 3, 2, 7, 4);
    assertArrayEquals(planar.getMatrix(), yPlane.getMatrix());
  }
}
