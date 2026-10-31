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
internal class JavaFrameDecoder(
    private val fixedHints: Map<DecodeHintType, @JvmSuppressWildcards Any?>,
    formats: Collection<@JvmSuppressWildcards BarcodeFormat>?
) : FrameDecoder {

  private val reader = MultiFormatReader()

  init {
    setFormats(formats)
  }

  override fun decode(source: LuminanceSource): Result? {
    return try {
      reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
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
    // Nothing held that the garbage collector will not take.
  }

  override fun name(): String = NAME

  companion object {
    const val NAME: String = "zxing-java"
  }
}
