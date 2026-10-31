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
import io.github.kakakakacat.zxing.client.result.ParsedResult
import io.github.kakakakacat.zxing.client.result.TelParsedResult

/**
 * Offers relevant actions for telephone numbers.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class TelResultHandler(activity: Activity, result: ParsedResult) :
    ResultHandler(activity, result) {

  override fun getButtonCount(): Int = buttons.size

  override fun getButtonText(index: Int): Int = buttons[index]

  override fun handleButtonPress(index: Int) {
    val telResult = getResult() as TelParsedResult
    when (index) {
      0 -> {
        dialPhoneFromUri(telResult.telURI)
        // When dialer comes up, it allows underlying display activity to continue or something,
        // but app can't get camera in this state. Avoid issues by just quitting, only in the
        // case of a phone number
        activity.finish()
      }
      1 -> addPhoneOnlyContact(arrayOf(telResult.number), null)
    }
  }

  // Overriden so we can take advantage of Android's phone number hyphenation routines.
  override fun getDisplayContents(): CharSequence {
    val contents = getResult().displayResult.replace("\r", "")
    return formatPhone(contents) ?: contents
  }

  override fun getDisplayTitle(): Int = R.string.result_tel

  private companion object {
    private val buttons = intArrayOf(
        R.string.button_dial,
        R.string.button_add_contact)
  }
}
