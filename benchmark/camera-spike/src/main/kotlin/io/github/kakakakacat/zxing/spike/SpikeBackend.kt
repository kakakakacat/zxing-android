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
import android.view.View

/**
 * One camera backend, driven far enough to answer Phase 6's questions and no further.
 *
 * Each product flavor supplies exactly one implementation under the same name, so the two
 * APKs differ only in which camera stack is compiled in. That is what makes the size delta
 * between them attributable to the stack rather than to anything else this module does.
 *
 * The contract is deliberately thin: give me a preview view, start, and tell me when the
 * first frame arrived and when the first decode attempt finished. Nothing here is a scanner.
 */
interface SpikeBackend {

  /** Recorded next to every measurement. */
  val name: String

  /** The preview surface. Added to the activity's content view before [start]. */
  fun createView(activity: Activity): View

  /**
   * Opens the camera and begins delivering frames.
   *
   * @param events called on an arbitrary thread with one of [EVENT_OPEN_START],
   *     [EVENT_FIRST_FRAME] or [EVENT_FIRST_DECODE] and the `SystemClock.uptimeMillis()`
   *     at which it happened, plus a free-form detail string for the result line.
   */
  fun start(activity: Activity, events: (String, Long, String) -> Unit)

  /** Releases the camera. Safe to call when [start] was never called or already failed. */
  fun stop()

  companion object {
    /** The instant just before the backend asks the platform for a camera. */
    const val EVENT_OPEN_START = "open-start"

    /** The first frame delivered to *this process* — not the first frame drawn on screen. */
    const val EVENT_FIRST_FRAME = "first-frame"

    /** The first decode attempt over a real camera frame has returned, hit or miss. */
    const val EVENT_FIRST_DECODE = "first-decode"

    /** The backend could not start. The detail string says why. */
    const val EVENT_FAILED = "failed"
  }
}
