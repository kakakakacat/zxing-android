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

package io.github.kakakakacat.zxing.android.camera

import io.github.kakakakacat.zxing.LuminanceSource

/**
 * One camera frame on its way to the decode thread, together with whatever has to happen
 * when the decoder is finished with it.
 *
 * The two backends own their buffers differently and that difference is the whole reason
 * this exists. Camera1 hands over a `byte[]` it has already finished with; CameraX hands
 * over an `ImageProxy` that holds a slot in a fixed-size buffer queue, and the camera stops
 * delivering frames once every slot is outstanding. [YPlaneLuminanceSource] reads that
 * buffer in place and copies nothing, so the image must stay open until the decode has
 * returned and must not stay open a moment longer.
 *
 * So the rule for whoever receives a frame is the same either way: decode, then [close],
 * exactly once, in a `finally`.
 */
internal interface ScanFrame {

  /**
   * The scan rectangle, ready to decode, or null when the crop could not be worked out —
   * which happens for the first frames of a session, before the frame size is known.
   *
   * Reading it is only valid before [close].
   */
  val source: LuminanceSource?

  /** Returns the frame to the camera. Called exactly once, after the decode has returned. */
  fun close()
}
