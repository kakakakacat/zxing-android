/*
 * Copyright (C) 2008 ZXing authors
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

package io.github.kakakakacat.zxing.android.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.kakakakacat.zxing.BarcodeFormat
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource
import io.github.kakakakacat.zxing.ResultPoint
import io.github.kakakakacat.zxing.ResultPointCallback
import io.github.kakakakacat.zxing.android.AmbientLightManager
import io.github.kakakakacat.zxing.android.ViewfinderView
import io.github.kakakakacat.zxing.android.camera.CameraManager
import java.io.IOException

/**
 * A drop-in scanning surface: add it to a layout, give it a [Listener], call [resume] and
 * [pause] from the host's lifecycle, and it reports decoded barcodes.
 *
 * It owns the camera, the preview surface, the viewfinder overlay and the decode thread. It
 * deliberately does *not* request the CAMERA permission: the host owns that conversation with
 * the user. If the permission is missing, [resume] reports [ScannerError.PERMISSION_DENIED]
 * instead of opening the camera.
 *
 * All callbacks arrive on the main thread. Results carry the id of the scanning session that
 * produced them, so a decode that finishes after [pause] is dropped rather than delivered into
 * a torn-down screen.
 */
class ScannerView(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int
) : FrameLayout(context, attrs, defStyleAttr), SurfaceHolder.Callback {

  /** Receives scanner events. Always called on the main thread. */
  interface Listener {
    /**
     * A barcode was decoded. Scanning is paused until [resumeScanning] is called, so the host
     * can show the result without the next frame overwriting it.
     */
    fun onScanned(outcome: ScanOutcome)

    /** The scanner could not start, or the camera went away. Scanning is stopped. */
    fun onScannerError(error: ScannerError, cause: Exception?)
  }

  private val surfaceView: SurfaceView = SurfaceView(context)
  private val viewfinderView: ViewfinderView = ViewfinderView(context)
  private val mainHandler: Handler

  private var options: ScannerOptions = ScannerOptions.defaults()
  private var formats: Collection<BarcodeFormat>? = null
  private var listener: Listener? = null
  private var thumbnailsEnabled = true

  private var cameraManager: CameraManager? = null
  private var ambientLightManager: AmbientLightManager? = null
  private var decoderThread: DecoderThread? = null
  private var hasSurface = false
  private var resumed = false

  /** Bumped on every start, so replies from an older session can be identified. */
  private var sessionId = 0

  var isScanning = false
    private set

  constructor(context: Context) : this(context, null, 0)

  constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

  init {
    addView(surfaceView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                      ViewGroup.LayoutParams.MATCH_PARENT))
    addView(viewfinderView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                         ViewGroup.LayoutParams.MATCH_PARENT))

    mainHandler = object : Handler(Looper.getMainLooper()) {
      override fun handleMessage(msg: Message) {
        this@ScannerView.handleDecodeReply(msg)
      }
    }
  }

  /** Must be set before [resume]; changing it later takes effect on the next resume. */
  fun setOptions(options: ScannerOptions?) {
    this.options = options ?: ScannerOptions.defaults()
  }

  /**
   * Formats to decode, or `null` for every format the reader supports. Kept separate from
   * [ScannerOptions] because hosts commonly derive it per scan request (for example from an
   * Intent) while the rest of the options are fixed.
   *
   * **Takes effect while scanning**, without reopening the camera. The change is applied on
   * the decode thread, so a frame already being decoded finishes under the previous selection
   * and the next frame uses the new one. Until this method did that, calling it on a running
   * scanner silently did nothing until the next [resume].
   *
   * How much this is worth is measured, not assumed: on the reference device a frame that
   * decodes nothing costs 81 ms at 1920x1080 with every format enabled and 1.2 ms with a
   * single 1D format — see `docs/maint/BENCHMARKS.md` §10. Restricting formats also removes
   * misreads: two corpus samples are read as the wrong symbology by an all-formats reader
   * (§9). Narrow this to what the host actually accepts.
   */
  fun setFormats(formats: Collection<@JvmSuppressWildcards BarcodeFormat>?) {
    this.formats = formats
    val decodeHandler = decoderThread?.getHandler()
    decodeHandler?.obtainMessage(DecoderThread.MSG_SET_FORMATS, formats)?.sendToTarget()
  }

  fun setListener(listener: Listener?) {
    this.listener = listener
  }

  /** Thumbnails cost an allocation and a pixel copy per successful scan. Default on. */
  fun setThumbnailsEnabled(enabled: Boolean) {
    this.thumbnailsEnabled = enabled
  }

  fun getViewfinderView(): ViewfinderView = viewfinderView

  /** Null until the camera is open. */
  fun getCameraManager(): CameraManager? = cameraManager

  /** Call from the host's onResume. Opens the camera and starts decoding. */
  fun resume() {
    if (resumed) {
      return
    }
    resumed = true
    if (!hasCameraPermission()) {
      resumed = false
      reportError(ScannerError.PERMISSION_DENIED, null)
      return
    }
    cameraManager = CameraManager(context.applicationContext, options)
    ambientLightManager = AmbientLightManager(context, options.frontLightMode)
    viewfinderView.setCameraManager(cameraManager)
    val holder = surfaceView.holder
    if (hasSurface) {
      openCamera(holder)
    } else {
      holder.addCallback(this)
    }
  }

  /** Call from the host's onPause. Releases the camera and stops the decode thread. */
  fun pause() {
    if (!resumed) {
      return
    }
    resumed = false
    stopScanning()
    ambientLightManager?.stop()
    ambientLightManager = null
    cameraManager?.closeDriver()
    cameraManager = null
    if (!hasSurface) {
      surfaceView.holder.removeCallback(this)
    }
  }

  /** After a result has been shown, ask for the next frame. */
  fun resumeScanning() {
    if (!resumed || cameraManager == null || decoderThread == null || isScanning) {
      return
    }
    isScanning = true
    viewfinderView.drawViewfinder()
    requestFrame()
  }

  /** Turns the torch on or off. No-op if the camera is not open. */
  fun setTorch(on: Boolean) {
    cameraManager?.setTorch(on)
  }

  // No SDK_INT guard: minSdk is 23, so the runtime permission model is the only one
  // there is and the old "granted at install time" branch could never be taken.
  private fun hasCameraPermission(): Boolean =
      context.checkSelfPermission(Manifest.permission.CAMERA) ==
          PackageManager.PERMISSION_GRANTED

  private fun openCamera(holder: SurfaceHolder) {
    val manager = cameraManager
    if (manager == null || manager.isOpen) {
      return
    }
    try {
      manager.openDriver(holder)
    } catch (ioe: IOException) {
      // openDriver reports "no camera object from the driver" and a failed
      // setPreviewDisplay through the same exception type; isOpen tells them apart.
      Log.w(TAG, "Could not open the camera or attach the preview", ioe)
      reportError(if (manager.isOpen) ScannerError.PREVIEW_FAILED
                  else cameraUnavailableOrMissing(manager), ioe)
      return
    } catch (re: RuntimeException) {
      // Camera.open() throws RuntimeException for "no camera", "camera in use" and
      // "service died" alike; distinguish what we can rather than blaming the device.
      Log.w(TAG, "Could not open the camera", re)
      reportError(cameraUnavailableOrMissing(manager), re)
      return
    }

    sessionId++
    val thisSession = sessionId
    val thread = DecoderThread(
        object : DecoderThread.FrameSource {
          override fun buildLuminanceSource(
              data: ByteArray?,
              width: Int,
              height: Int
          ): PlanarYUVLuminanceSource? = cameraManager?.buildLuminanceSource(data, width, height)
        },
        mainHandler, options, formats,
        object : ResultPointCallback {
          override fun foundPossibleResultPoint(point: ResultPoint) {
            viewfinderView.addPossibleResultPoint(point)
          }
        },
        thumbnailsEnabled, thisSession)
    thread.start()
    decoderThread = thread

    try {
      manager.startPreview()
    } catch (re: RuntimeException) {
      Log.w(TAG, "Could not start the preview", re)
      stopScanning()
      reportError(ScannerError.PREVIEW_FAILED, re)
      return
    }
    ambientLightManager?.start(manager)
    isScanning = true
    viewfinderView.drawViewfinder()
    requestFrame()
  }

  private fun requestFrame() {
    val manager = cameraManager ?: return
    val thread = decoderThread ?: return
    val decodeHandler = thread.getHandler()
    if (decodeHandler != null) {
      manager.requestPreviewFrame(decodeHandler, DecoderThread.MSG_DECODE)
    }
  }

  private fun stopScanning() {
    isScanning = false
    // Invalidate the session first: anything still in flight is now stale.
    sessionId++
    val thread = decoderThread
    decoderThread = null
    cameraManager?.stopPreview()
    if (thread != null) {
      thread.getHandler()?.obtainMessage(DecoderThread.MSG_QUIT)?.sendToTarget()
      try {
        thread.join(500L)
      } catch (ie: InterruptedException) {
        Thread.currentThread().interrupt()
      }
    }
    mainHandler.removeMessages(MSG_DECODE_SUCCEEDED)
    mainHandler.removeMessages(MSG_DECODE_FAILED)
  }

  private fun handleDecodeReply(msg: Message) {
    if (msg.arg1 != sessionId) {
      // A reply from a session that has already been torn down. Dropping it is the
      // whole point of the session id: upstream could deliver a decode from the previous
      // camera session into a freshly resumed screen.
      return
    }
    if (msg.what == MSG_DECODE_FAILED) {
      if (isScanning) {
        requestFrame()
      }
      return
    }
    if (msg.what == MSG_DECODE_SUCCEEDED) {
      isScanning = false
      listener?.onScanned(msg.obj as ScanOutcome)
    }
  }

  private fun reportError(error: ScannerError, cause: Exception?) {
    listener?.onScannerError(error, cause)
  }

  override fun surfaceCreated(holder: SurfaceHolder) {
    if (!hasSurface) {
      hasSurface = true
      if (resumed) {
        openCamera(holder)
      }
    }
  }

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    // Preview size is negotiated when the camera opens; nothing to do here.
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    hasSurface = false
    // The camera cannot keep drawing into a surface that is going away.
    if (resumed) {
      stopScanning()
      cameraManager?.closeDriver()
    }
  }

  /** The scan rectangle in view coordinates, or null before the camera is configured. */
  fun getFramingRect(): Rect? = cameraManager?.getFramingRect()

  companion object {
    internal const val MSG_DECODE_SUCCEEDED = 10
    internal const val MSG_DECODE_FAILED = 11

    private const val TAG = "ScannerView"

    private fun cameraUnavailableOrMissing(manager: CameraManager): ScannerError =
        if (manager.hasAnyCamera()) ScannerError.CAMERA_UNAVAILABLE else ScannerError.NO_CAMERA
  }
}
