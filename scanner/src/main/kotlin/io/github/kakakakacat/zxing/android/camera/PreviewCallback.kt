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

/**
 * @param buildFrame wraps the delivered buffer and the frame size into the [ScanFrame] the
 *   decode thread consumes. Supplied by [CameraManager], which owns the crop rectangle.
 */
@Suppress("DEPRECATION") // camera APIs
internal class PreviewCallback(
    private val configManager: CameraConfigurationManager,
    private val buildFrame: (ByteArray?, Int, Int) -> ScanFrame
) : Camera.PreviewCallback {

  private var previewHandler: Handler? = null
  private var previewMessage = 0

  fun setHandler(previewHandler: Handler?, previewMessage: Int) {
    this.previewHandler = previewHandler
    this.previewMessage = previewMessage
  }

  // Both parameters nullable: the Java original declared no nullability and did no checks,
  // and a non-null Kotlin parameter compiles to a null check that throws. Drivers have been
  // seen delivering a null frame, and dropping it is what this always did.
  override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
    val cameraResolution = configManager.cameraResolution
    val thePreviewHandler = previewHandler
    if (cameraResolution != null && thePreviewHandler != null) {
      previewHandler = null
      val frame = buildFrame(data, cameraResolution.x, cameraResolution.y)
      val message = thePreviewHandler.obtainMessage(previewMessage, frame)
      // A decode thread that has already quit will not take the message. Nothing is leaked
      // on this backend either way, but the contract is that whoever holds a ScanFrame
      // closes it, and after a failed send that is still this method.
      if (!thePreviewHandler.sendMessage(message)) {
        frame.close()
      }
    }
  }
}
