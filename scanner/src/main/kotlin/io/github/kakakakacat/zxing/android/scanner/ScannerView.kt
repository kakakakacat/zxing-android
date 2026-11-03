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
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.kakakakacat.zxing.BarcodeFormat
import io.github.kakakakacat.zxing.ResultPoint
import io.github.kakakakacat.zxing.ResultPointCallback
import io.github.kakakakacat.zxing.android.AmbientLightManager
import io.github.kakakakacat.zxing.android.ViewfinderView
import io.github.kakakakacat.zxing.android.camera.Camera1Backend
import io.github.kakakakacat.zxing.android.camera.CameraCapabilities
import io.github.kakakakacat.zxing.android.camera.CameraManager
import io.github.kakakakacat.zxing.android.camera.CameraXBackend
import io.github.kakakakacat.zxing.android.camera.FrontLightMode
import io.github.kakakakacat.zxing.android.camera.ScannerCamera
import io.github.kakakakacat.zxing.android.camera.ScannerCameraBackend

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

  private var backend: ScannerCameraBackend? = null
  private var ambientLightManager: AmbientLightManager? = null
  private var decoderThread: DecoderThread? = null
  private var hasSurface = false
  private var resumed = false

  /**
   * Bumped on every start, so replies from an older session can be identified.
   *
   * Doubles as the cookie for [ScannerTrace]'s asynchronous spans, which is why it is
   * bumped when the camera is asked to open rather than when it reports ready: the
   * interval between those two is the first thing L3 measures.
   */
  private var sessionId = 0

  /** The two one-shot L3 spans end once per session; these say whether they have. */
  private var firstScanTraced = false

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

    // Registered for the life of the view rather than around each resume.
    //
    // It used to be added in resume() and only when the surface did not exist yet, which
    // is right for a host that resumes from onResume -- the surface is created after
    // that, and surfaceCreated opens the camera. A host that calls resume() on a view
    // already on screen never got that callback, because SurfaceHolder does not replay
    // surfaceCreated for a callback added after the surface exists. The camera simply
    // never opened, and nothing said so. A tab, a reused dialog, a scanner started from a
    // button all produce that order.
    surfaceView.holder.addCallback(this)
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
    // Recorded by the decode thread whether or not its decoder exists yet. Reaching for
    // its handler here used to mean blocking this thread until the decoder was built, and
    // dropping the change silently if it never was.
    decoderThread?.setFormats(formats)
  }

  fun setListener(listener: Listener?) {
    this.listener = listener
  }

  /**
   * Thumbnails cost an allocation and a pixel copy per successful scan. Default on.
   *
   * **Takes effect on the next [resume]**, not on the next scan: the decode thread is
   * built with this once and reads it per frame thereafter. [setFormats] is the one
   * setting that changes a running session, and it says so.
   */
  fun setThumbnailsEnabled(enabled: Boolean) {
    this.thumbnailsEnabled = enabled
  }

  fun getViewfinderView(): ViewfinderView = viewfinderView

  /** Null until the camera is open. */
  fun getCamera(): ScannerCamera? = backend

  /**
   * The Camera1 stack, or null — including whenever
   * [ScannerOptions.CameraBackend.CAMERAX] is the backend in use, which has no
   * `CameraManager` behind it at all.
   *
   * Prefer [getCamera], which answers for either backend. This remains for callers that
   * reach into the Camera1 parameter handling directly, and it is the one thing the CameraX
   * path cannot offer them.
   */
  fun getCameraManager(): CameraManager? = (backend as? Camera1Backend)?.cameraManager

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
    val opened = createBackend()
    backend = opened
    ambientLightManager = AmbientLightManager(context, options.frontLightMode)
    // The viewfinder gets the camera when the camera is ready, not when it is asked for.
    // `ViewfinderView.onDraw` reads the scan rectangle off it, and on the Camera1 path
    // every `CameraManager` accessor is `@Synchronized` -- so handing it over up here put
    // the first layout pass's `onDraw` on the main thread behind an open running on the
    // worker. That is the stall this change exists to remove, in a second place.
    val holder = surfaceView.holder
    if (!hasSurface && holder.surface.isValid) {
      // Already on screen when the host resumed: surfaceCreated has been and gone.
      hasSurface = true
    }
    if (hasSurface) {
      openCamera(holder)
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
    backend?.close()
    backend = null
    viewfinderView.setCamera(null)
  }

  /** After a result has been shown, ask for the next frame. */
  fun resumeScanning() {
    if (!resumed || backend == null || decoderThread == null || isScanning) {
      return
    }
    isScanning = true
    viewfinderView.drawViewfinder()
    requestFrame()
  }

  /** Turns the torch on or off. No-op if the camera is not open. */
  fun setTorch(on: Boolean) {
    backend?.setTorch(on)
  }

  // No SDK_INT guard: minSdk is 23, so the runtime permission model is the only one
  // there is and the old "granted at install time" branch could never be taken.
  private fun hasCameraPermission(): Boolean =
      context.checkSelfPermission(Manifest.permission.CAMERA) ==
          PackageManager.PERMISSION_GRANTED

  private fun createBackend(): ScannerCameraBackend {
    val created = if (options.cameraBackend == ScannerOptions.CameraBackend.CAMERAX) {
      CameraXBackend(context, options)
    } else {
      Camera1Backend(context, options)
    }
    // Before it opens, so the scan rectangle is computed in this view's coordinates from
    // the first time it is asked for rather than in the display's.
    created.setViewportSize(width, height)
    return created
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    // A rotation, a split-screen drag, a host resizing the dialog this is in: all of them
    // change the space the scan rectangle is drawn in, and both of the backend's cached
    // rectangles are in the old one.
    backend?.setViewportSize(w, h)
    viewfinderView.drawViewfinder()
  }

  /**
   * Which of the options the running backend actually applies, or null before the camera
   * is open.
   *
   * The two camera stacks honour different settings — the CameraX path leaves focus,
   * metering, exposure and scene mode to CameraX's own 3A and binds the back camera
   * whatever `cameraId` says — and a setting that is not honoured is a call that succeeds
   * and does nothing. The backend logs its own list once per session as well.
   */
  fun getCameraCapabilities(): CameraCapabilities? = backend?.capabilities()

  private fun openCamera(holder: SurfaceHolder) {
    val camera = backend
    if (camera == null || camera.isOpen) {
      return
    }
    sessionId++
    firstScanTraced = false
    ScannerTrace.begin(ScannerTrace.CAMERA_OPEN, sessionId)
    ScannerTrace.begin(ScannerTrace.backendSpan(options.cameraBackend), sessionId)
    // Opening can finish later than this call -- it does on CameraX -- so everything that
    // follows a successful open lives in the callback, for both backends alike.
    camera.open(holder, cameraCallback)
  }

  private val cameraCallback = object : ScannerCameraBackend.Callback {

    override fun onCameraReady() {
      ScannerTrace.end(ScannerTrace.CAMERA_OPEN, sessionId)
      ScannerTrace.end(ScannerTrace.backendSpan(options.cameraBackend), sessionId)
      val camera = backend
      if (!resumed || camera == null) {
        // Paused while the camera was opening. On the CameraX path that is a real race, not
        // a theoretical one: the provider comes up asynchronously.
        return
      }
      viewfinderView.setCamera(camera)
      ScannerTrace.begin(ScannerTrace.FIRST_FRAME, sessionId)
      ScannerTrace.begin(ScannerTrace.FIRST_SCAN, sessionId)
      val thread = DecoderThread(
          mainHandler, options, formats,
          object : ResultPointCallback {
            override fun foundPossibleResultPoint(point: ResultPoint) {
              viewfinderView.addPossibleResultPoint(point)
            }
          },
          thumbnailsEnabled, sessionId)
      thread.start()
      decoderThread = thread

      // FrontLightMode.ON means the torch is on from the moment the camera opens, and
      // until this line that rule lived in `CameraConfigurationManager.initializeTorch`
      // -- inside the Camera1 parameter configuration, which the CameraX backend never
      // goes through. So ON silently did nothing on CameraX, while OFF and AUTO both
      // worked, AUTO because `AmbientLightManager` drives it through `ScannerCamera`.
      // Measured on an Android 15 device: with ON, Camera1 reported `flash.mode=TORCH`
      // and CameraX `OFF`.
      //
      // The rule belongs here, where both backends pass, rather than in a second
      // backend-specific place. It costs the Camera1 path nothing: `CameraManager`
      // only acts when the requested state differs from the camera's current one, and
      // on that path the parameters already carry it.
      if (options.frontLightMode == FrontLightMode.ON) {
        camera.setTorch(true)
      }
      ambientLightManager?.start(camera)
      viewfinderView.drawViewfinder()
      // The first frame is asked for when the decoder says it is up, in handleDecodeReply.
      // Asking here meant waiting on the decode thread's latch from the main thread --
      // unbounded, and for ever if the decoder failed to build.
    }

    override fun onCameraError(error: ScannerError, cause: Exception?) {
      ScannerTrace.end(ScannerTrace.CAMERA_OPEN, sessionId)
      ScannerTrace.end(ScannerTrace.backendSpan(options.cameraBackend), sessionId)
      stopScanning()
      ambientLightManager?.stop()
      backend?.close()
      viewfinderView.setCamera(null)
      reportError(error, cause)
    }
  }

  private val nextFrame = Runnable { requestFrame() }

  private fun requestNextFrame() {
    mainHandler.removeCallbacks(nextFrame)
    if (options.decodeIntervalMillis == 0L) requestFrame()
    else mainHandler.postDelayed(nextFrame, options.decodeIntervalMillis)
  }

  private fun requestFrame() {
    val camera = backend ?: return
    val thread = decoderThread ?: return
    // Never blocks: a frame is only ever asked for after the decoder has announced itself.
    val decodeHandler = thread.handlerIfReady() ?: return
    camera.requestFrame(decodeHandler, DecoderThread.MSG_DECODE)
  }

  private fun stopScanning() {
    isScanning = false
    mainHandler.removeCallbacks(nextFrame)
    // Invalidate the session first: anything still in flight is now stale.
    sessionId++
    val thread = decoderThread
    decoderThread = null
    // Cancel the outstanding request before the thread goes away, so no frame is delivered
    // to a looper that has quit.
    backend?.stopFrames()
    // Asked to stop, not waited for. The 500 ms join this replaces ran on whichever
    // thread called pause() -- the main thread, for every host that follows the
    // documented lifecycle -- and a single slow decode spent it there. Nothing needs the
    // thread gone before this returns: a Camera1 frame is a heap array the decoder owns
    // once delivered, and a CameraX ImageProxy keeps its reader open until it is closed,
    // so neither backend's close() can pull a buffer out from under a decode in flight.
    thread?.requestQuit()
    mainHandler.removeMessages(MSG_DECODE_SUCCEEDED)
    mainHandler.removeMessages(MSG_DECODE_FAILED)
    mainHandler.removeMessages(MSG_DECODER_READY)
    mainHandler.removeMessages(MSG_DECODER_FAILED)
  }

  private fun handleDecodeReply(msg: Message) {
    if (msg.arg1 != sessionId) {
      // A reply from a session that has already been torn down. Dropping it is the
      // whole point of the session id: upstream could deliver a decode from the previous
      // camera session into a freshly resumed screen.
      return
    }
    if (msg.what == MSG_DECODER_READY) {
      // The decoder exists. This is where a session starts asking for frames, and the
      // only place it does so for the first time.
      isScanning = true
      requestFrame()
      return
    }
    if (msg.what == MSG_DECODER_FAILED) {
      // Either the decoder could not be built -- an explicit NATIVE backend on an ABI
      // with no native build, a format selection zxing-cpp cannot be asked for -- or it
      // threw on frame after frame. Both used to end with a thread that was simply gone
      // and a scanner that waited for a frame that would never come.
      val cause = msg.obj as? Throwable
      stopScanning()
      reportError(ScannerError.DECODER_UNAVAILABLE, cause as? Exception)
      return
    }
    if (msg.what == MSG_DECODE_FAILED) {
      if (isScanning) {
        requestNextFrame()
      }
      return
    }
    if (msg.what == MSG_DECODE_SUCCEEDED) {
      if (!firstScanTraced) {
        firstScanTraced = true
        ScannerTrace.end(ScannerTrace.FIRST_SCAN, sessionId)
      }
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
    // The camera cannot keep drawing into a surface that is going away. The backend stays,
    // so surfaceCreated reopens it with the same options.
    if (resumed) {
      stopScanning()
      backend?.close()
    }
  }

  /** The scan rectangle in view coordinates, or null before the camera is configured. */
  fun getFramingRect(): Rect? = backend?.getFramingRect()

  companion object {
    internal const val MSG_DECODE_SUCCEEDED = 10
    internal const val MSG_DECODE_FAILED = 11

    /** The decode thread's decoder is up; `arg1` is the session. */
    internal const val MSG_DECODER_READY = 12

    /** The decoder could not be built or gave up; `obj` is the cause. */
    internal const val MSG_DECODER_FAILED = 13
  }
}
