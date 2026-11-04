/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.camera

/** The stretched preview mapper requires both streams to cover the same aspect ratio. */
internal object StreamGeometry {
  fun sameAspect(aw: Int, ah: Int, bw: Int, bh: Int): Boolean {
    if (aw <= 0 || ah <= 0 || bw <= 0 || bh <= 0) return false
    return maxOf(aw, ah).toLong() * minOf(bw, bh) ==
        maxOf(bw, bh).toLong() * minOf(aw, ah)
  }
}
