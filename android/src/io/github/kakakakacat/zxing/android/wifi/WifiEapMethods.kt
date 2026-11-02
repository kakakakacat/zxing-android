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

import android.net.wifi.WifiEnterpriseConfig

/**
 * The `WIFI:` barcode's EAP field names, mapped onto the platform's constants.
 *
 * Shared by the two routes a WPA2-EAP network can take onto a device — the legacy
 * [WifiConfigManager] and the network suggestion in [WifiNetworkAdder] — so the mapping
 * exists once rather than once per route.
 */
internal object WifiEapMethods {

  fun parseEap(eapString: String?): Int = when (eapString) {
    null, "NONE" -> WifiEnterpriseConfig.Eap.NONE
    "PEAP" -> WifiEnterpriseConfig.Eap.PEAP
    "PWD" -> WifiEnterpriseConfig.Eap.PWD
    "TLS" -> WifiEnterpriseConfig.Eap.TLS
    "TTLS" -> WifiEnterpriseConfig.Eap.TTLS
    else -> throw IllegalArgumentException("Unknown value for EAP method: $eapString")
  }

  fun parsePhase2(phase2String: String?): Int = when (phase2String) {
    null, "NONE" -> WifiEnterpriseConfig.Phase2.NONE
    "GTC" -> WifiEnterpriseConfig.Phase2.GTC
    "MSCHAP" -> WifiEnterpriseConfig.Phase2.MSCHAP
    "MSCHAPV2" -> WifiEnterpriseConfig.Phase2.MSCHAPV2
    "PAP" -> WifiEnterpriseConfig.Phase2.PAP
    else -> throw IllegalArgumentException("Unknown value for phase 2 method: $phase2String")
  }
}
