/*
 * Copyright (C) 2010 ZXing authors
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

package io.github.kakakakacat.zxing.android

import android.content.Intent
import android.net.Uri
import io.github.kakakakacat.zxing.BarcodeFormat
import java.util.EnumSet
import java.util.regex.Pattern

/**
 * The format selection carried by a SCAN Intent or a scan URI.
 *
 * Both entry points are called from `:app`'s `CaptureActivity`, which is Java and sits in this
 * same package in a different module. That is why the members here are plain `public` rather
 * than `internal`: Kotlin mangles an `internal` function's name and Java cannot call it, so
 * `internal` would break the only caller while protecting nothing -- the class is public in
 * bytecode either way. See the Kotlin note in docs/maint/HANDOFF.md.
 */
internal object DecodeFormatManager {

  private val COMMA_PATTERN: Pattern = Pattern.compile(",")

  @JvmField
  val PRODUCT_FORMATS: Set<BarcodeFormat> = EnumSet.of(
      BarcodeFormat.UPC_A,
      BarcodeFormat.UPC_E,
      BarcodeFormat.EAN_13,
      BarcodeFormat.EAN_8,
      BarcodeFormat.RSS_14,
      BarcodeFormat.RSS_EXPANDED)

  @JvmField
  val INDUSTRIAL_FORMATS: Set<BarcodeFormat> = EnumSet.of(
      BarcodeFormat.CODE_39,
      BarcodeFormat.CODE_93,
      BarcodeFormat.CODE_128,
      BarcodeFormat.ITF,
      BarcodeFormat.CODABAR)

  private val ONE_D_FORMATS: Set<BarcodeFormat> =
      EnumSet.copyOf(PRODUCT_FORMATS).apply { addAll(INDUSTRIAL_FORMATS) }

  @JvmField
  val QR_CODE_FORMATS: Set<BarcodeFormat> = EnumSet.of(BarcodeFormat.QR_CODE)

  @JvmField
  val DATA_MATRIX_FORMATS: Set<BarcodeFormat> = EnumSet.of(BarcodeFormat.DATA_MATRIX)

  @JvmField
  val AZTEC_FORMATS: Set<BarcodeFormat> = EnumSet.of(BarcodeFormat.AZTEC)

  @JvmField
  val PDF417_FORMATS: Set<BarcodeFormat> = EnumSet.of(BarcodeFormat.PDF_417)

  private val FORMATS_FOR_MODE: Map<String, Set<BarcodeFormat>> = HashMap<String, Set<BarcodeFormat>>().apply {
    put(Intents.Scan.ONE_D_MODE, ONE_D_FORMATS)
    put(Intents.Scan.PRODUCT_MODE, PRODUCT_FORMATS)
    put(Intents.Scan.QR_CODE_MODE, QR_CODE_FORMATS)
    put(Intents.Scan.DATA_MATRIX_MODE, DATA_MATRIX_FORMATS)
    put(Intents.Scan.AZTEC_MODE, AZTEC_FORMATS)
    put(Intents.Scan.PDF417_MODE, PDF417_FORMATS)
  }

  @JvmStatic
  fun parseDecodeFormats(intent: Intent): Set<@JvmSuppressWildcards BarcodeFormat>? {
    var scanFormats: Iterable<String>? = null
    val scanFormatsString = intent.getStringExtra(Intents.Scan.FORMATS)
    if (scanFormatsString != null) {
      scanFormats = COMMA_PATTERN.split(scanFormatsString).asList()
    }
    return parseDecodeFormats(scanFormats, intent.getStringExtra(Intents.Scan.MODE))
  }

  @JvmStatic
  fun parseDecodeFormats(inputUri: Uri): Set<@JvmSuppressWildcards BarcodeFormat>? {
    var formats: List<String>? = inputUri.getQueryParameters(Intents.Scan.FORMATS)
    if (formats != null && formats.size == 1 && formats[0] != null) {
      formats = COMMA_PATTERN.split(formats[0]).asList()
    }
    return parseDecodeFormats(formats, inputUri.getQueryParameter(Intents.Scan.MODE))
  }

  private fun parseDecodeFormats(
      scanFormats: Iterable<String>?,
      decodeMode: String?
  ): Set<BarcodeFormat>? {
    if (scanFormats != null) {
      val formats: MutableSet<BarcodeFormat> = EnumSet.noneOf(BarcodeFormat::class.java)
      try {
        for (format in scanFormats) {
          formats.add(BarcodeFormat.valueOf(format))
        }
        return formats
      } catch (iae: IllegalArgumentException) {
        // ignore it then
      }
    }
    if (decodeMode != null) {
      return FORMATS_FOR_MODE[decodeMode]
    }
    return null
  }
}
