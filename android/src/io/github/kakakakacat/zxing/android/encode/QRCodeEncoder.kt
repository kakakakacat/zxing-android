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

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import io.github.kakakakacat.zxing.BarcodeFormat
import io.github.kakakakacat.zxing.EncodeHintType
import io.github.kakakakacat.zxing.MultiFormatWriter
import io.github.kakakakacat.zxing.Result
import io.github.kakakakacat.zxing.WriterException
import io.github.kakakakacat.zxing.android.Contents
import io.github.kakakakacat.zxing.android.Intents
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.client.result.AddressBookParsedResult
import io.github.kakakakacat.zxing.client.result.ResultParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.EnumMap

/**
 * This class does the work of decoding the user's request and extracting all the data
 * to be encoded in a barcode.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
internal class QRCodeEncoder
@Throws(WriterException::class)
constructor(
    private val activity: Context,
    intent: Intent,
    private val dimension: Int,
    private val useVCard: Boolean
) {

  private var contents: String? = null
  private var displayContents: String? = null
  private var title: String? = null
  private var format: BarcodeFormat? = null

  init {
    when (intent.action) {
      Intents.Encode.ACTION -> encodeContentsFromZXingIntent(intent)
      Intent.ACTION_SEND -> encodeContentsFromShareIntent(intent)
    }
  }

  fun getContents(): String? = contents

  fun getDisplayContents(): String? = displayContents

  fun getTitle(): String? = title

  fun isUseVCard(): Boolean = useVCard

  // It would be nice if the string encoding lived in the core ZXing library,
  // but we use platform specific code like PhoneNumberUtils, so it can't.
  private fun encodeContentsFromZXingIntent(intent: Intent) {
    // Default to QR_CODE if no format given.
    val formatString = intent.getStringExtra(Intents.Encode.FORMAT)
    format = null
    if (formatString != null) {
      try {
        format = BarcodeFormat.valueOf(formatString)
      } catch (iae: IllegalArgumentException) {
        // Ignore it then
      }
    }
    if (format == null || format == BarcodeFormat.QR_CODE) {
      val type = intent.getStringExtra(Intents.Encode.TYPE)
      if (!type.isNullOrEmpty()) {
        format = BarcodeFormat.QR_CODE
        encodeQRCodeContents(intent, type)
      }
    } else {
      val data = intent.getStringExtra(Intents.Encode.DATA)
      if (!data.isNullOrEmpty()) {
        contents = data
        displayContents = data
        title = activity.getString(R.string.contents_text)
      }
    }
  }

  // Handles send intents from multitude of Android applications
  @Throws(WriterException::class)
  private fun encodeContentsFromShareIntent(intent: Intent) {
    // Check if this is a plain text encoding, or contact
    if (intent.hasExtra(Intent.EXTRA_STREAM)) {
      encodeFromStreamExtra(intent)
    } else {
      encodeFromTextExtras(intent)
    }
  }

  @Throws(WriterException::class)
  private fun encodeFromTextExtras(intent: Intent) {
    // Notice: Google Maps shares both URL and details in one text, bummer!
    var theContents = ContactEncoder.trim(intent.getStringExtra(Intent.EXTRA_TEXT))
    if (theContents == null) {
      theContents = ContactEncoder.trim(intent.getStringExtra("android.intent.extra.HTML_TEXT"))
      // Intent.EXTRA_HTML_TEXT
      if (theContents == null) {
        theContents = ContactEncoder.trim(intent.getStringExtra(Intent.EXTRA_SUBJECT))
        if (theContents == null) {
          val emails = intent.getStringArrayExtra(Intent.EXTRA_EMAIL)
          theContents = if (emails != null) ContactEncoder.trim(emails[0]) else "?"
        }
      }
    }

    // Trim text to avoid URL breaking.
    if (theContents.isNullOrEmpty()) {
      throw WriterException("Empty EXTRA_TEXT")
    }
    contents = theContents
    // We only do QR code.
    format = BarcodeFormat.QR_CODE
    displayContents = when {
      intent.hasExtra(Intent.EXTRA_SUBJECT) -> intent.getStringExtra(Intent.EXTRA_SUBJECT)
      intent.hasExtra(Intent.EXTRA_TITLE) -> intent.getStringExtra(Intent.EXTRA_TITLE)
      else -> contents
    }
    title = activity.getString(R.string.contents_text)
  }

  // Handles send intents from the Contacts app, retrieving a contact as a VCARD.
  @Throws(WriterException::class)
  @Suppress("DEPRECATION") // Bundle.getParcelable(String)
  private fun encodeFromStreamExtra(intent: Intent) {
    format = BarcodeFormat.QR_CODE
    val bundle = intent.extras ?: throw WriterException("No extras")
    val uri = bundle.getParcelable<Uri>(Intent.EXTRA_STREAM)
        ?: throw WriterException("No EXTRA_STREAM")
    rejectUnsafeStream(uri)
    val vcard: ByteArray
    val vcardString: String
    try {
      activity.contentResolver.openInputStream(uri).use { stream ->
        if (stream == null) {
          throw WriterException("Can't open stream for $uri")
        }
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } > 0) {
          baos.write(buffer, 0, bytesRead)
        }
        vcard = baos.toByteArray()
        vcardString = String(vcard, 0, vcard.size, StandardCharsets.UTF_8)
      }
    } catch (ioe: IOException) {
      throw WriterException(ioe)
    } catch (se: SecurityException) {
      // A caller can name a URI it never granted this app, and `openInputStream` answers
      // that with SecurityException, which is not an IOException. Before this was caught,
      // any application on the device could crash this exported activity with one intent
      // -- the `am start` that checks the guard above does it too, because the shell grants
      // nothing. Refusing is the same outcome as any other unreadable stream.
      throw WriterException(se)
    } catch (iae: IllegalArgumentException) {
      // An authority no provider answers for reaches the resolver as this, from the same
      // untrusted extra and with the same consequence.
      throw WriterException(iae)
    }
    val result = Result(vcardString, vcard, null, BarcodeFormat.QR_CODE)
    val parsedResult = ResultParser.parseResult(result)
    if (parsedResult !is AddressBookParsedResult) {
      throw WriterException("Result was not an address")
    }
    encodeQRCodeContents(parsedResult)
    if (contents.isNullOrEmpty()) {
      throw WriterException("No content to encode")
    }
  }

  /**
   * Refuses an `EXTRA_STREAM` this process would be opening on someone else's behalf.
   *
   * `EncodeActivity` is exported for `ACTION_SEND` of `text/x-vcard` -- that is how the
   * Contacts app shares a card -- so any installed application can hand this one a [Uri]
   * and have it opened with *this* app's identity. That is the confused deputy, and the
   * two shapes it takes here are:
   *
   * - **A non-`content` scheme.** `file:///data/data/<this package>/databases/...` is
   *   readable by this uid and by nothing else on the device; the caller could not open
   *   it and this app can. `content` is also the only scheme `ACTION_SEND` is specified
   *   to carry, so nothing legitimate is being turned away.
   * - **This app's own provider.** `${applicationId}.fileprovider` is `exported="false"`
   *   and hands out one cache subtree; a caller naming it is asking this app to read its
   *   own files back to itself, which no sender has a reason to do.
   *
   * What this deliberately does not do is verify that the *sender* could read the URI.
   * An activity cannot: `Binder.getCallingUid` is this process outside a transaction, and
   * `callingPackage` is null unless the sender used `startActivityForResult`, which the
   * share flow does not. The grant the sender attaches is what the resolver then enforces.
   */
  @Throws(WriterException::class)
  private fun rejectUnsafeStream(uri: Uri) {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
      throw WriterException("EXTRA_STREAM is not a content URI: ${uri.scheme}")
    }
    val authority = uri.authority ?: throw WriterException("EXTRA_STREAM has no authority")
    val provider = activity.packageManager.resolveContentProvider(authority, 0)
    if (provider != null && provider.packageName == activity.packageName) {
      throw WriterException("EXTRA_STREAM names this application's own provider")
    }
  }

  private fun encodeQRCodeContents(intent: Intent, type: String) {
    when (type) {
      Contents.Type.TEXT -> {
        val textData = intent.getStringExtra(Intents.Encode.DATA)
        if (!textData.isNullOrEmpty()) {
          contents = textData
          displayContents = textData
          title = activity.getString(R.string.contents_text)
        }
      }

      Contents.Type.EMAIL -> {
        val emailData = ContactEncoder.trim(intent.getStringExtra(Intents.Encode.DATA))
        if (emailData != null) {
          contents = "mailto:$emailData"
          displayContents = emailData
          title = activity.getString(R.string.contents_email)
        }
      }

      Contents.Type.PHONE -> {
        val phoneData = ContactEncoder.trim(intent.getStringExtra(Intents.Encode.DATA))
        if (phoneData != null) {
          contents = "tel:$phoneData"
          displayContents = ContactEncoder.formatPhone(phoneData)
          title = activity.getString(R.string.contents_phone)
        }
      }

      Contents.Type.SMS -> {
        val smsData = ContactEncoder.trim(intent.getStringExtra(Intents.Encode.DATA))
        if (smsData != null) {
          contents = "sms:$smsData"
          displayContents = ContactEncoder.formatPhone(smsData)
          title = activity.getString(R.string.contents_sms)
        }
      }

      Contents.Type.CONTACT -> {
        val contactBundle = intent.getBundleExtra(Intents.Encode.DATA)
        if (contactBundle != null) {

          val name = contactBundle.getString(ContactsContract.Intents.Insert.NAME)
          val organization = contactBundle.getString(ContactsContract.Intents.Insert.COMPANY)
          val address = contactBundle.getString(ContactsContract.Intents.Insert.POSTAL)
          val phones = getAllBundleValues(contactBundle, Contents.PHONE_KEYS)
          val phoneTypes = getAllBundleValues(contactBundle, Contents.PHONE_TYPE_KEYS)
          val emails = getAllBundleValues(contactBundle, Contents.EMAIL_KEYS)
          val url = contactBundle.getString(Contents.URL_KEY)
          val urls = if (url == null) null else listOf(url)
          val note = contactBundle.getString(Contents.NOTE_KEY)

          val encoder: ContactEncoder =
              if (useVCard) VCardContactEncoder() else MECARDContactEncoder()
          val encoded = encoder.encode(listOf(name),
                                       organization,
                                       listOf(address),
                                       phones,
                                       phoneTypes,
                                       emails,
                                       urls,
                                       note)
          // Make sure we've encoded at least one field.
          if (encoded[1].isNotEmpty()) {
            contents = encoded[0]
            displayContents = encoded[1]
            title = activity.getString(R.string.contents_contact)
          }
        }
      }

      Contents.Type.LOCATION -> {
        val locationBundle = intent.getBundleExtra(Intents.Encode.DATA)
        if (locationBundle != null) {
          // These must use Bundle.getFloat(), not getDouble(), it's part of the API.
          val latitude = locationBundle.getFloat("LAT", Float.MAX_VALUE)
          val longitude = locationBundle.getFloat("LONG", Float.MAX_VALUE)
          if (latitude != Float.MAX_VALUE && longitude != Float.MAX_VALUE) {
            contents = "geo:" + latitude + ',' + longitude
            displayContents = "$latitude,$longitude"
            title = activity.getString(R.string.contents_location)
          }
        }
      }
    }
  }

  private fun encodeQRCodeContents(contact: AddressBookParsedResult) {
    val encoder: ContactEncoder = if (useVCard) VCardContactEncoder() else MECARDContactEncoder()
    val encoded = encoder.encode(toList(contact.names),
                                 contact.org,
                                 toList(contact.addresses),
                                 toList(contact.phoneNumbers),
                                 null,
                                 toList(contact.emails),
                                 toList(contact.getURLs()),
                                 null)
    // Make sure we've encoded at least one field.
    if (encoded[1].isNotEmpty()) {
      contents = encoded[0]
      displayContents = encoded[1]
      title = activity.getString(R.string.contents_contact)
    }
  }

  @Throws(WriterException::class)
  fun encodeAsBitmap(): Bitmap? {
    val contentsToEncode = contents ?: return null
    var hints: MutableMap<EncodeHintType, Any>? = null
    val encoding = guessAppropriateEncoding(contentsToEncode)
    if (encoding != null) {
      hints = EnumMap(EncodeHintType::class.java)
      hints[EncodeHintType.CHARACTER_SET] = encoding
    }
    val result = try {
      MultiFormatWriter().encode(contentsToEncode, format, dimension, dimension, hints)
    } catch (iae: IllegalArgumentException) {
      // Unsupported format
      return null
    }
    val width = result.width
    val height = result.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
      val offset = y * width
      for (x in 0 until width) {
        pixels[offset + x] = if (result.get(x, y)) BLACK else WHITE
      }
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
  }

  private companion object {

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    @Suppress("DEPRECATION") // Bundle.get(String)
    private fun getAllBundleValues(bundle: Bundle, keys: Array<String>): List<String?> {
      val values = ArrayList<String?>(keys.size)
      for (key in keys) {
        val value = bundle.get(key)
        values.add(value?.toString())
      }
      return values
    }

    private fun toList(values: Array<String>?): List<String>? = values?.asList()

    private fun guessAppropriateEncoding(contents: CharSequence): String? {
      // Very crude at the moment
      for (i in 0 until contents.length) {
        if (contents[i].code > 0xFF) {
          return "UTF-8"
        }
      }
      return null
    }
  }
}
