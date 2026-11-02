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

package io.github.kakakakacat.zxing.spike

import io.github.kakakakacat.zxing.android.scanner.FrameDecoder
import io.github.kakakakacat.zxing.android.scanner.FrameDecoders

/**
 * The decoder both flavors use, picked the way `ScannerOptions.DecoderBackend.AUTO` picks it:
 * the native one where its ABI exists, the Java one otherwise.
 *
 * Shared on purpose. The spike compares two *camera* stacks, so everything downstream of the
 * frame has to be the same object on both sides, and the backend name goes into the result
 * line so a reader can see that it was.
 */
object SpikeDecoder {

  val backendName: String =
      if (FrameDecoders.isAvailable(FrameDecoders.NATIVE)) FrameDecoders.NATIVE
      else FrameDecoders.JAVA

  fun create(): FrameDecoder = FrameDecoders.create(backendName, null, null)

  fun buildInfo(): String = "decoder=${FrameDecoders.buildInfo(backendName)}"
}
