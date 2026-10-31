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

import android.util.Log
import io.github.kakakakacat.zxing.BarcodeFormat
import io.github.kakakakacat.zxing.DecodeHintType
import io.github.kakakakacat.zxing.LuminanceSource
import io.github.kakakakacat.zxing.Result
import io.github.kakakakacat.zxing.ResultPoint
import java.util.Collections
import java.util.EnumMap

/**
 * The zxing-cpp decoder behind the [FrameDecoder] seam.
 *
 * **The shipping default since `ebe1708b4`**, on the strength of the A/B in
 * `docs/maint/BENCHMARKS.md` §10.6c: 56–82% faster on 2D symbols and on all-format misses.
 * §9.1 is the other half and now agrees: on the 1168-sample corpus this backend reads 890
 * samples against the Java decoder's 862, with fewer wrong answers and no false positives.
 *
 * The native library is loaded once, lazily, and a failure to load is reported through
 * [isAvailable] rather than thrown: a device without the right ABI should fall back to the
 * Java decoder, not crash.
 *
 * ### The native declarations are deliberately shaped
 *
 * Each one is `@JvmStatic private external` in the companion. That combination, and only
 * that combination, emits `private static native` on *this* class, so the symbols stay
 * `Java_io_github_kakakakacat_zxing_android_scanner_NativeFrameDecoder_*` and
 * `zxing_native.cpp` needs no change. Dropping `@JvmStatic` puts the native method on
 * `NativeFrameDecoder$Companion` instead and every one of them fails to bind at run time.
 */
internal class NativeFrameDecoder
/**
 * @param compatibilityRendering whether to report a symbol the way this library always has
 *     rather than the way zxing-cpp spells it: UPC-A as 12 digits and [BarcodeFormat.UPC_A]
 *     rather than 13 and `EAN_13`, UPC-E compacted back to its eight printed digits, and
 *     Codabar without its start and stop characters. On by default: making the native
 *     decoder the default must not silently change what callers receive. Off is for
 *     measurement, where the raw behaviour of each decoder is the point.
 *
 *     A fourth difference — zxing-cpp escaping every non-graphical character — is handled on
 *     the native side with `TextMode::Plain`, because it is a property of the whole decode
 *     rather than of one symbology, and so is not switchable here. All four were found by
 *     running the corpus through both backends; BENCHMARKS.md §9.1 counts them.
 */
constructor(
    hints: Map<DecodeHintType, *>?,
    formats: Collection<@JvmSuppressWildcards BarcodeFormat>?,
    private val compatibilityRendering: Boolean
) : FrameDecoder {

  private var handle: Long = 0L

  constructor(formats: Collection<@JvmSuppressWildcards BarcodeFormat>?) :
      this(null, formats, true)

  constructor(
      formats: Collection<@JvmSuppressWildcards BarcodeFormat>?,
      compatibilityRendering: Boolean
  ) : this(null, formats, compatibilityRendering)

  init {
    // Not check(): the load failure is the whole diagnosis when a device cannot run the
    // native backend, and check() has nowhere to put a cause.
    if (!AVAILABLE) {
      throw IllegalStateException("libzxingnative is not loaded", LOAD_FAILURE)
    }
    handle = nativeCreate()
    check(handle != 0L) { "could not create the native decoder" }
    // Presence is what means true in this decoder -- MultiFormatReader reads it as
    // containsKey -- so a hint whose value is Boolean.FALSE still turns it on, here as
    // there. Matching the Java backend's reading of the hint matters more than being
    // right about a map nobody builds that way.
    nativeSetTryHarder(handle, hints != null && hints.containsKey(DecodeHintType.TRY_HARDER))
    setFormats(formats)
  }

  override fun decode(source: LuminanceSource): Result? {
    check(handle != 0L) { "decoder already closed" }
    // getMatrix() is the scan rectangle, not the frame. Handing the native side a
    // greyscale plane rather than a BinaryBitmap is the point of the seam: zxing-cpp
    // does its own thresholding.
    val decoded = nativeDecode(handle, source.matrix, source.width, source.height)
    if (decoded == null || decoded.size < 2) {
      return null
    }
    var format = formatFromNativeName(decoded[1])
    if (format == null) {
      // A format this fork has no equivalent for. Reporting it as a miss is the honest
      // answer: the caller asked for formats it can name, and inventing one would put a
      // value into Result.getBarcodeFormat() that no caller can switch on.
      //
      // Logged, once per name, because the silent version of this cost 90 corpus reads
      // and took a full L1 run to notice: a decoded symbol thrown away here is
      // indistinguishable from a frame that held no barcode.
      warnOnceAboutUnmappedFormat(decoded[1])
      return null
    }
    var text = decoded[0]
    if (compatibilityRendering) {
      val compatFormat = upcAFormatFor(format, text)
      if (compatFormat != null) {
        format = compatFormat
        text = text.substring(1)
      } else if (format == BarcodeFormat.UPC_E) {
        text = compactUpcE(text)
      } else if (format == BarcodeFormat.CODABAR) {
        text = withoutCodabarGuards(text)
      } else if (format == BarcodeFormat.RSS_14) {
        text = withoutGs1GtinPrefix(text)
      }
    }
    return Result(text, null, cornerPoints(decoded), format)
  }

  override fun setFormats(formats: Collection<@JvmSuppressWildcards BarcodeFormat>?) {
    check(handle != 0L) { "decoder already closed" }
    nativeSetFormats(handle, nativeFormatList(formats))
  }

  override fun close() {
    if (handle != 0L) {
      nativeDestroy(handle)
      handle = 0L
    }
  }

  override fun name(): String = NAME

  companion object {

    const val NAME: String = "zxing-cpp"

    private val AVAILABLE: Boolean
    private val LOAD_FAILURE: Throwable?

    init {
      var loaded: Boolean
      var failure: Throwable? = null
      try {
        System.loadLibrary("zxingnative")
        loaded = true
      } catch (e: UnsatisfiedLinkError) {
        loaded = false
        failure = e
      } catch (e: SecurityException) {
        loaded = false
        failure = e
      }
      AVAILABLE = loaded
      LOAD_FAILURE = failure
    }

    /**
     * The zxing-cpp names for this fork's formats.
     *
     * Mapped explicitly rather than by `name()`, because the two projects do not spell every
     * format the same way and a silently unmatched name would widen the format set instead of
     * narrowing it — turning a restriction into the opposite of what the caller asked for.
     *
     * [BarcodeFormat.UPC_EAN_EXTENSION] is deliberately absent: in zxing-cpp an EAN add-on is
     * a property of a UPC/EAN result, not a format that can be requested.
     */
    private val NATIVE_NAMES: MutableMap<BarcodeFormat, String> =
        EnumMap<BarcodeFormat, String>(BarcodeFormat::class.java).apply {
          put(BarcodeFormat.AZTEC, "Aztec")
          put(BarcodeFormat.CODABAR, "Codabar")
          put(BarcodeFormat.CODE_39, "Code39")
          put(BarcodeFormat.CODE_93, "Code93")
          put(BarcodeFormat.CODE_128, "Code128")
          put(BarcodeFormat.DATA_MATRIX, "DataMatrix")
          put(BarcodeFormat.EAN_8, "EAN-8")
          put(BarcodeFormat.EAN_13, "EAN-13")
          put(BarcodeFormat.ITF, "ITF")
          put(BarcodeFormat.MAXICODE, "MaxiCode")
          put(BarcodeFormat.PDF_417, "PDF417")
          put(BarcodeFormat.QR_CODE, "QRCode")
          put(BarcodeFormat.RSS_14, "DataBar")
          put(BarcodeFormat.RSS_EXPANDED, "DataBarExpanded")
          put(BarcodeFormat.UPC_A, "UPC-A")
          put(BarcodeFormat.UPC_E, "UPC-E")
        }

    /**
     * Reverse of [NATIVE_NAMES], keyed on the normalised spelling, plus the names that only
     * ever come *back*.
     *
     * **A zxing-cpp format name is not symmetric.** What you ask for can be a symbology —
     * `DataBar`, `PDF417` — and what comes back is always the specific variant the symbol
     * turned out to be: `"DataBar Stacked"`, `"DataBar Expanded Stacked"`,
     * `"Compact PDF417"`. Its own header says so: "only specific variants are returned by the
     * library when reading barcodes".
     *
     * Reversing the request table alone therefore misses every variant, and because an
     * unmapped name is reported as "nothing here", a symbol this decoder read perfectly was
     * scored as a miss. That is not theoretical: it cost **every one of the 30 RSS-14
     * samples** in the corpus, 59 of the 71 RSS-Expanded ones (all in
     * `rssexpandedstacked-*`) and part of the Code 39 loss — the whole "DataBar capability
     * gap" that §9.1 first reported as a difference between the two readers.
     *
     * Variants with no equivalent in this fork stay out deliberately: DataBar Limited,
     * MicroPDF417, Micro QR, rMQR, Aztec Rune, DX Film Edge, Telepen. A caller cannot switch
     * on a format this library does not define, so for those "nothing here" is the honest
     * answer rather than a lie about which symbology was read.
     */
    private val FORMATS_BY_NORMALISED_NAME: MutableMap<String, BarcodeFormat> = HashMap()

    init {
      for ((format, nativeName) in NATIVE_NAMES) {
        FORMATS_BY_NORMALISED_NAME[normalise(nativeName)] = format
      }
      // DataBar: the symbology is what gets requested, the variant is what comes back.
      // zxing-cpp's own blackbox suite reads rss14-1 as DataBarOmni and rss14-2 as
      // DataBarStk, so a reader that only knows "DataBar" sees neither.
      alias("DataBar Omni", BarcodeFormat.RSS_14)
      alias("DataBar Stacked", BarcodeFormat.RSS_14)
      alias("DataBar Stacked Omni", BarcodeFormat.RSS_14)
      alias("DataBar Expanded Stacked", BarcodeFormat.RSS_EXPANDED)
      // RSSExpandedReader reads the stacked form as RSS_EXPANDED too, so this is the same
      // format a caller already receives from the Java backend.
      alias("Compact PDF417", BarcodeFormat.PDF_417)
      // PDF417Reader decodes compact PDF417 and reports it as PDF_417.
      alias("Code 39 Standard", BarcodeFormat.CODE_39)
      alias("Code 39 Extended", BarcodeFormat.CODE_39)
      alias("ITF-14", BarcodeFormat.ITF)
      alias("ISBN", BarcodeFormat.EAN_13)
      alias("QR Code Model 1", BarcodeFormat.QR_CODE)
      alias("QR Code Model 2", BarcodeFormat.QR_CODE)
      alias("Aztec Code", BarcodeFormat.AZTEC)
    }

    private fun alias(nativeName: String, format: BarcodeFormat) {
      FORMATS_BY_NORMALISED_NAME[normalise(nativeName)] = format
    }

    /**
     * A format name with punctuation and case removed.
     *
     * zxing-cpp spells a format one way when parsing a request and another way when reporting
     * a result: `QRCode` is accepted, `"QR Code"` comes back. Matching the literal strings
     * looks right and silently turns every successful decode into a miss, which is how this
     * was found. Normalising both sides is spelling-independent, and no two formats collide
     * once normalised.
     */
    @JvmStatic
    fun normalise(name: String): String {
      val out = StringBuilder(name.length)
      for (c in name) {
        if (Character.isLetterOrDigit(c)) {
          out.append(Character.toLowerCase(c))
        }
      }
      return out.toString()
    }

    /** This fork's format for a zxing-cpp name, for turning a result back into a Result. */
    @JvmStatic
    fun formatFromNativeName(nativeName: String?): BarcodeFormat? {
      if (nativeName == null) {
        return null
      }
      return FORMATS_BY_NORMALISED_NAME[normalise(nativeName)]
    }

    /** Whether the native library loaded on this device. Checked before constructing one. */
    @JvmStatic
    fun isAvailable(): Boolean = AVAILABLE

    /** Why the library did not load, or `null` if it did. */
    @JvmStatic
    fun loadFailure(): Throwable? = LOAD_FAILURE

    /**
     * What the loaded library was compiled with, for example
     * `abi=arm64-v8a;neon=1;zxingcpp=3.1.1`.
     *
     * Compile-time facts, not a runtime CPU probe. zxing-cpp has no NEON intrinsics: it
     * relies on the auto-vectorizer and tests `__ARM_NEON` to decide whether a strided column
     * is worth packing before thresholding. Whether that macro was defined is a property of
     * the build, and a runtime feature check would not answer it.
     */
    @JvmStatic
    fun buildInfo(): String {
      if (!AVAILABLE) {
        throw IllegalStateException("libzxingnative is not loaded", LOAD_FAILURE)
      }
      return nativeBuildInfo()
    }

    /**
     * The format a UPC-A symbol should be reported as, or null if this is not one.
     *
     * zxing-cpp returns a UPC-A symbol as 13 digits with a leading zero, and labels it
     * `EAN_13` unless EAN-13 was excluded from the search. It cites ISO/IEC 15420:2009 and
     * GS1 General Specifications 5.1.3, under which the content behind symbology identifier
     * `]E0` is 13 digits. That is defensible and it is not what this library has ever
     * returned.
     *
     * GS1 prefix 0 is reserved for UPC-A, so a 13-digit EAN-13 beginning with zero is a UPC-A
     * and the conversion is exact, not a guess.
     */
    private fun upcAFormatFor(format: BarcodeFormat, text: String): BarcodeFormat? {
      if (format != BarcodeFormat.EAN_13 && format != BarcodeFormat.UPC_A) {
        return null
      }
      if (text.length != 13 || text[0] != '0') {
        return null
      }
      return BarcodeFormat.UPC_A
    }

    /**
     * A UPC-E symbol as this library has always reported it: the eight digits that are
     * actually printed under the symbol.
     *
     * zxing-cpp returns the expanded UPC-A equivalent — `0012345000065` for a symbol whose
     * digits are `01234565` — and still labels it `UPC_E`. Both carry the same information
     * and the printed form is what a host compares against a database, so the expansion is
     * undone here. This was 37 of the 1168 corpus samples.
     *
     * The four patterns are checked in the GS1 order, which inverts
     * `UPCEReader.convertUPCEtoUPCA` — the function the Java backend's own output comes
     * through. The inverse is not quite unique: that expansion is not injective, and 9% of
     * the two million syntactically possible UPC-E strings share a UPC-A with another one, so
     * for those the other spelling comes back. It is not a different product — every such
     * pair expands to the same UPC-A, checked exhaustively — and all 55 UPC-E samples in the
     * corpus come back as exactly the digits printed under the symbol. The ambiguity is
     * unavoidable here: zxing-cpp reports only the expanded form, so the parity bits that
     * would say which spelling the symbol carried are already gone.
     *
     * A string that matches none of the four patterns is left alone rather than mangled — it
     * did not come from a UPC-E symbol and guessing would be worse than passing it through.
     */
    @JvmStatic
    fun compactUpcE(text: String): String {
      var upcA = text
      if (upcA.length == 13 && upcA[0] == '0') {
        // zxing-cpp pads to the 13-digit GS1 rendering; UPC-A is the last 12.
        upcA = upcA.substring(1)
      }
      if (upcA.length != 12 || !isAllDigits(upcA)) {
        return text
      }
      val numberSystem = upcA[0]
      if (numberSystem != '0' && numberSystem != '1') {
        // UPC-E exists only for number systems 0 and 1.
        return text
      }
      val check = upcA[11]
      val d3 = upcA[3]
      if ((d3 == '0' || d3 == '1' || d3 == '2') && "0000" == upcA.substring(4, 8)) {
        return "" + numberSystem + upcA[1] + upcA[2] +
            upcA[8] + upcA[9] + upcA[10] + d3 + check
      }
      if ("00000" == upcA.substring(4, 9)) {
        return "" + numberSystem + upcA[1] + upcA[2] + upcA[3] +
            upcA[9] + upcA[10] + '3' + check
      }
      if ("00000" == upcA.substring(5, 10)) {
        return "" + numberSystem + upcA[1] + upcA[2] + upcA[3] +
            upcA[4] + upcA[10] + '4' + check
      }
      val last = upcA[10]
      if (last >= '5' && "0000" == upcA.substring(6, 10)) {
        return "" + numberSystem + upcA[1] + upcA[2] + upcA[3] +
            upcA[4] + upcA[5] + last + check
      }
      return text
    }

    /**
     * An RSS-14 symbol as the Java reader reports it: the 14-digit GTIN alone.
     *
     * A DataBar omnidirectional symbol always carries GS1 application identifier 01, and
     * zxing-cpp includes it in the text — `0104412345678909` where `RSS14Reader` returns
     * `04412345678909`. Only that exact shape is touched: 16 digits beginning with the
     * identifier. Anything else is passed through, because a string that is not this is not
     * something to go trimming digits off.
     */
    @JvmStatic
    fun withoutGs1GtinPrefix(text: String): String {
      if (text.length != 16 || !text.startsWith("01") || !isAllDigits(text)) {
        return text
      }
      return text.substring(2)
    }

    /**
     * Codabar without its start and stop characters.
     *
     * zxing-cpp keeps them — `A1234567890A` — and `CodaBarReader` removes them unless
     * `RETURN_CODABAR_START_END` was asked for. This library has never set that hint on the
     * scanning path, so the guards would be new characters in front of and behind every
     * Codabar result a host already handles. All 11 Codabar samples in the corpus were
     * affected.
     */
    @JvmStatic
    fun withoutCodabarGuards(text: String): String {
      if (text.length < 3 || !isCodabarGuard(text[0]) ||
          !isCodabarGuard(text[text.length - 1])) {
        return text
      }
      return text.substring(1, text.length - 1)
    }

    private fun isCodabarGuard(c: Char): Boolean {
      val upper = Character.toUpperCase(c)
      return upper >= 'A' && upper <= 'D'
    }

    private fun isAllDigits(s: String): Boolean {
      for (c in s) {
        if (c < '0' || c > '9') {
          return false
        }
      }
      return true
    }

    /**
     * The symbol's four corners, or null if the native side sent none.
     *
     * Only the points of a finished symbol. The Java reader also reports candidate points
     * *while* it searches, through `NEED_RESULT_POINT_CALLBACK`, which is what animates the
     * viewfinder; zxing-cpp has no streaming callback and those are lost.
     */
    private fun cornerPoints(decoded: Array<String>): Array<ResultPoint?>? {
      if (decoded.size < 10) {
        return null
      }
      return try {
        val points = arrayOfNulls<ResultPoint>(4)
        for (i in 0 until 4) {
          points[i] = ResultPoint(decoded[2 + i * 2].toFloat(), decoded[3 + i * 2].toFloat())
        }
        points
      } catch (e: NumberFormatException) {
        null
      }
    }

    private val REPORTED_UNMAPPED_FORMATS: MutableSet<String> =
        Collections.synchronizedSet(HashSet())

    private fun warnOnceAboutUnmappedFormat(nativeName: String?) {
      if (nativeName != null && REPORTED_UNMAPPED_FORMATS.add(nativeName)) {
        Log.w("NativeFrameDecoder", "zxing-cpp read a " + nativeName +
            ", which this library has no BarcodeFormat for; reporting it as no result")
      }
    }

    /**
     * The format selection as zxing-cpp spells it, or an empty string for "everything".
     *
     * Free of JNI so the mapping can be tested on the host: an unmatched name here would
     * quietly widen the format set.
     */
    @JvmStatic
    fun nativeFormatList(formats: Collection<@JvmSuppressWildcards BarcodeFormat>?): String {
      if (formats == null || formats.isEmpty()) {
        return ""
      }
      val joined = StringBuilder()
      for (format in formats) {
        val nativeName = NATIVE_NAMES[format]
        if (nativeName == null) {
          // zxing-cpp has no format of its own for this one -- UPC_EAN_EXTENSION is an
          // add-on to a UPC/EAN symbol there, not a format that can be asked for. Dropping
          // it from a list that still has other entries narrows the request, which is the
          // safe direction.
          continue
        }
        if (joined.isNotEmpty()) {
          joined.append(',')
        }
        joined.append(nativeName)
      }
      if (joined.isEmpty()) {
        // Everything asked for was unsupported. Returning "" here would mean "every
        // format" to zxing-cpp, turning a narrow request into the widest possible one, so
        // refuse instead. The caller keeps the Java backend for this selection.
        throw UnsupportedOperationException(
            "zxing-cpp supports none of the requested formats: $formats")
      }
      return joined.toString()
    }

    @JvmStatic private external fun nativeBuildInfo(): String

    @JvmStatic private external fun nativeCreate(): Long

    @JvmStatic private external fun nativeDestroy(handle: Long)

    @JvmStatic private external fun nativeSetFormats(handle: Long, formats: String)

    @JvmStatic private external fun nativeSetTryHarder(handle: Long, tryHarder: Boolean)

    @JvmStatic private external fun nativeDecode(
        handle: Long,
        luminance: ByteArray,
        width: Int,
        height: Int
    ): Array<String>?
  }
}
