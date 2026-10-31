/*
 * Copyright 2011 ZXing authors
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
import android.widget.TextView
import io.github.kakakakacat.zxing.android.HttpHelper
import io.github.kakakakacat.zxing.android.LocaleManager
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.android.history.HistoryManager
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException

/**
 * @author Kamil Kaczmarczyk
 * @author Sean Owen
 */
internal class BookResultInfoRetriever(
    textView: TextView,
    private val isbn: String,
    historyManager: HistoryManager?,
    private val context: Context
) : SupplementalInfoRetriever(textView, historyManager) {

  private val source: String = context.getString(R.string.msg_google_books)

  @Throws(IOException::class)
  override fun retrieveSupplementalInfo() {

    val contents = HttpHelper.downloadViaHttp(
        "https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn",
        HttpHelper.ContentType.JSON)

    if (contents.isEmpty()) {
      return
    }

    val title: String?
    val pages: String?
    var authors: Collection<String>? = null

    try {

      val topLevel = JSONTokener(contents.toString()).nextValue() as JSONObject
      val items = topLevel.optJSONArray("items")
      if (items == null || items.isNull(0)) {
        return
      }

      val volumeInfo = (items.get(0) as JSONObject).getJSONObject("volumeInfo")

      title = volumeInfo.optString("title")
      pages = volumeInfo.optString("pageCount")

      val authorsArray = volumeInfo.optJSONArray("authors")
      if (authorsArray != null && !authorsArray.isNull(0)) {
        authors = ArrayList<String>(authorsArray.length()).apply {
          for (i in 0 until authorsArray.length()) {
            add(authorsArray.getString(i))
          }
        }
      }

    } catch (e: JSONException) {
      throw IOException(e)
    }

    val newTexts: MutableCollection<String> = ArrayList()
    maybeAddText(title, newTexts)
    maybeAddTextSeries(authors, newTexts)
    maybeAddText(if (pages.isNullOrEmpty()) null else pages + "pp.", newTexts)

    val baseBookUri = "http://www.google." + LocaleManager.getBookSearchCountryTLD(context) +
        "/search?tbm=bks&source=zxing&q="

    append(isbn, source, newTexts.toTypedArray(), baseBookUri + isbn)
  }
}
