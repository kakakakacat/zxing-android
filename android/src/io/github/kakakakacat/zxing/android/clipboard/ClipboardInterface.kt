/*
 * Copyright (C) 2012 ZXing authors
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

package io.github.kakakakacat.zxing.android.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

/**
 * Abstraction over the [ClipboardManager] API that manages copying and pasting.
 */
object ClipboardInterface {

  private val TAG: String = ClipboardInterface::class.java.simpleName

  @JvmStatic
  fun getText(context: Context): CharSequence? {
    val clipboard = getManager(context)
    val clip = clipboard.primaryClip
    return if (hasText(context)) clip!!.getItemAt(0).coerceToText(context) else null
  }

  @JvmStatic
  fun setText(text: CharSequence?, context: Context) {
    if (text != null) {
      try {
        getManager(context).setPrimaryClip(ClipData.newPlainText(null, text))
      } catch (e: NullPointerException) {
        // Have seen this in the wild, bizarrely
        Log.w(TAG, "Clipboard bug", e)
      } catch (e: IllegalStateException) {
        Log.w(TAG, "Clipboard bug", e)
      } catch (e: SecurityException) {
        Log.w(TAG, "Clipboard bug", e)
      }
    }
  }

  @JvmStatic
  fun hasText(context: Context): Boolean {
    val clip = getManager(context).primaryClip
    return clip != null && clip.itemCount > 0
  }

  private fun getManager(context: Context): ClipboardManager =
      context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
}
