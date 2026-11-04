/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.camera

import android.hardware.Camera
import android.os.Handler
import android.os.Looper
import io.github.kakakakacat.zxing.LuminanceSource
import java.util.concurrent.atomic.AtomicReference

/** Pull-only callback buffering: free buffers stay in the pool until a frame is requested. */
@Suppress("DEPRECATION")
internal class BufferedPreviewCallback(
    private val camera: Camera,
    private val owner: Handler,
    size: Int,
    private val buildSource: (ByteArray) -> LuminanceSource?,
    private val rejected: (BufferedPreviewCallback, RuntimeException) -> Unit
) : Camera.PreviewCallback {
  private val pending = AtomicReference<FrameRequest?>()
  @Volatile private var active = true
  private val pool = CallbackBufferPool(size, 2) { owner.post { queueNext() } }
  @Volatile private var queued: CallbackBufferPool.Lease? = null

  fun attach() { camera.setPreviewCallbackWithBuffer(this) }
  fun request(handler: Handler, what: Int) {
    if (!active) return
    pending.getAndSet(FrameRequest(handler, what))?.cancel()
    if (Looper.myLooper() === owner.looper) queueNext() else owner.post { queueNext() }
  }
  fun cancelRequest() { pending.getAndSet(null)?.cancel() }
  private fun queueNext() {
    if (!active || pending.get() == null || queued != null) return
    val lease = pool.acquire() ?: return
    queued = lease
    try { camera.addCallbackBuffer(lease.bytes) }
    catch (failure: RuntimeException) { fail(failure) }
  }
  override fun onPreviewFrame(data: ByteArray?, source: Camera?) {
    if (!active || source !== camera) return
    val lease = queued
    queued = null
    if (lease == null || data !== lease.bytes) {
      lease?.close()
      fail(IllegalStateException("Camera rejected the configured NV21 callback buffer"))
      return
    }
    val request = pending.getAndSet(null)
    if (request == null) { lease.close(); return }
    try { request.deliver(Camera1ScanFrame(buildSource(lease.bytes)) { lease.close() }) }
    catch (failure: RuntimeException) { lease.close(); fail(failure, request) }
  }
  private fun fail(failure: RuntimeException, taken: FrameRequest? = null) {
    if (!active) { taken?.cancel(); return }
    val request = taken ?: pending.getAndSet(null)
    close()
    rejected(this, failure)
    // Let the ordinary miss path request another frame, now through one-shot fallback.
    request?.deliver(Camera1ScanFrame(null))
  }
  fun close() {
    if (!active) return
    active = false
    cancelRequest()
    pool.close()
    queued?.close(); queued = null
    try { camera.setPreviewCallbackWithBuffer(null) }
    catch (failure: RuntimeException) {
      android.util.Log.w("BufferedPreview", "Could not detach callback while camera was closing", failure)
    }
  }
}
