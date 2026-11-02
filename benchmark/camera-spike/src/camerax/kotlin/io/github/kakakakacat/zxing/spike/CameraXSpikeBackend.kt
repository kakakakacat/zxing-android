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
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.kakakakacat.zxing.android.scanner.FrameDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX with `PreviewView`, which is how the documentation tells an app to do it: a
 * `Preview` on screen and an `ImageAnalysis` delivering `YUV_420_888` frames to a background
 * executor, newest frame only.
 *
 * Two things this flavor is measuring at once:
 *
 *  1. **How long CameraX takes to hand this process a frame**, against the Camera1 flavor's
 *     number on the same device. `ProcessCameraProvider.getInstance` is asynchronous and the
 *     camera is not opened until the future completes, so the clock starts where the backend
 *     first asks for a provider — the earliest instant an app controls.
 *  2. **What `camera-view` costs**, against the `cameraxlean` flavor, which does the same
 *     work with a plain `SurfaceView`. `camera-view` depends on AppCompat and on
 *     `camera-video`, neither of which a scanner has any use for.
 *
 * `YPlaneLuminanceSource` reading a real `ImageProxy` is exercised here for the first time;
 * see [CameraXFrames], which both CameraX flavors share.
 */
class CameraXSpikeBackend : SpikeBackend {

  override val name: String = "camerax"

  private var previewView: PreviewView? = null
  private var provider: ProcessCameraProvider? = null
  private var analysisExecutor: ExecutorService? = null
  private var decoder: FrameDecoder? = null
  private var lifecycleOwner: SpikeLifecycleOwner? = null
  private var firstFrameSeen = false

  override fun createView(activity: Activity): View {
    val view = PreviewView(activity)
    previewView = view
    return view
  }

  override fun start(activity: Activity, events: (String, Long, String) -> Unit) {
    val view = previewView ?: return
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
        preview.surfaceProvider = view.surfaceProvider

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

  override fun stop() {
    lifecycleOwner?.destroy()
    lifecycleOwner = null
    provider?.unbindAll()
    provider = null
    decoder?.close()
    decoder = null
    analysisExecutor?.shutdown()
    analysisExecutor = null
    previewView = null
  }

  /**
   * CameraX binds to a lifecycle and this spike's activity is a plain [Activity], so it
   * brings its own. Deliberately not `ComponentActivity`: the base flavor must not gain an
   * AndroidX activity dependency, or the APK size delta between the flavors would stop being
   * the cost of CameraX.
   */
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
