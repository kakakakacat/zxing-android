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

import android.app.Activity
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.client.result.GeoParsedResult
import io.github.kakakakacat.zxing.client.result.ParsedResult

/**
 * Handles geographic coordinates (typically encoded as geo: URLs).
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class GeoResultHandler(activity: Activity, result: ParsedResult) :
    ResultHandler(activity, result) {

  override fun getButtonCount(): Int = buttons.size

  override fun getButtonText(index: Int): Int = buttons[index]

  override fun handleButtonPress(index: Int) {
    val geoResult = getResult() as GeoParsedResult
    when (index) {
      0 -> openMap(geoResult.geoURI)
      1 -> getDirections(geoResult.latitude, geoResult.longitude)
    }
  }

  override fun getDisplayTitle(): Int = R.string.result_geo

  private companion object {
    private val buttons = intArrayOf(
        R.string.button_show_map,
        R.string.button_get_directions)
  }
}
