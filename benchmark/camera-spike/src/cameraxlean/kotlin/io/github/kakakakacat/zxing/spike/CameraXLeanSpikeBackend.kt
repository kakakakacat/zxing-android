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

package io.github.kakakakacat.zxing.spike

import android.app.Activity
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.kakakakacat.zxing.android.scanner.FrameDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The same CameraX binding as the `camerax` flavor, without `camera-view`.
 *
 * `PreviewView` is convenient and expensive: `camera-view` depends on AppCompat and on
 * `camera-video`, and a barcode scanner uses neither. This flavor exists to put a number on
 * that — it hands CameraX the `SurfaceView` this project already draws the preview into and
 * answers the `SurfaceRequest` itself, which is the whole of what `PreviewView` was doing
 * for the scanner's purposes.
 *
 * **What it gives up is real**, and the decision record says so rather than pretending the
 * lean path is free: `PreviewView` also handles the scale/crop transform between the
 * preview's aspect ratio and the view's, the display-rotation transform, and the
 * `getOutputTransform` mapping an overlay needs to draw on top of the preview. This project
 * has its own `PreviewCoordinateMapper` for the last of those, which is why the trade is
 * worth measuring here at all.
 *
 * The scale/crop handling is deliberately *not* reimplemented. The preview on this flavor
 * may be stretched, and that is fine: nothing here measures what the preview looks like.
 */
class CameraXLeanSpikeBackend : SpikeBackend, SurfaceHolder.Callback {

  override val name: String = "cameraxlean"

  private var surfaceView: SurfaceView? = null
  private var provider: ProcessCameraProvider? = null
  private var analysisExecutor: ExecutorService? = null
  private var decoder: FrameDecoder? = null
  private var lifecycleOwner: SpikeLifecycleOwner? = null
  private var firstFrameSeen = false
  private var surfaceReady = false
  private var pendingRequest: SurfaceRequest? = null

  override fun createView(activity: Activity): View {
    val view = SurfaceView(activity)
    view.holder.addCallback(this)
    surfaceView = view
    return view
  }

  override fun start(activity: Activity, events: (String, Long, String) -> Unit) {
    val owner = SpikeLifecycleOwner()
    lifecycleOwner = owner
    decoder = SpikeDecoder.create()
    val executor = Executors.newSingleThreadExecutor()
    analysisExecutor = executor

    if (activity.intent?.getBooleanExtra(CameraXFrames.EXTRA_BACK_CAMERA_ONLY, false) == true) {
      CameraXFrames.configureBackCameraOnly()
    }

    events(SpikeBackend.EVENT_OPEN_START, SystemClock.uptimeMillis(), "")
    val future = ProcessCameraProvider.getInstance(activity)
    future.addListener({
      try {
        val cameraProvider = future.get()
        provider = cameraProvider

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider { request -> onSurfaceRequested(request) }

        val analysis = CameraXFrames.buildAnalysis()
        analysis.setAnalyzer(executor) { image ->
          if (firstFrameSeen) {
            image.close()
          } else {
            firstFrameSeen = true
            CameraXFrames.onFirstFrame(image, decoder, events)
          }
        }

        owner.resume()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
      } catch (t: Throwable) {
        events(SpikeBackend.EVENT_FAILED, SystemClock.uptimeMillis(),
               "error=${t.javaClass.name}: ${t.message}")
      }
    }, ContextCompat.getMainExecutor(activity))
  }

  /**
   * Answers CameraX's request for somewhere to draw.
   *
   * It can arrive before the surface exists, so a request that comes early is held until
   * [surfaceCreated]. Providing a surface that is not yet valid is how this fails silently
   * with a preview that never appears.
   */
  private fun onSurfaceRequested(request: SurfaceRequest) {
    val view = surfaceView ?: return
    if (!surfaceReady) {
      pendingRequest = request
      return
    }
    pendingRequest = null
    val holder = view.holder
    holder.setFixedSize(request.resolution.width, request.resolution.height)
    request.provideSurface(holder.surface, ContextCompat.getMainExecutor(view.context)) {
      // The result says whether the surface was used or the request was superseded. The
      // spike has nothing to do with either: the SurfaceView owns the surface and releases
      // it with the window.
    }
  }

  override fun surfaceCreated(holder: SurfaceHolder) {
    surfaceReady = true
    pendingRequest?.let { onSurfaceRequested(it) }
  }

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    surfaceReady = false
  }

  override fun stop() {
    surfaceView?.holder?.removeCallback(this)
    lifecycleOwner?.destroy()
    lifecycleOwner = null
    provider?.unbindAll()
    provider = null
    decoder?.close()
    decoder = null
    analysisExecutor?.shutdown()
    analysisExecutor = null
    surfaceView = null
    pendingRequest = null
  }

  /** See the note on the `camerax` flavor's copy: a plain Activity is not a LifecycleOwner. */
  private class SpikeLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    fun resume() {
      registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
      registry.currentState = Lifecycle.State.DESTROYED
    }
  }
}
