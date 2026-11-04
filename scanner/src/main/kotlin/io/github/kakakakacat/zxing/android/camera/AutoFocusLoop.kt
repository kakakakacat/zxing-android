/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.camera

/** No sleeping shared-pool thread. Late callbacks and cancelled timers cannot revive a loop. */
internal class AutoFocusLoop(
    private val focus: (() -> Unit) -> Unit,
    private val cancelFocus: () -> Unit,
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val onFailure: (RuntimeException) -> Unit
) {
  private var stopped = false
  private var focusing = false
  private var retry: Runnable? = null
  private var focusToken: Any? = null

  @Synchronized fun start() {
    if (stopped || focusing) return
    retry?.let(cancel); retry = null
    focusing = true
    val token = Any()
    focusToken = token
    try { focus { completed(token) } }
    catch (failure: RuntimeException) { focusing = false; onFailure(failure); later() }
  }
  @Synchronized private fun completed(token: Any) {
    if (stopped || !focusing || focusToken !== token) return
    focusing = false
    later()
  }
  private fun later() {
    if (stopped || retry != null) return
    lateinit var task: Runnable
    task = Runnable {
      synchronized(this) {
        if (stopped || retry !== task) return@Runnable
        retry = null
        start()
      }
    }
    retry = task
    schedule(task, 2000L)
  }
  @Synchronized fun stop() {
    if (stopped) return
    stopped = true
    focusToken = null
    retry?.let(cancel); retry = null
    try { cancelFocus() } catch (failure: RuntimeException) { onFailure(failure) }
  }
}
