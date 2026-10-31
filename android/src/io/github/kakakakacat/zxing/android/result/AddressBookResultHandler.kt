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
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.client.result.AddressBookParsedResult
import io.github.kakakakacat.zxing.client.result.ParsedResult
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Handles address book entries.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class AddressBookResultHandler(activity: Activity, result: ParsedResult) :
    ResultHandler(activity, result) {

  private val fields: BooleanArray
  private val buttonCount: Int

  init {
    val addressResult = result as AddressBookParsedResult
    val addresses = addressResult.addresses
    val phoneNumbers = addressResult.phoneNumbers
    val emails = addressResult.emails

    fields = BooleanArray(MAX_BUTTON_COUNT)
    fields[0] = true // Add contact is always available
    fields[1] = addresses != null && addresses.isNotEmpty() && !addresses[0].isNullOrEmpty()
    fields[2] = phoneNumbers != null && phoneNumbers.isNotEmpty()
    fields[3] = emails != null && emails.isNotEmpty()

    buttonCount = fields.count { it }
  }

  // This takes all the work out of figuring out which buttons/actions should be in which
  // positions, based on which fields are present in this barcode.
  private fun mapIndexToAction(index: Int): Int {
    if (index < buttonCount) {
      var count = -1
      for (x in 0 until MAX_BUTTON_COUNT) {
        if (fields[x]) {
          count++
        }
        if (count == index) {
          return x
        }
      }
    }
    return -1
  }

  override fun getButtonCount(): Int = buttonCount

  override fun getButtonText(index: Int): Int = BUTTON_TEXTS[mapIndexToAction(index)]

  override fun handleButtonPress(index: Int) {
    val addressResult = getResult() as AddressBookParsedResult
    val addresses = addressResult.addresses
    val address1 = if (addresses == null || addresses.isEmpty()) null else addresses[0]
    val addressTypes = addressResult.addressTypes
    val address1Type = if (addressTypes == null || addressTypes.isEmpty()) null else addressTypes[0]
    when (mapIndexToAction(index)) {
      0 -> addContact(addressResult.names,
                      addressResult.nicknames,
                      addressResult.pronunciation,
                      addressResult.phoneNumbers,
                      addressResult.phoneTypes,
                      addressResult.emails,
                      addressResult.emailTypes,
                      addressResult.note,
                      addressResult.instantMessenger,
                      address1,
                      address1Type,
                      addressResult.org,
                      addressResult.title,
                      addressResult.getURLs(),
                      addressResult.birthday,
                      addressResult.geo)
      1 -> searchMap(address1)
      2 -> dialPhone(addressResult.phoneNumbers[0])
      3 -> sendEmail(addressResult.emails, null, null, null, null)
    }
  }

  // Overriden so we can hyphenate phone numbers, format birthdays, and bold the name.
  override fun getDisplayContents(): CharSequence {
    val result = getResult() as AddressBookParsedResult
    val contents = StringBuilder(100)
    ParsedResult.maybeAppend(result.names, contents)
    val namesLength = contents.length

    val pronunciation = result.pronunciation
    if (!pronunciation.isNullOrEmpty()) {
      contents.append("\n(")
      contents.append(pronunciation)
      contents.append(')')
    }

    ParsedResult.maybeAppend(result.title, contents)
    ParsedResult.maybeAppend(result.org, contents)
    ParsedResult.maybeAppend(result.addresses, contents)
    val numbers = result.phoneNumbers
    if (numbers != null) {
      for (number in numbers) {
        if (number != null) {
          ParsedResult.maybeAppend(formatPhone(number), contents)
        }
      }
    }
    ParsedResult.maybeAppend(result.emails, contents)
    ParsedResult.maybeAppend(result.getURLs(), contents)

    val birthday = result.birthday
    if (!birthday.isNullOrEmpty()) {
      val date = parseDate(birthday)
      if (date >= 0L) {
        ParsedResult.maybeAppend(DateFormat.getDateInstance(DateFormat.MEDIUM).format(date), contents)
      }
    }
    ParsedResult.maybeAppend(result.note, contents)

    return if (namesLength > 0) {
      // Bold the full name to make it stand out a bit.
      val styled: Spannable = SpannableString(contents.toString())
      styled.setSpan(StyleSpan(Typeface.BOLD), 0, namesLength, 0)
      styled
    } else {
      contents.toString()
    }
  }

  override fun getDisplayTitle(): Int = R.string.result_address_book

  private companion object {

    private val DATE_FORMATS = arrayOf<DateFormat>(
        SimpleDateFormat("yyyyMMdd", Locale.ENGLISH),
        SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)).apply {
      forEach { it.isLenient = false }
    }

    private val BUTTON_TEXTS = intArrayOf(
        R.string.button_add_contact,
        R.string.button_show_map,
        R.string.button_dial,
        R.string.button_email)

    private fun parseDate(s: String): Long {
      for (currentFormat in DATE_FORMATS) {
        try {
          return currentFormat.parse(s).time
        } catch (e: ParseException) {
          // continue
        }
      }
      return -1L
    }
  }
}
