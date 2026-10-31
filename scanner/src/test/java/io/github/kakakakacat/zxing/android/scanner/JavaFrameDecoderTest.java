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
import static org.junit.Assert.assertNull;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.DecodeHintType;
import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.MultiFormatWriter;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.common.BitMatrix;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.junit.Test;

/**
 * The contract every decoder backend has to meet, checked against the Java one.
 *
 * <p>The Java backend is the baseline a native backend will be compared against, so the
 * behaviour pinned here is what that comparison assumes on both sides.
 */
public final class JavaFrameDecoderTest {

  private static final int SIZE = 160;

  private static Map<DecodeHintType,Object> noHints() {
    return new EnumMap<>(DecodeHintType.class);
  }

  private static LuminanceSource frameWith(String payload, BarcodeFormat format)
      throws Exception {
    BitMatrix matrix = new MultiFormatWriter().encode(payload, format, SIZE, SIZE);
    byte[] luminance = new byte[SIZE * SIZE];
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        luminance[y * SIZE + x] = matrix.get(x, y) ? (byte) 0x00 : (byte) 0xFF;
      }
    }
    return new PlanarYUVLuminanceSource(luminance, SIZE, SIZE, 0, 0, SIZE, SIZE, false);
  }

  private static LuminanceSource blankFrame() {
    byte[] luminance = new byte[SIZE * SIZE];
    Arrays.fill(luminance, (byte) 0xFF);
    return new PlanarYUVLuminanceSource(luminance, SIZE, SIZE, 0, 0, SIZE, SIZE, false);
  }

  @Test
  public void aFrameWithABarcodeDecodes() throws Exception {
    FrameDecoder decoder = new JavaFrameDecoder(noHints(), null);
    Result result = decoder.decode(frameWith("ZX-SEAM", BarcodeFormat.QR_CODE));
    assertNotNull(result);
    assertEquals("ZX-SEAM", result.getText());
    assertEquals(BarcodeFormat.QR_CODE, result.getBarcodeFormat());
    decoder.close();
  }

  @Test
  public void anEmptyFrameReturnsNullRatherThanThrowing() {
    // Live preview misses far more often than it hits, so "nothing here" is a return
    // value. A backend that throws instead would make the common path the costly one.
    FrameDecoder decoder = new JavaFrameDecoder(noHints(), null);
    assertNull(decoder.decode(blankFrame()));
    decoder.close();
  }

  @Test
  public void aRestrictedFormatSetHidesOtherFormats() throws Exception {
    FrameDecoder decoder = new JavaFrameDecoder(noHints(), Arrays.asList(BarcodeFormat.EAN_13));
    assertNull("QR must not be returned when only EAN-13 was asked for",
               decoder.decode(frameWith("ZX-SEAM", BarcodeFormat.QR_CODE)));
    decoder.close();
  }

  @Test
  public void theFormatSetCanBeChangedBetweenFrames() throws Exception {
    LuminanceSource frame = frameWith("ZX-SEAM", BarcodeFormat.QR_CODE);
    FrameDecoder decoder = new JavaFrameDecoder(noHints(), null);
    assertNotNull(decoder.decode(frame));

    decoder.setFormats(Arrays.asList(BarcodeFormat.EAN_13));
    assertNull(decoder.decode(frame));

    decoder.setFormats(Arrays.asList(BarcodeFormat.QR_CODE));
    assertNotNull(decoder.decode(frame));

    decoder.setFormats(null);
    assertNotNull(decoder.decode(frame));
    decoder.close();
  }

  @Test
  public void anEmptyFormatSetMeansEveryFormatNotNoFormat() throws Exception {
    FrameDecoder decoder =
        new JavaFrameDecoder(noHints(), Collections.<BarcodeFormat>emptyList());
    assertNotNull(decoder.decode(frameWith("ZX-SEAM", BarcodeFormat.QR_CODE)));
    decoder.close();
  }

  @Test
  public void repeatedDecodesOfTheSameFrameAgree() throws Exception {
    // decodeWithState reuses readers between frames. If state leaked across frames the
    // second answer could differ from the first, which would make any A/B meaningless.
    LuminanceSource frame = frameWith("ZX-SEAM", BarcodeFormat.QR_CODE);
    FrameDecoder decoder = new JavaFrameDecoder(noHints(), null);
    String first = decoder.decode(frame).getText();
    for (int i = 0; i < 5; i++) {
      assertEquals(first, decoder.decode(frame).getText());
    }
    decoder.close();
  }

  @Test
  public void aMissBetweenTwoHitsDoesNotDisturbThem() throws Exception {
    LuminanceSource frame = frameWith("ZX-SEAM", BarcodeFormat.QR_CODE);
    FrameDecoder decoder = new JavaFrameDecoder(noHints(), null);
    assertNotNull(decoder.decode(frame));
    assertNull(decoder.decode(blankFrame()));
    assertNotNull(decoder.decode(frame));
    decoder.close();
  }

  @Test
  public void theBackendNamesItself() {
    FrameDecoder decoder = new JavaFrameDecoder(noHints(), null);
    assertEquals("zxing-java", decoder.name());
    decoder.close();
  }
}
