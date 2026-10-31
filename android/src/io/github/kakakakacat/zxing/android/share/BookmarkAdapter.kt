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

package io.github.kakakakacat.zxing.android.share

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kakakakacat.zxing.android.R

/**
 * A custom adapter designed to fetch bookmarks from a cursor. Before Honeycomb we used
 * SimpleCursorAdapter, but it assumes the existence of an _id column, and the bookmark schema was
 * rewritten for HC without one. This caused the app to crash, hence this new class, which is
 * forwards and backwards compatible.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
internal class BookmarkAdapter(
    private val context: Context,
    private val titleURLs: List<Array<String>>
) : BaseAdapter() {

  override fun getCount(): Int = titleURLs.size

  override fun getItem(index: Int): Any = titleURLs[index]

  override fun getItemId(index: Int): Long = index.toLong()

  override fun getView(index: Int, view: View?, viewGroup: ViewGroup): View {
    val layout = if (view is LinearLayout) {
      view
    } else {
      LayoutInflater.from(context).inflate(R.layout.bookmark_picker_list_item, viewGroup, false)
    }
    val titleURL = titleURLs[index]
    layout.findViewById<TextView>(R.id.bookmark_title).text = titleURL[0]
    layout.findViewById<TextView>(R.id.bookmark_url).text = titleURL[1]
    return layout
  }
}
