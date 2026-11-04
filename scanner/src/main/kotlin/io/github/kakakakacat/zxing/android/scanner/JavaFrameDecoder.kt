/*
 * Copyright (C) 2026 ZXing Android maintenance fork contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.kakakakacat.zxing.android.scanner

import io.github.kakakakacat.zxing.BarcodeFormat
import io.github.kakakakacat.zxing.BinaryBitmap
import io.github.kakakakacat.zxing.DecodeHintType
import io.github.kakakakacat.zxing.LuminanceSource
import io.github.kakakakacat.zxing.MultiFormatReader
import io.github.kakakakacat.zxing.ReaderException
import io.github.kakakakacat.zxing.Result
import io.github.kakakakacat.zxing.common.HybridBinarizer
import io.github.kakakakacat.zxing.android.camera.YPlaneLuminanceSource

/**
 * This fork's own Java decoder behind the [FrameDecoder] seam.
 *
 * The fallback backend, and the baseline every other backend is measured against. Behaviour
 * is deliberately identical to what the decode thread did before the seam existed:
 * [HybridBinarizer], [MultiFormatReader.decodeWithState] to reuse the readers between frames,
 * and a `reset()` afterwards so state from one frame cannot leak into the next.
 */
// @JvmSuppressWildcards throughout, so the Java-facing signatures stay the invariant ones
// the Java classes had. Kotlin's Collection and Map are declared `out`, and without it a
// Java caller sees Collection<? extends BarcodeFormat> -- which is not what the seam's
// contract tests, the benchmark module or the instrumentation tests were written against.
internal class JavaFrameDecoder @JvmOverloads constructor(
    private val fixedHints: Map<DecodeHintType, @JvmSuppressWildcards Any?>,
    formats: Collection<@JvmSuppressWildcards BarcodeFormat>?,
    private val reuseBuffers: Boolean = false
) : FrameDecoder {

  private val reader = MultiFormatReader()
  private var matrixScratch = ByteArray(0)

  private fun input(source: LuminanceSource): LuminanceSource {
    if (!reuseBuffers || source !is YPlaneLuminanceSource) return source
    // Lazy: one-dimensional readers request rows and must not pay for a full matrix.
    return object : LuminanceSource(source.width, source.height) {
      private var filled = false
      override fun getRow(y: Int, row: ByteArray?): ByteArray = source.getRow(y, row)
      override fun getMatrix(): ByteArray {
        if (!filled) {
          val size = width.toLong() * height
          require(size <= Int.MAX_VALUE) { "ROI is too large" }
          // LuminanceSource allows a larger backing array. Grow only across ROI phases.
          if (matrixScratch.size < size.toInt()) matrixScratch = ByteArray(size.toInt())
          source.copyMatrixTo(matrixScratch)
          filled = true
        }
        return matrixScratch
      }
      override fun isCropSupported(): Boolean = source.isCropSupported
      override fun crop(left: Int, top: Int, width: Int, height: Int): LuminanceSource =
          source.crop(left, top, width, height)
    }
  }

  init {
    setFormats(formats)
  }

  override fun decode(source: LuminanceSource): Result? {
    return try {
      reader.decodeWithState(BinaryBitmap(HybridBinarizer(input(source))))
    } catch (re: ReaderException) {
      // Nothing in this frame. The ordinary case on live preview.
      null
    } finally {
      reader.reset()
    }
  }

  override fun setFormats(formats: Collection<@JvmSuppressWildcards BarcodeFormat>?) {
    // setHints rebuilds the reader list, which also discards the state decodeWithState
    // carries between frames. That is correct here: the state belongs to readers the
    // caller has just asked to stop using.
    reader.setHints(DecoderThread.hintsFor(fixedHints, formats))
  }

  override fun close() {
    // Do not retain the largest camera ROI after this decoder is closed.
    matrixScratch = ByteArray(0)
  }

  override fun name(): String = if (reuseBuffers) "$NAME-reuse" else NAME

  companion object {
    const val NAME: String = "zxing-java"
  }
}
