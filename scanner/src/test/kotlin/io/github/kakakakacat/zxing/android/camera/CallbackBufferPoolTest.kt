/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.camera
import org.junit.Assert.*
import org.junit.Test

class CallbackBufferPoolTest {
  @Test fun neverReusesHeldBytesAndDoubleCloseCannotIncreaseCapacity() {
    val pool = CallbackBufferPool(64)
    val first = pool.acquire()!!
    val second = pool.acquire()!!
    first.bytes[0] = 17
    assertNotSame(first.bytes, second.bytes)
    assertNull(pool.acquire())
    first.close(); first.close()
    val returned = pool.acquire()!!
    assertSame(first.bytes, returned.bytes)
    assertEquals(17, returned.bytes[0].toInt())
    assertNull(pool.acquire())
    returned.close(); second.close(); pool.close()
  }
  @Test fun lateReleaseCannotReviveAnOldPoolOrAffectANewPool() {
    var notifications = 0
    val old = CallbackBufferPool(8, 2) { notifications++ }
    val lease = old.acquire()!!
    old.close(); lease.close()
    assertNull(old.acquire())
    assertEquals(0, notifications)
    val fresh = CallbackBufferPool(8)
    val next = fresh.acquire()!!
    assertNotSame(lease.bytes, next.bytes)
    next.close(); fresh.close()
  }
  @Test fun acceptsOnlyKnownEvenSizedNv21LayoutsWithoutOverflow() {
    assertEquals(460800, CallbackBufferPool.nv21Size(640, 480))
    assertNull(CallbackBufferPool.nv21Size(641, 480))
    assertNull(CallbackBufferPool.nv21Size(0, 480))
    assertNull(CallbackBufferPool.nv21Size(Int.MAX_VALUE - 1, Int.MAX_VALUE - 1))
  }
}
