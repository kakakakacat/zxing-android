/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.kakakakacat.zxing.android.scanner

import io.github.kakakakacat.zxing.BarcodeFormat
import io.github.kakakakacat.zxing.DecodeHintType
import io.github.kakakakacat.zxing.LuminanceSource
import io.github.kakakakacat.zxing.Result

/** Opt-in routing, never two full decodes per frame. No measured speed claim. */
internal class FormatAwareDecoder(
    private val hints: Map<DecodeHintType, *>,
    formats: Collection<BarcodeFormat>?,
    private val javaFactory: (Collection<BarcodeFormat>?) -> FrameDecoder,
    private val autoFactory: (Collection<BarcodeFormat>?) -> FrameDecoder
) : FrameDecoder {
  private var delegate = create(formats)

  private fun create(formats: Collection<BarcodeFormat>?): FrameDecoder =
      if (prefersJava(hints, formats)) javaFactory(formats) else autoFactory(formats)

  override fun decode(source: LuminanceSource): Result? = delegate.decode(source)
  override fun close() = delegate.close()
  override fun name(): String = "format-aware:${delegate.name()}"
  override fun setFormats(formats: Collection<BarcodeFormat>?) {
    // A failed replacement leaves the old decoder owned and closeable by the caller.
    val replacement = create(formats)
    val old = delegate
    delegate = replacement
    old.close()
  }

  companion object {
    private val ONE_D = setOf(BarcodeFormat.CODABAR, BarcodeFormat.CODE_39,
        BarcodeFormat.CODE_93, BarcodeFormat.CODE_128, BarcodeFormat.EAN_8,
        BarcodeFormat.EAN_13, BarcodeFormat.ITF, BarcodeFormat.RSS_14,
        BarcodeFormat.RSS_EXPANDED, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
        BarcodeFormat.UPC_EAN_EXTENSION)

    @JvmStatic
    fun prefersJava(hints: Map<DecodeHintType, *>, formats: Collection<BarcodeFormat>?): Boolean {
      val javaOnlyHints = hints.keys.any {
        it != DecodeHintType.TRY_HARDER && it != DecodeHintType.POSSIBLE_FORMATS &&
            it != DecodeHintType.NEED_RESULT_POINT_CALLBACK
      }
      return javaOnlyHints || (!formats.isNullOrEmpty() && formats.all { it in ONE_D })
    }
  }
}
