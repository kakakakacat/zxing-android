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
import io.github.kakakakacat.zxing.DecodeHintType
import java.util.EnumMap

/**
 * Builds a decoder backend by name.
 *
 * Exists so the benchmark module can drive both backends through one interface over identical
 * frames. Until that was possible, "the native decoder is faster" had nothing to measure; see
 * `docs/maint/BENCHMARKS.md` for what a comparison must clear before it counts as a result.
 *
 * The native backend is the shipping default since `ebe1708b4`; the Java one is the fallback
 * where there is no native build for the ABI, and what `ScannerOptions.setDecoderBackend(JAVA)`
 * selects.
 */
object FrameDecoders {

  /** The Java decoder: the fallback, and the baseline of any comparison. */
  const val JAVA: String = JavaFrameDecoder.NAME

  /** The zxing-cpp decoder: the default. Available only where its ABI is. */
  const val NATIVE: String = NativeFrameDecoder.NAME

  /** Experimental direct-buffer path; a distinct benchmark identity. */
  const val NATIVE_DIRECT: String = "zxing-cpp-direct"

  /** Short spelling of [JAVA], for a command line or an instrumentation argument. */
  const val JAVA_ALIAS: String = "java"

  /** Short spelling of [NATIVE]. */
  const val NATIVE_ALIAS: String = "native"

  /**
   * The canonical backend name for a name or its short alias.
   *
   * `java` and `native` are what a person types; `zxing-java` and `zxing-cpp` are what gets
   * recorded next to a measurement. Accepting both in one place keeps a caller from having to
   * know which is which.
   *
   * @throws IllegalArgumentException if the name is not a known backend. An unknown name is a
   *     mistake in how the run was invoked, and has to be loud: treating it as "unavailable"
   *     would quietly skip the work someone asked for.
   */
  @JvmStatic
  fun canonical(name: String?): String {
    if (name == NATIVE_DIRECT || name == "native-direct") return NATIVE_DIRECT
    if (JAVA == name || JAVA_ALIAS == name) {
      return JAVA
    }
    if (NATIVE == name || NATIVE_ALIAS == name) {
      return NATIVE
    }
    throw IllegalArgumentException("unknown decoder backend: " + name
        + " (expected one of " + JAVA + "/" + JAVA_ALIAS + ", " + NATIVE + "/"
        + NATIVE_ALIAS + ")")
  }

  /**
   * @param rawName [JAVA] or [NATIVE]
   * @param hints   decode hints other than the format selection; may be null
   * @param formats the formats to look for, or null / empty for everything
   * @throws IllegalArgumentException if the name is not a known backend
   * @throws IllegalStateException if the native backend was asked for and is not loadable
   */
  @JvmStatic
  fun create(
      rawName: String?,
      hints: Map<DecodeHintType, *>?,
      formats: Collection<@JvmSuppressWildcards BarcodeFormat>?
  ): FrameDecoder {
    val name = canonical(rawName)
    if (JAVA == name) {
      // Any?, not Any: this is a copy of the caller's map and a null value has to survive
      // it. Presence is what means true to MultiFormatReader, so dropping a null-valued
      // hint would turn it off.
      val fixed: MutableMap<DecodeHintType, Any?> = EnumMap(DecodeHintType::class.java)
      if (hints != null) {
        fixed.putAll(hints)
      }
      fixed.remove(DecodeHintType.POSSIBLE_FORMATS)
      return JavaFrameDecoder(fixed, formats)
    }
    if (name == NATIVE_DIRECT) return NativeFrameDecoder(hints, formats, true, true)
    if (NATIVE == name) {
      // TRY_HARDER maps onto zxing-cpp's tryHarder and tryRotate; see the JNI for why
      // those two and not its other two effort flags. Every other hint is still dropped,
      // because zxing-cpp has its own options and no mapping exists -- a comparison must
      // say so rather than pretend the two were configured alike.
      return NativeFrameDecoder(hints, formats, true)
    }
    throw IllegalArgumentException("unknown decoder backend: $name")
  }

  /**
   * Whether this backend can be created on this device.
   *
   * False means a real device limitation — the native library has no build for this ABI. An
   * unknown name throws instead, because that is a mistake in the request rather than a
   * property of the device.
   */
  @JvmStatic
  fun isAvailable(rawName: String?): Boolean {
    val name = canonical(rawName)
    return JAVA == name || NativeFrameDecoder.isAvailable()
  }

  /**
   * What a backend was built with, recorded next to any measurement taken from it.
   *
   * For the native backend this is the compile-time ABI, NEON state and zxing-cpp version. A
   * timing whose decoder cannot be identified cannot be compared with anything.
   */
  @JvmStatic
  fun buildInfo(rawName: String?): String {
    val name = canonical(rawName)
    if (JAVA == name) {
      return "backend=zxing-java"
    }
    if (name == NATIVE_DIRECT) return "backend=$NATIVE_DIRECT;" + NativeFrameDecoder.buildInfo()
    if (NATIVE == name) {
      return "backend=zxing-cpp;" + NativeFrameDecoder.buildInfo()
    }
    throw IllegalArgumentException("unknown decoder backend: $name")
  }
}
