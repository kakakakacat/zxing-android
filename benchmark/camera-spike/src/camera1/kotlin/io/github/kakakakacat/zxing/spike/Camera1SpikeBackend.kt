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
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import io.github.kakakakacat.zxing.android.camera.CameraManager
import io.github.kakakakacat.zxing.android.scanner.FrameDecoder
import io.github.kakakakacat.zxing.android.scanner.ScannerOptions

/**
 * The stack that ships today, driven exactly the way [io.github.kakakakacat.zxing.android.scanner.ScannerView]
 * drives it: wait for the surface, open the driver, start the preview, ask for one frame.
 *
 * Deliberately `:scanner`'s own [CameraManager] rather than a fresh Camera1 implementation.
 * A hand-rolled minimal Camera1 open would be faster than what the library does and would
 * measure something nobody ships — the parameter negotiation in `CameraConfigurationManager`
 * is part of the cost of this backend, and the point of the comparison is the real path.
 */
class Camera1SpikeBackend : SpikeBackend, SurfaceHolder.Callback {

  override val name: String = "camera1"

  private var cameraManager: CameraManager? = null
  private var surfaceView: SurfaceView? = null
  private var decodeThread: HandlerThread? = null
  private var decoder: FrameDecoder? = null
  private var events: ((String, Long, String) -> Unit)? = null
  private var activity: Activity? = null
  private var started = false

  override fun createView(activity: Activity): View {
    val view = SurfaceView(activity)
    surfaceView = view
    return view
  }

  override fun start(activity: Activity, events: (String, Long, String) -> Unit) {
    this.activity = activity
    this.events = events
    // Camera1 cannot deliver a frame before it has a surface to draw into, so the clock for
    // this backend starts when the surface does. That is also where ScannerView starts it.
    surfaceView?.holder?.addCallback(this)
  }

  override fun surfaceCreated(holder: SurfaceHolder) {
    if (started) {
      return
    }
    started = true
    val activity = this.activity ?: return
    val report = events ?: return

    val thread = HandlerThread("spike-decode")
    thread.start()
    decodeThread = thread
    decoder = SpikeDecoder.create()

    val manager = CameraManager(activity.applicationContext, ScannerOptions.defaults())
    cameraManager = manager

    val frameHandler = object : Handler(thread.looper) {
      override fun handleMessage(msg: Message) {
        val arrivedAt = SystemClock.uptimeMillis()
        val width = msg.arg1
        val height = msg.arg2
        val data = msg.obj as? ByteArray
        report(SpikeBackend.EVENT_FIRST_FRAME, arrivedAt,
               "frame=${width}x${height} ${SpikeDecoder.buildInfo()}")
        decodeOnce(data, width, height, report)
      }
    }

    try {
      report(SpikeBackend.EVENT_OPEN_START, SystemClock.uptimeMillis(), "")
      manager.openDriver(holder)
      manager.startPreview()
      manager.requestPreviewFrame(frameHandler, MSG_FRAME)
    } catch (t: Throwable) {
      report(SpikeBackend.EVENT_FAILED, SystemClock.uptimeMillis(), "error=${t.javaClass.name}: ${t.message}")
    }
  }

  private fun decodeOnce(
      data: ByteArray?,
      width: Int,
      height: Int,
      report: (String, Long, String) -> Unit
  ) {
    val manager = cameraManager ?: return
    val decoder = this.decoder ?: return
    val startedAt = SystemClock.uptimeMillis()
    val source = manager.buildLuminanceSource(data, width, height)
    if (source == null) {
      report(SpikeBackend.EVENT_FIRST_DECODE, startedAt, "decode=no-source")
      return
    }
    val result = decoder.decode(source)
    val finishedAt = SystemClock.uptimeMillis()
    report(SpikeBackend.EVENT_FIRST_DECODE, finishedAt,
           "decodeMs=${finishedAt - startedAt} region=${source.width}x${source.height}" +
               " found=${result != null}")
  }

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

  override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

  override fun stop() {
    surfaceView?.holder?.removeCallback(this)
    cameraManager?.stopPreview()
    cameraManager?.closeDriver()
    cameraManager = null
    decoder?.close()
    decoder = null
    decodeThread?.quit()
    decodeThread = null
    activity = null
    events = null
  }

  private companion object {
    const val MSG_FRAME = 1
  }
}
