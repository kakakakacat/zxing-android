/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.camera

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The stretched preview mapper requires both streams to cover the same field of view. */
internal object StreamGeometry {

  /**
   * How far two aspect ratios may drift apart and still be treated as the same field of
   * view, as a fraction of the smaller one.
   *
   * This was an exact comparison -- the integer cross products had to be equal -- and that
   * is stricter than any camera. Vendors advertise sizes as "16:9" that are not 16:9:
   * DN-ENG41 (Android 14, API 34) offers 864x480 in the same bucket as 1280x720 and
   * 640x360, and with separate preview and analysis sizes requested CameraX negotiated
   * **preview 864x480 (1.800) against analysis 640x360 (1.778)**. Both are what that device
   * calls 16:9; they differ by 1.25%, and the exact comparison rejected the pair and failed
   * the bind outright.
   *
   * 5% is wide enough for that kind of vendor rounding and far below the gap that separates
   * the real buckets -- 4:3 against 16:9 is 33% -- so a genuine field-of-view difference,
   * which would put the viewfinder rectangle somewhere other than what the decoder crops,
   * is still refused. The cost of accepting 1.25% is that the crop is off by that much at
   * the frame edge, inside a framing rectangle that already carries a wider margin.
   */
  private const val ASPECT_TOLERANCE = 0.05

  fun sameAspect(aw: Int, ah: Int, bw: Int, bh: Int): Boolean {
    if (aw <= 0 || ah <= 0 || bw <= 0 || bh <= 0) return false
    // Orientation is not a difference in field of view: a rotated stream covers the same
    // scene, so both ratios are taken long-side over short-side.
    val a = max(aw, ah).toDouble() / min(aw, ah)
    val b = max(bw, bh).toDouble() / min(bw, bh)
    return abs(a - b) <= ASPECT_TOLERANCE * min(a, b)
  }
}
