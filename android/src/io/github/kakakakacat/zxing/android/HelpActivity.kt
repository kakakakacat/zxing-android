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

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView

/**
 * An HTML-based help screen.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class HelpActivity : Activity() {

  private lateinit var webView: WebView

  override fun onCreate(icicle: Bundle?) {
    super.onCreate(icicle)
    setContentView(R.layout.help)

    webView = findViewById(R.id.help_contents)

    if (icicle == null) {
      webView.loadUrl(BASE_URL + "index.html")
    } else {
      webView.restoreState(icicle)
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
      webView.goBack()
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onSaveInstanceState(icicle: Bundle) {
    super.onSaveInstanceState(icicle)
    webView.saveState(icicle)
  }

  private companion object {
    private val BASE_URL =
        "file:///android_asset/html-" + LocaleManager.getTranslatedAssetLanguage() + '/'
  }
}
