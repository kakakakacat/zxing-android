/*
 * Copyright (C) 2011 ZXing authors
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

import android.net.Uri
import io.github.kakakakacat.zxing.Result
import io.github.kakakakacat.zxing.android.result.ResultHandler
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

/**
 * Manages functionality related to responding to requests to scan from an HTTP link in a web page.
 * See [ScanningFromWebPages](https://github.com/zxing/zxing/wiki/ScanningFromWebPages).
 *
 * Members are plain public, not internal: CaptureActivity is still Java. See
 * docs/maint/HANDOFF.md.
 *
 * @author Sean Owen
 */
internal class ScanFromWebPageManager(inputUri: Uri) {

  private val returnUrlTemplate: String? = inputUri.getQueryParameter(RETURN_URL_PARAM)
  private val returnRaw: Boolean = inputUri.getQueryParameter(RAW_PARAM) != null

  fun isScanFromWebPage(): Boolean = returnUrlTemplate != null

  fun buildReplyURL(rawResult: Result, resultHandler: ResultHandler): String {
    var result = returnUrlTemplate!!
    result = replace(CODE_PLACEHOLDER,
                     if (returnRaw) rawResult.text else resultHandler.getDisplayContents(), result)
    result = replace(RAW_CODE_PLACEHOLDER, rawResult.text, result)
    result = replace(FORMAT_PLACEHOLDER, rawResult.barcodeFormat.toString(), result)
    result = replace(TYPE_PLACEHOLDER, resultHandler.getType().toString(), result)
    result = replace(META_PLACEHOLDER, "${rawResult.resultMetadata}", result)
    return result
  }

  private companion object {

    private const val CODE_PLACEHOLDER = "{CODE}"
    private const val RAW_CODE_PLACEHOLDER = "{RAWCODE}"
    private const val META_PLACEHOLDER = "{META}"
    private const val FORMAT_PLACEHOLDER = "{FORMAT}"
    private const val TYPE_PLACEHOLDER = "{TYPE}"

    private const val RETURN_URL_PARAM = "ret"
    private const val RAW_PARAM = "raw"

    private fun replace(placeholder: CharSequence, with: CharSequence?, pattern: String): String {
      var escapedWith: CharSequence = with ?: ""
      try {
        escapedWith = URLEncoder.encode(escapedWith.toString(), "UTF-8")
      } catch (e: UnsupportedEncodingException) {
        // can't happen; UTF-8 is always supported. Continue, I guess, without encoding
      }
      return pattern.replace(placeholder.toString(), escapedWith.toString())
    }
  }
}
