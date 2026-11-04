/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.camera
import org.junit.Assert.*
import org.junit.Test

class AutoFocusLoopTest {
  private class Driver {
    val completions = arrayListOf<() -> Unit>()
    val timers = arrayListOf<Runnable>()
    var cancels = 0
    var failures = 0
    var throwFocus = false
    val loop = AutoFocusLoop(
        { done -> if (throwFocus) throw IllegalStateException("driver busy") else completions.add(done) },
        { cancels++ },
        { task, delay -> assertEquals(2000L, delay); timers.add(task) },
        { task -> timers.remove(task); Unit },
        { failures++ })
  }
  @Test fun oneTimerPerCompletionAndNoDuplicateInFlightRequest() {
    val driver = Driver()
    driver.loop.start(); driver.loop.start()
    assertEquals(1, driver.completions.size)
    driver.completions[0](); driver.completions[0]()
    assertEquals(1, driver.timers.size)
    val timer = driver.timers.single()
    timer.run(); timer.run()
    assertEquals(2, driver.completions.size)
    driver.completions[0]() // late duplicate from the first operation
    assertEquals(1, driver.timers.size) // fake scheduler retains already-run entries
    driver.loop.stop()
  }
  @Test fun stoppedLoopsIgnoreLateCallbacksAndCancelledTimers() {
    val driver = Driver()
    driver.loop.start()
    driver.completions.single()()
    val timer = driver.timers.single()
    driver.loop.stop(); driver.loop.stop()
    timer.run(); driver.completions.single()(); driver.loop.start()
    assertEquals(1, driver.completions.size)
    assertTrue(driver.timers.isEmpty())
    assertEquals(1, driver.cancels)
  }
  @Test fun driverErrorsRetryWithDelayAndStopCancelsTheRetry() {
    val driver = Driver(); driver.throwFocus = true
    driver.loop.start()
    assertEquals(1, driver.failures)
    assertEquals(1, driver.timers.size)
    driver.loop.stop()
    assertTrue(driver.timers.isEmpty())
  }
}
