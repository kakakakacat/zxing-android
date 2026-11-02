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

/**
 * The `camera1` flavor's backend. Its twins live in `src/camerax/kotlin` and
 * `src/cameraxlean/kotlin`.
 *
 * One object per flavor, same fully-qualified name: nothing outside this file knows which
 * camera stack is in the APK, which is what keeps the two builds comparable.
 */
object SpikeBackends {
  fun create(): SpikeBackend = Camera1SpikeBackend()
}
