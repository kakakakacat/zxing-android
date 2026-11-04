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
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.kakakakacat.zxing.android.camera.open.OpenCameraInterface
import io.github.kakakakacat.zxing.android.scanner.ScannerError
import io.github.kakakakacat.zxing.android.scanner.ScannerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * The CameraX path, adopted in Phase 6.
 *
 * It binds two use cases — a [Preview] drawing into the `SurfaceView` the scanner already
 * owns, and an [ImageAnalysis] delivering `YUV_420_888` frames — and nothing else.
 * `camera-view` is not a dependency: `PreviewView` would have cost 350,792 bytes and an
 * AppCompat tree for a scale/crop transform this scanner does not use and an overlay
 * mapping [PreviewCoordinateMapper] already does. The measurements are in
 * `docs/maint/decisions/0001-camera-backend.md`.
 *
 * ### What it does differently from Camera1, and why
 *
 * **Frames are not copied.** [YPlaneLuminanceSource] reads the luma plane where the camera
 * left it, so one `ImageProxy` stays open across the decode and is closed the moment it
 * returns. That is [ScanFrame]'s whole contract; break it and the camera runs out of buffer
 * slots and silently stops.
 *
 * **Only the back camera is bound**, whatever `cameraId` says: a barcode scanner has no use
 * for the front one. That is a property of this backend's own use cases and
 * [capabilities] reports it rather than leaving a caller to find out.
 *
 * **Telling CameraX that process-wide is the host's call.**
 * `setAvailableCamerasLimiter(DEFAULT_BACK_CAMERA)` removes CameraX's check that both lens
 * facings exist, which otherwise retries initialisation for about five seconds on a
 * one-camera device — 5,987 ms against 114 ms, and no difference on a two-camera phone. It
 * goes through `ProcessCameraProvider.configureInstance`, which is process-wide and can be
 * called once, so a library that does it is deciding for every other part of the host
 * application. It is therefore off unless
 * [ScannerOptions.cameraXProcessConfiguration][io.github.kakakakacat.zxing.android.scanner.ScannerOptions.cameraXProcessConfiguration]
 * asks for it; this fork's own app asks, because the scanner is the only camera in it.
 *
 * **Only this scanner's use cases are unbound.** `unbindAll()` takes down every use case
 * on the process-wide provider, a host application's own preview and capture included.
 *
 * **The preview is stretched over the view, as Camera1's already was.** Nothing here
 * reimplements `PreviewView`'s scale/crop transform; the frame is handed to the surface and
 * the surface fills the view. [PreviewCoordinateMapper] assumes exactly that, which is why
 * the on-screen rectangle and the decoded region still agree.
 *
 * Everything is called from the main thread except the analyzer, which runs on its own
 * single-thread executor.
 */
internal class CameraXBackend(
    context: Context,
    private val options: ScannerOptions
) : ScannerCameraBackend {

  private val context: Context = context.applicationContext

  // Context.getMainExecutor() is API 28 and this library's floor is 23. One handler, posted
  // to, is the whole of what androidx.core's ContextCompat does for it -- and taking a
  // dependency on androidx.core so a published AAR could call one method is not a trade
  // this project makes.
  private val mainHandler = Handler(Looper.getMainLooper())
  private val mainExecutor = Executor { command -> mainHandler.post(command) }

  /** Where a requested frame should be posted, or null when nothing is waiting for one. */
  private val pendingRequest = AtomicReference<FrameRequest?>(null)

  private var provider: ProcessCameraProvider? = null
  private var camera: Camera? = null
  private var analysis: ImageAnalysis? = null
  private var preview: Preview? = null
  private var analysisExecutor: ExecutorService? = null
  private var lifecycleOwner: ScannerLifecycleOwner? = null
  private var holder: SurfaceHolder? = null

  /** The frame geometry, filled in from the first `ImageProxy` and read from any thread. */
  @Volatile
  private var frameSize: Point? = null

  @Volatile
  private var frameRotation = 0

  private var framingRect: Rect? = null
  private var framingRectInPreview: Rect? = null

  /** The scanning view's size, or null until it has been laid out. See [setViewportSize]. */
  private var viewportSize: Point? = null

  override val isOpen: Boolean
    get() = camera != null

  override fun open(holder: SurfaceHolder, callback: ScannerCameraBackend.Callback) {
    if (lifecycleOwner != null) {
      // Already open, or still opening. isOpen cannot answer this on its own: it is false
      // for the whole of the window between asking for the provider and binding to it.
      return
    }
    this.holder = holder
    if (options.cameraXProcessConfiguration) {
      configureBackCameraOnly(context)
    }

    val owner = ScannerLifecycleOwner()
    lifecycleOwner = owner
    val executor = Executors.newSingleThreadExecutor()
    analysisExecutor = executor

    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
      if (lifecycleOwner !== owner) {
        // close() ran while the provider was still coming up. Whatever was built here is
        // not wanted and has nothing bound to it.
        return@addListener
      }
      try {
        val cameraProvider = future.get()
        provider = cameraProvider
        if (cameraProvider.availableCameraInfos.isEmpty()) {
          callback.onCameraError(ScannerError.NO_CAMERA, null)
          return@addListener
        }
        bind(cameraProvider, owner, executor)
        callback.onCameraReady()
      } catch (e: Exception) {
        Log.w(TAG, "CameraX could not start", e)
        callback.onCameraError(classify(e), e)
      }
    }, mainExecutor)
  }

  private fun bind(
      cameraProvider: ProcessCameraProvider,
      owner: ScannerLifecycleOwner,
      executor: ExecutorService
  ) {
    // Before the new use cases replace the fields that name the old ones: a session that
    // did not close tidily is the only way there is anything to unbind here at all.
    unbindOurUseCases(cameraProvider)

    val rotation = displayRotation()
    val previewWidth = options.previewWidth.takeIf { it > 0 } ?: TARGET_WIDTH
    val previewHeight = options.previewHeight.takeIf { it > 0 } ?: TARGET_HEIGHT
    val analysisWidth = options.analysisWidth.takeIf { it > 0 } ?: previewWidth
    val analysisHeight = options.analysisHeight.takeIf { it > 0 } ?: previewHeight
    require(StreamGeometry.sameAspect(previewWidth, previewHeight, analysisWidth, analysisHeight)) {
      "Preview and analysis requests must have the same aspect ratio"
    }

    val preview = Preview.Builder()
        .setResolutionSelector(resolutionSelector(previewWidth, previewHeight))
        .setTargetRotation(rotation)
        .build()
    preview.setSurfaceProvider { request -> provideSurface(request) }

    val imageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(resolutionSelector(analysisWidth, analysisHeight))
        .setTargetRotation(rotation)
        // One frame at a time, and the newest one. The decoder pulls, so anything that
        // arrives while it is busy is closed immediately rather than queued behind it.
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .build()
    imageAnalysis.setAnalyzer(executor) { image -> onFrame(image) }
    analysis = imageAnalysis

    owner.resume()
    // Only this scanner's own use cases are unbound, and only if a previous session left
    // some. `unbindAll()` used to be called here: it takes down every use case on the
    // process-wide provider, including a host application's own preview or capture, which
    // a library has no business doing. Nothing in a standalone scanner app noticed.
    this.preview = preview
    camera = cameraProvider.bindToLifecycle(
        owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
    if (options.analysisWidth > 0) {
      val previewInfo = preview.resolutionInfo
      val analysisInfo = imageAnalysis.resolutionInfo
      check(previewInfo != null && analysisInfo != null && StreamGeometry.sameAspect(
          previewInfo.resolution.width, previewInfo.resolution.height,
          analysisInfo.resolution.width, analysisInfo.resolution.height)) {
        "Camera negotiated different preview/analysis aspect ratios; use a shared stream size"
      }
      Log.i(TAG, "Negotiated preview=${previewInfo.resolution}, analysis=${analysisInfo.resolution}")
    }
    warnAboutOptionsThisBackendIgnores()

    // Binding settles the resolution, so the frame geometry is known here rather than only
    // once a frame has arrived. That matters for more than tidiness: the viewfinder is
    // drawn when the camera reports ready, and a geometry that is still null at that moment
    // makes ViewfinderView return early -- and nothing invalidates it again, because the
    // laser animation reschedules itself from inside the draw it never got to do. The
    // overlay was simply missing until this was read here.
    val info = imageAnalysis.resolutionInfo
    if (info != null) {
      frameSize = Point(info.resolution.width, info.resolution.height)
      frameRotation = PreviewCoordinateMapper.normalizeRotation(info.rotationDegrees)
      Log.i(TAG, "Bound at ${info.resolution}, rotation $frameRotation")
    }
  }

  /**
   * Answers CameraX's request for somewhere to draw.
   *
   * The surface always exists by now — the scanner only opens a backend once
   * `surfaceCreated` has fired — but it can have gone away again between the request being
   * made and this running, and handing CameraX an invalid surface is how a preview fails
   * silently rather than loudly.
   */
  private fun provideSurface(request: SurfaceRequest) {
    val target = holder
    if (target == null || !target.surface.isValid) {
      request.willNotProvideSurface()
      return
    }
    // The buffer takes the camera's size; the SurfaceView still fills the scanner, so the
    // preview is scaled to the view exactly as the Camera1 path's was.
    target.setFixedSize(request.resolution.width, request.resolution.height)
    request.provideSurface(target.surface, mainExecutor) {
      // Either the surface was released or the request was superseded. The SurfaceView owns
      // the surface and releases it with the window, so there is nothing to do with either.
    }
  }

  private fun onFrame(image: ImageProxy) {
    // Every frame carries the size and rotation; they are cheap to re-read and the first
    // frame is the only moment the geometry can be known at all.
    rememberGeometry(image)

    val request = pendingRequest.getAndSet(null)
    if (request == null) {
      image.close()
      return
    }
    val frame = CameraXScanFrame(image, cropFor(image))
    val message = request.handler.obtainMessage(request.what, frame)
    if (!request.handler.sendMessage(message)) {
      // The decode thread has quit. Closing it here is not optional: an ImageProxy nobody
      // closes holds a buffer slot, and the camera stops delivering once they are all held.
      frame.close()
    }
  }

  private fun rememberGeometry(image: ImageProxy) {
    val size = frameSize
    if (size != null && size.x == image.width && size.y == image.height &&
        frameRotation == image.imageInfo.rotationDegrees) {
      return
    }
    frameSize = Point(image.width, image.height)
    frameRotation = PreviewCoordinateMapper.normalizeRotation(image.imageInfo.rotationDegrees)
    synchronized(this) {
      framingRectInPreview = null
    }
    Log.i(TAG, "Frame ${image.width}x${image.height}, rotation $frameRotation")
  }

  private fun cropFor(image: ImageProxy): YPlaneLuminanceSource? {
    val rect = getFramingRectInPreview() ?: return null
    val plane = image.planes[0]
    return try {
      YPlaneLuminanceSource(plane.buffer, plane.rowStride, plane.pixelStride,
                            rect.left, rect.top, rect.width(), rect.height())
    } catch (iae: IllegalArgumentException) {
      // The crop did not fit the buffer this frame actually carries. Dropping the frame
      // beats decoding a region that is not the one the user is aiming at.
      Log.w(TAG, "Scan rectangle does not fit the frame", iae)
      null
    }
  }

  @Synchronized
  override fun getFramingRect(): Rect? {
    framingRect?.let { return it }
    if (frameSize == null) {
      // No camera geometry yet, so nothing has been negotiated to draw against. Camera1
      // answers null before its driver is open for the same reason.
      return null
    }
    val screen = viewport() ?: return null
    val requestedWidth = options.framingRectWidth
    val requestedHeight = options.framingRectHeight
    val (width, height) = if (requestedWidth > 0 && requestedHeight > 0) {
      Pair(minOf(requestedWidth, screen.x), minOf(requestedHeight, screen.y))
    } else {
      FramingRectSizer.sizeFor(screen.x, screen.y)
    }
    val leftOffset = (screen.x - width) / 2
    val topOffset = (screen.y - height) / 2
    val rect = Rect(leftOffset, topOffset, leftOffset + width, topOffset + height)
    framingRect = rect
    return rect
  }

  @Synchronized
  override fun getFramingRectInPreview(): Rect? {
    framingRectInPreview?.let { return it }
    val rect = getFramingRect() ?: return null
    val frame = frameSize ?: return null
    val screen = viewport() ?: return null
    val rotation = frameRotation
    // The frame as it appears on screen: a 90 or 270 degree rotation swaps its axes.
    val onScreen = if (rotation == 90 || rotation == 270) Point(frame.y, frame.x) else frame
    val box = PreviewCoordinateMapper.toFrameCoordinates(
        rect.left, rect.top, rect.right, rect.bottom,
        screen.x, screen.y,
        onScreen.x, onScreen.y,
        frame.x, frame.y,
        rotation,
        // The back camera is the only one this backend binds, and it is not mirrored.
        false) ?: return null
    val mapped = Rect(box[0], box[1], box[2], box[3])
    framingRectInPreview = mapped
    return mapped
  }

  /** Unbinds this scanner's use cases and no one else's. */
  private fun unbindOurUseCases(cameraProvider: ProcessCameraProvider) {
    val ours = ArrayList<androidx.camera.core.UseCase>(2)
    preview?.let { ours.add(it) }
    analysis?.let { ours.add(it) }
    if (ours.isNotEmpty()) {
      cameraProvider.unbind(*ours.toTypedArray())
    }
  }

  /**
   * Says, once per session, which of the caller's settings this backend is going to
   * ignore.
   *
   * A setting that does nothing and reports nothing is the worst of the three possible
   * behaviours. [capabilities] is the same information for a caller that would rather ask
   * than read logcat.
   */
  private fun warnAboutOptionsThisBackendIgnores() {
    val set = ArrayList<String>(6)
    if (options.cameraId != OpenCameraInterface.NO_REQUESTED_CAMERA) set.add("cameraId")
    if (!options.isAutoFocus) set.add("autoFocus")
    if (options.isContinuousFocusDisabled) set.add("continuousFocusDisabled")
    if (options.isMeteringDisabled) set.add("meteringDisabled")
    if (options.isExposureDisabled) set.add("exposureDisabled")
    if (options.isBarcodeSceneModeDisabled) set.add("barcodeSceneModeDisabled")
    if (options.isInvertScan) set.add("invertScan")
    if (set.isNotEmpty()) {
      Log.w(TAG, "The CameraX backend does not apply $set; " +
          "focus, metering, exposure and scene mode are CameraX's own 3A, and it binds " +
          "the back camera whatever cameraId says. Use CameraBackend.CAMERA1 for those.")
    }
  }

  override fun capabilities(): CameraCapabilities = CameraCapabilities(
      backendName = "camerax",
      // Deliberately the back camera, before CameraX initialises; see the class comment.
      cameraIdHonoured = false,
      autoFocusHonoured = false,
      meteringHonoured = false,
      exposureHonoured = false,
      barcodeSceneModeHonoured = false,
      invertScanHonoured = false,
      framingRectHonoured = true,
      torchHonoured = true,
      torchAvailable = camera?.cameraInfo?.hasFlashUnit() ?: false)

  override fun setTorch(on: Boolean) {
    val theCamera = camera ?: return
    if (!theCamera.cameraInfo.hasFlashUnit()) {
      return
    }
    theCamera.cameraControl.enableTorch(on)
  }

  @Synchronized
  override fun setViewportSize(width: Int, height: Int) {
    val next = if (width > 0 && height > 0) Point(width, height) else null
    if (next == viewportSize) {
      return
    }
    viewportSize = next
    // A rotation, a split-screen drag or a host resizing its dialog all land here, and
    // both cached rectangles are in the old coordinate space.
    framingRect = null
    framingRectInPreview = null
  }

  /**
   * The coordinate space the scan rectangle lives in: the scanning view, or the display
   * when the view has not been laid out yet.
   */
  private fun viewport(): Point? = viewportSize ?: screenResolution()

  override fun requestFrame(handler: Handler, what: Int) {
    pendingRequest.set(FrameRequest(handler, what))
  }

  override fun stopFrames() {
    pendingRequest.set(null)
  }

  override fun close() {
    pendingRequest.set(null)
    analysis?.clearAnalyzer()
    analysis = null
    lifecycleOwner?.destroy()
    lifecycleOwner = null
    // Ours, not everything. Destroying the lifecycle owner above already unbinds them;
    // this is the belt to that's braces, and it is still scoped to this scanner.
    provider?.let { unbindOurUseCases(it) }
    provider = null
    preview = null
    camera = null
    holder = null
    // After unbindAll no further frame can arrive, so nothing is still reading the buffers
    // the executor's thread was handing out.
    analysisExecutor?.shutdown()
    analysisExecutor = null
    synchronized(this) {
      framingRect = null
      framingRectInPreview = null
    }
    frameSize = null
  }

  /**
   * The resolution both use cases ask for.
   *
   * 16:9 at 1920x1080 is the operating point everything in this repository was measured at —
   * the L2 benchmark matrix and the Phase 6 spike alike — and pinning the aspect ratio as
   * well as the size is not optional. Left to itself CameraX answered 1920x1440 on both test
   * devices while the Camera1 path negotiated 1920x1080: 38% more pixels per frame, decoded
   * every frame, for no gain.
   */
  private fun resolutionSelector(width: Int, height: Int): ResolutionSelector = ResolutionSelector.Builder()
      .setAspectRatioStrategy(
          AspectRatioStrategy(
              if (kotlin.math.abs(maxOf(width, height).toDouble() / minOf(width, height) - 4.0 / 3) <
                  kotlin.math.abs(maxOf(width, height).toDouble() / minOf(width, height) - 16.0 / 9))
                AspectRatio.RATIO_4_3 else AspectRatio.RATIO_16_9,
              AspectRatioStrategy.FALLBACK_RULE_AUTO))
      .setResolutionStrategy(
          ResolutionStrategy(Size(width, height),
                             ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
      .build()

  @Suppress("DEPRECATION") // WindowManager.getDefaultDisplay: the Camera1 path reads it too
  private fun display() =
      (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay

  private fun displayRotation(): Int = display()?.rotation ?: Surface.ROTATION_0

  @Suppress("DEPRECATION") // Display.getSize: matches what CameraConfigurationManager uses
  private fun screenResolution(): Point? {
    val size = Point()
    display()?.getSize(size) ?: return null
    if (size.x <= 0 || size.y <= 0) {
      return null
    }
    return size
  }

  private fun classify(e: Exception): ScannerError = when (e) {
    // bindToLifecycle rejects a selector no camera satisfies; with the back-camera limiter
    // set, that means there is no back camera to scan with.
    is IllegalArgumentException -> ScannerError.NO_CAMERA
    else -> ScannerError.CAMERA_UNAVAILABLE
  }

  /** Where a pending [requestFrame] should deliver. */
  private class FrameRequest(val handler: Handler, val what: Int)

  /**
   * CameraX binds use cases to a `LifecycleOwner`, and `ScannerView` is a plain `View` whose
   * host calls `resume` and `pause` explicitly. Rather than require the host to be a
   * lifecycle owner — which would make this library's contract depend on AndroidX lifecycle
   * for everybody, including callers on the Camera1 path — the backend drives a registry of
   * its own, resumed when it binds and destroyed when it closes.
   */
  private class ScannerLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    fun resume() {
      registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
      // A registry that was never resumed is still INITIALIZED, and LifecycleRegistry
      // throws rather than let that go straight to DESTROYED. It happens whenever the host
      // pauses while the camera provider is still coming up, which the 100-cycle
      // open/close loop hits about once in a hundred -- it crashed the app in onPause.
      if (registry.currentState == Lifecycle.State.INITIALIZED) {
        registry.currentState = Lifecycle.State.CREATED
      }
      registry.currentState = Lifecycle.State.DESTROYED
    }
  }

  companion object {
    private const val TAG = "CameraXBackend"

    private const val TARGET_WIDTH = 1920
    private const val TARGET_HEIGHT = 1080

    /** `configureInstance` may be called once per process and throws on the second call. */
    private var configured = false

    /**
     * Tells CameraX up front that only the back camera matters, before anything asks for an
     * instance.
     *
     * Without it CameraX's `CameraValidator` insists both lens facings exist and retries
     * initialisation for about five seconds when one does not. See the class comment; the
     * numbers are in the decision record.
     */
    @JvmStatic
    @Synchronized
    fun configureBackCameraOnly(context: Context) {
      if (configured) {
        return
      }
      configured = true
      try {
        ProcessCameraProvider.configureInstance(
            CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
                .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
                .build())
      } catch (ise: IllegalStateException) {
        // Something else in the process configured CameraX first. Its configuration wins,
        // which is the documented behaviour, and the scanner still works — it just pays the
        // validator's retry on a one-camera device.
        Log.i(TAG, "CameraX was already configured by this process", ise)
      }
    }
  }
}
