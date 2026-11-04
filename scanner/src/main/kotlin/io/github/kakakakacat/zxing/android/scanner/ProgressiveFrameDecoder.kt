/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.scanner

import io.github.kakakakacat.zxing.*

/** Coordinates remain relative to the original configured scan region, never the screen. */
internal class ProgressiveRegion {
  private var attempt = 0
  private var width = 0
  private var height = 0
  private var left = 0
  private var top = 0

  fun reset() { attempt = 0; left = 0; top = 0 }

  fun select(source: LuminanceSource): LuminanceSource {
    if (width != source.width || height != source.height) {
      reset(); width = source.width; height = source.height
    }
    val phase = attempt
    attempt = (attempt + 1) % 5
    val numerator = when (phase) { 0, 1 -> 2; 2, 3 -> 3; else -> 4 }
    if (!source.isCropSupported || numerator == 4) { left = 0; top = 0; return source }
    val cropWidth = maxOf(1, (width.toLong() * numerator / 4).toInt())
    val cropHeight = maxOf(1, (height.toLong() * numerator / 4).toInt())
    left = (width - cropWidth) / 2
    top = (height - cropHeight) / 2
    return source.crop(left, top, cropWidth, cropHeight)
  }

  fun toSource(point: ResultPoint): ResultPoint = ResultPoint(point.x + left, point.y + top)

  fun toSource(result: Result): Result {
    if (left == 0 && top == 0) return result
    val points = result.resultPoints?.map { point -> point?.let { toSource(it) } }?.toTypedArray()
    val mapped = Result(result.text, result.rawBytes, result.numBits, points,
        result.barcodeFormat, result.timestamp)
    mapped.putAllMetadata(result.resultMetadata)
    return mapped
  }
}

/** At most one decode per frame; every fifth miss attempts the complete host scan region. */
internal class ProgressiveFrameDecoder(
    private val delegate: FrameDecoder,
    private val region: ProgressiveRegion = ProgressiveRegion()
) : FrameDecoder {
  override fun decode(source: LuminanceSource): Result? {
    val result = delegate.decode(region.select(source)) ?: return null
    val mapped = region.toSource(result)
    region.reset()
    return mapped
  }
  override fun setFormats(formats: Collection<BarcodeFormat>?) {
    delegate.setFormats(formats)
    region.reset()
  }
  override fun close() { delegate.close(); region.reset() }
  override fun name() = "progressive:${delegate.name()}"
}
