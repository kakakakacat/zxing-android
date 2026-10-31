/*
 * Copyright 2009 ZXing authors
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

package io.github.kakakakacat.zxing.android.integration

/**
 * Encapsulates the result of a barcode scan invoked through [IntentIntegrator].
 *
 * Every property is nullable: a cancelled scan produces an instance with all of them
 * null, which is the contract Java callers have always seen.
 *
 * @author Sean Owen
 */
class IntentResult private constructor(
    /** Raw content of the barcode. */
    val contents: String?,
    /**
     * Name of the format, like "QR_CODE" or "UPC_A". See `BarcodeFormat` for the rest of
     * the names.
     */
    val formatName: String?,
    /** Raw bytes of the barcode content, if applicable, or null otherwise. */
    val rawBytes: ByteArray?,
    /** Rotation of the image, in degrees, which resulted in a successful scan. May be null. */
    val orientation: Int?,
    /** Name of the error correction level used in the barcode, if applicable. */
    val errorCorrectionLevel: String?
) {

  private constructor() : this(null, null, null, null, null)

  companion object {
    /**
     * The constructors are private rather than `internal` on purpose. In Java they were
     * package-private, and Kotlin compiles an `internal` constructor to a *public* one — which
     * would hand every consumer a way to build a scan result that did not come from a scan.
     * The two factories are `internal` members of a public companion, which is the shape that
     * actually mangles: an `internal` companion *object* is public in bytecode and its methods
     * keep their plain names, so Java would reach the constructor through it anyway.
     */
    internal fun of(
        contents: String?,
        formatName: String?,
        rawBytes: ByteArray?,
        orientation: Int?,
        errorCorrectionLevel: String?
    ): IntentResult = IntentResult(contents, formatName, rawBytes, orientation, errorCorrectionLevel)

    /** The all-null result a cancelled scan produces. */
    internal fun cancelled(): IntentResult = IntentResult()
  }

  override fun toString(): String {
    val rawBytesLength = rawBytes?.size ?: 0
    return "Format: " + formatName + '\n' +
        "Contents: " + contents + '\n' +
        "Raw bytes: (" + rawBytesLength + " bytes)\n" +
        "Orientation: " + orientation + '\n' +
        "EC level: " + errorCorrectionLevel + '\n'
  }
}
