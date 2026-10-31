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

import io.github.kakakakacat.zxing.Result
import io.github.kakakakacat.zxing.android.CaptureActivity
import io.github.kakakakacat.zxing.client.result.ParsedResult
import io.github.kakakakacat.zxing.client.result.ParsedResultType
import io.github.kakakakacat.zxing.client.result.ResultParser

/**
 * Manufactures Android-specific handlers based on the barcode content's type.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
object ResultHandlerFactory {

  @JvmStatic
  fun makeResultHandler(activity: CaptureActivity, rawResult: Result): ResultHandler {
    val result = parseResult(rawResult)
    return when (result.type) {
      ParsedResultType.ADDRESSBOOK -> AddressBookResultHandler(activity, result)
      ParsedResultType.EMAIL_ADDRESS -> EmailAddressResultHandler(activity, result)
      ParsedResultType.PRODUCT -> ProductResultHandler(activity, result, rawResult)
      ParsedResultType.URI -> URIResultHandler(activity, result)
      ParsedResultType.WIFI -> WifiResultHandler(activity, result)
      ParsedResultType.GEO -> GeoResultHandler(activity, result)
      ParsedResultType.TEL -> TelResultHandler(activity, result)
      ParsedResultType.SMS -> SMSResultHandler(activity, result)
      ParsedResultType.CALENDAR -> CalendarResultHandler(activity, result)
      ParsedResultType.ISBN -> ISBNResultHandler(activity, result, rawResult)
      else -> TextResultHandler(activity, result, rawResult)
    }
  }

  private fun parseResult(rawResult: Result): ParsedResult = ResultParser.parseResult(rawResult)
}
