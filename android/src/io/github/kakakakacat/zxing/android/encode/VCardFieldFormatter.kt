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
 * @author Sean Owen
 */
internal class VCardFieldFormatter(
    private val metadataForIndex: List<Map<String, Set<String>?>?>? = null
) : Formatter {

  override fun format(value: CharSequence, index: Int): CharSequence {
    var formatted: CharSequence = RESERVED_VCARD_CHARS.matcher(value).replaceAll("\\\\\$1")
    formatted = NEWLINE.matcher(formatted).replaceAll("")
    val metadata = if (metadataForIndex == null || metadataForIndex.size <= index) {
      null
    } else {
      metadataForIndex[index]
    }
    return formatMetadata(formatted, metadata)
  }

  private companion object {

    private val RESERVED_VCARD_CHARS: Pattern = Pattern.compile("([\\\\,;])")
    private val NEWLINE: Pattern = Pattern.compile("\\n")

    private fun formatMetadata(value: CharSequence, metadata: Map<String, Set<String>?>?): CharSequence {
      val withMetadata = StringBuilder()
      if (metadata != null) {
        for ((key, values) in metadata) {
          if (values.isNullOrEmpty()) {
            continue
          }
          withMetadata.append(';').append(key).append('=')
          if (values.size > 1) {
            withMetadata.append('"')
          }
          withMetadata.append(values.joinToString(","))
          if (values.size > 1) {
            withMetadata.append('"')
          }
        }
      }
      withMetadata.append(':').append(value)
      return withMetadata
    }
  }
}
