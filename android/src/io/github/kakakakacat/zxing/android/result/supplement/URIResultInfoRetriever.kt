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
import android.widget.TextView
import io.github.kakakakacat.zxing.android.HttpHelper
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.android.history.HistoryManager
import io.github.kakakakacat.zxing.client.result.URIParsedResult
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

internal class URIResultInfoRetriever(
    textView: TextView,
    private val result: URIParsedResult,
    historyManager: HistoryManager?,
    context: Context
) : SupplementalInfoRetriever(textView, historyManager) {

  private val redirectString: String = context.getString(R.string.msg_redirect)

  // Same as TitleRetriever: a scanned http:// shortener is not unwrapped, because
  // cleartext stays off. See the note there.
  @Throws(IOException::class)
  override fun retrieveSupplementalInfo() {
    var oldURI = try {
      URI(result.getURI())
    } catch (ignored: URISyntaxException) {
      return
    }
    var newURI = HttpHelper.unredirect(oldURI)
    var count = 0
    while (count++ < MAX_REDIRECTS && oldURI != newURI) {
      append(result.displayResult,
             null,
             arrayOf("$redirectString : $newURI"),
             newURI.toString())
      oldURI = newURI
      newURI = HttpHelper.unredirect(newURI)
    }
  }

  private companion object {
    private const val MAX_REDIRECTS = 5
  }
}
