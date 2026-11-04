/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.camera

import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicBoolean

/** Serialize close-before-open across backend instances; quit after the last cleanup, not on UI pause. */
internal object Camera1Worker {
  private var thread: HandlerThread? = null
  private var handler: Handler? = null
  private var leases = 0
  @Synchronized fun acquire(): Lease {
    val target = handler ?: HandlerThread("zxing-camera1").let {
      it.start(); thread = it; Handler(it.looper).also { created -> handler = created }
    }
    leases++
    return Lease(target) { release(target) }
  }
  @Synchronized private fun release(target: Handler) {
    check(handler === target && leases > 0)
    leases--
    if (leases == 0) { thread?.quitSafely(); thread = null; handler = null }
  }
  class Lease(val handler: Handler, private val release: () -> Unit) {
    private val closed = AtomicBoolean()
    fun close() { if (closed.compareAndSet(false, true)) release() }
  }
}
