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

import android.provider.ContactsContract

/**
 * Encodes contact information according to the vCard format.
 *
 * @author Sean Owen
 */
internal class VCardContactEncoder : ContactEncoder() {

  override fun encode(names: List<String?>?,
                      organization: String?,
                      addresses: List<String?>?,
                      phones: List<String?>?,
                      phoneTypes: List<String?>?,
                      emails: List<String?>?,
                      urls: List<String?>?,
                      note: String?): Array<String> {
    val newContents = StringBuilder(100)
    newContents.append("BEGIN:VCARD").append(TERMINATOR)
    newContents.append("VERSION:3.0").append(TERMINATOR)

    val newDisplayContents = StringBuilder(100)

    val fieldFormatter: Formatter = VCardFieldFormatter()

    appendUpToUnique(newContents, newDisplayContents, "N", names, 1, null, fieldFormatter, TERMINATOR)

    append(newContents, newDisplayContents, "ORG", organization, fieldFormatter, TERMINATOR)

    appendUpToUnique(newContents, newDisplayContents, "ADR", addresses, 1, null, fieldFormatter, TERMINATOR)

    val phoneMetadata = buildPhoneMetadata(phones, phoneTypes)
    appendUpToUnique(newContents, newDisplayContents, "TEL", phones, Int.MAX_VALUE,
                     VCardTelDisplayFormatter(phoneMetadata),
                     VCardFieldFormatter(phoneMetadata), TERMINATOR)

    appendUpToUnique(newContents, newDisplayContents, "EMAIL", emails, Int.MAX_VALUE, null,
                     fieldFormatter, TERMINATOR)

    appendUpToUnique(newContents, newDisplayContents, "URL", urls, Int.MAX_VALUE, null,
                     fieldFormatter, TERMINATOR)

    append(newContents, newDisplayContents, "NOTE", note, fieldFormatter, TERMINATOR)

    newContents.append("END:VCARD").append(TERMINATOR)

    return arrayOf(newContents.toString(), newDisplayContents.toString())
  }

  private companion object {

    private const val TERMINATOR = '\n'

    private fun buildPhoneMetadata(phones: Collection<String?>?,
                                   phoneTypes: List<String?>?): List<Map<String, Set<String>?>?>? {
      if (phoneTypes.isNullOrEmpty() || phones == null) {
        return null
      }
      val metadataForIndex = ArrayList<Map<String, Set<String>?>?>()
      for (i in 0 until phones.size) {
        if (phoneTypes.size <= i) {
          metadataForIndex.add(null)
        } else {
          val typeTokens = HashSet<String>()
          metadataForIndex.add(mapOf<String, Set<String>?>("TYPE" to typeTokens))
          val typeString = phoneTypes[i]
          val androidType = maybeIntValue(typeString)
          if (androidType == null) {
            if (typeString != null) {
              typeTokens.add(typeString)
            }
          } else {
            vCardPurposeLabelForAndroidType(androidType)?.let { typeTokens.add(it) }
            vCardContextLabelForAndroidType(androidType)?.let { typeTokens.add(it) }
          }
        }
      }
      return metadataForIndex
    }

    private fun maybeIntValue(value: String?): Int? = value?.toIntOrNull()

    private fun vCardPurposeLabelForAndroidType(androidType: Int): String? = when (androidType) {
      ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME,
      ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK,
      ContactsContract.CommonDataKinds.Phone.TYPE_OTHER_FAX -> "fax"
      ContactsContract.CommonDataKinds.Phone.TYPE_PAGER,
      ContactsContract.CommonDataKinds.Phone.TYPE_WORK_PAGER -> "pager"
      ContactsContract.CommonDataKinds.Phone.TYPE_TTY_TDD -> "textphone"
      ContactsContract.CommonDataKinds.Phone.TYPE_MMS -> "text"
      else -> null
    }

    private fun vCardContextLabelForAndroidType(androidType: Int): String? = when (androidType) {
      ContactsContract.CommonDataKinds.Phone.TYPE_HOME,
      ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
      ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME,
      ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "home"
      ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN,
      ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
      ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE,
      ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK,
      ContactsContract.CommonDataKinds.Phone.TYPE_WORK_PAGER -> "work"
      else -> null
    }
  }
}
