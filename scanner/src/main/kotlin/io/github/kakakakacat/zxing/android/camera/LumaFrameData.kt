/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.camera

import java.nio.ByteBuffer

/**
 * Backend-neutral luma storage and geometry. Coordinates start at storage index zero.
 * The enclosing [ScanFrame] owns storage; all views expire when that frame is closed.
 */
internal class LumaFrameData private constructor(
    val buffer: ByteBuffer?,
    val bytes: ByteArray?,
    val imageWidth: Int,
    val imageHeight: Int,
    val rowStride: Int,
    val pixelStride: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropWidth: Int,
    val cropHeight: Int,
    val rotationDegrees: Int,
    /** Process-local monotonic receipt time, comparable across both camera backends. */
    val receivedAtNanos: Long
) {
  init {
    require((buffer == null) != (bytes == null)) { "exactly one luma storage kind is required" }
    require(imageWidth > 0 && imageHeight > 0) { "image dimensions must be positive" }
    require(rowStride > 0 && pixelStride > 0) { "strides must be positive" }
    require(cropLeft >= 0 && cropTop >= 0 && cropWidth > 0 && cropHeight > 0) {
      "crop must be non-empty and inside the image origin"
    }
    require(cropLeft.toLong() + cropWidth <= imageWidth &&
        cropTop.toLong() + cropHeight <= imageHeight) { "crop runs outside the image" }
    require(rotationDegrees == 0 || rotationDegrees == 90 ||
        rotationDegrees == 180 || rotationDegrees == 270) { "rotation must be a right angle" }
    val rowEnd = (cropLeft.toLong() + cropWidth - 1) * pixelStride + 1
    require(rowEnd <= rowStride) { "crop row does not fit rowStride" }
    val last = (cropTop.toLong() + cropHeight - 1) * rowStride + rowEnd - 1
    val limit = buffer?.limit()?.toLong() ?: bytes!!.size.toLong()
    require(last >= 0 && last < limit) { "crop runs past luma storage" }
  }

  /** Read-only ROI slice bounded to the last byte that the decoder may read. */
  fun directRoiBuffer(): ByteBuffer? {
    val source = buffer ?: return null
    if (!source.isDirect) return null
    val start = cropTop.toLong() * rowStride + cropLeft.toLong() * pixelStride
    val required = (cropHeight.toLong() - 1) * rowStride +
        (cropWidth.toLong() - 1) * pixelStride + 1
    if (start > Int.MAX_VALUE || start + required > Int.MAX_VALUE) return null
    val cursor = source.asReadOnlyBuffer()
    cursor.position(start.toInt())
    cursor.limit((start + required).toInt())
    return cursor.slice().asReadOnlyBuffer()
  }

  companion object {
    fun direct(buffer: ByteBuffer, imageWidth: Int, imageHeight: Int,
        rowStride: Int, pixelStride: Int, cropLeft: Int, cropTop: Int,
        cropWidth: Int, cropHeight: Int, rotationDegrees: Int,
        receivedAtNanos: Long): LumaFrameData = LumaFrameData(
        buffer.asReadOnlyBuffer(), null, imageWidth, imageHeight, rowStride, pixelStride,
        cropLeft, cropTop, cropWidth, cropHeight, rotationDegrees, receivedAtNanos)

    fun array(bytes: ByteArray, imageWidth: Int, imageHeight: Int,
        rowStride: Int, pixelStride: Int, cropLeft: Int, cropTop: Int,
        cropWidth: Int, cropHeight: Int, rotationDegrees: Int,
        receivedAtNanos: Long): LumaFrameData = LumaFrameData(
        null, bytes, imageWidth, imageHeight, rowStride, pixelStride,
        cropLeft, cropTop, cropWidth, cropHeight, rotationDegrees, receivedAtNanos)
  }
}
