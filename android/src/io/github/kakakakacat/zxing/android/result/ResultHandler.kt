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

package io.github.kakakakacat.zxing.android.result

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.preference.PreferenceManager
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log
import io.github.kakakakacat.zxing.Result
import io.github.kakakakacat.zxing.android.Contents
import io.github.kakakakacat.zxing.android.Intents
import io.github.kakakakacat.zxing.android.LocaleManager
import io.github.kakakakacat.zxing.android.PreferencesActivity
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.android.book.SearchBookContentsActivity
import io.github.kakakakacat.zxing.client.result.ParsedResult
import io.github.kakakakacat.zxing.client.result.ParsedResultType
import io.github.kakakakacat.zxing.client.result.ResultParser
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.Locale

/**
 * A base class for the Android-specific barcode handlers. These allow the app to polymorphically
 * suggest the appropriate actions for each data type.
 *
 * This class also contains a bunch of utility methods to take common actions like opening a URL.
 * They could easily be moved into a helper object, but it can't be static because the Activity
 * instance is needed to launch an intent.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
abstract class ResultHandler internal constructor(
    internal val activity: Activity,
    private val parsedResult: ParsedResult,
    private val rawResult: Result? = null
) {

  private val customProductSearch: String? = parseCustomSearchURL()

  fun getResult(): ParsedResult = parsedResult

  internal fun hasCustomProductSearch(): Boolean = customProductSearch != null

  /**
   * Indicates how many buttons the derived class wants shown.
   *
   * @return The integer button count.
   */
  abstract fun getButtonCount(): Int

  /**
   * The text of the nth action button.
   *
   * @param index From 0 to getButtonCount() - 1
   * @return The button text as a resource ID
   */
  abstract fun getButtonText(index: Int): Int

  open fun getDefaultButtonID(): Int? = null

  /**
   * Execute the action which corresponds to the nth button.
   *
   * @param index The button that was clicked.
   */
  abstract fun handleButtonPress(index: Int)

  /**
   * Some barcode contents are considered secure, and should not be saved to history, copied to
   * the clipboard, or otherwise persisted.
   *
   * @return If true, do not create any permanent record of these contents.
   */
  open fun areContentsSecure(): Boolean = false

  /**
   * Create a possibly styled string for the contents of the current barcode.
   *
   * @return The text to be displayed.
   */
  open fun getDisplayContents(): CharSequence = parsedResult.displayResult.replace("\r", "")

  /**
   * A string describing the kind of barcode that was found, e.g. "Found contact info".
   *
   * @return The resource ID of the string.
   */
  abstract fun getDisplayTitle(): Int

  /**
   * A convenience method to get the parsed type. Should not be overridden.
   *
   * @return The parsed type, e.g. URI or ISBN
   */
  fun getType(): ParsedResultType = parsedResult.type

  internal fun addPhoneOnlyContact(phoneNumbers: Array<String>?, phoneTypes: Array<String>?) {
    addContact(null, null, null, phoneNumbers, phoneTypes,
        null, null, null, null, null, null, null, null, null, null, null)
  }

  internal fun addEmailOnlyContact(emails: Array<String>?, emailTypes: Array<String>?) {
    addContact(null, null, null, null, null, emails, emailTypes,
        null, null, null, null, null, null, null, null, null)
  }

  internal fun addContact(names: Array<String>?,
                          nicknames: Array<String>?,
                          pronunciation: String?,
                          phoneNumbers: Array<String>?,
                          phoneTypes: Array<String>?,
                          emails: Array<String>?,
                          emailTypes: Array<String>?,
                          note: String?,
                          instantMessenger: String?,
                          address: String?,
                          addressType: String?,
                          org: String?,
                          title: String?,
                          urls: Array<String>?,
                          birthday: String?,
                          geo: Array<String>?) {

    // Only use the first name in the array, if present.
    val intent = Intent(Intent.ACTION_INSERT_OR_EDIT, ContactsContract.Contacts.CONTENT_URI)
    intent.type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
    putExtra(intent, ContactsContract.Intents.Insert.NAME,
        if (names != null && names.isNotEmpty()) names[0] else null)

    putExtra(intent, ContactsContract.Intents.Insert.PHONETIC_NAME, pronunciation)

    if (phoneNumbers != null) {
      val phoneCount = minOf(phoneNumbers.size, Contents.PHONE_KEYS.size)
      for (x in 0 until phoneCount) {
        putExtra(intent, Contents.PHONE_KEYS[x], phoneNumbers[x])
        if (phoneTypes != null && x < phoneTypes.size) {
          val type = toPhoneContractType(phoneTypes[x])
          if (type >= 0) {
            intent.putExtra(Contents.PHONE_TYPE_KEYS[x], type)
          }
        }
      }
    }

    if (emails != null) {
      val emailCount = minOf(emails.size, Contents.EMAIL_KEYS.size)
      for (x in 0 until emailCount) {
        putExtra(intent, Contents.EMAIL_KEYS[x], emails[x])
        if (emailTypes != null && x < emailTypes.size) {
          val type = toEmailContractType(emailTypes[x])
          if (type >= 0) {
            intent.putExtra(Contents.EMAIL_TYPE_KEYS[x], type)
          }
        }
      }
    }

    val data = ArrayList<ContentValues>()
    if (urls != null) {
      for (url in urls) {
        if (!url.isNullOrEmpty()) {
          val row = ContentValues(2)
          row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
          row.put(ContactsContract.CommonDataKinds.Website.URL, url)
          data.add(row)
          break
        }
      }
    }

    if (birthday != null) {
      val row = ContentValues(3)
      row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
      row.put(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
      row.put(ContactsContract.CommonDataKinds.Event.START_DATE, birthday)
      data.add(row)
    }

    if (nicknames != null) {
      for (nickname in nicknames) {
        if (!nickname.isNullOrEmpty()) {
          val row = ContentValues(3)
          row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
          row.put(ContactsContract.CommonDataKinds.Nickname.TYPE,
                  ContactsContract.CommonDataKinds.Nickname.TYPE_DEFAULT)
          row.put(ContactsContract.CommonDataKinds.Nickname.NAME, nickname)
          data.add(row)
          break
        }
      }
    }

    if (data.isNotEmpty()) {
      intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data)
    }

    val aggregatedNotes = StringBuilder()
    if (note != null) {
      aggregatedNotes.append('\n').append(note)
    }
    if (geo != null && geo.size >= 2) {
      aggregatedNotes.append('\n').append(geo[0]).append(',').append(geo[1])
    }

    if (aggregatedNotes.isNotEmpty()) {
      // Remove extra leading '\n'
      putExtra(intent, ContactsContract.Intents.Insert.NOTES, aggregatedNotes.substring(1))
    }

    if (instantMessenger != null && instantMessenger.startsWith("xmpp:")) {
      intent.putExtra(ContactsContract.Intents.Insert.IM_PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER)
      intent.putExtra(ContactsContract.Intents.Insert.IM_HANDLE, instantMessenger.substring(5))
    } else {
      putExtra(intent, ContactsContract.Intents.Insert.IM_HANDLE, instantMessenger)
    }

    putExtra(intent, ContactsContract.Intents.Insert.POSTAL, address)
    if (addressType != null) {
      val type = toAddressContractType(addressType)
      if (type >= 0) {
        intent.putExtra(ContactsContract.Intents.Insert.POSTAL_TYPE, type)
      }
    }
    putExtra(intent, ContactsContract.Intents.Insert.COMPANY, org)
    putExtra(intent, ContactsContract.Intents.Insert.JOB_TITLE, title)
    launchIntent(intent)
  }

  internal fun shareByEmail(contents: String) {
    sendEmail(null, null, null, null, contents)
  }

  internal fun sendEmail(to: Array<String>?,
                         cc: Array<String>?,
                         bcc: Array<String>?,
                         subject: String?,
                         body: String?) {
    val intent = Intent(Intent.ACTION_SEND, Uri.parse("mailto:"))
    if (to != null && to.isNotEmpty()) {
      intent.putExtra(Intent.EXTRA_EMAIL, to)
    }
    if (cc != null && cc.isNotEmpty()) {
      intent.putExtra(Intent.EXTRA_CC, cc)
    }
    if (bcc != null && bcc.isNotEmpty()) {
      intent.putExtra(Intent.EXTRA_BCC, bcc)
    }
    putExtra(intent, Intent.EXTRA_SUBJECT, subject)
    putExtra(intent, Intent.EXTRA_TEXT, body)
    intent.type = "text/plain"
    launchIntent(intent)
  }

  internal fun shareBySMS(contents: String) {
    sendSMSFromUri("smsto:", contents)
  }

  internal fun sendSMS(phoneNumber: String, body: String?) {
    sendSMSFromUri("smsto:$phoneNumber", body)
  }

  private fun sendSMSFromUri(uri: String, body: String?) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uri))
    putExtra(intent, "sms_body", body)
    // Exit the app once the SMS is sent
    intent.putExtra("compose_mode", true)
    launchIntent(intent)
  }

  internal fun sendMMS(phoneNumber: String, subject: String?, body: String?) {
    sendMMSFromUri("mmsto:$phoneNumber", subject, body)
  }

  private fun sendMMSFromUri(uri: String, subject: String?, body: String?) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uri))
    // The Messaging app needs to see a valid subject or else it will treat this an an SMS.
    if (subject.isNullOrEmpty()) {
      putExtra(intent, "subject", activity.getString(R.string.msg_default_mms_subject))
    } else {
      putExtra(intent, "subject", subject)
    }
    putExtra(intent, "sms_body", body)
    intent.putExtra("compose_mode", true)
    launchIntent(intent)
  }

  internal fun dialPhone(phoneNumber: String) {
    launchIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")))
  }

  internal fun dialPhoneFromUri(uri: String) {
    launchIntent(Intent(Intent.ACTION_DIAL, Uri.parse(uri)))
  }

  internal fun openMap(geoURI: String) {
    launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse(geoURI)))
  }

  /**
   * Do a geo search using the address as the query.
   *
   * @param address The address to find
   */
  internal fun searchMap(address: String?) {
    launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))))
  }

  internal fun getDirections(latitude: Double, longitude: Double) {
    launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("http://maps.google." +
        LocaleManager.getCountryTLD(activity) + "/maps?f=d&daddr=" + latitude + ',' + longitude)))
  }

  // Uses the mobile-specific version of Product Search, which is formatted for small screens.
  internal fun openProductSearch(upc: String) {
    val uri = Uri.parse("http://www.google." + LocaleManager.getProductSearchCountryTLD(activity) +
        "/m/products?q=" + upc + "&source=zxing")
    launchIntent(Intent(Intent.ACTION_VIEW, uri))
  }

  internal fun openBookSearch(isbn: String) {
    val uri = Uri.parse("http://books.google." + LocaleManager.getBookSearchCountryTLD(activity) +
        "/books?vid=isbn" + isbn)
    launchIntent(Intent(Intent.ACTION_VIEW, uri))
  }

  internal fun searchBookContents(isbnOrUrl: String) {
    val intent = Intent(Intents.SearchBookContents.ACTION)
    intent.setClassName(activity, SearchBookContentsActivity::class.java.name)
    putExtra(intent, Intents.SearchBookContents.ISBN, isbnOrUrl)
    launchIntent(intent)
  }

  internal fun openURL(url: String) {
    // Strangely, some Android browsers don't seem to register to handle HTTP:// or HTTPS://.
    // Lower-case these as it should always be OK to lower-case these schemes.
    val lowerCased = when {
      url.startsWith("HTTP://") -> "http" + url.substring(4)
      url.startsWith("HTTPS://") -> "https" + url.substring(5)
      else -> url
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(lowerCased))
    try {
      launchIntent(intent)
    } catch (ignored: ActivityNotFoundException) {
      Log.w(TAG, "Nothing available to handle $intent")
    }
  }

  internal fun webSearch(query: String) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra("query", query)
    launchIntent(intent)
  }

  /**
   * Like [launchIntent] but will tell you if it is not handle-able
   * via [ActivityNotFoundException].
   *
   * @throws ActivityNotFoundException if Intent can't be handled
   */
  internal fun rawLaunchIntent(intent: Intent?) {
    if (intent != null) {
      intent.addFlags(Intents.FLAG_NEW_DOC)
      activity.startActivity(intent)
    }
  }

  /**
   * Like [rawLaunchIntent] but will show a user dialog if nothing is available to handle.
   */
  internal fun launchIntent(intent: Intent?) {
    try {
      rawLaunchIntent(intent)
    } catch (ignored: ActivityNotFoundException) {
      val builder = AlertDialog.Builder(activity)
      builder.setTitle(R.string.app_name)
      builder.setMessage(R.string.msg_intent_failed)
      builder.setPositiveButton(R.string.button_ok, null)
      builder.show()
    }
  }

  @Suppress("DEPRECATION") // android.preference.PreferenceManager
  private fun parseCustomSearchURL(): String? {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    val customProductSearch = prefs.getString(PreferencesActivity.KEY_CUSTOM_PRODUCT_SEARCH, null)
    if (customProductSearch != null && customProductSearch.trim().isEmpty()) {
      return null
    }
    return customProductSearch
  }

  internal fun fillInCustomSearchURL(text: String): String {
    if (customProductSearch == null) {
      return text // ?
    }
    var encoded = text
    try {
      encoded = URLEncoder.encode(text, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
      // can't happen; UTF-8 is always supported. Continue, I guess, without encoding
    }
    var url = customProductSearch
    if (rawResult != null) {
      // Replace %f but only if it doesn't seem to be a hex escape sequence. This remains
      // problematic but avoids the more surprising problem of breaking escapes
      url = url.replaceFirst("%f(?![0-9a-f])".toRegex(), rawResult.barcodeFormat.toString())
      if (url.contains("%t")) {
        val parsedResultAgain = ResultParser.parseResult(rawResult)
        url = url.replace("%t", parsedResultAgain.type.toString())
      }
    }
    // Replace %s last as it might contain itself %f or %t
    return url.replace("%s", encoded)
  }

  internal companion object {

    private val TAG = ResultHandler::class.java.simpleName

    private val EMAIL_TYPE_STRINGS = arrayOf("home", "work", "mobile")
    private val PHONE_TYPE_STRINGS = arrayOf("home", "work", "mobile", "fax", "pager", "main")
    private val ADDRESS_TYPE_STRINGS = arrayOf("home", "work")
    private val EMAIL_TYPE_VALUES = intArrayOf(
        ContactsContract.CommonDataKinds.Email.TYPE_HOME,
        ContactsContract.CommonDataKinds.Email.TYPE_WORK,
        ContactsContract.CommonDataKinds.Email.TYPE_MOBILE)
    private val PHONE_TYPE_VALUES = intArrayOf(
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME,
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK,
        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER,
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN)
    private val ADDRESS_TYPE_VALUES = intArrayOf(
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME,
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK)
    private const val NO_TYPE = -1

    // Public, and a compile-time constant, because CaptureActivity is still Java and reads it.
    const val MAX_BUTTON_COUNT = 4

    private fun toEmailContractType(typeString: String?): Int =
        doToContractType(typeString, EMAIL_TYPE_STRINGS, EMAIL_TYPE_VALUES)

    private fun toPhoneContractType(typeString: String?): Int =
        doToContractType(typeString, PHONE_TYPE_STRINGS, PHONE_TYPE_VALUES)

    private fun toAddressContractType(typeString: String?): Int =
        doToContractType(typeString, ADDRESS_TYPE_STRINGS, ADDRESS_TYPE_VALUES)

    private fun doToContractType(typeString: String?, types: Array<String>, values: IntArray): Int {
      if (typeString == null) {
        return NO_TYPE
      }
      for (i in types.indices) {
        val type = types[i]
        if (typeString.startsWith(type) || typeString.startsWith(type.uppercase(Locale.ENGLISH))) {
          return values[i]
        }
      }
      return NO_TYPE
    }

    private fun putExtra(intent: Intent, key: String, value: String?) {
      if (!value.isNullOrEmpty()) {
        intent.putExtra(key, value)
      }
    }

    @Suppress("DEPRECATION") // PhoneNumberUtils.formatNumber(String)
    internal fun formatPhone(phoneData: String): String? {
      // Just collect the call to a deprecated method in one place
      return PhoneNumberUtils.formatNumber(phoneData)
    }
  }
}
