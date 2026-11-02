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

import android.telephony.PhoneNumberUtils

/**
 * Implementations encode according to some scheme for encoding contact information, like VCard or
 * MECARD.
 *
 * @author Sean Owen
 */
internal abstract class ContactEncoder {

  /**
   * @return first, the best effort encoding of all data in the appropriate format; second, a
   *   display-appropriate version of the contact information
   */
  abstract fun encode(names: List<String?>?,
                      organization: String?,
                      addresses: List<String?>?,
                      phones: List<String?>?,
                      phoneTypes: List<String?>?,
                      emails: List<String?>?,
                      urls: List<String?>?,
                      note: String?): Array<String>

  companion object {

    /**
     * @return null if s is null or empty, or result of s.trim() otherwise
     */
    fun trim(s: String?): String? {
      if (s == null) {
        return null
      }
      val result = s.trim()
      return result.ifEmpty { null }
    }

    fun append(newContents: StringBuilder,
               newDisplayContents: StringBuilder,
               prefix: String,
               value: String?,
               fieldFormatter: Formatter,
               terminator: Char) {
      val trimmed = trim(value)
      if (trimmed != null) {
        newContents.append(prefix).append(fieldFormatter.format(trimmed, 0)).append(terminator)
        newDisplayContents.append(trimmed).append('\n')
      }
    }

    fun appendUpToUnique(newContents: StringBuilder,
                         newDisplayContents: StringBuilder,
                         prefix: String,
                         values: List<String?>?,
                         max: Int,
                         displayFormatter: Formatter?,
                         fieldFormatter: Formatter,
                         terminator: Char) {
      if (values == null) {
        return
      }
      var count = 0
      val uniques: MutableCollection<String> = HashSet(2)
      for (i in values.indices) {
        val trimmed = trim(values[i])
        if (!trimmed.isNullOrEmpty() && !uniques.contains(trimmed)) {
          newContents.append(prefix).append(fieldFormatter.format(trimmed, i)).append(terminator)
          val display = displayFormatter?.format(trimmed, i) ?: trimmed
          newDisplayContents.append(display).append('\n')
          if (++count == max) {
            break
          }
          uniques.add(trimmed)
        }
      }
    }

    @Suppress("DEPRECATION") // PhoneNumberUtils.formatNumber(String)
    fun formatPhone(phoneData: String): String {
      // Just collect the call to a deprecated method in one place
      return PhoneNumberUtils.formatNumber(phoneData)
    }
  }
}
