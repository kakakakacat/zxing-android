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
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Launches one camera backend, times how long it takes to produce a frame this process can
 * decode, prints one line and stops.
 *
 * **This is a spike, not a layer of the benchmark suite.** It does not clear `BENCHMARKS.md`
 * §2: there is no repeat count here, no cool-down protocol and no result envelope. It exists
 * to answer one Phase 6 question — what does each camera stack cost to start, and how big is
 * it — with a number taken the same way for both stacks on the same device, rather than with
 * a quotation from somebody's blog. The repeats and the median live in
 * `tools/maint/camera-spike-run.sh`, which launches this activity N times and aggregates the
 * lines it prints.
 *
 * Three instants are reported, all from the same monotonic clock:
 *
 *  - **process start → first frame.** What a user waits through on a cold launch, minus the
 *    part Android does before this process exists.
 *  - **onCreate → first frame.** The same, measured from code this project actually controls.
 *  - **open start → first frame.** The camera stack alone, with the activity's own work and
 *    layout taken out. This is the number that compares two camera APIs rather than two apps.
 *
 * "First frame" means the first frame delivered to *this* process, which is what a decoder
 * waits for. It is not the first frame drawn on screen, and the two are not the same thing.
 */
class SpikeActivity : Activity() {

  private var backend: SpikeBackend? = null
  private var createdAt = 0L
  private var openStartedAt = 0L
  private var reported = false

  override fun onCreate(savedInstanceState: Bundle?) {
    createdAt = SystemClock.uptimeMillis()
    super.onCreate(savedInstanceState)

    val root = FrameLayout(this)
    setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT))

    val chosen = SpikeBackends.create()
    backend = chosen
    root.addView(chosen.createView(this),
                 FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                          ViewGroup.LayoutParams.MATCH_PARENT))
    chosen.start(this) { event, at, detail -> onBackendEvent(chosen, event, at, detail) }
  }

  private fun onBackendEvent(backend: SpikeBackend, event: String, at: Long, detail: String) {
    when (event) {
      SpikeBackend.EVENT_OPEN_START -> openStartedAt = at
      SpikeBackend.EVENT_FIRST_FRAME -> {
        if (reported) {
          return
        }
        reported = true
        Log.i(TAG, buildString {
          append(RESULT_PREFIX)
          append(" backend=").append(backend.name)
          append(" processToFirstFrameMs=").append(sinceProcessStart(at))
          append(" createToFirstFrameMs=").append(at - createdAt)
          append(" openToFirstFrameMs=")
              .append(if (openStartedAt == 0L) UNKNOWN else (at - openStartedAt).toString())
          append(' ').append(detail)
        })
      }
      SpikeBackend.EVENT_FIRST_DECODE -> Log.i(TAG, "$DECODE_PREFIX backend=${backend.name} $detail")
      SpikeBackend.EVENT_FAILED -> Log.e(TAG, "$FAILED_PREFIX backend=${backend.name} $detail")
    }
  }

  /**
   * Milliseconds from the moment the zygote forked this process. `getStartUptimeMillis` is
   * API 24; below that the figure is simply not reported rather than guessed at from
   * something that is not the same clock.
   */
  private fun sinceProcessStart(at: Long): String =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        (at - Process.getStartUptimeMillis()).toString()
      } else {
        UNKNOWN
      }

  override fun onDestroy() {
    backend?.stop()
    backend = null
    super.onDestroy()
  }

  companion object {
    const val TAG = "ZXingCameraSpike"

    /** The line `camera-spike-run.sh` greps for. Changing it changes that script too. */
    const val RESULT_PREFIX = "SPIKE-RESULT"
    const val DECODE_PREFIX = "SPIKE-DECODE"
    const val FAILED_PREFIX = "SPIKE-FAILED"

    private const val UNKNOWN = "unknown"
  }
}
