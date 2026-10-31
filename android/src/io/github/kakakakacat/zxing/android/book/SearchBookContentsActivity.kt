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

import android.app.Activity
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import io.github.kakakakacat.zxing.android.HttpHelper
import io.github.kakakakacat.zxing.android.Intents
import io.github.kakakakacat.zxing.android.LocaleManager
import io.github.kakakakacat.zxing.android.R
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.regex.Pattern

/**
 * Uses Google Book Search to find a word or phrase in the requested book.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
@Suppress("DEPRECATION") // AsyncTask
class SearchBookContentsActivity : Activity() {

  private lateinit var isbn: String
  private lateinit var queryTextView: EditText
  private lateinit var queryButton: View
  private lateinit var resultListView: ListView
  private lateinit var headerView: TextView
  private var networkTask: AsyncTask<String, *, *>? = null

  private val buttonListener = View.OnClickListener { launchSearch() }

  private val keyListener = View.OnKeyListener { _, keyCode, event ->
    if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
      launchSearch()
      true
    } else {
      false
    }
  }

  internal fun getISBN(): String = isbn

  override fun onCreate(icicle: Bundle?) {
    super.onCreate(icicle)

    val intent = getIntent()
    if (intent == null || Intents.SearchBookContents.ACTION != intent.action) {
      finish()
      return
    }

    val isbn = intent.getStringExtra(Intents.SearchBookContents.ISBN)
    if (isbn == null) {
      finish()
      return
    }
    this.isbn = isbn

    title = if (LocaleManager.isBookSearchUrl(isbn)) {
      getString(R.string.sbc_name)
    } else {
      getString(R.string.sbc_name) + ": ISBN " + isbn
    }

    setContentView(R.layout.search_book_contents)
    queryTextView = findViewById(R.id.query_text_view)

    val initialQuery = intent.getStringExtra(Intents.SearchBookContents.QUERY)
    if (!initialQuery.isNullOrEmpty()) {
      // Populate the search box but don't trigger the search
      queryTextView.setText(initialQuery)
    }
    queryTextView.setOnKeyListener(keyListener)

    queryButton = findViewById(R.id.query_button)
    queryButton.setOnClickListener(buttonListener)

    resultListView = findViewById(R.id.result_list_view)
    headerView = LayoutInflater.from(this).inflate(
        R.layout.search_book_contents_header, resultListView, false) as TextView
    resultListView.addHeaderView(headerView)
  }

  override fun onResume() {
    super.onResume()
    queryTextView.selectAll()
  }

  override fun onPause() {
    val oldTask = networkTask
    if (oldTask != null) {
      oldTask.cancel(true)
      networkTask = null
    }
    super.onPause()
  }

  private fun launchSearch() {
    val query = queryTextView.text.toString()
    if (query.isNotEmpty()) {
      networkTask?.cancel(true)
      val task = NetworkTask()
      networkTask = task
      task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, query, isbn)
      headerView.setText(R.string.msg_sbc_searching_book)
      resultListView.adapter = null
      queryTextView.isEnabled = false
      queryButton.isEnabled = false
    }
  }

  private inner class NetworkTask : AsyncTask<String, Any, JSONObject?>() {

    override fun doInBackground(vararg args: String): JSONObject? {
      return try {
        // These return a JSON result which describes if and where the query was found. This API may
        // break or disappear at any time in the future. Since this is an API call rather than a
        // website, we don't use LocaleManager to change the TLD.
        val theQuery = args[0]
        val theIsbn = args[1]
        // Defect A-09: cleartext http, blocked from targetSdk 28 on. Carried forward.
        val uri = if (LocaleManager.isBookSearchUrl(theIsbn)) {
          val equals = theIsbn.indexOf('=')
          val volumeId = theIsbn.substring(equals + 1)
          "http://www.google.com/books?id=$volumeId&jscmd=SearchWithinVolume2&q=$theQuery"
        } else {
          "http://www.google.com/books?vid=isbn$theIsbn&jscmd=SearchWithinVolume2&q=$theQuery"
        }
        val content = HttpHelper.downloadViaHttp(uri, HttpHelper.ContentType.JSON)
        JSONObject(content.toString())
      } catch (ioe: IOException) {
        Log.w(TAG, "Error accessing book search", ioe)
        null
      } catch (je: JSONException) {
        Log.w(TAG, "Error accessing book search", je)
        null
      }
    }

    override fun onPostExecute(result: JSONObject?) {
      if (result == null) {
        headerView.setText(R.string.msg_sbc_failed)
      } else {
        handleSearchResults(result)
      }
      queryTextView.isEnabled = true
      queryTextView.selectAll()
      queryButton.isEnabled = true
    }

    // Currently there is no way to distinguish between a query which had no results and a book
    // which is not searchable - both return zero results.
    private fun handleSearchResults(json: JSONObject) {
      try {
        val count = json.getInt("number_of_results")
        headerView.text = getString(R.string.msg_sbc_results) + " : " + count
        if (count > 0) {
          val results = json.getJSONArray("search_results")
          SearchBookContentsResult.setQuery(queryTextView.text.toString())
          val items = ArrayList<SearchBookContentsResult>(count)
          for (x in 0 until count) {
            items.add(parseResult(results.getJSONObject(x)))
          }
          resultListView.onItemClickListener =
              BrowseBookListener(this@SearchBookContentsActivity, items)
          resultListView.adapter =
              SearchBookContentsAdapter(this@SearchBookContentsActivity, items)
        } else {
          val searchable = json.optString("searchable")
          if ("false" == searchable) {
            headerView.setText(R.string.msg_sbc_book_not_searchable)
          }
          resultListView.adapter = null
        }
      } catch (e: JSONException) {
        Log.w(TAG, "Bad JSON from book search", e)
        resultListView.adapter = null
        headerView.setText(R.string.msg_sbc_failed)
      }
    }

    // Available fields: page_id, page_number, snippet_text
    private fun parseResult(json: JSONObject): SearchBookContentsResult {

      val pageId: String
      var pageNumber: String?
      var snippet: String?
      try {
        pageId = json.getString("page_id")
        pageNumber = json.optString("page_number")
        snippet = json.optString("snippet_text")
      } catch (e: JSONException) {
        Log.w(TAG, e)
        // Never seen in the wild, just being complete.
        return SearchBookContentsResult(getString(R.string.msg_sbc_no_page_returned), "", "", false)
      }

      pageNumber = if (pageNumber.isNullOrEmpty()) {
        // This can happen for text on the jacket, and possibly other reasons.
        ""
      } else {
        getString(R.string.msg_sbc_page) + ' ' + pageNumber
      }

      val valid = !snippet.isNullOrEmpty()
      snippet = if (valid) {
        // Remove all HTML tags and encoded characters.
        var text = TAG_PATTERN.matcher(snippet).replaceAll("")
        text = LT_ENTITY_PATTERN.matcher(text).replaceAll("<")
        text = GT_ENTITY_PATTERN.matcher(text).replaceAll(">")
        text = QUOTE_ENTITY_PATTERN.matcher(text).replaceAll("'")
        QUOT_ENTITY_PATTERN.matcher(text).replaceAll("\"")
      } else {
        '(' + getString(R.string.msg_sbc_snippet_unavailable) + ')'
      }

      return SearchBookContentsResult(pageId, pageNumber, snippet, valid)
    }
  }

  private companion object {

    private val TAG = SearchBookContentsActivity::class.java.simpleName

    private val TAG_PATTERN: Pattern = Pattern.compile("<.*?>")
    private val LT_ENTITY_PATTERN: Pattern = Pattern.compile("&lt;")
    private val GT_ENTITY_PATTERN: Pattern = Pattern.compile("&gt;")
    private val QUOTE_ENTITY_PATTERN: Pattern = Pattern.compile("&#39;")
    private val QUOT_ENTITY_PATTERN: Pattern = Pattern.compile("&quot;")
  }
}
