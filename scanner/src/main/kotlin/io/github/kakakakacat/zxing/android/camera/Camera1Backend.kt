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

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.util.Log
import android.view.SurfaceHolder
import io.github.kakakakacat.zxing.android.scanner.ScannerError
import io.github.kakakakacat.zxing.android.scanner.ScannerOptions
import java.io.IOException

/**
 * The original `android.hardware.Camera` path, behind the backend interface.
 *
 * It owns nothing: [CameraManager] is still the whole of the Camera1 stack and is still
 * public API. This class only adapts it to the shape [CameraXBackend] also has, and holds
 * the open-failure classification that used to sit inside `ScannerView` — which is where it
 * stopped belonging the moment there were two stacks to classify failures from.
 */
internal class Camera1Backend(
    context: Context,
    options: ScannerOptions
) : ScannerCameraBackend {

  /** Exposed so `ScannerView.getCameraManager()` can still hand it to a caller. */
  val cameraManager: CameraManager = CameraManager(context.applicationContext, options)

  override val isOpen: Boolean
    get() = cameraManager.isOpen

  override fun getFramingRect(): Rect? = cameraManager.getFramingRect()

  override fun getFramingRectInPreview(): Rect? = cameraManager.getFramingRectInPreview()

  override fun setTorch(on: Boolean) {
    cameraManager.setTorch(on)
  }

  override fun open(holder: SurfaceHolder, callback: ScannerCameraBackend.Callback) {
    try {
      cameraManager.openDriver(holder)
    } catch (ioe: IOException) {
      // openDriver reports "no camera object from the driver" and a failed
      // setPreviewDisplay through the same exception type; isOpen tells them apart.
      Log.w(TAG, "Could not open the camera or attach the preview", ioe)
      callback.onCameraError(
          if (cameraManager.isOpen) ScannerError.PREVIEW_FAILED else unavailableOrMissing(), ioe)
      return
    } catch (re: RuntimeException) {
      // Camera.open() throws RuntimeException for "no camera", "camera in use" and
      // "service died" alike; distinguish what we can rather than blaming the device.
      Log.w(TAG, "Could not open the camera", re)
      callback.onCameraError(unavailableOrMissing(), re)
      return
    }
    try {
      cameraManager.startPreview()
    } catch (re: RuntimeException) {
      Log.w(TAG, "Could not start the preview", re)
      callback.onCameraError(ScannerError.PREVIEW_FAILED, re)
      return
    }
    callback.onCameraReady()
  }

  override fun stopFrames() {
    cameraManager.stopPreview()
  }

  override fun close() {
    cameraManager.closeDriver()
  }

  override fun requestFrame(handler: Handler, what: Int) {
    cameraManager.requestPreviewFrame(handler, what)
  }

  private fun unavailableOrMissing(): ScannerError =
      if (cameraManager.hasAnyCamera()) ScannerError.CAMERA_UNAVAILABLE else ScannerError.NO_CAMERA

  private companion object {
    private const val TAG = "Camera1Backend"
  }
}
