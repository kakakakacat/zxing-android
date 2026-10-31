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

package io.github.kakakakacat.zxing.android.history

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kakakakacat.zxing.android.R

internal class HistoryItemAdapter(private val activity: Context) :
    ArrayAdapter<HistoryItem>(activity, R.layout.history_list_item, ArrayList()) {

  override fun getView(position: Int, view: View?, viewGroup: ViewGroup): View {
    val layout = if (view is LinearLayout) {
      view
    } else {
      LayoutInflater.from(activity).inflate(R.layout.history_list_item, viewGroup, false)
    }

    val item = getItem(position)
    val result = item?.getResult()

    val title: CharSequence
    val detail: CharSequence
    if (result != null) {
      title = result.text
      detail = item.getDisplayAndDetails()
    } else {
      val resources = context.resources
      title = resources.getString(R.string.history_empty)
      detail = resources.getString(R.string.history_empty_detail)
    }

    layout.findViewById<TextView>(R.id.history_title).text = title
    layout.findViewById<TextView>(R.id.history_detail).text = detail

    return layout
  }
}
