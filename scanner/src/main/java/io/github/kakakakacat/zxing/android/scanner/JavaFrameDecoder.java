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
import io.github.kakakakacat.zxing.BinaryBitmap;
import io.github.kakakakacat.zxing.DecodeHintType;
import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.MultiFormatReader;
import io.github.kakakakacat.zxing.ReaderException;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.common.HybridBinarizer;

import java.util.Collection;
import java.util.Map;

/**
 * This fork's own Java decoder behind the {@link FrameDecoder} seam.
 *
 * <p>The shipping default, and the baseline every other backend is measured against.
 * Behaviour is deliberately identical to what the decode thread did before the seam
 * existed: {@link HybridBinarizer}, {@link MultiFormatReader#decodeWithState} to reuse the
 * readers between frames, and a {@code reset()} afterwards so state from one frame cannot
 * leak into the next.
 */
final class JavaFrameDecoder implements FrameDecoder {

  static final String NAME = "zxing-java";

  private final Map<DecodeHintType,Object> fixedHints;
  private final MultiFormatReader reader = new MultiFormatReader();

  JavaFrameDecoder(Map<DecodeHintType,Object> fixedHints,
                   Collection<BarcodeFormat> formats) {
    this.fixedHints = fixedHints;
    setFormats(formats);
  }

  @Override
  public Result decode(LuminanceSource source) {
    try {
      return reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source)));
    } catch (ReaderException re) {
      // Nothing in this frame. The ordinary case on live preview.
      return null;
    } finally {
      reader.reset();
    }
  }

  @Override
  public void setFormats(Collection<BarcodeFormat> formats) {
    // setHints rebuilds the reader list, which also discards the state decodeWithState
    // carries between frames. That is correct here: the state belongs to readers the
    // caller has just asked to stop using.
    reader.setHints(DecoderThread.hintsFor(fixedHints, formats));
  }

  @Override
  public void close() {
    // Nothing held that the garbage collector will not take.
  }

  @Override
  public String name() {
    return NAME;
  }
}
