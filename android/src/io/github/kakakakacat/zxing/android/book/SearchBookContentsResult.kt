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

/**
 * The underlying data for a SBC result.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
internal class SearchBookContentsResult(
    private val pageId: String,
    private val pageNumber: String,
    private val snippet: String,
    private val validSnippet: Boolean
) {

  fun getPageId(): String = pageId

  fun getPageNumber(): String = pageNumber

  fun getSnippet(): String = snippet

  fun getValidSnippet(): Boolean = validSnippet

  companion object {

    private var query: String? = null

    @JvmStatic
    fun setQuery(query: String?) {
      Companion.query = query
    }

    @JvmStatic
    fun getQuery(): String? = query
  }
}
