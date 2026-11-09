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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.DecodeHintType;
import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.MultiFormatWriter;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.common.BitMatrix;
import io.github.kakakakacat.zxing.android.camera.LumaFrameData;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;

/**
 * The zxing-cpp backend, on the device's own ART and the device's own ABI.
 *
 * <p>Correctness only. <strong>No timing is taken here and none may be</strong>: an
 * instrumentation test is not a benchmark, and comparing the two backends is phase 5
 * item 10's job under the gates of {@code docs/maint/BENCHMARKS.md} §10.6.
 *
 * <p>The native library is built for {@code arm64-v8a} and {@code armeabi-v7a} only, so
 * on any other ABI these are skipped rather than failed — that is the fallback path
 * working, not a defect.
 */
@RunWith(AndroidJUnit4.class)
public final class NativeFrameDecoderTest {

  private static final int SIZE = 200;

  @Before
  public void requireTheNativeLibrary() {
    Assume.assumeTrue(
        "libzxingnative is not loadable on this ABI (" + Arrays.toString(Build.SUPPORTED_ABIS)
            + "), which is the documented fallback case",
        NativeFrameDecoder.isAvailable());
  }

  /**
   * A frame holding one barcode.
   *
   * <p>The writer decides the matrix shape: a 1D symbol or a PDF417 comes back far wider
   * than it is tall, whatever height was requested. The frame therefore follows the
   * matrix rather than assuming it is square.
   */
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

  private static LuminanceSource blankFrame() {
    byte[] luminance = new byte[SIZE * SIZE];
    Arrays.fill(luminance, (byte) 0xFF);
    return new PlanarYUVLuminanceSource(luminance, SIZE, SIZE, 0, 0, SIZE, SIZE, false);
  }

  @Test
  public void itDecodesAQrCode() throws Exception {
    FrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      Result result = decoder.decode(frameWith("ZX-NATIVE", BarcodeFormat.QR_CODE));
      assertNotNull("the native backend found nothing in a clean QR frame", result);
      assertEquals("ZX-NATIVE", result.getText());
      assertEquals(BarcodeFormat.QR_CODE, result.getBarcodeFormat());
    } finally {
      decoder.close();
    }
  }

  @Test
  public void arrayRoiPreservesNativeResultsAcrossPaddedCrops() throws Exception {
    BarcodeFormat[] formats = {BarcodeFormat.UPC_A, BarcodeFormat.QR_CODE};
    String[] payloads = {"012345678905", "ARRAY-ROI"};
    FrameDecoder baseline = new NativeFrameDecoder(null, null, true, false, null, false);
    FrameDecoder candidate = new NativeFrameDecoder(null, null, true);
    try {
      for (int i = 0; i < formats.length; i++) {
        BitMatrix bits = new MultiFormatWriter().encode(payloads[i], formats[i], SIZE, SIZE);
        int imageWidth = bits.getWidth() + 19;
        int imageHeight = bits.getHeight() + 17;
        byte[] pixels = new byte[imageWidth * imageHeight];
        Arrays.fill(pixels, (byte) 0xff);
        for (int y = 0; y < bits.getHeight(); y++) {
          for (int x = 0; x < bits.getWidth(); x++) {
            if (bits.get(x, y)) pixels[(y + 9) * imageWidth + x + 7] = 0;
          }
        }
        LuminanceSource source = new PlanarYUVLuminanceSource(pixels, imageWidth,
            imageHeight, 7, 9, bits.getWidth(), bits.getHeight(), false);
        LumaFrameData data = LumaFrameData.Companion.array(pixels, imageWidth, imageHeight,
            imageWidth, 1, 7, 9, bits.getWidth(), bits.getHeight(), 0, 0L);
        Result expected = baseline.decode(source);
        Result actual = ((FrameDataDecoder) candidate).decode(data, source);
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.getText(), actual.getText());
        assertEquals(expected.getBarcodeFormat(), actual.getBarcodeFormat());
        assertArrayEquals(expected.getRawBytes(), actual.getRawBytes());
        assertEquals(expected.getResultMetadata(), actual.getResultMetadata());
      }
    } finally {
      baseline.close();
      candidate.close();
    }
  }

  @Test
  public void itAgreesWithTheJavaBackendOnTheSameFrames() throws Exception {
    // Not a timing comparison -- an agreement check. If the two backends disagree about
    // what a frame says, item 10's A/B would be comparing two different behaviours.
    BarcodeFormat[] formats = {
        BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128, BarcodeFormat.EAN_13,
        BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417,
    };
    String[] payloads = {"ZX-NATIVE", "ZX-NATIVE-128", "9780441014989", "ZX-DM", "ZX-AZ", "ZX-PDF"};

    FrameDecoder nativeDecoder = new NativeFrameDecoder(null);
    FrameDecoder javaDecoder =
        new JavaFrameDecoder(new EnumMap<DecodeHintType,Object>(DecodeHintType.class), null);
    try {
      for (int i = 0; i < formats.length; i++) {
        LuminanceSource frame = frameWith(payloads[i], formats[i]);
        Result fromJava = javaDecoder.decode(frame);
        Result fromNative = nativeDecoder.decode(frame);
        assertNotNull("the Java backend missed " + formats[i], fromJava);
        assertNotNull("the native backend missed " + formats[i], fromNative);
        assertEquals("text differs for " + formats[i],
                     fromJava.getText(), fromNative.getText());
        assertEquals("format differs for " + formats[i],
                     fromJava.getBarcodeFormat(), fromNative.getBarcodeFormat());
      }
    } finally {
      nativeDecoder.close();
      javaDecoder.close();
    }
  }

  @Test
  public void anEmptyFrameIsAMissNotAnException() {
    FrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      assertNull(decoder.decode(blankFrame()));
    } finally {
      decoder.close();
    }
  }

  @Test
  public void aRestrictedFormatSetIsHonouredByTheNativeReader() throws Exception {
    FrameDecoder decoder = new NativeFrameDecoder(Arrays.asList(BarcodeFormat.EAN_13));
    try {
      assertNull("QR must not come back when only EAN-13 was asked for",
                 decoder.decode(frameWith("ZX-NATIVE", BarcodeFormat.QR_CODE)));
    } finally {
      decoder.close();
    }
  }

  @Test
  public void everyMappedFormatNameIsAcceptedByZxingCpp() {
    // The mapping that fails silently in the worst direction: an unrecognised name would
    // widen the format set rather than narrow it. zxing-cpp throws on a name it cannot
    // parse and the JNI layer turns that into an IllegalArgumentException, so asking for
    // each format on its own proves every name in the table is really accepted.
    FrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      for (BarcodeFormat format : BarcodeFormat.values()) {
        if (format == BarcodeFormat.UPC_EAN_EXTENSION) {
          continue;  // covered by its own test below
        }
        String names = NativeFrameDecoder.nativeFormatList(Collections.singletonList(format));
        assertTrue("no zxing-cpp name mapped for " + format, names.length() > 0);
        decoder.setFormats(Collections.singletonList(format));
      }
    } finally {
      decoder.close();
    }
  }

  @Test
  public void aFormatZxingCppCannotBeAskedForIsRefusedRatherThanWidened() {
    // An EAN add-on is a property of a result in zxing-cpp, not a requestable format.
    // Sending an empty list would mean "every format", so a request for only this one
    // has to fail rather than quietly become the widest possible search.
    try {
      NativeFrameDecoder.nativeFormatList(
          Collections.singletonList(BarcodeFormat.UPC_EAN_EXTENSION));
      org.junit.Assert.fail("a wholly unsupported selection must not become 'everything'");
    } catch (UnsupportedOperationException expected) {
      // expected
    }
  }

  @Test
  public void anUnsupportedFormatIsDroppedFromASelectionThatHasOthers() {
    String names = NativeFrameDecoder.nativeFormatList(
        Arrays.asList(BarcodeFormat.QR_CODE, BarcodeFormat.UPC_EAN_EXTENSION));
    assertEquals("QRCode", names);
  }

  @Test
  public void resultNamesSurviveTheSpellingDifference() {
    // zxing-cpp accepts "QRCode" and reports "QR Code". Matching literal strings looks
    // correct and turns every successful decode into a miss.
    assertEquals(BarcodeFormat.QR_CODE, NativeFrameDecoder.formatFromNativeName("QR Code"));
    assertEquals(BarcodeFormat.QR_CODE, NativeFrameDecoder.formatFromNativeName("QRCode"));
    assertEquals(BarcodeFormat.EAN_13, NativeFrameDecoder.formatFromNativeName("EAN-13"));
    assertEquals(BarcodeFormat.DATA_MATRIX,
                 NativeFrameDecoder.formatFromNativeName("Data Matrix"));
    assertEquals(BarcodeFormat.RSS_14, NativeFrameDecoder.formatFromNativeName("DataBar"));
    assertNull(NativeFrameDecoder.formatFromNativeName("NotAFormat"));
  }

  /**
   * The payloads a format name table can lose a reader on.
   *
   * <p>zxing-cpp makes Code 32 (Italian pharmacode) and PZN variants of Code 39, and
   * `Code39Reader` reports one of them whenever the decoded <em>content</em> parses as
   * one — ahead of Code 39, and for Code 32 with the text rewritten. Neither has an
   * equivalent here, so the result was dropped and the frame came back as holding
   * nothing: an ordinary Code 39 symbol that this library simply could not read, while
   * the Java backend read it. These six are valid Code 32, and about 9% of six-character
   * Code 39 contents over Code 32's alphabet are.
   */
  private static final String[] CODE_39_ALIASING_ANOTHER_VARIANT = {
      "5J2451", "NWTNT4", "4NX7JF", "P65HG1", "8F92NF", "F0KDXU", "-10000001",
  };

  @Test
  public void aCode39WhoseContentParsesAsAnotherVariantIsStillACode39() throws Exception {
    FrameDecoder everything = new NativeFrameDecoder(null);
    FrameDecoder code39 = new NativeFrameDecoder(Arrays.asList(BarcodeFormat.CODE_39));
    try {
      for (String payload : CODE_39_ALIASING_ANOTHER_VARIANT) {
        LuminanceSource frame = frameWith(payload, BarcodeFormat.CODE_39);
        for (FrameDecoder decoder : new FrameDecoder[] { everything, code39 }) {
          Result result = decoder.decode(frame);
          assertNotNull("the native backend found nothing in a Code 39 frame: " + payload,
                        result);
          assertEquals(payload, result.getText());
          assertEquals(BarcodeFormat.CODE_39, result.getBarcodeFormat());
        }
      }
    } finally {
      everything.close();
      code39.close();
    }
  }

  @Test
  public void noFormatSelectionAsksForTheNameableFormatsRatherThanAllOfThem() {
    // An empty string means "every format" to zxing-cpp, and that includes symbologies
    // and variants this fork cannot name. A result in one of those is dropped, and
    // because the decoder is configured for a single symbol it also ends the search.
    //
    // Checked against the per-format spellings rather than a second copy of the table:
    // the expected list is what asking for every format this fork can name produces, so
    // a format added to the table on one side and not the other fails here.
    StringBuilder expected = new StringBuilder();
    for (BarcodeFormat format : BarcodeFormat.values()) {
      String one;
      try {
        one = NativeFrameDecoder.nativeFormatList(Collections.singletonList(format));
      } catch (UnsupportedOperationException noEquivalent) {
        continue; // zxing-cpp cannot be asked for this one; UPC_EAN_EXTENSION is the case.
      }
      if (expected.length() > 0) {
        expected.append(',');
      }
      expected.append(one);
    }
    String names = NativeFrameDecoder.nativeFormatList(null);
    assertTrue("a format selection must never be empty", names.length() > 0);
    assertEquals(expected.toString(), names);
    assertEquals(names, NativeFrameDecoder.nativeFormatList(Collections.emptyList()));
  }

  @Test
  public void theFormatSetCanBeChangedBetweenFrames() throws Exception {
    LuminanceSource frame = frameWith("ZX-NATIVE", BarcodeFormat.QR_CODE);
    FrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      assertNotNull(decoder.decode(frame));
      decoder.setFormats(Arrays.asList(BarcodeFormat.EAN_13));
      assertNull(decoder.decode(frame));
      decoder.setFormats(Arrays.asList(BarcodeFormat.QR_CODE));
      assertNotNull(decoder.decode(frame));
      decoder.setFormats(null);
      assertNotNull(decoder.decode(frame));
    } finally {
      decoder.close();
    }
  }

  @Test
  public void closingTwiceIsSafe() {
    FrameDecoder decoder = new NativeFrameDecoder(null);
    decoder.close();
    decoder.close();
  }

  @Test
  public void theLibraryWasBuiltWithNeonOnArm() {
    // zxing-cpp ships no NEON intrinsics; it relies on the auto-vectorizer and tests
    // __ARM_NEON to decide whether to pack a strided column before thresholding. If the
    // macro were undefined that path would be compiled out and nothing would say so.
    // The C++ side also refuses to compile without it on an ARM ABI; this is the same
    // fact confirmed on the artifact that actually loaded.
    String info = NativeFrameDecoder.buildInfo();
    assertTrue("unexpected build info: " + info, info.contains("abi="));
    assertTrue("C++20 build marker missing: " + info, info.contains("cxx=20"));
    assertTrue("no zxing-cpp version in: " + info, info.contains("zxingcpp=3.1.1"));
    if (info.contains("abi=arm64-v8a") || info.contains("abi=armeabi-v7a")) {
      assertTrue("built without NEON on an ARM ABI: " + info, info.contains("neon=1"));
    }
  }

  @Test
  public void theBackendNamesItself() {
    FrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      assertEquals("zxing-cpp", decoder.name());
    } finally {
      decoder.close();
    }
  }
}
