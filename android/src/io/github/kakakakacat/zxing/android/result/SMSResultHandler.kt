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
import io.github.kakakakacat.zxing.client.result.SMSParsedResult

/**
 * Handles SMS addresses, offering a choice of composing a new SMS or MMS message.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class SMSResultHandler(activity: Activity, result: ParsedResult) :
    ResultHandler(activity, result) {

  override fun getButtonCount(): Int = buttons.size

  override fun getButtonText(index: Int): Int = buttons[index]

  override fun handleButtonPress(index: Int) {
    val smsResult = getResult() as SMSParsedResult
    val number = smsResult.numbers[0]
    when (index) {
      // Don't know of a way yet to express a SENDTO intent with multiple recipients
      0 -> sendSMS(number, smsResult.body)
      1 -> sendMMS(number, smsResult.subject, smsResult.body)
    }
  }

  override fun getDisplayContents(): CharSequence {
    val smsResult = getResult() as SMSParsedResult
    val rawNumbers = smsResult.numbers
    val formattedNumbers = arrayOfNulls<String>(rawNumbers.size)
    for (i in rawNumbers.indices) {
      formattedNumbers[i] = formatPhone(rawNumbers[i])
    }
    val contents = StringBuilder(50)
    ParsedResult.maybeAppend(formattedNumbers, contents)
    ParsedResult.maybeAppend(smsResult.subject, contents)
    ParsedResult.maybeAppend(smsResult.body, contents)
    return contents.toString()
  }

  override fun getDisplayTitle(): Int = R.string.result_sms

  private companion object {
    private val buttons = intArrayOf(
        R.string.button_sms,
        R.string.button_mms)
  }
}
