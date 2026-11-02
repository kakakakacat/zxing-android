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
 * A Camera1 preview frame.
 *
 * `setOneShotPreviewCallback` delivers a `byte[]` the driver has finished with, so there is
 * nothing to hand back and [close] does nothing. The whole frame is already in the heap;
 * [PlanarYUVLuminanceSource][io.github.kakakakacat.zxing.PlanarYUVLuminanceSource] reads the
 * scan rectangle straight out of it.
 */
internal class Camera1ScanFrame(override val source: LuminanceSource?) : ScanFrame {

  override fun close() {
    // Nothing to release: the array belongs to the caller once the driver has delivered it.
  }
}
