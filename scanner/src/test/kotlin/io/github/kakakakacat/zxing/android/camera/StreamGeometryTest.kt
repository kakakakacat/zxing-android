/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.camera
import org.junit.Assert.*
import org.junit.Test
class StreamGeometryTest {
  @Test fun acceptsScaledAndRotatedSizesButRefusesDifferentFieldsOfView() {
    assertTrue(StreamGeometry.sameAspect(1920,1080,640,360))
    assertTrue(StreamGeometry.sameAspect(1080,1920,640,360))
    assertFalse(StreamGeometry.sameAspect(1920,1080,640,480))
    assertFalse(StreamGeometry.sameAspect(0,1080,640,360))
    assertTrue(StreamGeometry.sameAspect(Int.MAX_VALUE,Int.MAX_VALUE,1,1))
  }
}
