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

package io.github.kakakakacat.zxing.android.result

import android.widget.Toast
import io.github.kakakakacat.zxing.android.CaptureActivity
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.android.wifi.WifiNetworkAdder
import io.github.kakakakacat.zxing.client.result.ParsedResult
import io.github.kakakakacat.zxing.client.result.WifiParsedResult

/**
 * Handles wifi access information.
 *
 * @author Vikram Aggarwal
 * @author Sean Owen
 */
class WifiResultHandler(private val parent: CaptureActivity, result: ParsedResult) :
    ResultHandler(parent, result) {

  // We just need one button, and that is to configure the wireless.  This could change in the future.
  override fun getButtonCount(): Int = 1

  override fun getButtonText(index: Int): Int = R.string.button_wifi

  override fun handleButtonPress(index: Int) {
    if (index == 0) {
      val wifiResult = getResult() as WifiParsedResult
      // Which route is taken depends on the Android version; WifiNetworkAdder says why.
      // The toast has to follow the outcome rather than announce a connection in advance:
      // on Android 11+ the user still has a system prompt to confirm, on Android 10 the
      // platform decides when to use the suggestion, and a WEP barcode cannot be added at
      // all. Saying "requesting connection" to all of those was half of what made defect
      // A-08 invisible.
      val message = when (WifiNetworkAdder.add(activity, wifiResult)) {
        WifiNetworkAdder.Outcome.PROMPTING -> null // the system dialog is the feedback
        WifiNetworkAdder.Outcome.SUGGESTED -> R.string.wifi_suggested_network
        WifiNetworkAdder.Outcome.LEGACY -> R.string.wifi_changing_network
        WifiNetworkAdder.Outcome.UNSUPPORTED -> R.string.wifi_wep_unsupported
        WifiNetworkAdder.Outcome.BAD_BARCODE -> R.string.wifi_add_failed
      }
      if (message != null) {
        activity.runOnUiThread {
          Toast.makeText(activity.applicationContext, message, Toast.LENGTH_LONG).show()
        }
      }
      parent.restartPreviewAfterDelay(0L)
    }
  }

  // Display the name of the network and the network type to the user.
  override fun getDisplayContents(): CharSequence {
    val wifiResult = getResult() as WifiParsedResult
    return wifiResult.ssid + " (" + wifiResult.networkEncryption + ')'
  }

  override fun getDisplayTitle(): Int = R.string.result_wifi

}
