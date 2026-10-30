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

import android.graphics.Bitmap;
import io.github.kakakakacat.zxing.Result;

/** A successful decode, together with the thumbnail the result points refer to. */
public final class ScanOutcome {

  private final Result result;
  private final Bitmap thumbnail;
  private final float thumbnailScaleFactor;

  ScanOutcome(Result result, Bitmap thumbnail, float thumbnailScaleFactor) {
    this.result = result;
    this.thumbnail = thumbnail;
    this.thumbnailScaleFactor = thumbnailScaleFactor;
  }

  public Result getResult() {
    return result;
  }

  /**
   * A mutable greyscale thumbnail of the decoded frame, or {@code null} if thumbnails are
   * switched off. Callers may draw on it; it is not reused by the scanner.
   */
  public Bitmap getThumbnail() {
    return thumbnail;
  }

  /** Thumbnail width divided by the decoded frame width, for mapping result points. */
  public float getThumbnailScaleFactor() {
    return thumbnailScaleFactor;
  }
}
