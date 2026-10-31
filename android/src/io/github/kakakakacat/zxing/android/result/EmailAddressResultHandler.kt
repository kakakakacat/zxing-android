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
import io.github.kakakakacat.zxing.client.result.EmailAddressParsedResult
import io.github.kakakakacat.zxing.client.result.ParsedResult

/**
 * Handles email addresses.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class EmailAddressResultHandler(activity: Activity, result: ParsedResult) :
    ResultHandler(activity, result) {

  override fun getButtonCount(): Int = buttons.size

  override fun getButtonText(index: Int): Int = buttons[index]

  override fun handleButtonPress(index: Int) {
    val emailResult = getResult() as EmailAddressParsedResult
    when (index) {
      0 -> sendEmail(emailResult.tos,
                     emailResult.getCCs(),
                     emailResult.getBCCs(),
                     emailResult.subject,
                     emailResult.body)
      1 -> addEmailOnlyContact(emailResult.tos, null)
    }
  }

  override fun getDisplayTitle(): Int = R.string.result_email_address

  private companion object {
    private val buttons = intArrayOf(
        R.string.button_email,
        R.string.button_add_contact)
  }
}
