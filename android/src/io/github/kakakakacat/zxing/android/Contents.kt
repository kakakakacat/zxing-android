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

package io.github.kakakakacat.zxing.android

import android.provider.ContactsContract

/**
 * The set of constants to use when sending Barcode Scanner an Intent which requests a barcode
 * to be encoded.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
object Contents {

  /**
   * Contains type constants used when sending Intents.
   */
  object Type {
    /**
     * Plain text. Use Intent.putExtra(DATA, string). This can be used for URLs too, but string
     * must include "http://" or "https://".
     */
    const val TEXT = "TEXT_TYPE"

    /**
     * An email type. Use Intent.putExtra(DATA, string) where string is the email address.
     */
    const val EMAIL = "EMAIL_TYPE"

    /**
     * Use Intent.putExtra(DATA, string) where string is the phone number to call.
     */
    const val PHONE = "PHONE_TYPE"

    /**
     * An SMS type. Use Intent.putExtra(DATA, string) where string is the number to SMS.
     */
    const val SMS = "SMS_TYPE"

    /**
     * A contact. Send a request to encode it as follows:
     * ```
     * Intent intent = new Intent(Intents.Encode.ACTION);
     * intent.putExtra(Intents.Encode.TYPE, CONTACT);
     * Bundle bundle = new Bundle();
     * bundle.putString(ContactsContract.Intents.Insert.NAME, "Jenny");
     * bundle.putString(ContactsContract.Intents.Insert.PHONE, "8675309");
     * bundle.putString(ContactsContract.Intents.Insert.EMAIL, "jenny@the80s.com");
     * bundle.putString(ContactsContract.Intents.Insert.POSTAL,
     *                  "123 Fake St. San Francisco, CA 94102");
     * intent.putExtra(Intents.Encode.DATA, bundle);
     * ```
     */
    const val CONTACT = "CONTACT_TYPE"

    /**
     * A geographic location. Use as follows:
     * ```
     * Bundle bundle = new Bundle();
     * bundle.putFloat("LAT", latitude);
     * bundle.putFloat("LONG", longitude);
     * intent.putExtra(Intents.Encode.DATA, bundle);
     * ```
     */
    const val LOCATION = "LOCATION_TYPE"
  }

  const val URL_KEY = "URL_KEY"

  const val NOTE_KEY = "NOTE_KEY"

  /**
   * When using Type.CONTACT, these arrays provide the keys for adding or retrieving multiple
   * phone numbers and addresses.
   */
  @JvmField
  val PHONE_KEYS = arrayOf(
      ContactsContract.Intents.Insert.PHONE,
      ContactsContract.Intents.Insert.SECONDARY_PHONE,
      ContactsContract.Intents.Insert.TERTIARY_PHONE)

  @JvmField
  val PHONE_TYPE_KEYS = arrayOf(
      ContactsContract.Intents.Insert.PHONE_TYPE,
      ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE,
      ContactsContract.Intents.Insert.TERTIARY_PHONE_TYPE)

  @JvmField
  val EMAIL_KEYS = arrayOf(
      ContactsContract.Intents.Insert.EMAIL,
      ContactsContract.Intents.Insert.SECONDARY_EMAIL,
      ContactsContract.Intents.Insert.TERTIARY_EMAIL)

  @JvmField
  val EMAIL_TYPE_KEYS = arrayOf(
      ContactsContract.Intents.Insert.EMAIL_TYPE,
      ContactsContract.Intents.Insert.SECONDARY_EMAIL_TYPE,
      ContactsContract.Intents.Insert.TERTIARY_EMAIL_TYPE)
}
