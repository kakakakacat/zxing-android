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

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kakakakacat.zxing.android.R
import java.util.Locale

/**
 * A list item which displays the page number and snippet of this search result.
 *
 * The layout inflater builds this by name from `search_book_contents_list_item.xml`, so
 * the class name and the (Context, AttributeSet) constructor are part of its contract.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class SearchBookContentsListItem : LinearLayout {

  private lateinit var pageNumberView: TextView
  private lateinit var snippetView: TextView

  internal constructor(context: Context) : super(context)

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

  override fun onFinishInflate() {
    super.onFinishInflate()
    pageNumberView = findViewById(R.id.page_number_view)
    snippetView = findViewById(R.id.snippet_view)
  }

  internal fun set(result: SearchBookContentsResult?) {
    pageNumberView.text = result!!.getPageNumber()
    val snippet = result.getSnippet()
    if (snippet.isEmpty()) {
      snippetView.text = ""
    } else {
      if (result.getValidSnippet()) {
        val lowerQuery = SearchBookContentsResult.getQuery().orEmpty().lowercase(Locale.getDefault())
        val lowerSnippet = snippet.lowercase(Locale.getDefault())
        val styledSnippet: Spannable = SpannableString(snippet)
        val boldSpan = StyleSpan(Typeface.BOLD)
        val queryLength = lowerQuery.length
        var offset = 0
        while (true) {
          val pos = lowerSnippet.indexOf(lowerQuery, offset)
          if (pos < 0) {
            break
          }
          styledSnippet.setSpan(boldSpan, pos, pos + queryLength, 0)
          offset = pos + queryLength
        }
        snippetView.text = styledSnippet
      } else {
        // This may be an error message, so don't try to bold the query terms within it
        snippetView.text = snippet
      }
    }
  }
}
