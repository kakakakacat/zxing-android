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

/**
 * @author Sean Owen
 */
internal class VCardTelDisplayFormatter(
    private val metadataForIndex: List<Map<String, Set<String>?>?>? = null
) : Formatter {

  override fun format(value: CharSequence, index: Int): CharSequence {
    val formatted = ContactEncoder.formatPhone(value.toString())
    val metadata = if (metadataForIndex == null || metadataForIndex.size <= index) {
      null
    } else {
      metadataForIndex[index]
    }
    return formatMetadata(formatted, metadata)
  }

  private companion object {

    private fun formatMetadata(value: CharSequence, metadata: Map<String, Set<String>?>?): CharSequence {
      if (metadata.isNullOrEmpty()) {
        return value
      }
      val withMetadata = StringBuilder()
      for ((_, values) in metadata) {
        if (values.isNullOrEmpty()) {
          continue
        }
        withMetadata.append(values.joinToString(","))
      }
      if (withMetadata.isNotEmpty()) {
        withMetadata.append(' ')
      }
      withMetadata.append(value)
      return withMetadata
    }
  }
}
