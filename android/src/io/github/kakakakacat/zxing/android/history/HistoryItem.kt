/*
 * Copyright (C) 2008 ZXing authors
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

package io.github.kakakakacat.zxing.android.history

import io.github.kakakakacat.zxing.Result

/**
 * Represents one item in the scan history.
 */
class HistoryItem internal constructor(
    private val result: Result?,
    private val display: String?,
    private val details: String?
) {

  fun getResult(): Result? = result

  fun getDisplayAndDetails(): String {
    val displayResult = StringBuilder()
    if (display.isNullOrEmpty()) {
      displayResult.append(result?.text)
    } else {
      displayResult.append(display)
    }
    if (!details.isNullOrEmpty()) {
      displayResult.append(" : ").append(details)
    }
    return displayResult.toString()
  }
}
