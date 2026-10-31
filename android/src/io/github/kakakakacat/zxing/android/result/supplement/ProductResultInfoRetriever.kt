/*
 * Copyright (C) 2010 ZXing authors
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

package io.github.kakakakacat.zxing.android.result.supplement

import android.content.Context
import android.text.Html
import android.widget.TextView
import io.github.kakakakacat.zxing.android.HttpHelper
import io.github.kakakakacat.zxing.android.LocaleManager
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.android.history.HistoryManager
import java.io.IOException
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Retrieves product information from Google Product search.
 *
 * **Please do not reuse this code.** Using results in this way requires permission
 * from Google, and that is not granted to users via this project.
 *
 * @author Sean Owen
 */
internal class ProductResultInfoRetriever(
    textView: TextView,
    private val productID: String,
    historyManager: HistoryManager?,
    private val context: Context
) : SupplementalInfoRetriever(textView, historyManager) {

  private val source: String = context.getString(R.string.msg_google_product)

  @Throws(IOException::class)
  override fun retrieveSupplementalInfo() {

    val encodedProductID = URLEncoder.encode(productID, "UTF-8")
    val uri = "https://www.google." + LocaleManager.getProductSearchCountryTLD(context) +
        "/m/products?ie=utf8&oe=utf8&scoring=p&source=zxing&q=" + encodedProductID
    val content = HttpHelper.downloadViaHttp(uri, HttpHelper.ContentType.HTML)

    for (p in PRODUCT_NAME_PRICE_PATTERNS) {
      val matcher = p.matcher(content)
      if (matcher.find()) {
        append(productID,
               source,
               arrayOf(unescapeHTML(matcher.group(1)), unescapeHTML(matcher.group(2))),
               uri)
        break
      }
    }
  }

  private companion object {

    private val PRODUCT_NAME_PRICE_PATTERNS = arrayOf(
        Pattern.compile(",event\\)\">([^<]+)</a></h3>.+<span class=psrp>([^<]+)</span>"),
        Pattern.compile("owb63p\">([^<]+).+zdi3pb\">([^<]+)"))

    @Suppress("DEPRECATION") // Html.fromHtml(String)
    private fun unescapeHTML(raw: String?): String = Html.fromHtml(raw).toString()
  }
}
