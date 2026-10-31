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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import io.github.kakakakacat.zxing.android.R

/**
 * Manufactures list items which represent SBC results.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
internal class SearchBookContentsAdapter(context: Context, items: List<SearchBookContentsResult>) :
    ArrayAdapter<SearchBookContentsResult>(context, R.layout.search_book_contents_list_item, 0, items) {

  override fun getView(position: Int, view: View?, viewGroup: ViewGroup): View {
    val listItem: SearchBookContentsListItem
    if (view == null) {
      listItem = LayoutInflater.from(context).inflate(
          R.layout.search_book_contents_list_item, viewGroup, false) as SearchBookContentsListItem
    } else {
      if (view is SearchBookContentsListItem) {
        listItem = view
      } else {
        return view
      }
    }

    listItem.set(getItem(position))
    return listItem
  }
}
