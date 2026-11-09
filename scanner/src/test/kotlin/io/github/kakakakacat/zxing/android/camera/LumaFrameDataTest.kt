/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.camera

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class LumaFrameDataTest {
  @Test fun camera1FrameRetainsMetadataAndReleasesItsLeaseOnce() {
    val data = LumaFrameData.array(ByteArray(4), 2, 2, 2, 1, 0, 0, 2, 2, 0, 0L)
    var releases = 0
    val frame = Camera1ScanFrame(null, data) { releases++ }
    assertSame(data, frame.data)
    frame.close()
    frame.close()
    assertEquals(1, releases)
  }

  @Test fun legacyCamera1ConstructorKeepsTrailingLambdaRelease() {
    var releases = 0
    val frame = Camera1ScanFrame(null) { releases++ }
    assertNull(frame.data)
    frame.close()
    frame.close()
    assertEquals(1, releases)
    Camera1ScanFrame(null).close()
  }

  @Test fun directSliceStartsAtCropAndEndsAtLastRequiredByte() {
    val plane = ByteBuffer.allocateDirect(12 * 6)
    repeat(plane.capacity()) { plane.put(it, it.toByte()) }
    plane.position(7)
    val data = LumaFrameData.direct(plane, 10, 6, 12, 1,
        2, 1, 5, 3, 90, 123L)
    val roi = data.directRoiBuffer()!!
    assertTrue(roi.isReadOnly)
    assertEquals(0, roi.position())
    assertEquals(29, roi.limit())
    assertEquals(14.toByte(), roi.get(0))
    assertEquals(42.toByte(), roi.get(roi.limit() - 1))
    assertEquals(7, plane.position())
  }

  @Test fun interleavedPixelsAndShortFinalRowAreValid() {
    val plane = ByteBuffer.allocateDirect(27)
    val data = LumaFrameData.direct(plane, 4, 3, 10, 2,
        1, 1, 3, 2, 0, 0L)
    assertEquals(15, data.directRoiBuffer()!!.remaining())
  }

  @Test fun heapBufferUsesArrayFallback() {
    val data = LumaFrameData.direct(ByteBuffer.allocate(32), 8, 4, 8, 1,
        0, 0, 8, 4, 0, 0L)
    assertNull(data.directRoiBuffer())
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsCropPastStorage() {
    LumaFrameData.array(ByteArray(16), 8, 4, 8, 1, 0, 0, 8, 4, 0, 0L)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsCropCrossingRowStride() {
    LumaFrameData.array(ByteArray(32), 8, 4, 6, 1, 4, 0, 4, 4, 0, 0L)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsUnsupportedRotation() {
    LumaFrameData.array(ByteArray(32), 8, 4, 8, 1, 0, 0, 8, 4, 45, 0L)
  }
}
