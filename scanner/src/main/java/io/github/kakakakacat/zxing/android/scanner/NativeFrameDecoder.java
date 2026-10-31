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

package io.github.kakakakacat.zxing.android.scanner;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.DecodeHintType;
import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.ResultPoint;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The zxing-cpp decoder behind the {@link FrameDecoder} seam.
 *
 * <p><strong>The shipping default since {@code ebe1708b4}</strong>, on the strength of
 * the A/B in {@code docs/maint/BENCHMARKS.md} §10.6c: 56–82% faster on 2D symbols and on
 * all-format misses. §9.1 is the other half of that picture and is less flattering — on
 * the 1168-sample corpus this backend reads 100 fewer samples than the Java one, almost
 * all of them DataBar — so which backend should be the default is an open question again.
 *
 * <p>The native library is loaded once, lazily, and a failure to load is reported through
 * {@link #isAvailable()} rather than thrown: a device without the right ABI should fall
 * back to the Java decoder, not crash.
 */
final class NativeFrameDecoder implements FrameDecoder {

  static final String NAME = "zxing-cpp";

  private static final boolean AVAILABLE;
  private static final Throwable LOAD_FAILURE;

  static {
    boolean loaded;
    Throwable failure = null;
    try {
      System.loadLibrary("zxingnative");
      loaded = true;
    } catch (UnsatisfiedLinkError | SecurityException e) {
      loaded = false;
      failure = e;
    }
    AVAILABLE = loaded;
    LOAD_FAILURE = failure;
  }

  /**
   * The zxing-cpp names for this fork's formats.
   *
   * <p>Mapped explicitly rather than by {@code name()}, because the two projects do not
   * spell every format the same way and a silently unmatched name would widen the format
   * set instead of narrowing it — turning a restriction into the opposite of what the
   * caller asked for.
   *
   * <p>{@link BarcodeFormat#UPC_EAN_EXTENSION} is deliberately absent: in zxing-cpp an
   * EAN add-on is a property of a UPC/EAN result, not a format that can be requested.
   */
  private static final Map<BarcodeFormat,String> NATIVE_NAMES =
      new EnumMap<>(BarcodeFormat.class);

  static {
    NATIVE_NAMES.put(BarcodeFormat.AZTEC, "Aztec");
    NATIVE_NAMES.put(BarcodeFormat.CODABAR, "Codabar");
    NATIVE_NAMES.put(BarcodeFormat.CODE_39, "Code39");
    NATIVE_NAMES.put(BarcodeFormat.CODE_93, "Code93");
    NATIVE_NAMES.put(BarcodeFormat.CODE_128, "Code128");
    NATIVE_NAMES.put(BarcodeFormat.DATA_MATRIX, "DataMatrix");
    NATIVE_NAMES.put(BarcodeFormat.EAN_8, "EAN-8");
    NATIVE_NAMES.put(BarcodeFormat.EAN_13, "EAN-13");
    NATIVE_NAMES.put(BarcodeFormat.ITF, "ITF");
    NATIVE_NAMES.put(BarcodeFormat.MAXICODE, "MaxiCode");
    NATIVE_NAMES.put(BarcodeFormat.PDF_417, "PDF417");
    NATIVE_NAMES.put(BarcodeFormat.QR_CODE, "QRCode");
    NATIVE_NAMES.put(BarcodeFormat.RSS_14, "DataBar");
    NATIVE_NAMES.put(BarcodeFormat.RSS_EXPANDED, "DataBarExpanded");
    NATIVE_NAMES.put(BarcodeFormat.UPC_A, "UPC-A");
    NATIVE_NAMES.put(BarcodeFormat.UPC_E, "UPC-E");
  }

  /** Reverse of {@link #NATIVE_NAMES}, keyed on the normalised spelling. */
  private static final Map<String,BarcodeFormat> FORMATS_BY_NORMALISED_NAME = new HashMap<>();

  static {
    for (Map.Entry<BarcodeFormat,String> entry : NATIVE_NAMES.entrySet()) {
      FORMATS_BY_NORMALISED_NAME.put(normalise(entry.getValue()), entry.getKey());
    }
  }

  /**
   * A format name with punctuation and case removed.
   *
   * <p>zxing-cpp spells a format one way when parsing a request and another way when
   * reporting a result: {@code QRCode} is accepted, {@code "QR Code"} comes back. Matching
   * the literal strings looks right and silently turns every successful decode into a
   * miss, which is how this was found. Normalising both sides is spelling-independent, and
   * no two formats collide once normalised.
   */
  static String normalise(String name) {
    StringBuilder out = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        out.append(Character.toLowerCase(c));
      }
    }
    return out.toString();
  }

  /** This fork's format for a zxing-cpp name, for turning a result back into a Result. */
  static BarcodeFormat formatFromNativeName(String nativeName) {
    if (nativeName == null) {
      return null;
    }
    return FORMATS_BY_NORMALISED_NAME.get(normalise(nativeName));
  }

  private long handle;
  private final boolean compatibilityRendering;

  NativeFrameDecoder(Collection<BarcodeFormat> formats) {
    this(null, formats, true);
  }

  NativeFrameDecoder(Collection<BarcodeFormat> formats, boolean compatibilityRendering) {
    this(null, formats, compatibilityRendering);
  }

  /**
   * @param compatibilityRendering whether to report a symbol the way this library always
   *     has rather than the way zxing-cpp spells it: UPC-A as 12 digits and
   *     {@link BarcodeFormat#UPC_A} rather than 13 and {@code EAN_13}, UPC-E compacted
   *     back to its eight printed digits, and Codabar without its start and stop
   *     characters. On by default: making the native decoder the default must not
   *     silently change what callers receive. Off is for measurement, where the raw
   *     behaviour of each decoder is the point.
   *     <p>A fourth difference — zxing-cpp escaping every non-graphical character — is
   *     handled on the native side with {@code TextMode::Plain}, because it is a property
   *     of the whole decode rather than of one symbology, and so is not switchable here.
   *     All four were found by running the corpus through both backends; BENCHMARKS.md
   *     §9.1 counts them.
   */
  NativeFrameDecoder(Map<DecodeHintType,?> hints,
                     Collection<BarcodeFormat> formats,
                     boolean compatibilityRendering) {
    this.compatibilityRendering = compatibilityRendering;
    if (!AVAILABLE) {
      throw new IllegalStateException("libzxingnative is not loaded", LOAD_FAILURE);
    }
    handle = nativeCreate();
    if (handle == 0L) {
      throw new IllegalStateException("could not create the native decoder");
    }
    // Presence is what means true in this decoder -- MultiFormatReader reads it as
    // containsKey -- so a hint whose value is Boolean.FALSE still turns it on, here as
    // there. Matching the Java backend's reading of the hint matters more than being
    // right about a map nobody builds that way.
    nativeSetTryHarder(handle, hints != null && hints.containsKey(DecodeHintType.TRY_HARDER));
    setFormats(formats);
  }

  /** Whether the native library loaded on this device. Checked before constructing one. */
  static boolean isAvailable() {
    return AVAILABLE;
  }

  /** Why the library did not load, or {@code null} if it did. */
  static Throwable loadFailure() {
    return LOAD_FAILURE;
  }

  /**
   * What the loaded library was compiled with, for example
   * {@code abi=arm64-v8a;neon=1;zxingcpp=3.1.1}.
   *
   * <p>Compile-time facts, not a runtime CPU probe. zxing-cpp has no NEON intrinsics: it
   * relies on the auto-vectorizer and tests {@code __ARM_NEON} to decide whether a
   * strided column is worth packing before thresholding. Whether that macro was defined
   * is a property of the build, and a runtime feature check would not answer it.
   */
  static String buildInfo() {
    if (!AVAILABLE) {
      throw new IllegalStateException("libzxingnative is not loaded", LOAD_FAILURE);
    }
    return nativeBuildInfo();
  }

  @Override
  public Result decode(LuminanceSource source) {
    if (handle == 0L) {
      throw new IllegalStateException("decoder already closed");
    }
    // getMatrix() is the scan rectangle, not the frame. Handing the native side a
    // greyscale plane rather than a BinaryBitmap is the point of the seam: zxing-cpp
    // does its own thresholding.
    String[] decoded =
        nativeDecode(handle, source.getMatrix(), source.getWidth(), source.getHeight());
    if (decoded == null || decoded.length < 2) {
      return null;
    }
    BarcodeFormat format = formatFromNativeName(decoded[1]);
    if (format == null) {
      // A format this fork has no equivalent for. Reporting it as a miss is the honest
      // answer: the caller asked for formats it can name, and inventing one would put a
      // value into Result.getBarcodeFormat() that no caller can switch on.
      return null;
    }
    String text = decoded[0];
    if (compatibilityRendering) {
      BarcodeFormat compatFormat = upcAFormatFor(format, text);
      if (compatFormat != null) {
        format = compatFormat;
        text = text.substring(1);
      } else if (format == BarcodeFormat.UPC_E) {
        text = compactUpcE(text);
      } else if (format == BarcodeFormat.CODABAR) {
        text = withoutCodabarGuards(text);
      }
    }
    return new Result(text, null, cornerPoints(decoded), format);
  }

  /**
   * The format a UPC-A symbol should be reported as, or null if this is not one.
   *
   * <p>zxing-cpp returns a UPC-A symbol as 13 digits with a leading zero, and labels it
   * {@code EAN_13} unless EAN-13 was excluded from the search. It cites ISO/IEC
   * 15420:2009 and GS1 General Specifications 5.1.3, under which the content behind
   * symbology identifier {@code ]E0} is 13 digits. That is defensible and it is not what
   * this library has ever returned.
   *
   * <p>GS1 prefix 0 is reserved for UPC-A, so a 13-digit EAN-13 beginning with zero is a
   * UPC-A and the conversion is exact, not a guess.
   */
  private static BarcodeFormat upcAFormatFor(BarcodeFormat format, String text) {
    if (format != BarcodeFormat.EAN_13 && format != BarcodeFormat.UPC_A) {
      return null;
    }
    if (text.length() != 13 || text.charAt(0) != '0') {
      return null;
    }
    return BarcodeFormat.UPC_A;
  }

  /**
   * A UPC-E symbol as this library has always reported it: the eight digits that are
   * actually printed under the symbol.
   *
   * <p>zxing-cpp returns the expanded UPC-A equivalent — {@code 0012345000065} for a
   * symbol whose digits are {@code 01234565} — and still labels it {@code UPC_E}. Both
   * carry the same information and the printed form is what a host compares against a
   * database, so the expansion is undone here. This was 37 of the 1168 corpus samples.
   *
   * <p>The four patterns are checked in the GS1 order, which inverts
   * {@code UPCEReader.convertUPCEtoUPCA} — the function the Java backend's own output
   * comes through. The inverse is not quite unique: that expansion is not injective, and
   * 9% of the two million syntactically possible UPC-E strings share a UPC-A with another
   * one, so for those the other spelling comes back. It is not a different product —
   * every such pair expands to the same UPC-A, checked exhaustively — and all 55 UPC-E
   * samples in the corpus come back as exactly the digits printed under the symbol. The
   * ambiguity is unavoidable here: zxing-cpp reports only the expanded form, so the
   * parity bits that would say which spelling the symbol carried are already gone.
   *
   * <p>A string that matches none of the four patterns is left alone rather than mangled
   * — it did not come from a UPC-E symbol and guessing would be worse than passing it
   * through.
   */
  static String compactUpcE(String text) {
    String upcA = text;
    if (upcA.length() == 13 && upcA.charAt(0) == '0') {
      // zxing-cpp pads to the 13-digit GS1 rendering; UPC-A is the last 12.
      upcA = upcA.substring(1);
    }
    if (upcA.length() != 12 || !isAllDigits(upcA)) {
      return text;
    }
    char numberSystem = upcA.charAt(0);
    if (numberSystem != '0' && numberSystem != '1') {
      // UPC-E exists only for number systems 0 and 1.
      return text;
    }
    char check = upcA.charAt(11);
    char d3 = upcA.charAt(3);
    if ((d3 == '0' || d3 == '1' || d3 == '2') && "0000".equals(upcA.substring(4, 8))) {
      return "" + numberSystem + upcA.charAt(1) + upcA.charAt(2)
          + upcA.charAt(8) + upcA.charAt(9) + upcA.charAt(10) + d3 + check;
    }
    if ("00000".equals(upcA.substring(4, 9))) {
      return "" + numberSystem + upcA.charAt(1) + upcA.charAt(2) + upcA.charAt(3)
          + upcA.charAt(9) + upcA.charAt(10) + '3' + check;
    }
    if ("00000".equals(upcA.substring(5, 10))) {
      return "" + numberSystem + upcA.charAt(1) + upcA.charAt(2) + upcA.charAt(3)
          + upcA.charAt(4) + upcA.charAt(10) + '4' + check;
    }
    char last = upcA.charAt(10);
    if (last >= '5' && "0000".equals(upcA.substring(6, 10))) {
      return "" + numberSystem + upcA.charAt(1) + upcA.charAt(2) + upcA.charAt(3)
          + upcA.charAt(4) + upcA.charAt(5) + last + check;
    }
    return text;
  }

  /**
   * Codabar without its start and stop characters.
   *
   * <p>zxing-cpp keeps them — {@code A1234567890A} — and {@code CodaBarReader} removes
   * them unless {@code RETURN_CODABAR_START_END} was asked for. This library has never
   * set that hint on the scanning path, so the guards would be new characters in front of
   * and behind every Codabar result a host already handles. All 11 Codabar samples in the
   * corpus were affected.
   */
  static String withoutCodabarGuards(String text) {
    if (text.length() < 3 || !isCodabarGuard(text.charAt(0))
        || !isCodabarGuard(text.charAt(text.length() - 1))) {
      return text;
    }
    return text.substring(1, text.length() - 1);
  }

  private static boolean isCodabarGuard(char c) {
    char upper = Character.toUpperCase(c);
    return upper >= 'A' && upper <= 'D';
  }

  private static boolean isAllDigits(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  /**
   * The symbol's four corners, or null if the native side sent none.
   *
   * <p>Only the points of a finished symbol. The Java reader also reports candidate
   * points <em>while</em> it searches, through {@code NEED_RESULT_POINT_CALLBACK}, which
   * is what animates the viewfinder; zxing-cpp has no streaming callback and those are
   * lost.
   */
  private static ResultPoint[] cornerPoints(String[] decoded) {
    if (decoded.length < 10) {
      return null;
    }
    try {
      ResultPoint[] points = new ResultPoint[4];
      for (int i = 0; i < 4; i++) {
        points[i] = new ResultPoint(Float.parseFloat(decoded[2 + i * 2]),
                                    Float.parseFloat(decoded[3 + i * 2]));
      }
      return points;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public void setFormats(Collection<BarcodeFormat> formats) {
    if (handle == 0L) {
      throw new IllegalStateException("decoder already closed");
    }
    nativeSetFormats(handle, nativeFormatList(formats));
  }

  /**
   * The format selection as zxing-cpp spells it, or an empty string for "everything".
   *
   * <p>Package-private and free of JNI so the mapping can be tested on the host: an
   * unmatched name here would quietly widen the format set.
   */
  static String nativeFormatList(Collection<BarcodeFormat> formats) {
    if (formats == null || formats.isEmpty()) {
      return "";
    }
    StringBuilder joined = new StringBuilder();
    for (BarcodeFormat format : formats) {
      String nativeName = NATIVE_NAMES.get(format);
      if (nativeName == null) {
        // zxing-cpp has no format of its own for this one -- UPC_EAN_EXTENSION is an
        // add-on to a UPC/EAN symbol there, not a format that can be asked for. Dropping
        // it from a list that still has other entries narrows the request, which is the
        // safe direction.
        continue;
      }
      if (joined.length() > 0) {
        joined.append(',');
      }
      joined.append(nativeName);
    }
    if (joined.length() == 0) {
      // Everything asked for was unsupported. Returning "" here would mean "every
      // format" to zxing-cpp, turning a narrow request into the widest possible one, so
      // refuse instead. The caller keeps the Java backend for this selection.
      throw new UnsupportedOperationException(
          "zxing-cpp supports none of the requested formats: " + formats);
    }
    return joined.toString();
  }

  @Override
  public void close() {
    if (handle != 0L) {
      nativeDestroy(handle);
      handle = 0L;
    }
  }

  @Override
  public String name() {
    return NAME;
  }

  private static native String nativeBuildInfo();

  private static native long nativeCreate();

  private static native void nativeDestroy(long handle);

  private static native void nativeSetFormats(long handle, String formats);

  private static native void nativeSetTryHarder(long handle, boolean tryHarder);

  private static native String[] nativeDecode(long handle, byte[] luminance,
                                              int width, int height);
}
