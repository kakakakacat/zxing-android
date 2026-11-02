/*
 * Copyright (C) 2026 ZXing authors
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

package io.github.kakakakacat.zxing.android.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Defect A-16. The landscape cases pin what must not change; the portrait ones are the
 * fix.
 */
class FramingRectSizerTest {

  @Test
  fun landscapeFullHdIsUnchanged() {
    assertEquals(Pair(1200, 675), FramingRectSizer.sizeFor(1920, 1080))
  }

  @Test
  fun landscapeBelowTheCapsIsUnchanged() {
    assertEquals(Pair(800, 450), FramingRectSizer.sizeFor(1280, 720))
    assertEquals(Pair(400, 300), FramingRectSizer.sizeFor(640, 480))
  }

  @Test
  fun aSquareWindowCountsAsLandscape_asItAlwaysDid() {
    assertEquals(Pair(625, 625), FramingRectSizer.sizeFor(1000, 1000))
  }

  @Test
  fun theFloorHoldsOnATinyWindow() {
    assertEquals(Pair(240, 240), FramingRectSizer.sizeFor(320, 240))
  }

  @Test
  fun theLargeScreenPortraitCaseIsNoLongerAThinBand() {
    // The A-16 reproduction: 1600x2560 at 240dpi on an API 36 emulator.
    val (width, height) = FramingRectSizer.sizeFor(1600, 2560)
    assertEquals(Pair(675, 1200), Pair(width, height))
    // The old rule gave 1000 x 675 here: 62% of the width and 26% of the height.
    val heightFraction = height.toDouble() / 2560
    assertTrue("frame covered $heightFraction of the height", heightFraction > 0.40)
  }

  @Test
  fun portraitAndLandscapeCoverSimilarFractionsOfTheirWindow() {
    val (landscapeWidth, landscapeHeight) = FramingRectSizer.sizeFor(1920, 1080)
    val (portraitWidth, portraitHeight) = FramingRectSizer.sizeFor(1080, 1920)
    // The same screen, turned: the frame turns with it rather than staying a landscape
    // band in a portrait window.
    assertEquals(landscapeWidth, portraitHeight)
    assertEquals(landscapeHeight, portraitWidth)
  }

  @Test
  fun neitherDimensionEverExceedsTheWindow() {
    val sizes = listOf(320 to 240, 640 to 480, 1080 to 1920, 1600 to 2560, 2560 to 1600,
                       3840 to 2160, 240 to 320)
    for ((w, h) in sizes) {
      val (frameWidth, frameHeight) = FramingRectSizer.sizeFor(w, h)
      assertTrue("$w x $h -> width $frameWidth", frameWidth <= maxOf(w, 240))
      assertTrue("$w x $h -> height $frameHeight", frameHeight <= maxOf(h, 240))
    }
  }
}
