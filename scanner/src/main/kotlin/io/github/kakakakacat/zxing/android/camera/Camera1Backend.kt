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

/** Camera1 owns a looper. Only open/error notifications are dispatched to the main thread. */
internal class Camera1Backend(context: Context, private val options: ScannerOptions) : ScannerCameraBackend {
  val cameraManager = CameraManager(context.applicationContext, options)
  private val mainHandler = Handler(Looper.getMainLooper())
  private val lock = Any()
  private var worker: Handler? = null
  private var workerLease: Camera1Worker.Lease? = null
  @Volatile private var session: Any? = null
  @Volatile private var opened = false
  @Volatile private var framesEnabled = false
  private var callback: ScannerCameraBackend.Callback? = null

  override val isOpen get() = opened
  override fun getFramingRect(): Rect? = cameraManager.getFramingRect()
  override fun getFramingRectInPreview(): Rect? = cameraManager.getFramingRectInPreview()
  override fun capabilities(): CameraCapabilities = cameraManager.capabilities()

  override fun open(holder: SurfaceHolder, callback: ScannerCameraBackend.Callback) {
    synchronized(lock) {
      if (session != null) return
      if (options.analysisWidth > 0) Log.w(TAG, "Camera1 has one stream; analysis size is ignored")
      val token = Any()
      session = token
      this.callback = callback
      val handler = worker ?: Camera1Worker.acquire().let { lease ->
        workerLease = lease
        lease.handler.also { worker = it }
      }
      handler.post {
        val failure = openOnWorker(holder)
        if (failure != null) {
          try { cameraManager.closeDriver() }
          catch (cleanup: RuntimeException) { Log.w(TAG, "Camera cleanup failed", cleanup) }
        }
        mainHandler.post {
          if (session === token) {
            if (failure == null) {
              opened = true
              framesEnabled = true
              callback.onCameraReady()
            } else callback.onCameraError(failure.error, failure.cause)
          }
        }
      }
    }
  }

  private fun openOnWorker(holder: SurfaceHolder): OpenFailure? {
    try { cameraManager.openDriver(holder) }
    catch (failure: IOException) {
      return OpenFailure(if (cameraManager.isOpen) ScannerError.PREVIEW_FAILED else unavailable(), failure)
    } catch (failure: RuntimeException) { return OpenFailure(unavailable(), failure) }
    return try { cameraManager.startPreview(); null }
    catch (failure: RuntimeException) { OpenFailure(ScannerError.PREVIEW_FAILED, failure) }
  }

  private fun enqueue(action: () -> Unit) {
    synchronized(lock) {
      val token = session ?: return
      worker?.post {
        if (session === token) {
          try { action() }
          catch (failure: RuntimeException) {
            mainHandler.post {
              if (session === token) callback?.onCameraError(ScannerError.PREVIEW_FAILED, failure)
            }
          }
        }
      }
    }
  }
  override fun setTorch(on: Boolean) { enqueue { cameraManager.setTorch(on) } }
  override fun setViewportSize(width: Int, height: Int) {
    synchronized(lock) {
      val handler = worker
      if (handler == null) cameraManager.setViewportSize(width, height)
      else handler.post { cameraManager.setViewportSize(width, height) }
    }
  }
  override fun requestFrame(handler: Handler, what: Int) {
    if (!framesEnabled) return
    enqueue { if (framesEnabled) cameraManager.requestPreviewFrame(handler, what) }
  }
  override fun stopFrames() {
    framesEnabled = false
    cameraManager.cancelPreviewRequest()
    enqueue { cameraManager.stopPreview() }
  }
  override fun close() {
    synchronized(lock) {
      if (session == null) return
      opened = false
      framesEnabled = false
      session = null
      callback = null
      cameraManager.cancelPreviewRequest()
      val handler = worker ?: return
      handler.post {
        try { cameraManager.closeDriver() }
        catch (failure: RuntimeException) { Log.w(TAG, "Could not close camera", failure) }
        finally {
          synchronized(lock) {
            // A quick surface recreation may already have enqueued another open behind us.
            if (session == null && worker === handler) {
              worker = null
              workerLease?.close()
              workerLease = null
            }
          }
        }
      }
    }
  }
  private fun unavailable() = if (cameraManager.hasAnyCamera())
    ScannerError.CAMERA_UNAVAILABLE else ScannerError.NO_CAMERA
  private class OpenFailure(val error: ScannerError, val cause: Exception)
  private companion object { const val TAG = "Camera1Backend" }
}
