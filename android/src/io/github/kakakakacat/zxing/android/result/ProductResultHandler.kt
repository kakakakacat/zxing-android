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
import io.github.kakakakacat.zxing.client.result.ExpandedProductParsedResult
import io.github.kakakakacat.zxing.client.result.ParsedResult
import io.github.kakakakacat.zxing.client.result.ProductParsedResult

/**
 * Handles generic products which are not books.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class ProductResultHandler(activity: Activity, result: ParsedResult, rawResult: Result) :
    ResultHandler(activity, result, rawResult) {

  override fun getButtonCount(): Int = if (hasCustomProductSearch()) buttons.size else buttons.size - 1

  override fun getButtonText(index: Int): Int = buttons[index]

  override fun handleButtonPress(index: Int) {
    val productID = getProductIDFromResult(getResult())
    when (index) {
      0 -> openProductSearch(productID)
      1 -> webSearch(productID)
      2 -> openURL(fillInCustomSearchURL(productID))
    }
  }

  override fun getDisplayTitle(): Int = R.string.result_product

  private companion object {

    private val buttons = intArrayOf(
        R.string.button_product_search,
        R.string.button_web_search,
        R.string.button_custom_product_search)

    private fun getProductIDFromResult(rawResult: ParsedResult): String = when (rawResult) {
      is ProductParsedResult -> rawResult.normalizedProductID
      is ExpandedProductParsedResult -> rawResult.rawText
      else -> throw IllegalArgumentException(rawResult.javaClass.toString())
    }
  }
}
