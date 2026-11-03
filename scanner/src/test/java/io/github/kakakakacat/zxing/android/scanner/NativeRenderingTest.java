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
import static org.junit.Assert.assertNull;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.oned.UPCEReader;

import org.junit.Test;

/**
 * Turning zxing-cpp's spelling of a symbol back into this library's.
 *
 * <p>These two conversions exist because the corpus run in {@code BENCHMARKS.md} §9.1
 * found 48 samples — every Codabar and every UPC-E in it — where the native backend read
 * the symbol correctly and returned a string no existing caller would recognise. They are
 * pure functions over a decoded string, so they are tested here rather than on a device.
 */
public final class NativeRenderingTest {

  @Test
  public void upcEComesBackAsThePrintedEightDigits() {
    // The three samples quoted in §9.1, as the native backend rendered them.
    assertEquals("01234565", NativeFrameDecoder.compactUpcE("0012345000065"));
    assertEquals("00123457", NativeFrameDecoder.compactUpcE("0001234000057"));
    assertEquals("01234531", NativeFrameDecoder.compactUpcE("0012300000451"));
  }

  @Test
  public void theTwelveDigitFormIsAcceptedToo() {
    // zxing-cpp pads to 13 with the GS1 leading zero; the same symbol as UPC-A is 12.
    assertEquals("01234565", NativeFrameDecoder.compactUpcE("012345000065"));
  }

  @Test
  public void compactingAndExpandingAgainAlwaysNamesTheSameProduct() {
    // The expansion is not injective -- 9% of the two million possible UPC-E strings
    // share a UPC-A with another one -- so compacting cannot always return the exact
    // spelling the symbol carried. What it must never do is return a code for a
    // different product, and that is checked here over every one of them.
    int differentSpelling = 0;
    for (char numberSystem : new char[] { '0', '1' }) {
      for (int body = 0; body < 1000000; body++) {
        String upce = numberSystem + String.format("%06d", body) + '0';
        String upcA = UPCEReader.convertUPCEtoUPCA(upce);
        String compacted = NativeFrameDecoder.compactUpcE(upcA);
        assertEquals(upcA, UPCEReader.convertUPCEtoUPCA(compacted));
        if (!upce.equals(compacted)) {
          differentSpelling++;
        }
      }
    }
    assertEquals(180000, differentSpelling);
  }

  @Test
  public void aStringThatIsNotACompressedUpcEIsLeftAlone() {
    // Better a string the caller can see is odd than one this code has rearranged.
    assertEquals("123456789012", NativeFrameDecoder.compactUpcE("123456789012"));
    assertEquals("12345", NativeFrameDecoder.compactUpcE("12345"));
    assertEquals("01234a000065", NativeFrameDecoder.compactUpcE("01234a000065"));
  }

  @Test
  public void theVariantNamesZxingCppActuallyReportsAreRecognised() {
    // What you ask zxing-cpp for is a symbology; what it hands back is the variant the
    // symbol turned out to be. Knowing only the request spelling meant a symbol it read
    // perfectly was reported as "nothing here" -- every RSS-14 sample in the corpus and
    // 59 of the RSS-Expanded ones.
    assertEquals(BarcodeFormat.RSS_14, NativeFrameDecoder.formatFromNativeName("DataBar"));
    assertEquals(BarcodeFormat.RSS_14,
                 NativeFrameDecoder.formatFromNativeName("DataBar Omni"));
    assertEquals(BarcodeFormat.RSS_14,
                 NativeFrameDecoder.formatFromNativeName("DataBar Stacked"));
    assertEquals(BarcodeFormat.RSS_14,
                 NativeFrameDecoder.formatFromNativeName("DataBar Stacked Omni"));
    assertEquals(BarcodeFormat.RSS_EXPANDED,
                 NativeFrameDecoder.formatFromNativeName("DataBar Expanded"));
    assertEquals(BarcodeFormat.RSS_EXPANDED,
                 NativeFrameDecoder.formatFromNativeName("DataBar Expanded Stacked"));
    assertEquals(BarcodeFormat.PDF_417,
                 NativeFrameDecoder.formatFromNativeName("Compact PDF417"));
    assertEquals(BarcodeFormat.CODE_39,
                 NativeFrameDecoder.formatFromNativeName("Code 39 Extended"));
    assertEquals(BarcodeFormat.QR_CODE,
                 NativeFrameDecoder.formatFromNativeName("QR Code Model 2"));
    assertEquals(BarcodeFormat.ITF, NativeFrameDecoder.formatFromNativeName("ITF-14"));
    assertEquals(BarcodeFormat.AZTEC, NativeFrameDecoder.formatFromNativeName("Aztec Code"));
  }

  @Test
  public void aVariantThisLibraryCannotNameIsStillNotRecognised() {
    // Deliberate. A caller cannot switch on a format this library does not define, so
    // claiming the nearest one would be worse than reporting nothing.
    assertNull(NativeFrameDecoder.formatFromNativeName("DataBar Limited"));
    assertNull(NativeFrameDecoder.formatFromNativeName("MicroPDF417"));
    assertNull(NativeFrameDecoder.formatFromNativeName("Micro QR Code"));
    assertNull(NativeFrameDecoder.formatFromNativeName("rMQR Code"));
    assertNull(NativeFrameDecoder.formatFromNativeName("DX Film Edge"));
  }

  @Test
  public void rss14LosesTheGs1ApplicationIdentifier() {
    assertEquals("04412345678909",
                 NativeFrameDecoder.withoutGs1GtinPrefix("0104412345678909"));
    assertEquals("02001234567893",
                 NativeFrameDecoder.withoutGs1GtinPrefix("0102001234567893"));
  }

  @Test
  public void anythingThatIsNotThatShapeKeepsItsDigits() {
    // 14 digits that happen to start with 01 are a GTIN, not a GTIN behind an AI.
    assertEquals("04412345678909",
                 NativeFrameDecoder.withoutGs1GtinPrefix("04412345678909"));
    assertEquals("0104412345678909x",
                 NativeFrameDecoder.withoutGs1GtinPrefix("0104412345678909x"));
    assertEquals("0204412345678909",
                 NativeFrameDecoder.withoutGs1GtinPrefix("0204412345678909"));
  }

  @Test
  public void codabarLosesItsStartAndStopCharacters() {
    assertEquals("1234567890", NativeFrameDecoder.withoutCodabarGuards("A1234567890A"));
    assertEquals("294/586", NativeFrameDecoder.withoutCodabarGuards("A294/586B"));
    assertEquals("1234", NativeFrameDecoder.withoutCodabarGuards("c1234d"));
  }

  @Test
  public void aCodabarStringWithoutGuardsIsNotTrimmedTwice() {
    // Running this over an already-trimmed string would eat two real characters. The
    // guard alphabet is A-D, so a payload can legitimately start with one.
    assertEquals("1234567890", NativeFrameDecoder.withoutCodabarGuards("1234567890"));
    assertEquals("12", NativeFrameDecoder.withoutCodabarGuards("12"));
    assertEquals("A1", NativeFrameDecoder.withoutCodabarGuards("A1"));
  }

  @Test
  public void anEciShiftsTheQrSymbologyModifierAsIsoSaysItShould() {
    // zxing-cpp knows the offset and does not apply it: Barcode.symbologyIdentifier()
    // calls SymbologyIdentifier.toString() with hasECI defaulted to false. A QR with an
    // ECI therefore arrives as ]Q1 where this fork's own reader says ]Q2.
    assertEquals("]Q2", NativeFrameDecoder.symbologyIdentifier(
        "]Q1", BarcodeFormat.QR_CODE, true));
    assertEquals("]Q4", NativeFrameDecoder.symbologyIdentifier(
        "]Q3", BarcodeFormat.QR_CODE, true));
    assertEquals("]Q6", NativeFrameDecoder.symbologyIdentifier(
        "]Q5", BarcodeFormat.QR_CODE, true));
  }

  @Test
  public void withoutAnEciTheIdentifierIsPassedThroughUnchanged() {
    assertEquals("]Q1", NativeFrameDecoder.symbologyIdentifier(
        "]Q1", BarcodeFormat.QR_CODE, false));
    assertEquals("]C0", NativeFrameDecoder.symbologyIdentifier(
        "]C0", BarcodeFormat.CODE_128, false));
    assertEquals("]e0", NativeFrameDecoder.symbologyIdentifier(
        "]e0", BarcodeFormat.RSS_14, false));
  }

  @Test
  public void aSymbologyWhoseEciOffsetIsUnknownReportsNoIdentifier() {
    // Data Matrix shifts its modifier by 4, Aztec by something else again, and zxing-cpp
    // exposes none of them. An unshifted identifier under the right-looking name would be
    // a different symbol's identifier, so there is none rather than a wrong one.
    assertNull(NativeFrameDecoder.symbologyIdentifier(
        "]d1", BarcodeFormat.DATA_MATRIX, true));
    assertNull(NativeFrameDecoder.symbologyIdentifier(
        "]z0", BarcodeFormat.AZTEC, true));
    // Model 1 QR reports ]Q0 and this fork has no Model 1 reader to agree with.
    assertNull(NativeFrameDecoder.symbologyIdentifier(
        "]Q0", BarcodeFormat.QR_CODE, true));
    assertNull(NativeFrameDecoder.symbologyIdentifier(null, BarcodeFormat.QR_CODE, false));
    assertNull(NativeFrameDecoder.symbologyIdentifier("", BarcodeFormat.QR_CODE, false));
  }
}
