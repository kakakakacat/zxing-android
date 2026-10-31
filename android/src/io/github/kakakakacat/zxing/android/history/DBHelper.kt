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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * @author Sean Owen
 */
internal class DBHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

  override fun onCreate(sqLiteDatabase: SQLiteDatabase) {
    sqLiteDatabase.execSQL(
        "CREATE TABLE " + TABLE_NAME + " (" +
        ID_COL + " INTEGER PRIMARY KEY, " +
        TEXT_COL + " TEXT, " +
        FORMAT_COL + " TEXT, " +
        DISPLAY_COL + " TEXT, " +
        TIMESTAMP_COL + " INTEGER, " +
        DETAILS_COL + " TEXT);")
  }

  override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    sqLiteDatabase.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
    onCreate(sqLiteDatabase)
  }

  internal companion object {
    private const val DB_VERSION = 5
    private const val DB_NAME = "barcode_scanner_history.db"
    const val TABLE_NAME = "history"
    const val ID_COL = "id"
    const val TEXT_COL = "text"
    const val FORMAT_COL = "format"
    const val DISPLAY_COL = "display"
    const val TIMESTAMP_COL = "timestamp"
    const val DETAILS_COL = "details"
  }
}
