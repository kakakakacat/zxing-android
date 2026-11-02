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

import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.AsyncTask
import android.util.Log
import io.github.kakakakacat.zxing.client.result.WifiParsedResult
import java.util.regex.Pattern

/**
 * @author Vikram Aggarwal
 * @author Sean Owen
 * @author Steffen Kieß
 */
/**
 * The pre-Android-10 route for adding a scanned network, kept for the devices where it is
 * the only one. `addNetwork`/`enableNetwork` return failure for a non-system app from
 * API 29 on, which was defect A-08; [WifiNetworkAdder] picks this path only below that
 * and uses network suggestions above it. `minSdk` is 21, so this is still reachable.
 */
@Suppress("DEPRECATION") // the whole WifiConfiguration API, deliberately, see above
internal class WifiConfigManager(private val wifiManager: WifiManager) :
    AsyncTask<WifiParsedResult, Any, Any?>() {

  override fun doInBackground(vararg args: WifiParsedResult): Any? {
    val theWifiResult = args[0]
    // Start WiFi, otherwise nothing will work
    if (!wifiManager.isWifiEnabled) {
      Log.i(TAG, "Enabling wi-fi...")
      if (wifiManager.setWifiEnabled(true)) {
        Log.i(TAG, "Wi-fi enabled")
      } else {
        Log.w(TAG, "Wi-fi could not be enabled!")
        return null
      }
      // This happens very quickly, but need to wait for it to enable. A little busy wait?
      var count = 0
      while (!wifiManager.isWifiEnabled) {
        if (count >= 10) {
          Log.i(TAG, "Took too long to enable wi-fi, quitting")
          return null
        }
        Log.i(TAG, "Still waiting for wi-fi to enable...")
        try {
          Thread.sleep(1000L)
        } catch (ie: InterruptedException) {
          // continue
        }
        count++
      }
    }
    val networkTypeString = theWifiResult.networkEncryption
    val networkType = try {
      NetworkType.forIntentValue(networkTypeString)
    } catch (ignored: IllegalArgumentException) {
      Log.w(TAG, "Bad network type")
      return null
    }
    if (networkType == NetworkType.NO_PASSWORD) {
      changeNetworkUnEncrypted(wifiManager, theWifiResult)
    } else {
      val password = theWifiResult.password
      if (!password.isNullOrEmpty()) {
        when (networkType) {
          NetworkType.WEP -> changeNetworkWEP(wifiManager, theWifiResult)
          NetworkType.WPA -> changeNetworkWPA(wifiManager, theWifiResult)
          NetworkType.WPA2_EAP -> changeNetworkWPA2EAP(wifiManager, theWifiResult)
          NetworkType.NO_PASSWORD -> {}
        }
      }
    }
    return null
  }

  private companion object {

    private val TAG = WifiConfigManager::class.java.simpleName

    private val HEX_DIGITS: Pattern = Pattern.compile("[0-9A-Fa-f]+")

    /**
     * Update the network: either create a new network or modify an existing network
     * @param config the new network configuration
     */
    private fun updateNetwork(wifiManager: WifiManager, config: WifiConfiguration) {
      val foundNetworkID = findNetworkInExistingConfig(wifiManager, config.SSID)
      if (foundNetworkID != null) {
        Log.i(TAG, "Removing old configuration for network " + config.SSID)
        wifiManager.removeNetwork(foundNetworkID)
        wifiManager.saveConfiguration()
      }
      val networkId = wifiManager.addNetwork(config)
      if (networkId >= 0) {
        // Try to disable the current network and start a new one.
        if (wifiManager.enableNetwork(networkId, true)) {
          Log.i(TAG, "Associating to network " + config.SSID)
          wifiManager.saveConfiguration()
        } else {
          Log.w(TAG, "Failed to enable network " + config.SSID)
        }
      } else {
        Log.w(TAG, "Unable to add network " + config.SSID)
      }
    }

    private fun changeNetworkCommon(wifiResult: WifiParsedResult): WifiConfiguration {
      val config = WifiConfiguration()
      config.allowedAuthAlgorithms.clear()
      config.allowedGroupCiphers.clear()
      config.allowedKeyManagement.clear()
      config.allowedPairwiseCiphers.clear()
      config.allowedProtocols.clear()
      // Android API insists that an ascii SSID must be quoted to be correctly handled.
      config.SSID = quoteNonHex(wifiResult.ssid)
      config.hiddenSSID = wifiResult.isHidden
      return config
    }

    // Adding a WEP network
    private fun changeNetworkWEP(wifiManager: WifiManager, wifiResult: WifiParsedResult) {
      val config = changeNetworkCommon(wifiResult)
      config.wepKeys[0] = quoteNonHex(wifiResult.password, 10, 26, 58)
      config.wepTxKeyIndex = 0
      config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
      config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
      config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
      config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
      config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
      config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
      updateNetwork(wifiManager, config)
    }

    // Adding a WPA or WPA2 network
    private fun changeNetworkWPA(wifiManager: WifiManager, wifiResult: WifiParsedResult) {
      val config = changeNetworkCommon(wifiResult)
      // Hex passwords that are 64 bits long are not to be quoted.
      config.preSharedKey = quoteNonHex(wifiResult.password, 64)
      config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
      config.allowedProtocols.set(WifiConfiguration.Protocol.WPA) // For WPA
      config.allowedProtocols.set(WifiConfiguration.Protocol.RSN) // For WPA2
      config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
      config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP)
      config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
      config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
      config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
      config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
      updateNetwork(wifiManager, config)
    }

    // Adding a WPA2 enterprise (EAP) network
    private fun changeNetworkWPA2EAP(wifiManager: WifiManager, wifiResult: WifiParsedResult) {
      val config = changeNetworkCommon(wifiResult)
      // Hex passwords that are 64 bits long are not to be quoted.
      config.preSharedKey = quoteNonHex(wifiResult.password, 64)
      config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
      config.allowedProtocols.set(WifiConfiguration.Protocol.RSN) // For WPA2
      config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP)
      config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
      config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
      config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
      config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
      config.enterpriseConfig.identity = wifiResult.identity
      config.enterpriseConfig.anonymousIdentity = wifiResult.anonymousIdentity
      config.enterpriseConfig.password = wifiResult.password
      config.enterpriseConfig.eapMethod = parseEap(wifiResult.eapMethod)
      config.enterpriseConfig.phase2Method = parsePhase2(wifiResult.phase2Method)
      updateNetwork(wifiManager, config)
    }

    // Adding an open, unsecured network
    private fun changeNetworkUnEncrypted(wifiManager: WifiManager, wifiResult: WifiParsedResult) {
      val config = changeNetworkCommon(wifiResult)
      config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
      updateNetwork(wifiManager, config)
    }

    private fun findNetworkInExistingConfig(wifiManager: WifiManager, ssid: String?): Int? {
      val existingConfigs: Iterable<WifiConfiguration>? = wifiManager.configuredNetworks
      if (existingConfigs != null) {
        for (existingConfig in existingConfigs) {
          val existingSSID = existingConfig.SSID
          if (existingSSID != null && existingSSID == ssid) {
            return existingConfig.networkId
          }
        }
      }
      return null
    }

    private fun quoteNonHex(value: String?, vararg allowedLengths: Int): String? =
        if (isHexOfLength(value, *allowedLengths)) value else convertToQuotedString(value)

    /**
     * Encloses the incoming string inside double quotes, if it isn't already quoted.
     * @param s the input string
     * @return a quoted string, of the form "input".  If the input string is null, it returns null
     * as well.
     */
    private fun convertToQuotedString(s: String?): String? {
      if (s.isNullOrEmpty()) {
        return null
      }
      // If already quoted, return as-is
      if (s[0] == '"' && s[s.length - 1] == '"') {
        return s
      }
      return "\"" + s + '"'
    }

    /**
     * @param value input to check
     * @param allowedLengths allowed lengths, if any
     * @return true if value is a non-null, non-empty string of hex digits, and if allowed lengths are given, has
     *  an allowed length
     */
    private fun isHexOfLength(value: CharSequence?, vararg allowedLengths: Int): Boolean {
      if (value == null || !HEX_DIGITS.matcher(value).matches()) {
        return false
      }
      if (allowedLengths.isEmpty()) {
        return true
      }
      for (length in allowedLengths) {
        if (value.length == length) {
          return true
        }
      }
      return false
    }

    private fun parseEap(eapString: String?): Int = WifiEapMethods.parseEap(eapString)

    private fun parsePhase2(phase2String: String?): Int =
        WifiEapMethods.parsePhase2(phase2String)
  }
}
