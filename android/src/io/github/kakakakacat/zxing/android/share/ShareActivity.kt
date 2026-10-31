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

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.ContactsContract
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import io.github.kakakakacat.zxing.BarcodeFormat
import io.github.kakakakacat.zxing.android.Contents
import io.github.kakakakacat.zxing.android.Intents
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.android.clipboard.ClipboardInterface

/**
 * Barcode Scanner can share data like contacts and bookmarks by displaying a QR Code on screen,
 * such that another user can scan the barcode with their phone.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class ShareActivity : Activity() {

  private lateinit var clipboardButton: View

  private val contactListener = View.OnClickListener {
    val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
    intent.addFlags(Intents.FLAG_NEW_DOC)
    startActivityForResult(intent, PICK_CONTACT)
  }

  private val bookmarkListener = View.OnClickListener {
    val intent = Intent(Intent.ACTION_PICK)
    intent.addFlags(Intents.FLAG_NEW_DOC)
    intent.setClassName(this, BookmarkPickerActivity::class.java.name)
    startActivityForResult(intent, PICK_BOOKMARK)
  }

  private val appListener = View.OnClickListener {
    val intent = Intent(Intent.ACTION_PICK)
    intent.addFlags(Intents.FLAG_NEW_DOC)
    intent.setClassName(this, AppPickerActivity::class.java.name)
    startActivityForResult(intent, PICK_APP)
  }

  private val clipboardListener = View.OnClickListener {
    // Should always be true, because we grey out the clipboard button in onResume() if it's empty
    val text = ClipboardInterface.getText(this)
    if (text != null) {
      launchSearch(text.toString())
    }
  }

  private val textListener = View.OnKeyListener { view, keyCode, event ->
    if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
      val text = (view as TextView).text.toString()
      if (text.isNotEmpty()) {
        launchSearch(text)
      }
      true
    } else {
      false
    }
  }

  private fun launchSearch(text: String) {
    val intent = buildEncodeIntent(Contents.Type.TEXT)
    intent.putExtra(Intents.Encode.DATA, text)
    startActivity(intent)
  }

  override fun onCreate(icicle: Bundle?) {
    super.onCreate(icicle)
    setContentView(R.layout.share)

    findViewById<View>(R.id.share_contact_button).setOnClickListener(contactListener)
    if (Build.VERSION.SDK_INT >= 23) { // Marshmallow / 6.0
      // Can't access bookmarks in 6.0+
      findViewById<View>(R.id.share_bookmark_button).isEnabled = false
    } else {
      findViewById<View>(R.id.share_bookmark_button).setOnClickListener(bookmarkListener)
    }
    findViewById<View>(R.id.share_app_button).setOnClickListener(appListener)
    clipboardButton = findViewById(R.id.share_clipboard_button)
    clipboardButton.setOnClickListener(clipboardListener)
    findViewById<View>(R.id.share_text_view).setOnKeyListener(textListener)
  }

  override fun onResume() {
    super.onResume()
    clipboardButton.isEnabled = ClipboardInterface.hasText(this)
  }

  @Deprecated("Deprecated in Java")
  @Suppress("DEPRECATION") // onActivityResult / startActivityForResult
  override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
    if (resultCode == RESULT_OK) {
      when (requestCode) {
        PICK_BOOKMARK, PICK_APP ->
          showTextAsBarcode(intent?.getStringExtra("url")) // Browser.BookmarkColumns.URL
        // Data field is content://contacts/people/984
        PICK_CONTACT -> showContactAsBarcode(intent?.data)
      }
    }
  }

  private fun showTextAsBarcode(text: String?) {
    if (text == null) {
      return // Show error?
    }
    val intent = buildEncodeIntent(Contents.Type.TEXT)
    intent.putExtra(Intents.Encode.DATA, text)
    startActivity(intent)
  }

  /**
   * Takes a contact Uri and does the necessary database lookups to retrieve that person's info,
   * then sends an Encode intent to render it as a QR Code.
   *
   * @param contactUri A Uri of the form content://contacts/people/17
   */
  @Suppress("DEPRECATION") // Cursor.getColumnIndex on a name that may be absent
  private fun showContactAsBarcode(contactUri: Uri?) {
    if (contactUri == null) {
      return // Show error?
    }
    val resolver = contentResolver

    val id: String
    val name: String?
    val hasPhone: Boolean
    resolver.query(contactUri, null, null, null, null).use { cursor ->
      if (cursor == null || !cursor.moveToFirst()) {
        return
      }
      id = cursor.getString(cursor.getColumnIndex(BaseColumns._ID))
      name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
      hasPhone = cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0
    }

    // Don't require a name to be present, this contact might be just a phone number.
    val bundle = Bundle()
    if (!name.isNullOrEmpty()) {
      bundle.putString(ContactsContract.Intents.Insert.NAME, massageContactData(name))
    }

    if (hasPhone) {
      resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                     null,
                     ContactsContract.CommonDataKinds.Phone.CONTACT_ID + '=' + id,
                     null,
                     null).use { phonesCursor ->
        if (phonesCursor != null) {
          var foundPhone = 0
          val phonesNumberColumn = phonesCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
          val phoneTypeColumn = phonesCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
          while (phonesCursor.moveToNext() && foundPhone < Contents.PHONE_KEYS.size) {
            val number = phonesCursor.getString(phonesNumberColumn)
            if (!number.isNullOrEmpty()) {
              bundle.putString(Contents.PHONE_KEYS[foundPhone], massageContactData(number))
            }
            val type = phonesCursor.getInt(phoneTypeColumn)
            bundle.putInt(Contents.PHONE_TYPE_KEYS[foundPhone], type)
            foundPhone++
          }
        }
      }
    }

    resolver.query(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
                   null,
                   ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID + '=' + id,
                   null,
                   null).use { methodsCursor ->
      if (methodsCursor != null && methodsCursor.moveToNext()) {
        val data = methodsCursor.getString(methodsCursor.getColumnIndex(
            ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS))
        if (!data.isNullOrEmpty()) {
          bundle.putString(ContactsContract.Intents.Insert.POSTAL, massageContactData(data))
        }
      }
    }

    resolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                   null,
                   ContactsContract.CommonDataKinds.Email.CONTACT_ID + '=' + id,
                   null,
                   null).use { emailCursor ->
      if (emailCursor != null) {
        var foundEmail = 0
        val emailColumn = emailCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA)
        while (emailCursor.moveToNext() && foundEmail < Contents.EMAIL_KEYS.size) {
          val email = emailCursor.getString(emailColumn)
          if (!email.isNullOrEmpty()) {
            bundle.putString(Contents.EMAIL_KEYS[foundEmail], massageContactData(email))
          }
          foundEmail++
        }
      }
    }

    val intent = buildEncodeIntent(Contents.Type.CONTACT)
    intent.putExtra(Intents.Encode.DATA, bundle)
    startActivity(intent)
  }

  private fun buildEncodeIntent(type: String): Intent {
    val intent = Intent(Intents.Encode.ACTION)
    // Target this app, not whichever app happens to own the upstream package name.
    // Upstream hardcoded com.google.zxing.client.android here, which in this fork would
    // hand our own share flow to a different app, or to nothing at all.
    intent.setPackage(packageName)
    intent.addFlags(Intents.FLAG_NEW_DOC)
    intent.putExtra(Intents.Encode.TYPE, type)
    intent.putExtra(Intents.Encode.FORMAT, BarcodeFormat.QR_CODE.toString())
    return intent
  }

  private companion object {

    private const val PICK_BOOKMARK = 0
    private const val PICK_CONTACT = 1
    private const val PICK_APP = 2

    private fun massageContactData(data: String): String {
      // For now -- make sure we don't put newlines in shared contact data. It messes up
      // any known encoding of contact data. Replace with space.
      var massaged = data
      if (massaged.indexOf('\n') >= 0) {
        massaged = massaged.replace("\n", " ")
      }
      if (massaged.indexOf('\r') >= 0) {
        massaged = massaged.replace("\r", " ")
      }
      return massaged
    }
  }
}
