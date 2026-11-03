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

import android.graphics.Rect

/**
 * The part of an open camera that the overlay and the torch policy need.
 *
 * Phase 6 put a second camera stack behind [io.github.kakakakacat.zxing.android.scanner.ScannerView]
 * — CameraX beside the original Camera1 path — and this is the surface the rest of the
 * library talks to so that neither [ViewfinderView][io.github.kakakakacat.zxing.android.ViewfinderView]
 * nor [AmbientLightManager][io.github.kakakakacat.zxing.android.AmbientLightManager] has to
 * know which one is running.
 *
 * It is deliberately small. Everything about opening, closing and feeding frames lives in
 * the internal backend interface; what is published here is only what a caller that draws
 * on top of the preview, or decides when the torch should come on, actually reads.
 */
interface ScannerCamera {

  /** True once the camera is open. Geometry is null until then. */
  val isOpen: Boolean

  /**
   * The scan rectangle to draw, in the window's coordinates, or null before the camera has
   * reported a frame size.
   */
  fun getFramingRect(): Rect?

  /**
   * The same rectangle expressed in the camera frame's own coordinates, or null while it
   * cannot be computed. This is the region that is decoded.
   */
  fun getFramingRectInPreview(): Rect?

  /** Turns the torch on or off. A device with no flash unit ignores it. */
  fun setTorch(on: Boolean)

  /**
   * Which of the scanner's options this backend actually applies, and whether there is a
   * flash unit to turn on.
   *
   * Ask before assuming a setting took: the two backends honour different ones, and a
   * setting the running backend does not honour is a call that succeeds and does nothing.
   * The backend also logs its own list once per session.
   */
  fun capabilities(): CameraCapabilities
}
