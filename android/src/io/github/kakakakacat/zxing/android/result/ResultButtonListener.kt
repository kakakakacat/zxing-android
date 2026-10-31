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

import android.view.View

/**
 * Handles the result of barcode decoding in the context of the Android platform, by dispatching the
 * proper intents to open other activities like GMail, Maps, etc.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class ResultButtonListener(
    private val resultHandler: ResultHandler,
    private val index: Int
) : View.OnClickListener {

  override fun onClick(view: View?) {
    resultHandler.handleButtonPress(index)
  }
}
