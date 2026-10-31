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

import android.content.Context
import android.net.wifi.WifiManager
import android.os.AsyncTask
import android.util.Log
import android.widget.Toast
import io.github.kakakakacat.zxing.android.CaptureActivity
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.android.wifi.WifiConfigManager
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

  @Suppress("DEPRECATION") // AsyncTask, and WifiConfigManager's own API: defect A-08
  override fun handleButtonPress(index: Int) {
    if (index == 0) {
      val wifiResult = getResult() as WifiParsedResult
      val wifiManager =
          activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
      if (wifiManager == null) {
        Log.w(TAG, "No WifiManager available from device")
        return
      }
      activity.runOnUiThread {
        Toast.makeText(activity.applicationContext, R.string.wifi_changing_network, Toast.LENGTH_SHORT).show()
      }
      WifiConfigManager(wifiManager).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, wifiResult)
      parent.restartPreviewAfterDelay(0L)
    }
  }

  // Display the name of the network and the network type to the user.
  override fun getDisplayContents(): CharSequence {
    val wifiResult = getResult() as WifiParsedResult
    return wifiResult.ssid + " (" + wifiResult.networkEncryption + ')'
  }

  override fun getDisplayTitle(): Int = R.string.result_wifi

  private companion object {
    private val TAG = WifiResultHandler::class.java.simpleName
  }
}
