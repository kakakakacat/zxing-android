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

  /**
   * The pair that an exact comparison rejected on a real device. CameraX negotiated these
   * two from one 16:9 request on DN-ENG41; refusing them failed the bind and the scanner
   * reported CAMERA_UNAVAILABLE on a device whose camera was perfectly available.
   */
  @Test fun acceptsTheVendorRoundingRealCamerasNegotiate() {
    assertTrue("864x480 against 640x360 is 1.25% apart",
               StreamGeometry.sameAspect(864, 480, 640, 360))
    assertTrue(StreamGeometry.sameAspect(640, 360, 864, 480))
    assertTrue("rotation is not a field-of-view difference",
               StreamGeometry.sameAspect(480, 864, 640, 360))
  }

  /** The tolerance may not grow far enough to admit a different bucket. */
  @Test fun stillRefusesARealFieldOfViewDifference() {
    assertFalse("4:3 against 16:9 is 33% apart",
                StreamGeometry.sameAspect(1280, 960, 1280, 720))
    assertFalse("5:3 against 16:9 is 6.7% apart",
                StreamGeometry.sameAspect(800, 480, 640, 360))
    assertFalse(StreamGeometry.sameAspect(1024, 738, 1280, 720))
  }
}
