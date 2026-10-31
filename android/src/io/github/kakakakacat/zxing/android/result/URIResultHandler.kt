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
import io.github.kakakakacat.zxing.android.LocaleManager
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.client.result.ParsedResult
import io.github.kakakakacat.zxing.client.result.URIParsedResult
import java.util.Locale

/**
 * Offers appropriate actions for URLS.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class URIResultHandler(activity: Activity, result: ParsedResult) :
    ResultHandler(activity, result) {

  override fun getButtonCount(): Int =
      if (LocaleManager.isBookSearchUrl((getResult() as URIParsedResult).getURI())) {
        buttons.size
      } else {
        buttons.size - 1
      }

  override fun getButtonText(index: Int): Int = buttons[index]

  override fun getDefaultButtonID(): Int? = 0

  override fun handleButtonPress(index: Int) {
    val uri = (getResult() as URIParsedResult).getURI()
    when (index) {
      0 -> openURL(uri)
      1 -> shareByEmail(uri)
      2 -> shareBySMS(uri)
      3 -> searchBookContents(uri)
    }
  }

  override fun getDisplayTitle(): Int = R.string.result_uri

  override fun areContentsSecure(): Boolean {
    val uri = (getResult() as URIParsedResult).getURI().lowercase(Locale.ENGLISH)
    return SECURE_PROTOCOLS.any { uri.startsWith(it) }
  }

  private companion object {

    // URIs beginning with entries in this array will not be saved to history or copied to the
    // clipboard for security.
    private val SECURE_PROTOCOLS = arrayOf("otpauth:")

    private val buttons = intArrayOf(
        R.string.button_open_browser,
        R.string.button_share_by_email,
        R.string.button_share_by_sms,
        R.string.button_search_book_contents)
  }
}
