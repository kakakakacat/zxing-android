/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.scanner

import io.github.kakakakacat.zxing.*
import io.github.kakakakacat.zxing.android.camera.YPlaneLuminanceSource
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class JavaBufferReuseTest {
  @Test fun alternatingHitsMissesSizesAndStridesDoNotLeakPixelsAcrossFrames() {
    val formats = listOf(BarcodeFormat.QR_CODE)
    val baseline = JavaFrameDecoder(emptyMap(), formats)
    val reused = JavaFrameDecoder(emptyMap(), formats, true)
    try {
      for (size in listOf(240, 240, 320, 240)) for (pixelStride in listOf(1, 2)) {
        val rowStride = (size + 8) * pixelStride
        val plane = ByteBuffer.allocateDirect(rowStride * (size + 4))
        val matrix = MultiFormatWriter().encode("reuse-$size", BarcodeFormat.QR_CODE, size, size)
        val source = YPlaneLuminanceSource(plane, rowStride, pixelStride, 3, 2, size, size)
        for (hit in listOf(true, false, true)) {
          for (i in 0 until plane.capacity()) plane.put(i, 0xff.toByte())
          if (hit) for (y in 0 until size) for (x in 0 until size) {
            plane.put((y + 2) * rowStride + (x + 3) * pixelStride,
                if (matrix[x,y]) 0.toByte() else 0xff.toByte())
          }
          val expected = baseline.decode(source)
          val actual = reused.decode(source)
          assertEquals(expected?.text, actual?.text)
          if (hit) {
            assertNotNull(actual)
            assertArrayEquals(expected!!.rawBytes, actual!!.rawBytes)
            assertArrayEquals(expected.resultPoints, actual.resultPoints)
          } else assertNull(actual)
          assertEquals(0, plane.position())
        }
        val first = source.matrix
        val second = source.matrix
        assertNotSame(first, second)
        assertArrayEquals(first, second)
      }
    } finally { baseline.close(); reused.close() }
  }
}
