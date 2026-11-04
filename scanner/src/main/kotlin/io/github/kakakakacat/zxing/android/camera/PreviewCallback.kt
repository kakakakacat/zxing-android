/*
 * Copyright (C) 2010 ZXing authors
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

import android.hardware.Camera
import android.os.Handler
import java.util.concurrent.atomic.AtomicReference

/**
 * @param buildFrame wraps the delivered buffer and the frame size into the [ScanFrame] the
 *   decode thread consumes. Supplied by [CameraManager], which owns the crop rectangle.
 */
@Suppress("DEPRECATION") // camera APIs
internal class PreviewCallback(
    private val configManager: CameraConfigurationManager,
    private val buildFrame: (ByteArray?, Int, Int) -> ScanFrame
) : Camera.PreviewCallback {

  private val pending = AtomicReference<FrameRequest?>()
  fun setHandler(handler: Handler?, what: Int) {
    pending.getAndSet(handler?.let { FrameRequest(it, what) })?.cancel()
  }
  override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
    val request = pending.getAndSet(null) ?: return
    val size = configManager.cameraResolution
    try {
      request.deliver(if (size == null) Camera1ScanFrame(null)
          else buildFrame(data, size.x, size.y))
    } catch (failure: RuntimeException) {
      request.cancel()
      throw failure
    }
  }
}
