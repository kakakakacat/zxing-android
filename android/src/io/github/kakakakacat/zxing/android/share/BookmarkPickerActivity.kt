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

import android.app.ListActivity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ListView
import io.github.kakakakacat.zxing.android.Intents

/**
 * This class is only needed because I can't successfully send an ACTION_PICK intent to
 * com.android.browser.BrowserBookmarksPage. It can go away if that starts working in the future.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
@Suppress("DEPRECATION") // android.app.ListActivity
class BookmarkPickerActivity : ListActivity() {

  private val titleURLs: MutableList<Array<String>> = ArrayList()

  override fun onResume() {
    super.onResume()
    titleURLs.clear()
    contentResolver.query(BOOKMARKS_URI, BOOKMARK_PROJECTION, BOOKMARK_SELECTION, null, null)
        .use { cursor ->
          if (cursor == null) {
            Log.w(TAG, "No cursor returned for bookmark query")
            finish()
            return
          }
          while (cursor.moveToNext()) {
            titleURLs.add(arrayOf(cursor.getString(0), cursor.getString(1)))
          }
        }
    listAdapter = BookmarkAdapter(this, titleURLs)
  }

  override fun onListItemClick(l: ListView, view: View, position: Int, id: Long) {
    val titleURL = titleURLs[position]
    val intent = Intent()
    intent.addFlags(Intents.FLAG_NEW_DOC)
    intent.putExtra("title", titleURL[0]) // Browser.BookmarkColumns.TITLE
    intent.putExtra("url", titleURL[1]) // Browser.BookmarkColumns.URL
    setResult(RESULT_OK, intent)
    finish()
  }

  private companion object {

    private val TAG = BookmarkPickerActivity::class.java.simpleName

    private val BOOKMARK_PROJECTION = arrayOf(
        "title", // Browser.BookmarkColumns.TITLE
        "url")   // Browser.BookmarkColumns.URL

    // Copied from android.provider.Browser.BOOKMARKS_URI:
    private val BOOKMARKS_URI: Uri = Uri.parse("content://browser/bookmarks")

    private const val BOOKMARK_SELECTION = "bookmark = 1 AND url IS NOT NULL"
  }
}
