/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.camera

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/** Fixed capacity; a lease returns to its original pool once, never to a replacement session. */
internal class CallbackBufferPool(size: Int, count: Int = 2, private val available: () -> Unit = {}) {
  private val free = ArrayDeque<ByteArray>()
  private var closed = false
  init { require(size > 0 && count > 0); repeat(count) { free.add(ByteArray(size)) } }
  @Synchronized fun acquire(): Lease? {
    if (closed || free.isEmpty()) return null
    return Lease(free.removeFirst()) { bytes ->
      val notify = synchronized(this) {
        if (closed) false else { free.addLast(bytes); true }
      }
      if (notify) available()
    }
  }
  @Synchronized fun close() { closed = true; free.clear() }
  class Lease(val bytes: ByteArray, private val release: (ByteArray) -> Unit) {
    private val returned = AtomicBoolean()
    fun close() { if (returned.compareAndSet(false, true)) release(bytes) }
  }
  companion object {
    fun nv21Size(width: Int, height: Int): Int? {
      if (width <= 0 || height <= 0 || width % 2 != 0 || height % 2 != 0) return null
      val pixels = width.toLong() * height
      if (pixels > Int.MAX_VALUE.toLong() * 2 / 3) return null
      return (pixels * 3 / 2).toInt()
    }
  }
}
