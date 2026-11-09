/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.scanner

import io.github.kakakakacat.zxing.LuminanceSource
import io.github.kakakakacat.zxing.Result
import io.github.kakakakacat.zxing.android.camera.LumaFrameData

/** Internal fast path for decoders that understand raw camera plane geometry. */
internal interface FrameDataDecoder {
  fun decode(data: LumaFrameData, fallback: LuminanceSource): Result?
}
