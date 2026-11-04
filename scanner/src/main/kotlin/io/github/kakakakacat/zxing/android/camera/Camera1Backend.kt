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
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import io.github.kakakakacat.zxing.android.scanner.ScannerError
import io.github.kakakakacat.zxing.android.scanner.ScannerOptions
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * The original `android.hardware.Camera` path, behind the backend interface.
 *
 * It owns nothing: [CameraManager] is still the whole of the Camera1 stack and is still
 * public API. This class only adapts it to the shape [CameraXBackend] also has, and holds
 * the open-failure classification that used to sit inside `ScannerView` — which is where it
 * stopped belonging the moment there were two stacks to classify failures from.
 *
 * ### Opening does not run on the main thread
 *
 * It used to. `ScannerView.surfaceCreated` runs on the main thread and called
 * `CameraManager.openDriver` straight from it, so the window could not draw until
 * `Camera.open()` and the whole `getParameters`/`setParameters` negotiation with the HAL
 * had finished. L3 measured that at **401–436 ms** on DN-ENG41
 * (`docs/maint/reports/l3-verdict-DN-ENG41-20261102.md`), and it is the reason the same
 * run has CameraX putting a window on screen ~330 ms sooner: CameraX's open is
 * asynchronous and this one was not.
 *
 * Now the open runs on one serial worker and the result arrives on the main thread through
 * [ScannerCameraBackend.Callback], which is the contract the interface always stated and
 * which `ScannerView` was already written against for CameraX's sake.
 *
 * **Everything that can block on [CameraManager]'s monitor goes through that same worker**
 * while an open is in flight. The monitor is the whole point: `CameraManager` is
 * `@Synchronized` throughout, so a main-thread call to `stopPreview`, `closeDriver` or even
 * `isOpen` during an open would simply wait out the open instead — the same stall under a
 * different name. The worker is serial, so a close queued behind an open still runs after
 * it and in the right order.
 *
 * The worker deliberately has **no `Looper`**. `android.hardware.Camera` delivers its
 * callbacks — one-shot preview frames and auto-focus — to the event loop of the thread that
 * called `Camera.open()`, falling back to the main looper when that thread has none. So a
 * plain worker keeps those callbacks arriving exactly where they arrived before, and a
 * `HandlerThread` here would silently move them.
 *
 * One hole is left open on purpose: `ScannerView.getCameraManager()` hands the
 * [CameraManager] to a caller, and a caller that touches it from the main thread during an
 * open will still block on that monitor. Closing that would mean changing a published API.
 */
internal class Camera1Backend(
    context: Context,
    private val options: ScannerOptions
) : ScannerCameraBackend {

  /** Exposed so `ScannerView.getCameraManager()` can still hand it to a caller. */
  val cameraManager: CameraManager = CameraManager(context.applicationContext, options)

  private val mainHandler = Handler(Looper.getMainLooper())

  /** The worker every blocking [CameraManager] call runs on. Null until the first open. */
  private var worker: ExecutorService? = null

  /**
   * Identity of the open in flight, or null when none is. Read and written on the main
   * thread only, which is what makes testing it from [close] and [stopFrames] sound.
   */
  private var pendingOpen: Any? = null

  /**
   * Whether the camera is open, tracked here rather than asked of [CameraManager].
   *
   * `CameraManager.isOpen` is `@Synchronized`, so delegating would make the one property
   * `ScannerView` checks before opening block for the length of an open already running.
   */
  @Volatile
  private var opened = false

  override val isOpen: Boolean
    get() = opened

  override fun getFramingRect(): Rect? = cameraManager.getFramingRect()

  override fun getFramingRectInPreview(): Rect? = cameraManager.getFramingRectInPreview()

  override fun setTorch(on: Boolean) {
    onWorkerIfOpening { cameraManager.setTorch(on) }
  }

  override fun open(holder: SurfaceHolder, callback: ScannerCameraBackend.Callback) {
    if (options.analysisWidth > 0) {
      Log.w(TAG, "Camera1 has one stream; analysis size is ignored, use setPreviewSize instead")
    }
    if (pendingOpen != null || opened) {
      // Open, or still opening. `isOpen` cannot answer this on its own: it is false for the
      // whole of the window between asking for the camera and getting it.
      return
    }
    val token = Any()
    pendingOpen = token
    val executor = worker ?: Executors.newSingleThreadExecutor { runnable ->
      // No Looper, deliberately -- see the class comment.
      Thread(runnable, "zxing-camera1")
    }
    worker = executor
    executor.execute {
      val failure = openOnWorker(holder)
      mainHandler.post {
        if (pendingOpen !== token) {
          // close() ran while the camera was opening. It queued the release behind this
          // open on the same worker, so the camera is already on its way out; there is no
          // caller left to tell either way.
          return@post
        }
        pendingOpen = null
        if (failure == null) {
          opened = true
          callback.onCameraReady()
        } else {
          callback.onCameraError(failure.error, failure.cause)
        }
      }
    }
  }

  /** Runs on [worker]. Returns null on success, or what went wrong, already classified. */
  private fun openOnWorker(holder: SurfaceHolder): OpenFailure? {
    try {
      cameraManager.openDriver(holder)
    } catch (ioe: IOException) {
      // openDriver reports "no camera object from the driver" and a failed
      // setPreviewDisplay through the same exception type; isOpen tells them apart.
      Log.w(TAG, "Could not open the camera or attach the preview", ioe)
      val error =
          if (cameraManager.isOpen) ScannerError.PREVIEW_FAILED else unavailableOrMissing()
      return OpenFailure(error, ioe)
    } catch (re: RuntimeException) {
      // Camera.open() throws RuntimeException for "no camera", "camera in use" and
      // "service died" alike; distinguish what we can rather than blaming the device.
      Log.w(TAG, "Could not open the camera", re)
      return OpenFailure(unavailableOrMissing(), re)
    }
    try {
      cameraManager.startPreview()
    } catch (re: RuntimeException) {
      Log.w(TAG, "Could not start the preview", re)
      return OpenFailure(ScannerError.PREVIEW_FAILED, re)
    }
    return null
  }

  override fun setViewportSize(width: Int, height: Int) {
    onWorkerIfOpening { cameraManager.setViewportSize(width, height) }
  }

  override fun capabilities(): CameraCapabilities = cameraManager.capabilities()

  override fun stopFrames() {
    onWorkerIfOpening { cameraManager.stopPreview() }
  }

  override fun close() {
    opened = false
    // Abandons the open in flight, if there is one: its completion will find the token
    // changed and say nothing.
    val wasOpening = pendingOpen != null
    pendingOpen = null
    val executor = worker
    worker = null
    if (wasOpening && executor != null) {
      // Queued behind the open rather than racing it, so the camera that open is about to
      // produce is the camera this releases.
      submit(executor) { cameraManager.closeDriver() }
      executor.shutdown()
    } else {
      cameraManager.closeDriver()
      executor?.shutdown()
    }
  }

  override fun requestFrame(handler: Handler, what: Int) {
    cameraManager.requestPreviewFrame(handler, what)
  }

  /**
   * Runs [action] on the worker when an open is in flight, and inline otherwise.
   *
   * Inline is the common case and keeps the old behaviour where it matters: a caller that
   * stops frames on an open camera has them stopped by the time the call returns.
   */
  private fun onWorkerIfOpening(action: () -> Unit) {
    val executor = worker
    if (pendingOpen != null && executor != null) {
      submit(executor, action)
    } else {
      action()
    }
  }

  private fun submit(executor: ExecutorService, action: () -> Unit) {
    try {
      executor.execute(action)
    } catch (ree: RejectedExecutionException) {
      // The worker was already shut down, which means the camera is already gone.
      Log.w(TAG, "Camera work rejected; the backend is closing", ree)
    }
  }

  private fun unavailableOrMissing(): ScannerError =
      if (cameraManager.hasAnyCamera()) ScannerError.CAMERA_UNAVAILABLE else ScannerError.NO_CAMERA

  private class OpenFailure(val error: ScannerError, val cause: Exception)

  private companion object {
    private const val TAG = "Camera1Backend"
  }
}
