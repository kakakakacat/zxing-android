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
import android.os.AsyncTask
import android.view.View
import android.widget.ListView
import io.github.kakakakacat.zxing.android.Intents

/**
 * Activity for picking an installed application to share via Intent.
 */
@Suppress("DEPRECATION") // android.app.ListActivity, AsyncTask
class AppPickerActivity : ListActivity() {

  private var backgroundTask: AsyncTask<Any, Any, List<AppInfo>>? = null

  override fun onResume() {
    super.onResume()
    val task = LoadPackagesAsyncTask(this)
    backgroundTask = task
    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
  }

  override fun onPause() {
    backgroundTask?.cancel(true)
    backgroundTask = null
    super.onPause()
  }

  override fun onListItemClick(l: ListView, view: View, position: Int, id: Long) {
    val adapter = listAdapter
    if (position >= 0 && position < adapter.count) {
      val packageName = (adapter.getItem(position) as AppInfo).getPackageName()
      val intent = Intent()
      intent.addFlags(Intents.FLAG_NEW_DOC)
      intent.putExtra("url", "market://details?id=$packageName") // Browser.BookmarkColumns.URL
      setResult(RESULT_OK, intent)
    } else {
      setResult(RESULT_CANCELED)
    }
    finish()
  }
}
