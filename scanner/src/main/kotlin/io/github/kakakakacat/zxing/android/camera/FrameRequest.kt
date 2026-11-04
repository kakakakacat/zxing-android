/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.camera

import android.os.Handler
import io.github.kakakakacat.zxing.android.scanner.ScannerTrace

/** Taken once from an atomic pending slot, either delivered or cancelled. */
internal class FrameRequest(private val handler: Handler, private val what: Int) {
  private val cookie = ScannerTrace.beginFrameRequest()
  fun cancel() { ScannerTrace.endFrameRequest(cookie) }
  fun deliver(frame: ScanFrame) {
    ScannerTrace.endFrameRequest(cookie)
    val owned = ScannerTrace.received(frame)
    if (!handler.sendMessage(handler.obtainMessage(what, owned))) owned.close()
  }
}
