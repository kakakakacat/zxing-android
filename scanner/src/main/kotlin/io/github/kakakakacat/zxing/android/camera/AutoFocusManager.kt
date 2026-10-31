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
import android.os.AsyncTask
import android.util.Log
import java.util.concurrent.RejectedExecutionException

@Suppress("DEPRECATION") // camera APIs, and AsyncTask
internal class AutoFocusManager(
    private val camera: Camera,
    autoFocusEnabled: Boolean
) : Camera.AutoFocusCallback {

  private var stopped = false
  private var focusing = false
  private val useAutoFocus: Boolean
  private var outstandingTask: AsyncTask<*, *, *>? = null

  init {
    val currentFocusMode = camera.parameters.focusMode
    useAutoFocus = autoFocusEnabled && FOCUS_MODES_CALLING_AF.contains(currentFocusMode)
    Log.i(TAG, "Current focus mode '$currentFocusMode'; use auto focus? $useAutoFocus")
    start()
  }

  @Synchronized
  override fun onAutoFocus(success: Boolean, theCamera: Camera?) {
    focusing = false
    autoFocusAgainLater()
  }

  @Synchronized
  private fun autoFocusAgainLater() {
    if (!stopped && outstandingTask == null) {
      val newTask = AutoFocusTask()
      try {
        newTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        outstandingTask = newTask
      } catch (ree: RejectedExecutionException) {
        Log.w(TAG, "Could not request auto focus", ree)
      }
    }
  }

  @Synchronized
  fun start() {
    if (useAutoFocus) {
      outstandingTask = null
      if (!stopped && !focusing) {
        try {
          camera.autoFocus(this)
          focusing = true
        } catch (re: RuntimeException) {
          // Have heard RuntimeException reported in Android 4.0.x+; continue?
          Log.w(TAG, "Unexpected exception while focusing", re)
          // Try again later to keep cycle going
          autoFocusAgainLater()
        }
      }
    }
  }

  @Synchronized
  private fun cancelOutstandingTask() {
    val task = outstandingTask
    if (task != null) {
      if (task.status != AsyncTask.Status.FINISHED) {
        task.cancel(true)
      }
      outstandingTask = null
    }
  }

  @Synchronized
  fun stop() {
    stopped = true
    if (useAutoFocus) {
      cancelOutstandingTask()
      // Doesn't hurt to call this even if not focusing
      try {
        camera.cancelAutoFocus()
      } catch (re: RuntimeException) {
        // Have heard RuntimeException reported in Android 4.0.x+; continue?
        Log.w(TAG, "Unexpected exception while cancelling focusing", re)
      }
    }
  }

  private inner class AutoFocusTask : AsyncTask<Any, Any, Any?>() {
    override fun doInBackground(vararg voids: Any): Any? {
      try {
        Thread.sleep(AUTO_FOCUS_INTERVAL_MS)
      } catch (e: InterruptedException) {
        // continue
      }
      start()
      return null
    }
  }

  private companion object {
    private val TAG: String = AutoFocusManager::class.java.simpleName

    private const val AUTO_FOCUS_INTERVAL_MS = 2000L
    private val FOCUS_MODES_CALLING_AF: Collection<String> =
        listOf(Camera.Parameters.FOCUS_MODE_AUTO, Camera.Parameters.FOCUS_MODE_MACRO)
  }
}
