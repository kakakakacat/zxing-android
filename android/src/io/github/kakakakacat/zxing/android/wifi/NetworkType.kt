/*
 * Copyright (C) 2011 ZXing authors
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

package io.github.kakakakacat.zxing.android.wifi

internal enum class NetworkType {

  WEP,
  WPA,
  NO_PASSWORD,
  WPA2_EAP;

  companion object {
    // Plain public rather than internal: WifiConfigManager is still Java and Kotlin mangles
    // an internal function's name out of its reach. See docs/maint/HANDOFF.md.
    @JvmStatic
    fun forIntentValue(networkTypeString: String?): NetworkType {
      if (networkTypeString == null) {
        return NO_PASSWORD
      }
      return when (networkTypeString) {
        "WPA", "WPA2" -> WPA
        "WPA2-EAP" -> WPA2_EAP
        "WEP" -> WEP
        "nopass" -> NO_PASSWORD
        else -> throw IllegalArgumentException(networkTypeString)
      }
    }
  }
}
