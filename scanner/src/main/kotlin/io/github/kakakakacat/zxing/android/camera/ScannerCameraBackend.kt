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

import android.os.Handler
import android.view.SurfaceHolder
import io.github.kakakakacat.zxing.android.scanner.ScannerError

/**
 * A camera stack, as the scanner uses one.
 *
 * Two implement it — [Camera1Backend] over the `android.hardware.Camera` code this fork
 * inherited, and [CameraXBackend] over CameraX, which Phase 6 adopted. Which one runs is
 * [io.github.kakakakacat.zxing.android.scanner.ScannerOptions.cameraBackend], so a device
 * CameraX handles badly has somewhere to fall back to; the reasoning is in
 * `docs/maint/decisions/0001-camera-backend.md`.
 *
 * **Opening is asynchronous** on both, and the result arrives on [Callback], on the main
 * thread. Camera1 used to open synchronously and answer through the callback anyway, so
 * that one caller could drive both; it now really is asynchronous, because the synchronous
 * open was measured blocking the main thread for 401–436 ms at startup. See
 * [Camera1Backend] for what that cost and what it took to move.
 *
 * Frames are pulled, not pushed: nothing is delivered until [requestFrame] asks, and one
 * request yields at most one frame. That is what keeps a single decode thread from
 * accumulating a queue of stale frames behind it, and on CameraX it is also what keeps
 * `ImageProxy` slots from being held by frames nobody is going to decode.
 *
 * Lifecycle methods are called from the main thread. requestFrame is also safe on the decode thread.
 */
internal interface ScannerCameraBackend : ScannerCamera {

  /**
   * Opens the camera against [holder] and starts the preview.
   *
   * The surface must already exist. Exactly one of [Callback.onCameraReady] or
   * [Callback.onCameraError] follows, on the main thread, after this returns — unless
   * [close] is called first, in which case neither does and the camera is released.
   */
  fun open(holder: SurfaceHolder, callback: Callback)

  /**
   * Cancels any outstanding [requestFrame] and stops frames arriving. The camera stays
   * open, so scanning can be resumed without paying for another open.
   */
  fun stopFrames()

  /** Releases the camera. Safe to call when it was never opened. */
  fun close()

  /**
   * The size, in pixels, of the view the preview is drawn into.
   *
   * **Not the display's size.** The scan rectangle and the mapping between it and the
   * camera frame were both computed from `WindowManager`'s display size, which is right
   * only for a scanner that fills the screen. Embedded in a host — half a screen, a
   * dialog, a split-screen window, a `ScannerView` with an offset — the rectangle the user
   * aims with and the region actually decoded were two different rectangles.
   *
   * Called by [io.github.kakakakacat.zxing.android.scanner.ScannerView] whenever its size
   * changes, including before the camera opens. A width or height of 0 means "not laid out
   * yet", and the backend falls back to the display size, which is what it used to do
   * always.
   */
  fun setViewportSize(width: Int, height: Int)

  /**
   * Asks for one frame, delivered to [handler] as a message whose `what` is [what] and
   * whose `obj` is a [ScanFrame].
   *
   * Ownership of the frame passes to the handler, which must [ScanFrame.close] it. If the
   * handler's looper has already quit the frame is closed here instead, because on CameraX
   * a frame nobody closes is a buffer slot the camera never gets back.
   */
  fun requestFrame(handler: Handler, what: Int)

  /** Main-thread callbacks for [open]. */
  interface Callback {

    /** The camera is open, the preview is running and frames can be requested. */
    fun onCameraReady()

    /** The camera could not be opened, or went away after it was. */
    fun onCameraError(error: ScannerError, cause: Exception?)
  }
}
