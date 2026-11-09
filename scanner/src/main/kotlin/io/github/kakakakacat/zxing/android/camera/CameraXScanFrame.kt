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

import androidx.camera.core.ImageProxy
import io.github.kakakakacat.zxing.LuminanceSource

/**
 * A CameraX frame, held open for exactly as long as the decode takes.
 *
 * [source] reads the image's luma plane in place and copies nothing, so the `ImageProxy` has
 * to outlive the decode. It also has to be closed immediately afterwards: an analyzer holds
 * a slot in a fixed-size buffer queue and the camera stops delivering frames once every slot
 * is outstanding — a stall with no exception behind it.
 *
 * [close] is idempotent, because the only safe answer to "was this closed on the error path
 * as well?" is one that does not depend on knowing.
 */
internal class CameraXScanFrame(
    private val image: ImageProxy,
    override val source: LuminanceSource?,
    override val data: LumaFrameData?
) : ScanFrame {

  private var closed = false

  override fun close() {
    if (closed) {
      return
    }
    closed = true
    image.close()
  }
}
