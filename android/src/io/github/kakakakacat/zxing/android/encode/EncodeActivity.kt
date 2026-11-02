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

package io.github.kakakakacat.zxing.android.encode

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import io.github.kakakakacat.zxing.WriterException
import io.github.kakakakacat.zxing.android.Contents
import io.github.kakakakacat.zxing.android.FinishListener
import io.github.kakakakacat.zxing.android.Intents
import io.github.kakakakacat.zxing.android.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.regex.Pattern

/**
 * This class encodes data from an Intent into a QR code, and then displays it full screen so that
 * another person can scan it with their device.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class EncodeActivity : Activity() {

  private var qrCodeEncoder: QRCodeEncoder? = null

  override fun onCreate(icicle: Bundle?) {
    super.onCreate(icicle)
    val intent = getIntent()
    if (intent == null) {
      finish()
    } else {
      val action = intent.action
      if (Intents.Encode.ACTION == action || Intent.ACTION_SEND == action) {
        setContentView(R.layout.encode)
      } else {
        finish()
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.encode, menu)
    val useVcard = qrCodeEncoder?.isUseVCard() == true
    val encodeNameResource = if (useVcard) R.string.menu_encode_mecard else R.string.menu_encode_vcard
    val encodeItem = menu.findItem(R.id.menu_encode)
    encodeItem.setTitle(encodeNameResource)
    val intent = getIntent()
    if (intent != null) {
      val type = intent.getStringExtra(Intents.Encode.TYPE)
      encodeItem.isVisible = Contents.Type.CONTACT == type
    }
    return super.onCreateOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val itemId = item.itemId
    if (itemId == R.id.menu_share) {
      share()
      return true
    }
    if (itemId == R.id.menu_encode) {
      val intent = getIntent() ?: return false
      intent.putExtra(USE_VCARD_KEY, qrCodeEncoder?.isUseVCard() != true)
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      startActivity(intent)
      finish()
      return true
    }
    return false
  }

  // Defect A-07: the barcode PNG goes to the legacy external storage root and is shared as a
  // file:// Uri. Both have been unusable since Android 10; the fix is app-specific storage plus
  // a FileProvider. Carried forward. See docs/maint/HANDOFF.md, Phase 3.
  @Suppress("DEPRECATION") // Environment.getExternalStorageDirectory()
  private fun share() {
    val encoder = qrCodeEncoder
    if (encoder == null) { // Odd
      Log.w(TAG, "No existing barcode to send?")
      return
    }

    val contents = encoder.getContents()
    if (contents == null) {
      Log.w(TAG, "No existing barcode to send?")
      return
    }

    val bitmap = try {
      encoder.encodeAsBitmap()
    } catch (we: WriterException) {
      Log.w(TAG, we)
      return
    } ?: return

    val bsRoot = File(Environment.getExternalStorageDirectory(), "BarcodeScanner")
    val barcodesRoot = File(bsRoot, "Barcodes")
    if (!barcodesRoot.exists() && !barcodesRoot.mkdirs()) {
      Log.w(TAG, "Couldn't make dir $barcodesRoot")
      showErrorMessage(R.string.msg_unmount_usb)
      return
    }
    val barcodeFile = File(barcodesRoot, makeBarcodeFileName(contents) + ".png")
    try {
      FileOutputStream(barcodeFile).use { fos ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, fos)
      }
    } catch (ioe: IOException) {
      Log.w(TAG, "Couldn't access barcode file", ioe)
      showErrorMessage(R.string.msg_unmount_usb)
      return
    }

    val intent = Intent(Intent.ACTION_SEND, Uri.parse("mailto:"))
    intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " - " + encoder.getTitle())
    intent.putExtra(Intent.EXTRA_TEXT, contents)
    intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + barcodeFile.absolutePath))
    intent.type = "image/png"
    intent.addFlags(Intents.FLAG_NEW_DOC)
    startActivity(Intent.createChooser(intent, null))
  }

  @Suppress("DEPRECATION") // Display.getSize(Point)
  override fun onResume() {
    super.onResume()
    // This assumes the view is full screen, which is a good assumption
    val manager = getSystemService(WINDOW_SERVICE) as WindowManager
    val display = manager.defaultDisplay
    val displaySize = Point()
    display.getSize(displaySize)
    val width = displaySize.x
    val height = displaySize.y
    val smallerDimension = minOf(width, height) * 7 / 8

    val intent = getIntent() ?: return

    try {
      val useVCard = intent.getBooleanExtra(USE_VCARD_KEY, false)
      val encoder = QRCodeEncoder(this, intent, smallerDimension, useVCard)
      qrCodeEncoder = encoder
      val bitmap = encoder.encodeAsBitmap()
      if (bitmap == null) {
        Log.w(TAG, "Could not encode barcode")
        showErrorMessage(R.string.msg_encode_contents_failed)
        qrCodeEncoder = null
        return
      }

      findViewById<ImageView>(R.id.image_view).setImageBitmap(bitmap)

      val contents = findViewById<TextView>(R.id.contents_text_view)
      if (intent.getBooleanExtra(Intents.Encode.SHOW_CONTENTS, true)) {
        contents.text = encoder.getDisplayContents()
        title = encoder.getTitle()
      } else {
        contents.text = ""
        title = ""
      }
    } catch (e: WriterException) {
      Log.w(TAG, "Could not encode barcode", e)
      showErrorMessage(R.string.msg_encode_contents_failed)
      qrCodeEncoder = null
    }
  }

  private fun showErrorMessage(message: Int) {
    val builder = AlertDialog.Builder(this)
    builder.setMessage(message)
    builder.setPositiveButton(R.string.button_ok, FinishListener(this))
    builder.setOnCancelListener(FinishListener(this))
    builder.show()
  }

  private companion object {

    private val TAG = EncodeActivity::class.java.simpleName

    private const val MAX_BARCODE_FILENAME_LENGTH = 24
    private val NOT_ALPHANUMERIC: Pattern = Pattern.compile("[^A-Za-z0-9]")
    private const val USE_VCARD_KEY = "USE_VCARD"

    private fun makeBarcodeFileName(contents: CharSequence): String {
      val fileName = NOT_ALPHANUMERIC.matcher(contents).replaceAll("_")
      return if (fileName.length > MAX_BARCODE_FILENAME_LENGTH) {
        fileName.substring(0, MAX_BARCODE_FILENAME_LENGTH)
      } else {
        fileName
      }
    }
  }
}
