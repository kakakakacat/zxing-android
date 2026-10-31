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

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Builds a decoder backend by name.
 *
 * <p>Exists so the benchmark module can drive both backends through one interface over
 * identical frames. Until that was possible, "the native decoder is faster" had nothing
 * to measure; see {@code docs/maint/BENCHMARKS.md} for what a comparison must clear
 * before it counts as a result.
 *
 * <p>The Java backend is the shipping default. Nothing here changes that, and nothing
 * here should until the comparison exists.
 */
public final class FrameDecoders {

  /** The Java decoder: the shipping default and the baseline of any comparison. */
  public static final String JAVA = JavaFrameDecoder.NAME;

  /** The zxing-cpp decoder. Available only where its ABI is. */
  public static final String NATIVE = NativeFrameDecoder.NAME;

  private FrameDecoders() {
  }

  /**
   * @param name    {@link #JAVA} or {@link #NATIVE}
   * @param hints   decode hints other than the format selection; may be null
   * @param formats the formats to look for, or null / empty for everything
   * @throws IllegalArgumentException if the name is not a known backend
   * @throws IllegalStateException if the native backend was asked for and is not loadable
   */
  public static FrameDecoder create(String name,
                                    Map<DecodeHintType,?> hints,
                                    Collection<BarcodeFormat> formats) {
    if (JAVA.equals(name)) {
      Map<DecodeHintType,Object> fixed = new EnumMap<>(DecodeHintType.class);
      if (hints != null) {
        fixed.putAll(hints);
      }
      fixed.remove(DecodeHintType.POSSIBLE_FORMATS);
      return new JavaFrameDecoder(fixed, formats);
    }
    if (NATIVE.equals(name)) {
      // Hints other than the format selection are deliberately not forwarded: zxing-cpp
      // has its own options and no mapping from this fork's DecodeHintType exists. A
      // comparison must say so rather than pretend the two were configured alike.
      return new NativeFrameDecoder(formats);
    }
    throw new IllegalArgumentException("unknown decoder backend: " + name);
  }

  /** Whether {@link #NATIVE} can be created on this device. */
  public static boolean isAvailable(String name) {
    if (JAVA.equals(name)) {
      return true;
    }
    if (NATIVE.equals(name)) {
      return NativeFrameDecoder.isAvailable();
    }
    return false;
  }

  /**
   * What a backend was built with, recorded next to any measurement taken from it.
   *
   * <p>For the native backend this is the compile-time ABI, NEON state and zxing-cpp
   * version. A timing whose decoder cannot be identified cannot be compared with
   * anything.
   */
  public static String buildInfo(String name) {
    if (JAVA.equals(name)) {
      return "backend=zxing-java";
    }
    if (NATIVE.equals(name)) {
      return "backend=zxing-cpp;" + NativeFrameDecoder.buildInfo();
    }
    throw new IllegalArgumentException("unknown decoder backend: " + name);
  }
}
