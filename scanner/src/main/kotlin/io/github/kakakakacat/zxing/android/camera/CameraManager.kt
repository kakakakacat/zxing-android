/*
 * Copyright (C) 2008 ZXing authors
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
import android.graphics.Point
import android.hardware.Camera
import android.os.Handler
import android.util.Log
import android.view.SurfaceHolder
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource
import io.github.kakakakacat.zxing.android.camera.open.OpenCamera
import io.github.kakakakacat.zxing.android.camera.open.OpenCameraInterface
import io.github.kakakakacat.zxing.android.scanner.ScannerOptions
import java.io.IOException

/**
 * This object wraps the Camera service object and expects to be the only one talking to it. The
 * implementation encapsulates the steps needed to take preview-sized images, which are used for
 * both preview and decoding.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
@Suppress("DEPRECATION") // camera APIs
class CameraManager(context: Context, options: ScannerOptions?) : ScannerCamera {

  private val options: ScannerOptions = options ?: ScannerOptions.defaults()
  private val configManager: CameraConfigurationManager =
      CameraConfigurationManager(context, this.options)

  /**
   * Preview frames are delivered here, which we pass on to the registered handler. Make sure to
   * clear the handler so it will only receive one message.
   */
  private val previewCallback: PreviewCallback =
      PreviewCallback(configManager) { data, width, height ->
        Camera1ScanFrame(buildLuminanceSource(data, width, height))
      }

  private var camera: OpenCamera? = null
  private var autoFocusManager: AutoFocusManager? = null
  private var framingRect: Rect? = null
  private var framingRectInPreview: Rect? = null

  /**
   * The scanning view's size, or null until it has been laid out.
   *
   * The scan rectangle is drawn by `ViewfinderView` in *its own* coordinates, and it used
   * to be computed from the display's size. The two are the same thing only when the
   * scanner fills the screen; embedded in a host they are not, and the rectangle the user
   * aims with was not the region being decoded. The preview resolution is still negotiated
   * against the display, because that is what every measurement in this repository was
   * taken at and the preview is stretched to the view either way.
   */
  private var viewportSize: Point? = null
  private var initialized = false
  private var previewing = false
  private var requestedCameraId: Int = this.options.cameraId
  private var requestedFramingRectWidth = 0
  private var requestedFramingRectHeight = 0

  init {
    if (this.options.framingRectWidth > 0 && this.options.framingRectHeight > 0) {
      requestedFramingRectWidth = this.options.framingRectWidth
      requestedFramingRectHeight = this.options.framingRectHeight
    }
  }

  /**
   * Opens the camera driver and initializes the hardware parameters.
   *
   * @param holder The surface object which the camera will draw preview frames into.
   * @throws IOException Indicates the camera driver failed to open.
   */
  @Synchronized
  @Throws(IOException::class)
  fun openDriver(holder: SurfaceHolder?) {
    var theCamera = camera
    if (theCamera == null) {
      theCamera = OpenCameraInterface.open(requestedCameraId)
          ?: throw IOException("Camera.open() failed to return object from driver")
      camera = theCamera
    }

    if (!initialized) {
      initialized = true
      configManager.initFromCameraParameters(theCamera)
      if (requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0) {
        setManualFramingRect(requestedFramingRectWidth, requestedFramingRectHeight)
        requestedFramingRectWidth = 0
        requestedFramingRectHeight = 0
      }
    }

    val cameraObject = theCamera.camera
    var parameters = cameraObject.parameters
    val parametersFlattened = parameters?.flatten() // Save these, temporarily
    try {
      configManager.setDesiredCameraParameters(theCamera, false)
    } catch (re: RuntimeException) {
      // Driver failed
      Log.w(TAG, "Camera rejected parameters. Setting only minimal safe-mode parameters")
      Log.i(TAG, "Resetting to saved camera params: $parametersFlattened")
      // Reset:
      if (parametersFlattened != null) {
        parameters = cameraObject.parameters
        parameters.unflatten(parametersFlattened)
        try {
          cameraObject.parameters = parameters
          configManager.setDesiredCameraParameters(theCamera, true)
        } catch (re2: RuntimeException) {
          // Well, darn. Give up
          Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration")
        }
      }
    }
    cameraObject.setPreviewDisplay(holder)
  }

  @get:Synchronized
  override val isOpen: Boolean
    get() = camera != null

  /**
   * Whether the device reports any camera at all. Lets a caller tell "this device has no camera"
   * apart from "the camera is there but could not be opened", which [android.hardware.Camera.open]
   * reports as the same RuntimeException.
   */
  fun hasAnyCamera(): Boolean {
    return try {
      Camera.getNumberOfCameras() > 0
    } catch (re: RuntimeException) {
      Log.w(TAG, "Could not count cameras", re)
      false
    }
  }

  /**
   * Closes the camera driver if still in use.
   */
  @Synchronized
  fun closeDriver() {
    val theCamera = camera
    if (theCamera != null) {
      theCamera.camera.release()
      camera = null
      // Make sure to clear these each time we close the camera, so that any scanning rect
      // requested by intent is forgotten.
      framingRect = null
      framingRectInPreview = null
    }
  }

  /**
   * Asks the camera hardware to begin drawing preview frames to the screen.
   */
  @Synchronized
  fun startPreview() {
    val theCamera = camera
    if (theCamera != null && !previewing) {
      theCamera.camera.startPreview()
      previewing = true
      autoFocusManager = AutoFocusManager(theCamera.camera, options.isAutoFocus)
    }
  }

  /**
   * Tells the camera to stop drawing preview frames.
   */
  @Synchronized
  fun stopPreview() {
    autoFocusManager?.stop()
    autoFocusManager = null
    val theCamera = camera
    if (theCamera != null && previewing) {
      theCamera.camera.stopPreview()
      previewCallback.setHandler(null, 0)
      previewing = false
    }
  }

  /**
   * Convenience method for the hosting capture screen.
   *
   * @param newSetting if `true`, light should be turned on if currently off. And vice versa.
   */
  @Synchronized
  override fun setTorch(newSetting: Boolean) {
    val theCamera = camera
    if (theCamera != null && newSetting != configManager.getTorchState(theCamera.camera)) {
      val wasAutoFocusManager = autoFocusManager != null
      if (wasAutoFocusManager) {
        autoFocusManager?.stop()
        autoFocusManager = null
      }
      configManager.setTorch(theCamera.camera, newSetting)
      if (wasAutoFocusManager) {
        val manager = AutoFocusManager(theCamera.camera, options.isAutoFocus)
        autoFocusManager = manager
        manager.start()
      }
    }
  }

  /**
   * A single preview frame will be returned to the handler supplied, as a [ScanFrame] in the
   * message's `obj` field — already cropped to the scan rectangle, because the crop is
   * computed from parameters only this class holds.
   *
   * It used to be the raw `byte[]` with the frame size in `arg1`/`arg2`, which left the crop
   * to the receiver. Phase 6 added a second camera backend whose frames are not a `byte[]`
   * and have to be handed back when the decode finishes, and [ScanFrame] is what the two
   * have in common.
   *
   * @param handler The handler to send the message to.
   * @param message The what field of the message to be sent.
   */
  @Synchronized
  fun requestPreviewFrame(handler: Handler?, message: Int) {
    val theCamera = camera
    if (theCamera != null && previewing) {
      previewCallback.setHandler(handler, message)
      theCamera.camera.setOneShotPreviewCallback(previewCallback)
    }
  }

  /**
   * Calculates the framing rect which the UI should draw to show the user where to place the
   * barcode. This target helps with alignment as well as forces the user to hold the device far
   * enough away to ensure the image will be in focus.
   *
   * @return The rectangle to draw on screen in window coordinates.
   */
  @Synchronized
  override fun getFramingRect(): Rect? {
    if (framingRect == null) {
      if (camera == null) {
        return null
      }
      // Called early, before init even finished
      val screenResolution = viewport() ?: return null

      val (width, height) = FramingRectSizer.sizeFor(screenResolution.x, screenResolution.y)

      val leftOffset = (screenResolution.x - width) / 2
      val topOffset = (screenResolution.y - height) / 2
      framingRect = Rect(leftOffset, topOffset, leftOffset + width, topOffset + height)
    }
    return framingRect
  }

  /**
   * Like [getFramingRect] but coordinates are in terms of the preview frame, not UI / screen.
   *
   * @return [Rect] expressing barcode scan area in terms of the preview size
   */
  @Synchronized
  override fun getFramingRectInPreview(): Rect? {
    if (framingRectInPreview == null) {
      val rect = getFramingRect() ?: return null
      // Called early, before init even finished
      val cameraResolution = configManager.cameraResolution ?: return null
      // The space getFramingRect answers in, which is the view's and not the display's.
      val screenResolution = viewport() ?: return null
      val previewOnScreen = configManager.previewSizeOnScreen ?: return null
      // Upstream scaled x and y independently from screen size to camera resolution,
      // which is only correct when the display-to-camera rotation is 0. See
      // PreviewCoordinateMapper.
      val box = PreviewCoordinateMapper.toFrameCoordinates(
          rect.left, rect.top, rect.right, rect.bottom,
          screenResolution.x, screenResolution.y,
          previewOnScreen.x, previewOnScreen.y,
          cameraResolution.x, cameraResolution.y,
          configManager.cwRotationFromDisplayToCamera,
          configManager.isMirrored) ?: return null
      framingRectInPreview = Rect(box[0], box[1], box[2], box[3])
    }
    return framingRectInPreview
  }

  /** The space the scan rectangle lives in: the view, or the display before it is laid out. */
  private fun viewport(): Point? = viewportSize ?: configManager.screenResolution

  /** See [ScannerCameraBackend.setViewportSize]. Invalidates both cached rectangles. */
  @Synchronized
  fun setViewportSize(width: Int, height: Int) {
    val next = if (width > 0 && height > 0) Point(width, height) else null
    if (next == viewportSize) {
      return
    }
    viewportSize = next
    framingRect = null
    framingRectInPreview = null
  }

  override fun capabilities(): CameraCapabilities = CameraCapabilities(
      backendName = "camera1",
      cameraIdHonoured = true,
      autoFocusHonoured = true,
      meteringHonoured = true,
      exposureHonoured = true,
      barcodeSceneModeHonoured = true,
      invertScanHonoured = true,
      framingRectHonoured = true,
      torchHonoured = true,
      torchAvailable = torchAvailable())

  /**
   * Whether this camera has a flash unit the torch can use.
   *
   * Read off the driver rather than assumed: DN-ENG41's back camera reports no flash unit
   * at all, which is why `AutoFocusManager` and the torch are both unexercised on it.
   */
  private fun torchAvailable(): Boolean {
    val modes = try {
      camera?.camera?.parameters?.supportedFlashModes
    } catch (re: RuntimeException) {
      // Some drivers throw out of getParameters once the camera is going away.
      null
    } ?: return false
    return modes.contains(Camera.Parameters.FLASH_MODE_TORCH) ||
        modes.contains(Camera.Parameters.FLASH_MODE_ON)
  }

  /**
   * Allows third party apps to specify the camera ID, rather than determine it automatically
   * based on available cameras and their orientation.
   *
   * @param cameraId camera ID of the camera to use. A negative value means "no preference".
   */
  @Synchronized
  fun setManualCameraId(cameraId: Int) {
    requestedCameraId = cameraId
  }

  /**
   * Allows third party apps to specify the scanning rectangle dimensions, rather than determine
   * them automatically based on screen resolution.
   *
   * @param width The width in pixels to scan.
   * @param height The height in pixels to scan.
   */
  @Synchronized
  fun setManualFramingRect(width: Int, height: Int) {
    if (initialized) {
      val screenResolution = viewport() ?: return
      val w = if (width > screenResolution.x) screenResolution.x else width
      val h = if (height > screenResolution.y) screenResolution.y else height
      val leftOffset = (screenResolution.x - w) / 2
      val topOffset = (screenResolution.y - h) / 2
      framingRect = Rect(leftOffset, topOffset, leftOffset + w, topOffset + h)
      Log.d(TAG, "Calculated manual framing rect: $framingRect")
      framingRectInPreview = null
    } else {
      requestedFramingRectWidth = width
      requestedFramingRectHeight = height
    }
  }

  /**
   * A factory method to build the appropriate LuminanceSource object based on the format of the
   * preview buffers, as described by Camera.Parameters.
   *
   * @param data A preview frame.
   * @param width The width of the image.
   * @param height The height of the image.
   * @return A PlanarYUVLuminanceSource instance.
   */
  fun buildLuminanceSource(data: ByteArray?, width: Int, height: Int): PlanarYUVLuminanceSource? {
    val rect = getFramingRectInPreview() ?: return null
    // Go ahead and assume it's YUV rather than die.
    return PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
                                    rect.width(), rect.height(), false)
  }

  private companion object {
    private val TAG: String = CameraManager::class.java.simpleName

  }
}
