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

package io.github.kakakakacat.zxing.android.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.MultiFormatWriter;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.ResultPoint;
import io.github.kakakakacat.zxing.common.BitMatrix;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * What a caller receives now that the native decoder is the default.
 *
 * <p>The switch is a performance change and must not be a behaviour change. These are the
 * parts of the contract that zxing-cpp would otherwise alter.
 */
@RunWith(AndroidJUnit4.class)
public final class DefaultBackendTest {

  private static final int SIZE = 240;

  private static LuminanceSource frameWith(String payload, BarcodeFormat format)
      throws Exception {
    BitMatrix matrix = new MultiFormatWriter().encode(payload, format, SIZE, SIZE);
    int width = matrix.getWidth();
    int height = matrix.getHeight();
    byte[] luminance = new byte[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        luminance[y * width + x] = matrix.get(x, y) ? (byte) 0x00 : (byte) 0xFF;
      }
    }
    return new PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false);
  }

  @Test
  public void aUpcASymbolStillComesBackAsTwelveDigitUpcA() throws Exception {
    // zxing-cpp renders UPC-A as 13 digits and labels it EAN_13, citing ISO/IEC 15420.
    // Defensible, and not what this library has ever returned. Making the native decoder
    // the default must not change the string every host receives.
    Assume.assumeTrue(NativeFrameDecoder.isAvailable());
    FrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      Result result = decoder.decode(frameWith("012345678905", BarcodeFormat.UPC_A));
      assertNotNull("the native decoder missed a clean UPC-A frame", result);
      assertEquals("012345678905", result.getText());
      assertEquals(BarcodeFormat.UPC_A, result.getBarcodeFormat());
    } finally {
      decoder.close();
    }
  }

  @Test
  public void theRawNativeBehaviourIsStillReachableForMeasurement() throws Exception {
    Assume.assumeTrue(NativeFrameDecoder.isAvailable());
    FrameDecoder decoder = new NativeFrameDecoder(null, false);
    try {
      Result result = decoder.decode(frameWith("012345678905", BarcodeFormat.UPC_A));
      assertNotNull(result);
      assertEquals("0012345678905", result.getText());
    } finally {
      decoder.close();
    }
  }

  @Test
  public void aCodabarSymbolStillComesBackWithoutItsGuards() throws Exception {
    // zxing-cpp keeps the start and stop characters; CodaBarReader drops them unless
    // RETURN_CODABAR_START_END is asked for, and this library has never asked. Found on
    // the corpus: all 11 Codabar samples read correctly and returned "A1234567890A".
    Assume.assumeTrue(NativeFrameDecoder.isAvailable());
    FrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      Result result = decoder.decode(frameWith("A1234567890A", BarcodeFormat.CODABAR));
      assertNotNull("the native decoder missed a clean Codabar frame", result);
      assertEquals("1234567890", result.getText());
      assertEquals(BarcodeFormat.CODABAR, result.getBarcodeFormat());
    } finally {
      decoder.close();
    }
  }

  @Test
  public void aUpcESymbolStillComesBackAsItsEightPrintedDigits() throws Exception {
    // zxing-cpp expands UPC-E to its UPC-A equivalent and still labels it UPC_E: 37 of
    // the corpus's 55 UPC-E samples came back as "0012345000065" instead of "01234565".
    Assume.assumeTrue(NativeFrameDecoder.isAvailable());
    FrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      Result result = decoder.decode(frameWith("01234565", BarcodeFormat.UPC_E));
      assertNotNull("the native decoder missed a clean UPC-E frame", result);
      assertEquals("01234565", result.getText());
      assertEquals(BarcodeFormat.UPC_E, result.getBarcodeFormat());
    } finally {
      decoder.close();
    }
  }

  @Test
  public void aGroupSeparatorArrivesAsItselfRatherThanEscaped() throws Exception {
    // zxing-cpp's default TextMode::HRI renders ASCII 29 as "<GS>", which a host parsing
    // a GS1 payload cannot read. The JNI asks for TextMode::Plain instead.
    Assume.assumeTrue(NativeFrameDecoder.isAvailable());
    FrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      Result result = decoder.decode(frameWith("a\u001db", BarcodeFormat.QR_CODE));
      assertNotNull(result);
      assertEquals("a\u001db", result.getText());
    } finally {
      decoder.close();
    }
  }

  @Test
  public void aFinishedSymbolStillCarriesItsCorners() throws Exception {
    // The viewfinder's animated candidate points are lost with the native backend --
    // zxing-cpp has no streaming callback. The points of a found symbol, which is what
    // gets drawn on the result thumbnail, are not.
    Assume.assumeTrue(NativeFrameDecoder.isAvailable());
    FrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      Result result = decoder.decode(frameWith("ZX-POINTS", BarcodeFormat.QR_CODE));
      assertNotNull(result);
      ResultPoint[] points = result.getResultPoints();
      assertNotNull("a found symbol should report where it was", points);
      assertEquals(4, points.length);
      for (ResultPoint point : points) {
        assertTrue("corner outside the frame: " + point,
                   point.getX() >= 0 && point.getY() >= 0);
      }
    } finally {
      decoder.close();
    }
  }

  @Test
  public void bothBackendsReadTheSameUpcAStringNowThatCompatibilityIsOn() throws Exception {
    Assume.assumeTrue(NativeFrameDecoder.isAvailable());
    LuminanceSource frame = frameWith("012345678905", BarcodeFormat.UPC_A);
    FrameDecoder nativeDecoder = new NativeFrameDecoder(null);
    FrameDecoder javaDecoder = FrameDecoders.create(
        FrameDecoders.JAVA, null, Arrays.asList(BarcodeFormat.UPC_A, BarcodeFormat.EAN_13));
    try {
      Result fromNative = nativeDecoder.decode(frame);
      Result fromJava = javaDecoder.decode(frame);
      assertNotNull(fromJava);
      assertNotNull(fromNative);
      assertEquals(fromJava.getText(), fromNative.getText());
      assertEquals(fromJava.getBarcodeFormat(), fromNative.getBarcodeFormat());
    } finally {
      nativeDecoder.close();
      javaDecoder.close();
    }
  }
}
