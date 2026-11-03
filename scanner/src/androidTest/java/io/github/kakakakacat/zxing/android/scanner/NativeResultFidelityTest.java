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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.DecodeHintType;
import io.github.kakakakacat.zxing.EncodeHintType;
import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.MultiFormatWriter;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.ResultMetadataType;
import io.github.kakakakacat.zxing.common.BitMatrix;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * What survives the JNI boundary: the text, the bytes, the metadata and the errors.
 *
 * <p>Every case here is one the old bridge could not state an answer for. It built every
 * string with {@code NewStringUTF}, which takes <em>modified</em> UTF-8 — so an embedded
 * {@code U+0000} and any supplementary-plane character were handed to it in an encoding
 * it does not accept, and what came back was the runtime's business rather than this
 * library's. It also returned no content bytes at all, and reported a decoder that threw
 * as a frame that held nothing.
 *
 * <p>On the device, because that is where the runtime that reads those strings is. The
 * native library is built for arm64-v8a, armeabi-v7a and x86_64, so on any other ABI
 * these skip rather than fail — the documented fallback, not a defect.
 */
@RunWith(AndroidJUnit4.class)
public final class NativeResultFidelityTest {

  private static final int SIZE = 400;

  @Before
  public void requireTheNativeLibrary() {
    Assume.assumeTrue(
        "libzxingnative is not loadable on this ABI (" + Arrays.toString(Build.SUPPORTED_ABIS)
            + "), which is the documented fallback case",
        NativeFrameDecoder.isAvailable());
  }

  /** A frame holding one symbol, encoded as UTF-8 so an ECI goes into the symbol. */
  private static LuminanceSource utf8Frame(String payload, BarcodeFormat format)
      throws Exception {
    Map<EncodeHintType,Object> hints = new EnumMap<>(EncodeHintType.class);
    hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
    return frame(payload, format, hints);
  }

  private static LuminanceSource frame(String payload, BarcodeFormat format,
                                       Map<EncodeHintType,Object> hints) throws Exception {
    BitMatrix matrix = new MultiFormatWriter().encode(payload, format, SIZE, SIZE, hints);
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

  private static Result decodeQr(String payload) throws Exception {
    NativeFrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      Result result = decoder.decode(utf8Frame(payload, BarcodeFormat.QR_CODE));
      assertNotNull("the native backend found nothing in a clean QR frame for "
                    + describe(payload), result);
      return result;
    } finally {
      decoder.close();
    }
  }

  /** Code points rather than the string itself: a failure message must survive logcat. */
  private static String describe(String s) {
    StringBuilder out = new StringBuilder(s.length() * 8);
    for (int i = 0; i < s.length(); i++) {
      out.append(String.format("U+%04X ", (int) s.charAt(i)));
    }
    return out.toString().trim();
  }

  @Test
  public void asciiComesBackUnchanged() throws Exception {
    assertEquals("ZX-NATIVE-ascii", decodeQr("ZX-NATIVE-ascii").getText());
  }

  @Test
  public void commonChineseComesBackUnchanged() throws Exception {
    String payload = "扫码测试-条形码";
    assertEquals(payload, decodeQr(payload).getText());
  }

  @Test
  public void aMixedLanguagePayloadComesBackUnchanged() throws Exception {
    String payload = "ZX 扫码 コード 코드 Ω é";
    assertEquals(payload, decodeQr(payload).getText());
  }

  @Test
  public void anEmojiSurvivesTheJniBoundary() throws Exception {
    // U+1F600, a supplementary-plane character: four bytes in standard UTF-8 and a
    // surrogate pair of three-byte sequences in the modified UTF-8 NewStringUTF takes.
    // Handing the first to the second is the case this test exists for.
    String payload = "ok 😀 ok";
    Result result = decodeQr(payload);
    assertEquals(describe(result.getText()), payload, result.getText());
    assertEquals("the surrogate pair did not survive as one code point",
                 1, result.getText().codePointCount(3, 5));
    assertEquals(0x1F600, result.getText().codePointAt(3));
  }

  @Test
  public void aSupplementaryPlaneHanCharacterSurvives() throws Exception {
    // U+20000, CJK extension B. Same four-byte shape, a character a host is more likely
    // to meet in a real payload than an emoji.
    String payload = "𠀀𠀁";
    Result result = decodeQr(payload);
    assertEquals(describe(result.getText()), payload, result.getText());
    assertEquals(0x20000, result.getText().codePointAt(0));
  }

  @Test
  public void anEmbeddedNulIsNotATruncation() throws Exception {
    // The whole reason this is dangerous: NewStringUTF reads to the first zero byte, and
    // standard UTF-8 spells U+0000 as one zero byte. A payload with a NUL in it would
    // come back cut off at the NUL, or not at all.
    String payload = "A\u0000B\u0000C";
    Result result = decodeQr(payload);
    assertEquals("the text was truncated at the embedded NUL: " + describe(result.getText()),
                 5, result.getText().length());
    assertEquals(payload, result.getText());
    assertArrayEquals("the content bytes lost the NUL",
                      new byte[] {'A', 0, 'B', 0, 'C'}, result.getRawBytes());
  }

  @Test
  public void aGs1SeparatorSurvivesInTheText() throws Exception {
    // ASCII 29, the GS1 field separator. zxing-cpp's default TextMode would render it as
    // "<GS>"; this library returns the character, and a host that parses GS1 needs it to
    // still be one character.
    String payload = "0112345678901231\u001D10BATCH7";
    Result result = decodeQr(payload);
    assertEquals(payload, result.getText());
    assertEquals(29, result.getText().charAt(16));
  }

  @Test
  public void aLongPayloadIsNotTruncated() throws Exception {
    StringBuilder payload = new StringBuilder();
    for (int i = 0; i < 300; i++) {
      payload.append((char) ('0' + (i % 10)));
    }
    Result result = decodeQr(payload.toString());
    assertEquals(300, result.getText().length());
    assertEquals(payload.toString(), result.getText());
  }

  @Test
  public void theContentBytesAreTheSymbolsAndNotTheStringsReEncoding() throws Exception {
    String payload = "扫码";
    Result result = decodeQr(payload);
    assertNotNull("no content bytes came back", result.getRawBytes());
    assertArrayEquals("the content bytes are the symbol's own, pre-conversion",
                      payload.getBytes(StandardCharsets.UTF_8), result.getRawBytes());
    assertEquals("numBits must describe the bytes that are there",
                 result.getRawBytes().length * 8, result.getNumBits());
  }

  @Test
  public void theContentBytesAreNotRewrittenByCompatibilityRendering() throws Exception {
    // UPC-E is reported as the eight printed digits rather than zxing-cpp's expanded
    // UPC-A. The bytes are the symbol's, so they keep the expansion -- rewriting them to
    // match the display string would be inventing a symbol that was never read.
    NativeFrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      Map<EncodeHintType,Object> none = new EnumMap<>(EncodeHintType.class);
      Result result = decoder.decode(frame("01234565", BarcodeFormat.UPC_E, none));
      assertNotNull("the native backend missed a clean UPC-E frame", result);
      assertEquals("01234565", result.getText());
      assertNotNull(result.getRawBytes());
      assertEquals("the bytes should be zxing-cpp's rendering, not the display string",
                   "0012345000065", new String(result.getRawBytes(), StandardCharsets.UTF_8));
    } finally {
      decoder.close();
    }
  }

  @Test
  public void aQrCarriesItsErrorCorrectionLevelAndSymbologyIdentifier() throws Exception {
    Result result = decodeQr("ZX-METADATA");
    assertEquals("L", result.getResultMetadata().get(
        ResultMetadataType.ERROR_CORRECTION_LEVEL));
    String symbology = (String) result.getResultMetadata().get(
        ResultMetadataType.SYMBOLOGY_IDENTIFIER);
    assertNotNull("no symbology identifier", symbology);
    assertTrue("a QR symbology identifier starts ]Q, got " + symbology,
               symbology.startsWith("]Q"));
  }

  @Test
  public void bothBackendsAgreeOnTextAndMetadataForTheSameSymbol() throws Exception {
    // The check that stops this from being a one-sided claim: if the two backends
    // disagree about what a symbol says, one of them is wrong about it.
    String payload = "ZX 扫码 😀";
    LuminanceSource frame = utf8Frame(payload, BarcodeFormat.QR_CODE);
    FrameDecoder nativeDecoder = new NativeFrameDecoder(null);
    FrameDecoder javaDecoder =
        new JavaFrameDecoder(new EnumMap<DecodeHintType,Object>(DecodeHintType.class), null);
    try {
      Result fromNative = nativeDecoder.decode(frame);
      Result fromJava = javaDecoder.decode(frame);
      assertNotNull("the native backend missed it", fromNative);
      assertNotNull("the Java backend missed it", fromJava);
      assertEquals(fromJava.getText(), fromNative.getText());
      assertEquals(fromJava.getResultMetadata().get(ResultMetadataType.ERROR_CORRECTION_LEVEL),
                   fromNative.getResultMetadata().get(
                       ResultMetadataType.ERROR_CORRECTION_LEVEL));
      assertEquals(fromJava.getResultMetadata().get(ResultMetadataType.SYMBOLOGY_IDENTIFIER),
                   fromNative.getResultMetadata().get(ResultMetadataType.SYMBOLOGY_IDENTIFIER));
    } finally {
      nativeDecoder.close();
      javaDecoder.close();
    }
  }

  @Test
  public void aFrameShorterThanItsDimensionsIsRefusedRatherThanRead() {
    // The out-of-bounds read this used to answer with a silent null. A silent null is
    // indistinguishable from an empty picture, and this is a caller defect.
    NativeFrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      // Claims 10x100 while holding ten bytes. PlanarYUVLuminanceSource cannot be made
      // to lie like this, which is the point: only a caller's own source can, and the
      // native side is what has to refuse it.
      final byte[] tooShort = new byte[10];
      decoder.decode(new LuminanceSource(10, 100) {
        @Override
        public byte[] getRow(int y, byte[] row) {
          return tooShort;
        }

        @Override
        public byte[] getMatrix() {
          return tooShort;
        }
      });
      fail("a frame whose dimensions do not describe its buffer must be refused");
    } catch (IllegalArgumentException expected) {
      assertTrue("the refusal should say what it was given: " + expected.getMessage(),
                 expected.getMessage().contains("10x100"));
    } finally {
      decoder.close();
    }
  }

  @Test
  public void diagnosticsSeparateAHitFromAMiss() throws Exception {
    NativeFrameDecoder decoder = new NativeFrameDecoder(null);
    try {
      assertTrue("a fresh decoder has done nothing",
                 decoder.diagnostics().startsWith("hits=0;misses=0;"));
      decoder.decode(utf8Frame("ZX-DIAG", BarcodeFormat.QR_CODE));
      byte[] blank = new byte[SIZE * SIZE];
      Arrays.fill(blank, (byte) 0xFF);
      decoder.decode(new PlanarYUVLuminanceSource(blank, SIZE, SIZE, 0, 0, SIZE, SIZE, false));
      String diagnostics = decoder.diagnostics();
      assertTrue("a hit and a miss should both be counted: " + diagnostics,
                 diagnostics.startsWith("hits=1;misses=1;"));
      assertTrue("nothing should have failed internally: " + diagnostics,
                 diagnostics.contains(";internalErrors=0"));
      assertTrue("no frame should have thrown: " + diagnostics,
                 diagnostics.endsWith(";decodeFailures=0"));
    } finally {
      decoder.close();
    }
  }
}
