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
}
