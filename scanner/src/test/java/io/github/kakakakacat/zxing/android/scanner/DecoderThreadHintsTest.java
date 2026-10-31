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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.DecodeHintType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.junit.Test;

/**
 * How a format selection becomes a hint map.
 *
 * <p>This decides how much work every preview frame costs -- on the reference device a
 * miss frame is 81 ms with every format enabled and 1.2 ms with one 1D format -- so the
 * rule is worth pinning down away from a camera.
 */
public final class DecoderThreadHintsTest {

  private static Map<DecodeHintType,Object> fixed() {
    Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
    hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    return hints;
  }

  @Test
  public void aSelectionBecomesPossibleFormats() {
    Collection<BarcodeFormat> formats = Arrays.asList(BarcodeFormat.QR_CODE);
    Map<DecodeHintType,Object> hints = DecoderThread.hintsFor(fixed(), formats);
    assertSame(formats, hints.get(DecodeHintType.POSSIBLE_FORMATS));
  }

  @Test
  public void noSelectionMeansEveryFormat() {
    // Leaving POSSIBLE_FORMATS out is how MultiFormatReader is told "try everything".
    // Putting an empty collection there instead would mean "try nothing", and no frame
    // would ever decode.
    assertFalse(DecoderThread.hintsFor(fixed(), null)
        .containsKey(DecodeHintType.POSSIBLE_FORMATS));
    assertFalse(DecoderThread.hintsFor(fixed(), Collections.<BarcodeFormat>emptyList())
        .containsKey(DecodeHintType.POSSIBLE_FORMATS));
  }

  @Test
  public void theFixedHintsSurviveAFormatChange() {
    Map<DecodeHintType,Object> hints =
        DecoderThread.hintsFor(fixed(), Arrays.asList(BarcodeFormat.EAN_13));
    assertEquals("UTF-8", hints.get(DecodeHintType.CHARACTER_SET));
    assertEquals(Boolean.TRUE, hints.get(DecodeHintType.TRY_HARDER));
  }

  @Test
  public void theFixedHintsSurviveAChangeBackToEveryFormat() {
    Map<DecodeHintType,Object> hints = DecoderThread.hintsFor(fixed(), null);
    assertEquals("UTF-8", hints.get(DecodeHintType.CHARACTER_SET));
    assertEquals(Boolean.TRUE, hints.get(DecodeHintType.TRY_HARDER));
  }

  @Test
  public void theFixedHintsAreNotMutatedByBuildingAMap() {
    Map<DecodeHintType,Object> fixed = fixed();
    DecoderThread.hintsFor(fixed, Arrays.asList(BarcodeFormat.QR_CODE));
    assertFalse("building a hint map must not write back into the fixed hints",
        fixed.containsKey(DecodeHintType.POSSIBLE_FORMATS));
  }

  @Test
  public void eachCallReturnsAnIndependentMap() {
    Map<DecodeHintType,Object> fixed = fixed();
    Map<DecodeHintType,Object> first =
        DecoderThread.hintsFor(fixed, Arrays.asList(BarcodeFormat.QR_CODE));
    Map<DecodeHintType,Object> second = DecoderThread.hintsFor(fixed, null);
    assertTrue(first.containsKey(DecodeHintType.POSSIBLE_FORMATS));
    assertFalse(second.containsKey(DecodeHintType.POSSIBLE_FORMATS));
  }
}
