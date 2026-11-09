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

package io.github.kakakakacat.zxing.android.camera

import io.github.kakakakacat.zxing.LuminanceSource

/** Heap frame with an optional buffer lease, released only after decoding/thumbnail creation. */
internal class Camera1ScanFrame(
    override val source: LuminanceSource?,
    override val data: LumaFrameData?,
    private val release: (() -> Unit)?
) : ScanFrame {
  constructor(source: LuminanceSource?) : this(source, null, null)

  /** Preserves the source/release constructor and Kotlin trailing lambda calls. */
  constructor(source: LuminanceSource?, release: (() -> Unit)?) : this(source, null, release)

  private val closed = java.util.concurrent.atomic.AtomicBoolean()
  override fun close() { if (closed.compareAndSet(false, true)) release?.invoke() }
}
