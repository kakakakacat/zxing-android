/*
 * Copyright (C) 2026 ZXing authors
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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.wifi.WifiEnterpriseConfig
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.AsyncTask
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.github.kakakakacat.zxing.client.result.WifiParsedResult

/**
 * Adds the network from a scanned `WIFI:` barcode, by whichever route the device allows.
 *
 * This was defect A-08. [WifiConfigManager] drives `WifiManager.addNetwork` and
 * `enableNetwork`, and since Android 10 those return failure for any app that is not the
 * system: scanning a Wi-Fi barcode logged "Unable to add network" and otherwise did
 * nothing at all, with a toast saying a connection had been requested.
 *
 * Three routes, because the platform changed twice:
 *
 *  - **Android 11+**: hand a [WifiNetworkSuggestion] to the settings app through
 *    [Settings.ACTION_WIFI_ADD_NETWORKS]. The system asks the user to confirm and then
 *    saves the network for real. No permission, and the user sees what is being added.
 *  - **Android 10**: no such Intent yet, so the suggestion is registered with
 *    `addNetworkSuggestions`. Weaker — the platform may connect to it later, on its own
 *    schedule, and the user gets a notification rather than a prompt — but it is the only
 *    route that exists on that version.
 *  - **Android 9 and below**: the old `WifiConfiguration` path, which still works there.
 *
 * WEP has no equivalent in the suggestion API at all, so a WEP barcode can only be added
 * on the old route. It is reported rather than silently dropped; WEP has been broken
 * cryptography for twenty years and no current Android will create such a network.
 */
internal object WifiNetworkAdder {

  private val TAG = WifiNetworkAdder::class.java.simpleName

  /** What happened, so the caller can tell the user something true. */
  enum class Outcome {
    /** The system is asking the user to confirm. Android 11+. */
    PROMPTING,
    /** Registered as a suggestion; the platform decides when to use it. Android 10. */
    SUGGESTED,
    /** Handed to the legacy WifiConfiguration path. Android 9 and below. */
    LEGACY,
    /** WEP on a platform whose suggestion API cannot express it. */
    UNSUPPORTED,
    /** The barcode did not describe a network this code understands. */
    BAD_BARCODE,
  }

  @Suppress("DEPRECATION") // AsyncTask, for the legacy path only
  fun add(activity: Activity, result: WifiParsedResult): Outcome {
    val networkType = try {
      NetworkType.forIntentValue(result.networkEncryption)
    } catch (ignored: IllegalArgumentException) {
      Log.w(TAG, "Bad network type: ${result.networkEncryption}")
      return Outcome.BAD_BARCODE
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      val wifiManager = activity.applicationContext
          .getSystemService(Activity.WIFI_SERVICE) as WifiManager?
          ?: return Outcome.BAD_BARCODE
      WifiConfigManager(wifiManager).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, result)
      return Outcome.LEGACY
    }

    if (networkType == NetworkType.WEP) {
      Log.w(TAG, "WEP cannot be expressed as a network suggestion")
      return Outcome.UNSUPPORTED
    }

    val suggestion = buildSuggestion(result, networkType) ?: return Outcome.BAD_BARCODE

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
          .putParcelableArrayListExtra(
              Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
      return try {
        activity.startActivity(intent)
        Outcome.PROMPTING
      } catch (anfe: ActivityNotFoundException) {
        // A device with no settings activity for this; fall back to the suggestion API,
        // which exists on every version that has this Intent.
        Log.w(TAG, "No activity for ACTION_WIFI_ADD_NETWORKS", anfe)
        suggest(activity, suggestion)
      }
    }

    return suggest(activity, suggestion)
  }

  private fun suggest(activity: Activity, suggestion: WifiNetworkSuggestion): Outcome {
    val wifiManager = activity.applicationContext
        .getSystemService(Activity.WIFI_SERVICE) as WifiManager?
        ?: return Outcome.BAD_BARCODE
    val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
    if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS &&
        status != WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE) {
      Log.w(TAG, "addNetworkSuggestions returned $status")
      return Outcome.BAD_BARCODE
    }
    return Outcome.SUGGESTED
  }

  private fun buildSuggestion(result: WifiParsedResult,
                              networkType: NetworkType): WifiNetworkSuggestion? {
    val ssid = result.ssid
    if (ssid.isNullOrEmpty()) {
      Log.w(TAG, "No SSID in the barcode")
      return null
    }
    // Unquoted here, unlike the WifiConfiguration path: the suggestion builder takes the
    // SSID as text and quotes it itself.
    val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
    builder.setIsHiddenSsid(result.isHidden)

    val password = result.password
    when (networkType) {
      NetworkType.NO_PASSWORD -> {}
      NetworkType.WPA -> {
        if (password.isNullOrEmpty()) {
          Log.w(TAG, "No password for a WPA network")
          return null
        }
        builder.setWpa2Passphrase(password)
      }
      NetworkType.WPA2_EAP -> {
        val enterpriseConfig = WifiEnterpriseConfig().apply {
          identity = result.identity
          anonymousIdentity = result.anonymousIdentity
          setPassword(password)
          eapMethod = WifiEapMethods.parseEap(result.eapMethod)
          phase2Method = WifiEapMethods.parsePhase2(result.phase2Method)
        }
        builder.setWpa2EnterpriseConfig(enterpriseConfig)
      }
      NetworkType.WEP -> return null // handled before this is reached
    }

    return try {
      builder.build()
    } catch (iae: IllegalArgumentException) {
      // The builder validates passphrase length and SSID encoding, and throws rather than
      // returning null.
      Log.w(TAG, "Could not build a suggestion for this barcode", iae)
      null
    }
  }
}
