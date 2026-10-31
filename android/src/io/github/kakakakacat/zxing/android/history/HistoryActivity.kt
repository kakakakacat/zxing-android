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

import android.app.Activity
import android.app.AlertDialog
import android.app.ListActivity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import io.github.kakakakacat.zxing.android.CaptureActivity
import io.github.kakakakacat.zxing.android.Intents
import io.github.kakakakacat.zxing.android.R

/**
 * The activity for interacting with the scan history.
 */
@Suppress("DEPRECATION") // android.app.ListActivity
class HistoryActivity : ListActivity() {

  private lateinit var historyManager: HistoryManager
  private lateinit var adapter: ArrayAdapter<HistoryItem>
  private var originalTitle: CharSequence? = null

  override fun onCreate(icicle: Bundle?) {
    super.onCreate(icicle)
    historyManager = HistoryManager(this)
    adapter = HistoryItemAdapter(this)
    listAdapter = adapter
    val listview: View = listView
    registerForContextMenu(listview)
    originalTitle = title
  }

  override fun onResume() {
    super.onResume()
    reloadHistoryItems()
  }

  private fun reloadHistoryItems() {
    val items = historyManager.buildHistoryItems()
    adapter.clear()
    for (item in items) {
      adapter.add(item)
    }
    title = originalTitle.toString() + " (" + adapter.count + ')'
    if (adapter.isEmpty) {
      adapter.add(HistoryItem(null, null, null))
    }
  }

  override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    if (adapter.getItem(position)?.getResult() != null) {
      val intent = Intent(this, CaptureActivity::class.java)
      intent.putExtra(Intents.History.ITEM_NUMBER, position)
      setResult(Activity.RESULT_OK, intent)
      finish()
    }
  }

  override fun onCreateContextMenu(menu: ContextMenu,
                                   v: View,
                                   menuInfo: ContextMenu.ContextMenuInfo?) {
    val position = (menuInfo as AdapterView.AdapterContextMenuInfo).position
    if (position >= adapter.count || adapter.getItem(position)?.getResult() != null) {
      menu.add(Menu.NONE, position, position, R.string.history_clear_one_history_text)
    } // else it's just that dummy "Empty" message
  }

  override fun onContextItemSelected(item: MenuItem): Boolean {
    historyManager.deleteHistoryItem(item.itemId)
    reloadHistoryItems()
    return true
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    if (historyManager.hasHistoryItems()) {
      menuInflater.inflate(R.menu.history, menu)
    }
    return super.onCreateOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val itemId = item.itemId
    if (itemId == R.id.menu_history_send) {
      val history = historyManager.buildHistory()
      val historyFile = HistoryManager.saveHistory(history.toString())
      if (historyFile == null) {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.msg_unmount_usb)
        builder.setPositiveButton(R.string.button_ok, null)
        builder.show()
      } else {
        val intent = Intent(Intent.ACTION_SEND, Uri.parse("mailto:"))
        intent.addFlags(Intents.FLAG_NEW_DOC)
        val subject = resources.getString(R.string.history_email_title)
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, subject)
        intent.putExtra(Intent.EXTRA_STREAM, historyFile)
        intent.type = "text/csv"
        try {
          startActivity(intent)
        } catch (anfe: ActivityNotFoundException) {
          Log.w(TAG, anfe.toString())
        }
      }
    } else if (itemId == R.id.menu_history_clear_text) {
      val builder = AlertDialog.Builder(this)
      builder.setMessage(R.string.msg_sure)
      builder.setCancelable(true)
      builder.setPositiveButton(R.string.button_ok) { dialog, _ ->
        historyManager.clearHistory()
        dialog.dismiss()
        finish()
      }
      builder.setNegativeButton(R.string.button_cancel, null)
      builder.show()
    } else {
      return super.onOptionsItemSelected(item)
    }
    return true
  }

  private companion object {
    private val TAG = HistoryActivity::class.java.simpleName
  }
}
