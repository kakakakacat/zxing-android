/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.scanner

import io.github.kakakakacat.zxing.android.camera.LumaFrameData

/**
 * Measurement only: a decoder that can run its thresholding and its detection separately.
 *
 * **Not part of the scanning path.** `DecoderThread` calls [FrameDecoder.decode] and
 * nothing in this module calls anything here; the interface exists so
 * `DecoderStageBenchmark` can attribute a frame's cost to one half or the other, which an
 * end-to-end case cannot do. Internal for the same reason `FrameDataDecoder` is: the
 * benchmark module is Java and so can see it, while the published Kotlin API cannot.
 *
 * ### What the split is, and what it is not
 *
 * [prepareStages] holds one frame and builds its black matrix once. [binarizeOnly] then
 * rebuilds that matrix from the held frame, and [detectOnly] runs the readers over the
 * matrix already built. So:
 *
 * - **`binarizeOnly + detectOnly` is a floor for an end-to-end decode, not a partition
 *   of it.** The matrix `detectOnly` reads is warm in cache, which it is not on a live
 *   frame, and the crop, the JNI transition and the pin are outside both halves.
 * - **A 1D symbol is found before any matrix is built, so for those frames [binarizeOnly]
 *   measures work the decode never does.** Only the 2D readers call `getBitMatrix`;
 *   `ODReader` thresholds a row at a time. And with `tryHarder` off — what this decoder is
 *   configured for — `MultiFormatReader` puts the 1D reader ahead of every 2D reader, so a
 *   1D hit ends the read before anything asks for a matrix. Measured on DN-ENG41 at HD:
 *   0.612 ms of thresholding against 0.047 ms of detection, for a frame whose whole decode
 *   is 0.096 ms. **Do not add the two halves for a 1D hit.**
 *
 * Read the two against the end-to-end case for the same frame, which every stage run also
 * produces. A split that does not roughly add up to it is the interesting result, not a
 * broken measurement.
 */
internal interface StagedFrameDecoder {

  /**
   * Holds [data] for the two stage calls, building its black matrix once.
   *
   * The frame is copied, because the matrix outlives the call that built it and a Java
   * array is only pinned for the duration of one. Geometry, both strides included, is
   * preserved — the binarizer sees the shape it sees on the live path.
   *
   * @throws IllegalStateException if this decoder's configuration would not decode this
   *     frame through the path the split reproduces, which would make the two timings
   *     describe work the frame never does
   */
  fun prepareStages(data: LumaFrameData)

  /**
   * Thresholding alone: a fresh binarizer over the held frame, forced to its matrix.
   *
   * @return the matrix cell count, so a caller has a value to sink, or -1 if no frame is
   *     held
   */
  fun binarizeOnly(): Long

  /** Detection alone, over the matrix [prepareStages] built. @return whether it hit */
  fun detectOnly(): Boolean

  /**
   * Detection by one reader group, over the same matrix [detectOnly] runs against.
   *
   * [detectOnly] is the whole reader loop and says nothing about which reader inside it
   * is expensive; the loop lives in zxing-cpp and keeps its reader list private, so there
   * is no seam to time from within. This reaches the same decomposition from outside: a
   * second reader built from this decoder's options with [formats] narrowed to one
   * symbology, which makes exactly one of the all-format run's readers exist.
   *
   * [formats] is a zxing-cpp format list. The six groups that partition the reader set
   * are `AllLinear`, `QRCode`, `DataMatrix`, `Aztec`, `PDF417` and `MaxiCode`; narrower
   * than a symbology is refused by the measurement, not by this call, because the readers
   * branch on their own format set and would then do less work than they do in the
   * all-format run.
   *
   * **On a miss the groups sum to [detectOnly]; on a hit they do not** — the reader loop
   * stops at the first symbol, so a hit charges nothing to the readers behind it.
   *
   * @throws IllegalStateException if no frame is held, or if this decoder's own format
   *     set would build no reader for the group
   */
  fun detectOnlyGroup(formats: String): Boolean

  /** Drops the held frame and its matrix. */
  fun releaseStages()
}
