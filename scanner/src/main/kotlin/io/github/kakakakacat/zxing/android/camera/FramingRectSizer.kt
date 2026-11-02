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

/**
 * How big the viewfinder rectangle should be for a given window.
 *
 * Separated from [CameraManager] so the rule can be tested without a camera.
 *
 * The rule is upstream's: aim for five eighths of each dimension, never below
 * [MIN_FRAME], and cap it so that the frame cannot swell to fill a large screen — part of
 * what the frame is for is to make the user hold the device far enough away to focus.
 *
 * What changed for defect A-16 is which dimension each cap applies to. The two caps were
 * written as a width cap and a height cap, 1200 and 675, which are five eighths of 1920
 * and of 1080: the long and the short side of a landscape phone. `CaptureActivity` asks
 * for landscape, so that held — until Android 16, which ignores `screenOrientation` on a
 * large screen and gives the activity a portrait window anyway. There the width cap
 * applied to the *short* side and the height cap to the *long* one, so a 1600x2560 window
 * got a frame 62% of its width and **26%** of its height: a thin band, where a landscape
 * phone gets 62% of both.
 *
 * So the caps now follow the window's long and short axes instead of x and y. On any
 * landscape window that is exactly what it was before — the long axis *is* x — and the
 * numbers below are deliberately unchanged, because this is a fix for a case nobody had,
 * not a retuning of the case everybody has.
 */
internal object FramingRectSizer {

  private const val MIN_FRAME = 240

  /** Five eighths of 1920: the long side of the landscape screen these were chosen for. */
  private const val MAX_ALONG_LONG_AXIS = 1200

  /** Five eighths of 1080, the short side. */
  private const val MAX_ALONG_SHORT_AXIS = 675

  /**
   * @return the frame's width and height, in the same pixels as [screenWidth] and
   *   [screenHeight]
   */
  fun sizeFor(screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
    // A square window is treated as landscape, which is what the old code did with it.
    val portrait = screenHeight > screenWidth
    val widthCap = if (portrait) MAX_ALONG_SHORT_AXIS else MAX_ALONG_LONG_AXIS
    val heightCap = if (portrait) MAX_ALONG_LONG_AXIS else MAX_ALONG_SHORT_AXIS
    return Pair(inRange(screenWidth, widthCap), inRange(screenHeight, heightCap))
  }

  private fun inRange(resolution: Int, hardMax: Int): Int {
    val dim = 5 * resolution / 8 // Target 5/8 of each dimension
    if (dim < MIN_FRAME) {
      return MIN_FRAME
    }
    return minOf(dim, hardMax)
  }
}
