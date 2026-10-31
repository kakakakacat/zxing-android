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

import android.graphics.Bitmap
import io.github.kakakakacat.zxing.Result

/** A successful decode, together with the thumbnail the result points refer to. */
class ScanOutcome private constructor(
    val result: Result,
    /**
     * A mutable greyscale thumbnail of the decoded frame, or `null` if thumbnails are switched
     * off. Callers may draw on it; it is not reused by the scanner.
     */
    val thumbnail: Bitmap?,
    /** Thumbnail width divided by the decoded frame width, for mapping result points. */
    val thumbnailScaleFactor: Float
) {

  companion object {
    /**
     * The constructor was package-private in Java. `internal` would compile it to a plain
     * public one and hand every consumer a way to manufacture a scan result that never came
     * from a scan, so it is private and reached through this -- an `internal` member of a
     * public companion, which is the shape whose name the compiler mangles.
     */
    internal fun of(result: Result, thumbnail: Bitmap?, scaleFactor: Float): ScanOutcome =
        ScanOutcome(result, thumbnail, scaleFactor)
  }
}
