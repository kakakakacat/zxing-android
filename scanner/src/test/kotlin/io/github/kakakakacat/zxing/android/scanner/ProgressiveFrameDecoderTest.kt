/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.scanner

import io.github.kakakakacat.zxing.*
import org.junit.Assert.*
import org.junit.Test

class ProgressiveFrameDecoderTest {
  private fun blank(width: Int = 100, height: Int = 80) =
      PlanarYUVLuminanceSource(ByteArray(width * height) { 0xff.toByte() },
          width, height, 0, 0, width, height, false)

  @Test fun expandsWithinTheHostRegionAndNeverRunsTwoDecodesOnAFrame() {
    val sizes = arrayListOf<Pair<Int, Int>>()
    val fake = object : FrameDecoder {
      override fun decode(source: LuminanceSource): Result? {
        sizes.add(source.width to source.height); return null
      }
      override fun close() {}
      override fun name() = "fake"
      override fun setFormats(formats: Collection<BarcodeFormat>?) {}
    }
    val decoder = ProgressiveFrameDecoder(fake)
    repeat(6) { decoder.decode(blank()) }
    assertEquals(listOf(50 to 40, 50 to 40, 75 to 60, 75 to 60, 100 to 80, 50 to 40), sizes)
    decoder.setFormats(listOf(BarcodeFormat.QR_CODE))
    decoder.decode(blank())
    assertEquals(50 to 40, sizes.last())
    decoder.decode(blank(200, 160))
    assertEquals(100 to 80, sizes.last())
  }

  @Test fun pointsMetadataBitsAndTimestampStayInTheOriginalRegion() {
    val region = ProgressiveRegion()
    region.select(blank()) // offsets 25,20
    assertEquals(ResultPoint(27f, 23f), region.toSource(ResultPoint(2f, 3f)))
    val raw = byteArrayOf(42)
    val result = Result("sample", raw, 7, arrayOf(ResultPoint(2f, 3f)), BarcodeFormat.QR_CODE, 123L)
    result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL, "M")
    val mapped = region.toSource(result)
    assertEquals(ResultPoint(27f, 23f), mapped.resultPoints[0])
    assertEquals(ResultPoint(2f, 3f), result.resultPoints[0])
    assertArrayEquals(raw, mapped.rawBytes)
    assertEquals(7, mapped.numBits)
    assertEquals(123L, mapped.timestamp)
    assertEquals(result.resultMetadata, mapped.resultMetadata)
  }

  @Test fun aRealQrAtTheEdgeIsReadOnTheFullRegionFallback() {
    val width = 400
    val bytes = ByteArray(width * width) { 0xff.toByte() }
    val symbol = MultiFormatWriter().encode("edge-ROI", BarcodeFormat.QR_CODE, 96, 96)
    for (y in 0 until symbol.height) for (x in 0 until symbol.width) {
      bytes[(y + 8) * width + x + 8] = if (symbol[x,y]) 0 else 0xff.toByte()
    }
    val source = PlanarYUVLuminanceSource(bytes, width, width, 0, 0, width, width, false)
    val formats = listOf(BarcodeFormat.QR_CODE)
    val expected = JavaFrameDecoder(emptyMap(), formats).decode(source)!!
    val decoder = ProgressiveFrameDecoder(JavaFrameDecoder(emptyMap(), formats))
    repeat(4) { assertNull(decoder.decode(source)) }
    val actual = decoder.decode(source)!!
    assertEquals(expected.text, actual.text)
    assertArrayEquals(expected.resultPoints, actual.resultPoints)
    decoder.close()
  }

  @Test fun nonCroppableSourcesUseTheCompleteInput() {
    val original = object : LuminanceSource(100, 80) {
      override fun getRow(y: Int, row: ByteArray?) = ByteArray(100)
      override fun getMatrix() = ByteArray(8000)
    }
    val region = ProgressiveRegion()
    repeat(10) { assertSame(original, region.select(original)) }
  }
}
