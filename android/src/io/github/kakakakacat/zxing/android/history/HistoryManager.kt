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
import android.content.ContentValues
import android.database.CursorIndexOutOfBoundsException
import android.database.SQLException
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import io.github.kakakakacat.zxing.BarcodeFormat
import io.github.kakakakacat.zxing.Result
import io.github.kakakakacat.zxing.android.Intents
import io.github.kakakakacat.zxing.android.PreferencesActivity
import io.github.kakakakacat.zxing.android.result.ResultHandler
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.util.regex.Pattern

/**
 * Manages functionality related to scan history.
 *
 * @author Sean Owen
 */
@Suppress("DEPRECATION") // android.preference.PreferenceManager
class HistoryManager(private val activity: Activity) {

  private val enableHistory: Boolean =
      PreferenceManager.getDefaultSharedPreferences(activity)
          .getBoolean(PreferencesActivity.KEY_ENABLE_HISTORY, true)

  fun hasHistoryItems(): Boolean {
    val helper: SQLiteOpenHelper = DBHelper(activity)
    try {
      helper.readableDatabase.use { db ->
        db.query(DBHelper.TABLE_NAME, COUNT_COLUMN, null, null, null, null, null).use { cursor ->
          cursor.moveToFirst()
          return cursor.getInt(0) > 0
        }
      }
    } catch (sqle: SQLException) {
      Log.w(TAG, sqle)
      return false
    }
  }

  fun buildHistoryItems(): List<HistoryItem> {
    val helper: SQLiteOpenHelper = DBHelper(activity)
    val items = ArrayList<HistoryItem>()
    try {
      helper.readableDatabase.use { db ->
        db.query(DBHelper.TABLE_NAME, COLUMNS, null, null, null, null,
                 DBHelper.TIMESTAMP_COL + " DESC").use { cursor ->
          while (cursor.moveToNext()) {
            items.add(itemFromCursorRow(cursor.getString(0),
                                        cursor.getString(1),
                                        cursor.getString(2),
                                        cursor.getLong(3),
                                        cursor.getString(4)))
          }
        }
      }
    } catch (cioobe: CursorIndexOutOfBoundsException) {
      Log.w(TAG, cioobe)
    }
    return items
  }

  fun buildHistoryItem(number: Int): HistoryItem {
    val helper: SQLiteOpenHelper = DBHelper(activity)
    helper.readableDatabase.use { db ->
      db.query(DBHelper.TABLE_NAME, COLUMNS, null, null, null, null,
               DBHelper.TIMESTAMP_COL + " DESC").use { cursor ->
        cursor.move(number + 1)
        return itemFromCursorRow(cursor.getString(0),
                                 cursor.getString(1),
                                 cursor.getString(2),
                                 cursor.getLong(3),
                                 cursor.getString(4))
      }
    }
  }

  fun deleteHistoryItem(number: Int) {
    val helper: SQLiteOpenHelper = DBHelper(activity)
    try {
      helper.writableDatabase.use { db ->
        db.query(DBHelper.TABLE_NAME, ID_COL_PROJECTION, null, null, null, null,
                 DBHelper.TIMESTAMP_COL + " DESC").use { cursor ->
          cursor.move(number + 1)
          db.delete(DBHelper.TABLE_NAME, DBHelper.ID_COL + '=' + cursor.getString(0), null)
        }
      }
    } catch (sqle: SQLException) {
      Log.w(TAG, sqle)
    }
  }

  fun addHistoryItem(result: Result, handler: ResultHandler) {
    // Do not save this item to the history if the preference is turned off, or the contents are
    // considered secure.
    if (!activity.intent.getBooleanExtra(Intents.Scan.SAVE_HISTORY, true) ||
        handler.areContentsSecure() || !enableHistory) {
      return
    }

    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    if (!prefs.getBoolean(PreferencesActivity.KEY_REMEMBER_DUPLICATES, false)) {
      deletePrevious(result.text)
    }

    val values = ContentValues()
    values.put(DBHelper.TEXT_COL, result.text)
    values.put(DBHelper.FORMAT_COL, result.barcodeFormat.toString())
    values.put(DBHelper.DISPLAY_COL, handler.getDisplayContents().toString())
    values.put(DBHelper.TIMESTAMP_COL, System.currentTimeMillis())

    val helper: SQLiteOpenHelper = DBHelper(activity)
    try {
      helper.writableDatabase.use { db ->
        // Insert the new entry into the DB.
        db.insert(DBHelper.TABLE_NAME, DBHelper.TIMESTAMP_COL, values)
      }
    } catch (sqle: SQLException) {
      Log.w(TAG, sqle)
    }
  }

  fun addHistoryItemDetails(itemID: String, itemDetails: String) {
    // As we're going to do an update only we don't need need to worry
    // about the preferences; if the item wasn't saved it won't be udpated
    val helper: SQLiteOpenHelper = DBHelper(activity)
    try {
      helper.writableDatabase.use { db ->
        db.query(DBHelper.TABLE_NAME,
                 ID_DETAIL_COL_PROJECTION,
                 DBHelper.TEXT_COL + "=?",
                 arrayOf(itemID),
                 null,
                 null,
                 DBHelper.TIMESTAMP_COL + " DESC",
                 "1").use { cursor ->
          var oldID: String? = null
          var oldDetails: String? = null
          if (cursor.moveToNext()) {
            oldID = cursor.getString(0)
            oldDetails = cursor.getString(1)
          }

          if (oldID != null) {
            val newDetails = when {
              oldDetails == null -> itemDetails
              oldDetails.contains(itemDetails) -> null
              else -> "$oldDetails : $itemDetails"
            }
            if (newDetails != null) {
              val values = ContentValues()
              values.put(DBHelper.DETAILS_COL, newDetails)
              db.update(DBHelper.TABLE_NAME, values, DBHelper.ID_COL + "=?", arrayOf(oldID))
            }
          }
        }
      }
    } catch (sqle: SQLException) {
      Log.w(TAG, sqle)
    }
  }

  private fun deletePrevious(text: String) {
    val helper: SQLiteOpenHelper = DBHelper(activity)
    try {
      helper.writableDatabase.use { db ->
        db.delete(DBHelper.TABLE_NAME, DBHelper.TEXT_COL + "=?", arrayOf(text))
      }
    } catch (sqle: SQLException) {
      Log.w(TAG, sqle)
    }
  }

  fun trimHistory() {
    val helper: SQLiteOpenHelper = DBHelper(activity)
    try {
      helper.writableDatabase.use { db ->
        db.query(DBHelper.TABLE_NAME, ID_COL_PROJECTION, null, null, null, null,
                 DBHelper.TIMESTAMP_COL + " DESC").use { cursor ->
          cursor.move(MAX_ITEMS)
          while (cursor.moveToNext()) {
            val id = cursor.getString(0)
            Log.i(TAG, "Deleting scan history ID $id")
            db.delete(DBHelper.TABLE_NAME, DBHelper.ID_COL + '=' + id, null)
          }
        }
      }
    } catch (sqle: SQLException) {
      Log.w(TAG, sqle)
    }
  }

  /**
   * Builds a text representation of the scanning history. Each scan is encoded on one
   * line, terminated by a line break (\r\n). The values in each line are comma-separated,
   * and double-quoted. Double-quotes within values are escaped with a sequence of two
   * double-quotes. The fields output are:
   *
   *  1. Raw text
   *  2. Display text
   *  3. Format (e.g. QR_CODE)
   *  4. Unix timestamp (milliseconds since the epoch)
   *  5. Formatted version of timestamp
   *  6. Supplemental info (e.g. price info for a product barcode)
   */
  internal fun buildHistory(): CharSequence {
    val historyText = StringBuilder(1000)
    val helper: SQLiteOpenHelper = DBHelper(activity)
    try {
      helper.readableDatabase.use { db ->
        db.query(DBHelper.TABLE_NAME, COLUMNS, null, null, null, null,
                 DBHelper.TIMESTAMP_COL + " DESC").use { cursor ->
          val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
          while (cursor.moveToNext()) {

            historyText.append('"').append(massageHistoryField(cursor.getString(0))).append("\",")
            historyText.append('"').append(massageHistoryField(cursor.getString(1))).append("\",")
            historyText.append('"').append(massageHistoryField(cursor.getString(2))).append("\",")
            historyText.append('"').append(massageHistoryField(cursor.getString(3))).append("\",")

            // Add timestamp again, formatted
            val timestamp = cursor.getLong(3)
            historyText.append('"').append(massageHistoryField(format.format(timestamp))).append("\",")

            // Above we're preserving the old ordering of columns which had formatted data in position 5

            historyText.append('"').append(massageHistoryField(cursor.getString(4))).append("\"\r\n")
          }
        }
      }
    } catch (sqle: SQLException) {
      Log.w(TAG, sqle)
    }
    return historyText
  }

  internal fun clearHistory() {
    val helper: SQLiteOpenHelper = DBHelper(activity)
    try {
      helper.writableDatabase.use { db ->
        db.delete(DBHelper.TABLE_NAME, null, null)
      }
    } catch (sqle: SQLException) {
      Log.w(TAG, sqle)
    }
  }

  internal companion object {

    private val TAG = HistoryManager::class.java.simpleName

    private const val MAX_ITEMS = 2000

    private val COLUMNS = arrayOf(
        DBHelper.TEXT_COL,
        DBHelper.DISPLAY_COL,
        DBHelper.FORMAT_COL,
        DBHelper.TIMESTAMP_COL,
        DBHelper.DETAILS_COL)

    private val COUNT_COLUMN = arrayOf("COUNT(1)")

    private val ID_COL_PROJECTION = arrayOf(DBHelper.ID_COL)
    private val ID_DETAIL_COL_PROJECTION = arrayOf(DBHelper.ID_COL, DBHelper.DETAILS_COL)
    private val DOUBLE_QUOTE: Pattern = Pattern.compile("\"", Pattern.LITERAL)

    private fun itemFromCursorRow(text: String?,
                                  display: String?,
                                  format: String,
                                  timestamp: Long,
                                  details: String?): HistoryItem {
      val result = Result(text, null, null, BarcodeFormat.valueOf(format), timestamp)
      return HistoryItem(result, display, details)
    }

    // Defect A-06: the legacy external storage root, unwritable under scoped storage since
    // Android 10. Carried forward unchanged; the fix needs app-specific storage plus a
    // FileProvider. See docs/maint/HANDOFF.md, Phase 3.
    @Suppress("DEPRECATION") // Environment.getExternalStorageDirectory()
    internal fun saveHistory(history: String): Uri? {
      val bsRoot = File(Environment.getExternalStorageDirectory(), "BarcodeScanner")
      val historyRoot = File(bsRoot, "History")
      if (!historyRoot.mkdirs() && !historyRoot.isDirectory) {
        Log.w(TAG, "Couldn't make dir $historyRoot")
        return null
      }
      val historyFile = File(historyRoot, "history-" + System.currentTimeMillis() + ".csv")
      return try {
        OutputStreamWriter(FileOutputStream(historyFile), StandardCharsets.UTF_8).use { out ->
          out.write(history)
        }
        Uri.parse("file://" + historyFile.absolutePath)
      } catch (ioe: IOException) {
        Log.w(TAG, "Couldn't access file $historyFile due to $ioe")
        null
      }
    }

    private fun massageHistoryField(value: String?): String =
        if (value == null) "" else DOUBLE_QUOTE.matcher(value).replaceAll("\"\"")
  }
}
