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

package io.github.kakakakacat.zxing.android.scanner

import android.os.Build
import android.os.Trace
import io.github.kakakakacat.zxing.android.camera.ScanFrame
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Startup spans for L3 and opt-in system-trace frame ownership/timing spans.
 *
 * `docs/maint/BENCHMARKS.md` §11 asks how long it takes to get from launch to a camera that
 * is ready, and from there to a frame and to a scan. None of that is visible from outside
 * the library: Macrobenchmark can time the window's first frame, but "the camera is ready"
 * and "a frame reached the decoder" are events only this code knows about. So it emits
 * them, and the L3 harness reads them out of a system trace.
 *
 * **Asynchronous sections, because these spans do not nest.** They begin and end on
 * different looper iterations and, for [FIRST_FRAME], on different threads. A synchronous
 * `beginSection` left open across a looper iteration swallows everything else on that
 * thread and produces a trace that means nothing.
 *
 * `Trace.beginAsyncSection` is API 29. Below that these are no-ops and **an L3 run on such
 * a device records nothing** — which is stated in §11 rather than worked around, because
 * the alternative is a dependency on `androidx.tracing` in a published AAR for the sake of
 * measurement on devices this project does not have.
 *
 * Cost when nothing is tracing: one `SDK_INT` comparison and one `Trace.isEnabled()` call,
 * which reads a process-local flag. Startup spans end once per session. Frame request,
 * queue, decode, hold and release spans are emitted only while system tracing is enabled;
 * no barcode payload is recorded.
 */
internal object ScannerTrace {

  /** Scanner asked to open a camera → the backend reported it ready. */
  const val CAMERA_OPEN = "zxing.camera.open"

  /** Camera ready → the first frame reached the decode thread. */
  const val FIRST_FRAME = "zxing.first.frame"

  /** Camera ready → the first successful decode. Needs a barcode in front of the lens. */
  const val FIRST_SCAN = "zxing.first.scan"

  /**
   * Which backend the session ran on, as a span over the same interval as [CAMERA_OPEN].
   *
   * It exists so an L3 result can be checked against itself. The backend is chosen by a
   * preference written before the run, outside the benchmark, and "the preference did not
   * take" looks exactly like "the other backend is the same speed". With this in the
   * trace, the run says which backend it measured and the report refuses a mismatch —
   * the same rule `:benchmark-android` applies to its runner arguments.
   */
  fun backendSpan(backend: ScannerOptions.CameraBackend): String = when (backend) {
    ScannerOptions.CameraBackend.CAMERAX -> "zxing.backend.camerax"
    ScannerOptions.CameraBackend.CAMERA1 -> "zxing.backend.camera1"
  }

  private val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

  /** @param cookie the scanning session id, so overlapping sessions cannot be confused */
  fun begin(name: String, cookie: Int) {
    if (supported && Trace.isEnabled()) {
      Trace.beginAsyncSection(name, cookie)
    }
  }

  fun end(name: String, cookie: Int) {
    if (supported && Trace.isEnabled()) {
      Trace.endAsyncSection(name, cookie)
    }
  }

  const val FRAME_REQUEST = "zxing.frame.request"
  const val FRAME_QUEUE = "zxing.frame.queue"
  const val FRAME_HOLD = "zxing.frame.hold"
  const val FRAME_DECODE = "zxing.frame.decode"
  const val FRAME_RELEASE = "zxing.frame.release"
  private val cookies = AtomicInteger()

  private fun nextCookie(): Int {
    val cookie = cookies.incrementAndGet()
    return if (cookie == 0) cookies.incrementAndGet() else cookie
  }
  fun beginFrameRequest(): Int {
    if (!supported || !Trace.isEnabled()) return 0
    val cookie = nextCookie()
    begin(FRAME_REQUEST, cookie)
    return cookie
  }
  fun endFrameRequest(cookie: Int) { if (cookie != 0) end(FRAME_REQUEST, cookie) }
  fun section(name: String): Boolean {
    if (!supported || !Trace.isEnabled()) return false
    Trace.beginSection(name)
    return true
  }
  fun endSection(started: Boolean) { if (started) Trace.endSection() }
  fun received(frame: ScanFrame): ScanFrame {
    if (!supported || !Trace.isEnabled()) return frame
    val cookie = nextCookie()
    return TracedFrame(frame, cookie)
  }
  fun decoding(frame: ScanFrame) { (frame as? TracedFrame)?.started() }

  private class TracedFrame(private val frame: ScanFrame, private val cookie: Int) : ScanFrame {
    private val queued = AtomicBoolean(true)
    private val closed = AtomicBoolean()
    init { begin(FRAME_QUEUE, cookie); begin(FRAME_HOLD, cookie) }
    override val source get() = frame.source
    override val data get() = frame.data
    fun started() { if (queued.compareAndSet(true, false)) end(FRAME_QUEUE, cookie) }
    override fun close() {
      if (!closed.compareAndSet(false, true)) return
      started()
      val releaseSpan = section(FRAME_RELEASE)
      try { frame.close() } finally { endSection(releaseSpan); end(FRAME_HOLD, cookie) }
    }
  }

}
