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

package io.github.kakakakacat.zxing.benchmark;

import io.github.kakakakacat.zxing.BinaryBitmap;
import io.github.kakakakacat.zxing.MultiFormatReader;
import io.github.kakakakacat.zxing.NotFoundException;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.ReaderException;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.common.BitMatrix;
import io.github.kakakakacat.zxing.common.HybridBinarizer;

/**
 * The frame path, stage by stage, as {@code docs/maint/BENCHMARKS.md} §10.3 defines it.
 *
 * <p>Each method is one stage, so a regression can be attributed instead of guessed at,
 * and {@link #endToEnd} runs them the way {@code DecoderThread} actually runs them —
 * {@code decodeWithState} on a reused reader, {@code reset()} in a finally block. A
 * benchmark that built a fresh {@code MultiFormatReader} per frame would be measuring a
 * pipeline this fork does not ship.
 */
public final class FramePipeline {

  private FramePipeline() {
  }

  /** Stage 1: the viewfinder rectangle through the display-to-camera rotation. */
  public static int[] crop(int cameraWidth, int cameraHeight) {
    return ScanGeometry.crop(cameraWidth, cameraHeight);
  }

  /**
   * Stage 2: the luminance source over the cropped region.
   *
   * <p>Deliberately the same constructor call as {@code CameraManager}: no copy of the
   * plane, the crop carried as offsets.
   */
  public static PlanarYUVLuminanceSource luminanceSource(FrameFactory.Frame frame, int[] crop) {
    return new PlanarYUVLuminanceSource(
        frame.luminance, frame.width, frame.height,
        crop[0], crop[1], crop[2] - crop[0], crop[3] - crop[1], false);
  }

  /** Stage 3: the binarizer producing the black matrix. */
  public static BitMatrix binarize(PlanarYUVLuminanceSource source) throws NotFoundException {
    return new BinaryBitmap(new HybridBinarizer(source)).getBlackMatrix();
  }

  /**
   * Stage 4: one decode attempt on a reused reader.
   *
   * <p>{@code BinaryBitmap} computes its black matrix lazily and then caches it, so this
   * measures the reader alone only when the caller has already called
   * {@link BinaryBitmap#getBlackMatrix()} on the bitmap it passes in. Handing it a fresh
   * bitmap measures stage 3 and stage 4 together; the benchmark does the former, and
   * {@link #endToEnd} deliberately does the latter.
   *
   * @return the result, or null for a miss — the common case on live preview
   */
  public static Result decode(MultiFormatReader reader, BinaryBitmap bitmap) {
    try {
      return reader.decodeWithState(bitmap);
    } catch (ReaderException re) {
      return null;
    } finally {
      reader.reset();
    }
  }

  /** Stages 1-4, as the decode thread runs them. */
  public static Result endToEnd(MultiFormatReader reader, FrameFactory.Frame frame) {
    int[] crop = crop(frame.width, frame.height);
    PlanarYUVLuminanceSource source = luminanceSource(frame, crop);
    return decode(reader, new BinaryBitmap(new HybridBinarizer(source)));
  }

  /** A reader configured the way the decode thread configures it. */
  public static MultiFormatReader reader(L2Config config) {
    MultiFormatReader reader = new MultiFormatReader();
    reader.setHints(config.hints());
    return reader;
  }
}
