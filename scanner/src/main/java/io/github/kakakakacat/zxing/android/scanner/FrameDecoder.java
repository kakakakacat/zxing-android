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

/**
 * One frame in, at most one barcode out.
 *
 * <p>The seam a decoder backend sits behind. It exists so that a second implementation
 * can be compared against the Java decoder on identical frames: until two backends can be
 * driven through the same interface, "the native one is faster" has nothing to measure.
 * See {@code docs/maint/BENCHMARKS.md} for what a comparison has to clear before it counts
 * as a result.
 *
 * <h3>Why a {@link LuminanceSource} and not a {@code BinaryBitmap}</h3>
 *
 * <p>Binarization is part of a decoder, not part of the frame. On the reference device it
 * is roughly 4.9 ms of a 1280x720 frame — the single most expensive stage — and a backend
 * that has its own thresholding should be free to use it rather than be handed the
 * output of {@code HybridBinarizer}. Handing over a greyscale plane also means the
 * comparison is between two whole decoders rather than between two halves of one.
 *
 * <p>Implementations are driven from a single decode thread: one frame at a time, never
 * concurrently. They may hold state between frames and need no internal locking, but
 * {@link #setFormats} can arrive between any two frames.
 */
public interface FrameDecoder {

  /**
   * Decodes one frame.
   *
   * @param source the frame, already cropped to the scan region
   * @return the result, or {@code null} if this frame holds no barcode this decoder was
   *         asked to look for. Not finding one is the ordinary case on live preview, so
   *         it is a return value rather than an exception.
   */
  Result decode(LuminanceSource source);

  /**
   * Restricts what is looked for, or {@code null} / empty for everything supported.
   *
   * <p>Called between frames, including while scanning. This is the cheapest performance
   * control the pipeline has — see {@code BENCHMARKS.md} §10 — so a backend is expected
   * to honour it rather than filter afterwards.
   */
  void setFormats(Collection<BarcodeFormat> formats);

  /**
   * Releases anything the backend holds. The decoder is not used again afterwards.
   *
   * <p>A Java backend has nothing to do here. A native one has memory that is not the
   * garbage collector's to free, which is the reason this method exists at all.
   */
  void close();

  /**
   * A short, stable name for this backend, recorded alongside any measurement.
   *
   * <p>A timing without the name of the decoder that produced it cannot be compared with
   * anything, so this is not decoration.
   */
  String name();
}
