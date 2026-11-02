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
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/**
 * An HTML-based help screen.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class HelpActivity : Activity() {

  private lateinit var webView: WebView

  /** Held as `Any?` so the API 33 type is never named in a field signature; see CaptureActivity. */
  private var backInvokedCallback: Any? = null

  override fun onCreate(icicle: Bundle?) {
    super.onCreate(icicle)
    setContentView(R.layout.help)

    webView = findViewById(R.id.help_contents)

    if (icicle == null) {
      webView.loadUrl(BASE_URL + "index.html")
    } else {
      webView.restoreState(icicle)
    }
    registerBackCallback()
  }

  // Below API 33 the back key still arrives here; at 33 and above it does not, and the
  // callback registered in onCreate does this instead. See CaptureActivity.syncBackCallback
  // for why the key stopped arriving at all.
  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
      webView.goBack()
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  /**
   * Registered for the life of the activity rather than only while the WebView has history,
   * which is what [CaptureActivity] does.
   *
   * `WebView` reports `canGoBack` on demand and has no callback for it changing, so keeping
   * a registration in step with it would mean polling. The cost of registering throughout is
   * that the system cannot draw a cross-activity preview animation for the back that leaves
   * this screen — on a help screen behind a WebView, that is the cheaper of the two.
   */
  private fun registerBackCallback() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return
    }
    val callback = OnBackInvokedCallback {
      if (webView.canGoBack()) {
        webView.goBack()
      } else {
        finish()
      }
    }
    onBackInvokedDispatcher.registerOnBackInvokedCallback(
        OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
    backInvokedCallback = callback
  }

  override fun onDestroy() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      (backInvokedCallback as OnBackInvokedCallback?)?.let {
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
      }
      backInvokedCallback = null
    }
    super.onDestroy()
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
