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

import java.util.regex.Pattern

/**
 * Encodes contact information according to the MECARD format.
 *
 * @author Sean Owen
 */
internal class MECARDContactEncoder : ContactEncoder() {

  override fun encode(names: List<String?>?,
                      organization: String?,
                      addresses: List<String?>?,
                      phones: List<String?>?,
                      phoneTypes: List<String?>?,
                      emails: List<String?>?,
                      urls: List<String?>?,
                      note: String?): Array<String> {
    val newContents = StringBuilder(100)
    newContents.append("MECARD:")

    val newDisplayContents = StringBuilder(100)

    val fieldFormatter: Formatter = MECARDFieldFormatter()

    appendUpToUnique(newContents, newDisplayContents, "N", names, 1,
                     MECARDNameDisplayFormatter(), fieldFormatter, TERMINATOR)

    append(newContents, newDisplayContents, "ORG", organization, fieldFormatter, TERMINATOR)

    appendUpToUnique(newContents, newDisplayContents, "ADR", addresses, 1, null, fieldFormatter, TERMINATOR)

    appendUpToUnique(newContents, newDisplayContents, "TEL", phones, Int.MAX_VALUE,
                     MECARDTelDisplayFormatter(), fieldFormatter, TERMINATOR)

    appendUpToUnique(newContents, newDisplayContents, "EMAIL", emails, Int.MAX_VALUE, null,
                     fieldFormatter, TERMINATOR)

    appendUpToUnique(newContents, newDisplayContents, "URL", urls, Int.MAX_VALUE, null,
                     fieldFormatter, TERMINATOR)

    append(newContents, newDisplayContents, "NOTE", note, fieldFormatter, TERMINATOR)

    newContents.append(';')

    return arrayOf(newContents.toString(), newDisplayContents.toString())
  }

  private class MECARDFieldFormatter : Formatter {
    override fun format(value: CharSequence, index: Int): CharSequence =
        ":" + NEWLINE.matcher(RESERVED_MECARD_CHARS.matcher(value).replaceAll("\\\\\$1")).replaceAll("")

    private companion object {
      private val RESERVED_MECARD_CHARS: Pattern = Pattern.compile("([\\\\:;])")
      private val NEWLINE: Pattern = Pattern.compile("\\n")
    }
  }

  private class MECARDTelDisplayFormatter : Formatter {
    override fun format(value: CharSequence, index: Int): CharSequence =
        NOT_DIGITS_OR_PLUS.matcher(ContactEncoder.formatPhone(value.toString())).replaceAll("")

    private companion object {
      private val NOT_DIGITS_OR_PLUS: Pattern = Pattern.compile("[^0-9+]+")
    }
  }

  private class MECARDNameDisplayFormatter : Formatter {
    override fun format(value: CharSequence, index: Int): CharSequence =
        COMMA.matcher(value).replaceAll("")

    private companion object {
      private val COMMA: Pattern = Pattern.compile(",")
    }
  }

  private companion object {
    private const val TERMINATOR = ';'
  }
}
