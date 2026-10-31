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
import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.Result;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The zxing-cpp decoder behind the {@link FrameDecoder} seam.
 *
 * <p><strong>Not the default, and not claimed to be faster.</strong> Phase 5 item 10
 * requires an A/B against {@link JavaFrameDecoder} on the fixed dataset, clearing the
 * gates in {@code docs/maint/BENCHMARKS.md} §10.6, before this becomes anyone's default
 * or before any speedup is stated. Swapping a decoder is not evidence.
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

  NativeFrameDecoder(Collection<BarcodeFormat> formats) {
    if (!AVAILABLE) {
      throw new IllegalStateException("libzxingnative is not loaded", LOAD_FAILURE);
    }
    handle = nativeCreate();
    if (handle == 0L) {
      throw new IllegalStateException("could not create the native decoder");
    }
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
    return new Result(decoded[0], null, null, format);
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

  private static native String[] nativeDecode(long handle, byte[] luminance,
                                              int width, int height);
}
