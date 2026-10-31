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

package io.github.kakakakacat.zxing.android.result

import android.app.Activity
import io.github.kakakakacat.zxing.Result
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.client.result.ISBNParsedResult
import io.github.kakakakacat.zxing.client.result.ParsedResult

/**
 * Handles books encoded by their ISBN values.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class ISBNResultHandler(activity: Activity, result: ParsedResult, rawResult: Result) :
    ResultHandler(activity, result, rawResult) {

  override fun getButtonCount(): Int = if (hasCustomProductSearch()) buttons.size else buttons.size - 1

  override fun getButtonText(index: Int): Int = buttons[index]

  override fun handleButtonPress(index: Int) {
    val isbnResult = getResult() as ISBNParsedResult
    when (index) {
      0 -> openProductSearch(isbnResult.getISBN())
      1 -> openBookSearch(isbnResult.getISBN())
      2 -> searchBookContents(isbnResult.getISBN())
      3 -> openURL(fillInCustomSearchURL(isbnResult.getISBN()))
    }
  }

  override fun getDisplayTitle(): Int = R.string.result_isbn

  private companion object {
    private val buttons = intArrayOf(
        R.string.button_product_search,
        R.string.button_book_search,
        R.string.button_search_book_contents,
        R.string.button_custom_product_search)
  }
}
