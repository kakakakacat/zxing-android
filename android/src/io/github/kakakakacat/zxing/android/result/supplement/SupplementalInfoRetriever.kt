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
import android.os.AsyncTask
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.util.Log
import android.widget.TextView
import io.github.kakakakacat.zxing.android.history.HistoryManager
import io.github.kakakakacat.zxing.client.result.ISBNParsedResult
import io.github.kakakakacat.zxing.client.result.ParsedResult
import io.github.kakakakacat.zxing.client.result.ProductParsedResult
import io.github.kakakakacat.zxing.client.result.URIParsedResult
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.RejectedExecutionException

/**
 * Superclass of implementations which can asynchronously retrieve more information
 * about a barcode scan.
 */
@Suppress("DEPRECATION") // AsyncTask
abstract class SupplementalInfoRetriever internal constructor(
    textView: TextView,
    historyManager: HistoryManager?
) : AsyncTask<Any, Any, Any?>() {

  private val textViewRef = WeakReference(textView)
  private val historyManagerRef = WeakReference(historyManager)
  private val newContents: MutableCollection<Spannable> = ArrayList()
  private val newHistories: MutableCollection<Array<String>> = ArrayList()

  final override fun doInBackground(vararg args: Any): Any? {
    try {
      retrieveSupplementalInfo()
    } catch (e: IOException) {
      Log.w(TAG, e)
    }
    return null
  }

  final override fun onPostExecute(arg: Any?) {
    val textView = textViewRef.get()
    if (textView != null) {
      for (content in newContents) {
        textView.append(content)
      }
      textView.movementMethod = LinkMovementMethod.getInstance()
    }
    val historyManager = historyManagerRef.get()
    if (historyManager != null) {
      for (text in newHistories) {
        historyManager.addHistoryItemDetails(text[0], text[1])
      }
    }
  }

  @Throws(IOException::class)
  internal abstract fun retrieveSupplementalInfo()

  internal fun append(itemID: String, source: String?, newTexts: Array<String>, linkURL: String?) {

    val newTextCombined = StringBuilder()

    if (source != null) {
      newTextCombined.append(source).append(' ')
    }

    val linkStart = newTextCombined.length

    var first = true
    for (newText in newTexts) {
      if (first) {
        newTextCombined.append(newText)
        first = false
      } else {
        newTextCombined.append(" [")
        newTextCombined.append(newText)
        newTextCombined.append(']')
      }
    }

    val linkEnd = newTextCombined.length

    val newText = newTextCombined.toString()
    val content: Spannable = SpannableString(newText + "\n\n")
    if (linkURL != null) {
      // Strangely, some Android browsers don't seem to register to handle HTTP:// or HTTPS://.
      // Lower-case these as it should always be OK to lower-case these schemes.
      val lowerCased = when {
        linkURL.startsWith("HTTP://") -> "http" + linkURL.substring(4)
        linkURL.startsWith("HTTPS://") -> "https" + linkURL.substring(5)
        else -> linkURL
      }
      content.setSpan(URLSpan(lowerCased), linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    newContents.add(content)
    newHistories.add(arrayOf(itemID, newText))
  }

  companion object {

    private const val TAG = "SupplementalInfo"

    @JvmStatic
    fun maybeInvokeRetrieval(
        textView: TextView,
        result: ParsedResult,
        historyManager: HistoryManager?,
        context: Context
    ) {
      try {
        when (result) {
          is URIParsedResult -> {
            URIResultInfoRetriever(textView, result, historyManager, context)
                .executeOnExecutor(THREAD_POOL_EXECUTOR)
            TitleRetriever(textView, result, historyManager)
                .executeOnExecutor(THREAD_POOL_EXECUTOR)
          }
          is ProductParsedResult -> {
            ProductResultInfoRetriever(textView, result.productID, historyManager, context)
                .executeOnExecutor(THREAD_POOL_EXECUTOR)
          }
          is ISBNParsedResult -> {
            val isbn = result.getISBN()
            ProductResultInfoRetriever(textView, isbn, historyManager, context)
                .executeOnExecutor(THREAD_POOL_EXECUTOR)
            BookResultInfoRetriever(textView, isbn, historyManager, context)
                .executeOnExecutor(THREAD_POOL_EXECUTOR)
          }
        }
      } catch (ree: RejectedExecutionException) {
        // do nothing
      }
    }

    internal fun maybeAddText(text: String?, texts: MutableCollection<String>) {
      if (!text.isNullOrEmpty()) {
        texts.add(text)
      }
    }

    internal fun maybeAddTextSeries(textSeries: Collection<String>?, texts: MutableCollection<String>) {
      if (!textSeries.isNullOrEmpty()) {
        texts.add(textSeries.joinToString(", "))
      }
    }
  }
}
