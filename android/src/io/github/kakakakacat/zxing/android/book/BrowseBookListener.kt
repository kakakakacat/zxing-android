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

package io.github.kakakakacat.zxing.android.book

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.AdapterView
import io.github.kakakakacat.zxing.android.Intents
import io.github.kakakakacat.zxing.android.LocaleManager

internal class BrowseBookListener(
    private val activity: SearchBookContentsActivity,
    private val items: List<SearchBookContentsResult>
) : AdapterView.OnItemClickListener {

  override fun onItemClick(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
    if (position < 1) {
      // Clicked header, ignore it
      return
    }
    val itemOffset = position - 1
    if (itemOffset >= items.size) {
      return
    }
    val pageId = items[itemOffset].getPageId()
    val query = SearchBookContentsResult.getQuery()
    val isbn = activity.getISBN()
    if (LocaleManager.isBookSearchUrl(isbn) && pageId.isNotEmpty()) {
      val equals = isbn.indexOf('=')
      val volumeId = isbn.substring(equals + 1)
      // Defect A-09: cleartext http, blocked from targetSdk 28 on. Carried forward.
      val readBookURI = "http://books.google." +
          LocaleManager.getBookSearchCountryTLD(activity) +
          "/books?id=" + volumeId + "&pg=" + pageId + "&vq=" + query
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(readBookURI))
      intent.addFlags(Intents.FLAG_NEW_DOC)
      activity.startActivity(intent)
    }
  }
}
