/*
 * Copyright 2012 ZXing authors
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

import android.text.Html
import android.widget.TextView
import io.github.kakakakacat.zxing.android.HttpHelper
import io.github.kakakakacat.zxing.android.history.HistoryManager
import io.github.kakakakacat.zxing.client.result.URIParsedResult
import java.io.IOException
import java.util.regex.Pattern

/**
 * Retrieves the title of a web page as supplemental info.
 *
 * @author Sean Owen
 */
internal class TitleRetriever(
    textView: TextView,
    result: URIParsedResult,
    historyManager: HistoryManager?
) : SupplementalInfoRetriever(textView, historyManager) {

  private val httpUrl: String = result.getURI()

  @Suppress("DEPRECATION") // Html.fromHtml(String)
  override fun retrieveSupplementalInfo() {
    val contents = try {
      HttpHelper.downloadViaHttp(httpUrl, HttpHelper.ContentType.HTML, 4096)
    } catch (ioe: IOException) {
      // ignore this
      return
    }
    if (contents != null && contents.isNotEmpty()) {
      val m = TITLE_PATTERN.matcher(contents)
      if (m.find()) {
        var title = m.group(1)
        if (!title.isNullOrEmpty()) {
          title = Html.fromHtml(title).toString()
          if (title.length > MAX_TITLE_LEN) {
            title = title.substring(0, MAX_TITLE_LEN) + "..."
          }
          append(httpUrl, null, arrayOf(title), httpUrl)
        }
      }
    }
  }

  private companion object {
    private val TITLE_PATTERN: Pattern = Pattern.compile("<title>([^<]+)")
    private const val MAX_TITLE_LEN = 100
  }
}
