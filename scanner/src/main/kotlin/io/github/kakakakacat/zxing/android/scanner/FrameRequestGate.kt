/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.scanner

import android.os.Handler

/** Cancellation and a fast backend request are serialized before the backend is torn down. */
internal class FrameRequestGate(private val next: (Handler) -> Unit) : DecoderThread.FrameRequester {
  private var active = true
  @Synchronized override fun request(handler: Handler) { if (active) next(handler) }
  @Synchronized fun cancel() { active = false }
}
