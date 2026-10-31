/*
 * Copyright (C) 2013 ZXing authors
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

import android.content.Intent
import android.net.Uri
import android.util.Log
import io.github.kakakakacat.zxing.DecodeHintType
import java.util.EnumMap
import java.util.regex.Pattern

/**
 * The decode hints carried by a SCAN Intent or a scan URI.
 *
 * Public members for the same reason as [DecodeFormatManager]: `:app`'s Java
 * `CaptureActivity` is the caller and an `internal` function's name is mangled out of its
 * reach.
 *
 * @author Lachezar Dobrev
 */
internal object DecodeHintManager {

  private val TAG: String = DecodeHintManager::class.java.simpleName

  // This pattern is used in decoding integer arrays.
  private val COMMA: Pattern = Pattern.compile(",")

  /**
   * Split a query string into a list of name-value pairs.
   *
   * This is an alternative to [Uri.getQueryParameterNames] and [Uri.getQueryParameters], which
   * are quirky and not suitable for exist-only Uri parameters.
   *
   * This method ignores multiple parameters with the same name and returns the first one only.
   * This is technically incorrect, but should be acceptable due to the method of processing
   * Hints: no multiple values for a hint.
   *
   * @param query query to split
   * @return name-value pairs
   */
  private fun splitQuery(query: String): Map<String, String> {
    val map = HashMap<String, String>()
    var pos = 0
    while (pos < query.length) {
      if (query[pos] == '&') {
        // Skip consecutive ampersand separators.
        pos++
        continue
      }
      val amp = query.indexOf('&', pos)
      val equ = query.indexOf('=', pos)
      if (amp < 0) {
        // This is the last element in the query, no more ampersand elements.
        val name: String
        val text: String
        if (equ < 0) {
          // No equal sign
          name = Uri.decode(query.substring(pos).replace('+', ' ')) // Preemptively decode +
          text = ""
        } else {
          // Split name and text.
          name = Uri.decode(query.substring(pos, equ).replace('+', ' '))
          text = Uri.decode(query.substring(equ + 1).replace('+', ' '))
        }
        if (!map.containsKey(name)) {
          map[name] = text
        }
        break
      }
      if (equ < 0 || equ > amp) {
        // No equal sign until the &: this is a simple parameter with no value.
        val name = Uri.decode(query.substring(pos, amp).replace('+', ' '))
        if (!map.containsKey(name)) {
          map[name] = ""
        }
        pos = amp + 1
        continue
      }
      val name = Uri.decode(query.substring(pos, equ).replace('+', ' '))
      val text = Uri.decode(query.substring(equ + 1, amp).replace('+', ' '))
      if (!map.containsKey(name)) {
        map[name] = text
      }
      pos = amp + 1
    }
    return map
  }

  @JvmStatic
  fun parseDecodeHints(inputUri: Uri): Map<DecodeHintType, *>? {
    val query = inputUri.encodedQuery
    if (query == null || query.isEmpty()) {
      return null
    }

    // Extract parameters
    val parameters = splitQuery(query)

    val hints: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)

    for (hintType in DecodeHintType.values()) {

      if (hintType == DecodeHintType.CHARACTER_SET ||
          hintType == DecodeHintType.NEED_RESULT_POINT_CALLBACK ||
          hintType == DecodeHintType.POSSIBLE_FORMATS) {
        continue // This hint is specified in another way
      }

      var parameterText = parameters[hintType.name] ?: continue
      if (hintType.valueType == Any::class.java) {
        // This is an unspecified type of hint content. Use the value as is.
        // TODO: Can we make a different assumption on this?
        hints[hintType] = parameterText
        continue
      }
      if (hintType.valueType == Void::class.java) {
        // Void hints are just flags: use the constant specified by DecodeHintType
        hints[hintType] = java.lang.Boolean.TRUE
        continue
      }
      if (hintType.valueType == String::class.java) {
        // A string hint: use the decoded value.
        hints[hintType] = parameterText
        continue
      }
      // javaObjectType, not java: `Boolean::class.java` is `boolean.class` and would never
      // equal the `Boolean.class` the Java source compared against. DecodeHintValueTypesTest
      // pins this.
      if (hintType.valueType == Boolean::class.javaObjectType) {
        // A boolean hint: a few values for false, everything else is true.
        // An empty parameter is simply a flag-style parameter, assuming true
        hints[hintType] = if (parameterText.isEmpty()) {
          java.lang.Boolean.TRUE
        } else if ("0" == parameterText ||
            "false".equals(parameterText, ignoreCase = true) ||
            "no".equals(parameterText, ignoreCase = true)) {
          java.lang.Boolean.FALSE
        } else {
          java.lang.Boolean.TRUE
        }
        continue
      }
      if (hintType.valueType == IntArray::class.java) {
        // An integer array. Used to specify valid lengths.
        // Strip a trailing comma as in Java style array initialisers.
        if (parameterText.isNotEmpty() && parameterText[parameterText.length - 1] == ',') {
          parameterText = parameterText.substring(0, parameterText.length - 1)
        }
        val values = COMMA.split(parameterText)
        var array: IntArray? = IntArray(values.size)
        for (i in values.indices) {
          try {
            array!![i] = values[i].toInt()
          } catch (ignored: NumberFormatException) {
            Log.w(TAG, "Skipping array of integers hint $hintType due to invalid numeric value")
            array = null
            break
          }
        }
        if (array != null) {
          hints[hintType] = array
        }
        continue
      }
      Log.w(TAG, "Unsupported hint type '$hintType' of type " + hintType.valueType)
    }

    return hints
  }

  @JvmStatic
  fun parseDecodeHints(intent: Intent): Map<DecodeHintType, @JvmSuppressWildcards Any>? {
    val extras = intent.extras
    if (extras == null || extras.isEmpty) {
      return null
    }
    val hints: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)

    for (hintType in DecodeHintType.values()) {

      if (hintType == DecodeHintType.CHARACTER_SET ||
          hintType == DecodeHintType.NEED_RESULT_POINT_CALLBACK ||
          hintType == DecodeHintType.POSSIBLE_FORMATS) {
        continue // This hint is specified in another way
      }

      val hintName = hintType.name
      if (extras.containsKey(hintName)) {
        if (hintType.valueType == Void::class.java) {
          // Void hints are just flags: use the constant specified by the DecodeHintType
          hints[hintType] = java.lang.Boolean.TRUE
        } else {
          @Suppress("DEPRECATION")
          val hintData = extras.get(hintName)
          if (hintType.valueType.isInstance(hintData)) {
            hints[hintType] = hintData!!
          } else {
            Log.w(TAG, "Ignoring hint $hintType because it is not a " + hintType.valueType)
          }
        }
      }
    }

    return hints
  }
}
