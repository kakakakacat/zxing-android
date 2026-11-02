/*
 * Copyright (C) 2026 ZXing authors
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

package io.github.kakakakacat.zxing.android

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Writes a file for another app to read, and hands back a `content://` URI for it.
 *
 * This replaces what defects A-06 and A-07 both did: write into
 * `Environment.getExternalStorageDirectory()/BarcodeScanner` and share a `file://` URI.
 * Neither half has worked since Android 10 — an app cannot create that directory under
 * scoped storage, and passing a `file://` URI to another app throws `FileUriExposedException`
 * from targetSdk 24 on. The replacement is the app's own cache directory plus a
 * [FileProvider], whose URI carries a read grant for exactly the one file.
 *
 * Exports are deliberately transient. They exist to be attached to a message that is being
 * composed right now, so they go in the cache and old ones are swept on the way in rather
 * than accumulating in a directory nobody looks at.
 */
internal object ExportFiles {

  private const val TAG = "ExportFiles"

  /** Everything this provider can serve lives under here; see res/xml/file_paths.xml. */
  private const val EXPORTS = "exports"

  /** Long enough to compose and send a message, short enough not to pile up. */
  private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L

  /**
   * Writes [fileName] inside [subdirectory] and returns a URI another app can read, or
   * null if the file could not be written — in which case the caller shows an error and
   * nothing has been shared.
   */
  fun write(context: Context,
            subdirectory: String,
            fileName: String,
            writeTo: (OutputStream) -> Unit): Uri? {
    val directory = File(File(context.cacheDir, EXPORTS), subdirectory)
    if (!directory.mkdirs() && !directory.isDirectory) {
      Log.w(TAG, "Couldn't make dir $directory")
      return null
    }
    deleteStaleExports(directory)

    val file = File(directory, fileName)
    try {
      FileOutputStream(file).use(writeTo)
    } catch (ioe: IOException) {
      Log.w(TAG, "Couldn't write $file", ioe)
      file.delete()
      return null
    }

    return try {
      FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    } catch (iae: IllegalArgumentException) {
      // Thrown when the file is outside every <paths> entry, which would mean this object
      // and res/xml/file_paths.xml have drifted apart.
      Log.w(TAG, "Not shareable: $file", iae)
      null
    }
  }

  /**
   * Attaches [uri] to a share Intent with a read grant. Without the grant the receiving
   * app gets a URI it is not permitted to open, which looks to the user exactly like a
   * corrupt attachment.
   *
   * The ClipData is set here rather than left to `startActivity`, which would otherwise
   * build it from EXTRA_STREAM on the way out. That is too late for
   * `Intent.createChooser`: the chooser copies the target's ClipData and flags when it is
   * built, so with no ClipData yet the share sheet gets no grant of its own and cannot
   * read the file to draw its preview. Measured, not guessed —
   * `Permission Denial: opening provider androidx.core.content.FileProvider from
   * com.android.intentresolver` with a broken thumbnail, while the app the user finally
   * picked could read the file perfectly well.
   */
  fun grantRead(context: Context, intent: Intent, uri: Uri): Intent {
    intent.putExtra(Intent.EXTRA_STREAM, uri)
    intent.clipData = ClipData.newUri(context.contentResolver, "", uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return intent
  }

  private fun deleteStaleExports(directory: File) {
    val cutoff = System.currentTimeMillis() - MAX_AGE_MS
    directory.listFiles()?.forEach { file ->
      if (file.isFile && file.lastModified() < cutoff && !file.delete()) {
        Log.w(TAG, "Couldn't delete stale export $file")
      }
    }
  }
}
