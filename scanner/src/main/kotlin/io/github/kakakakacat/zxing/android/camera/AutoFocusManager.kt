/*
 * Copyright (C) 2012 ZXing authors
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

package io.github.kakakakacat.zxing.android.camera

import android.hardware.Camera
import android.os.Handler
import android.os.Looper
import android.util.Log

@Suppress("DEPRECATION") // Camera1
internal class AutoFocusManager(private val camera: Camera, autoFocusEnabled: Boolean) {
  private val owner = Handler(Looper.myLooper() ?: Looper.getMainLooper())
  private val loop: AutoFocusLoop?
  init {
    val mode = camera.parameters.focusMode
    val enabled = autoFocusEnabled && mode in listOf(
        Camera.Parameters.FOCUS_MODE_AUTO, Camera.Parameters.FOCUS_MODE_MACRO)
    loop = if (!enabled) null else AutoFocusLoop(
        { done -> camera.autoFocus { _, _ -> done() } },
        { onOwner {
          try { camera.cancelAutoFocus() }
          catch (failure: RuntimeException) { Log.w("AutoFocusManager", "Could not cancel focus", failure) }
        } },
        { task, delay -> owner.postDelayed(task, delay); Unit },
        { task -> owner.removeCallbacks(task) },
        { failure -> Log.w("AutoFocusManager", "Camera focus operation failed", failure) })
    start()
  }
  private fun onOwner(action: () -> Unit) {
    if (Looper.myLooper() === owner.looper) action() else owner.post { action() }
  }
  fun start() { onOwner { loop?.start() } }
  fun stop() { loop?.stop() }
}
