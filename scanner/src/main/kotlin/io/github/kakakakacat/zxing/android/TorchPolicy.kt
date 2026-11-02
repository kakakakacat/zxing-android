/*
 * Copyright (C) 2026 ZXing authors
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

/**
 * Decides whether the torch should change state, given a light reading and the time.
 *
 * Separated from [AmbientLightManager] so the rule can be tested without a camera, a
 * sensor or a device, which is the only reason this is a class of its own.
 *
 * This is defect A-12. The old rule called `CameraManager.setTorch` on **every** sensor
 * callback, roughly five a second at `SENSOR_DELAY_NORMAL`. Two things follow from that:
 *
 *  - every call read the camera's parameters to decide whether anything had changed, and
 *  - every call that *did* change something tore down the `AutoFocusManager` and built a
 *    new one, because `CameraManager.setTorch` does that around the parameter change.
 *
 * The lux thresholds are far apart, so a steady scene never oscillates. The loop that
 * bites is the torch's own light: switching it on raises the measured lux, which can push
 * the reading past [BRIGHT_ENOUGH_LUX], which switches it off, which drops the reading
 * back under [TOO_DARK_LUX]. Nothing in the thresholds can see that happening, because the
 * sensor cannot tell the torch's light from the room's.
 *
 * So there are two rules here rather than one: the dead band between the thresholds, which
 * was already there, and a floor on how often the state may flip, which was not. The first
 * decision after a [reset] is never delayed — walking into a dark room should light up at
 * once — only a flip back is.
 */
internal class TorchPolicy(private val minToggleIntervalNanos: Long = MIN_TOGGLE_INTERVAL_NANOS) {

  /** What was last asked for, or null if nothing has been asked for yet. */
  private var requested: Boolean? = null
  private var lastToggleNanos = 0L

  /**
   * @param lux the latest reading
   * @param nowNanos a monotonic clock, in nanoseconds
   * @return the state the torch should be put into, or null to leave it alone — which
   *   covers all three of "the reading is in the dead band", "it already is in that
   *   state" and "it changed too recently".
   */
  fun decide(lux: Float, nowNanos: Long): Boolean? {
    val wanted = when {
      lux <= TOO_DARK_LUX -> true
      lux >= BRIGHT_ENOUGH_LUX -> false
      else -> return null
    }
    val last = requested
    if (wanted == last) {
      return null
    }
    if (last != null && nowNanos - lastToggleNanos < minToggleIntervalNanos) {
      return null
    }
    requested = wanted
    lastToggleNanos = nowNanos
    return wanted
  }

  /**
   * Forgets the current state. Called when the sensor is (re)attached to a camera, since
   * a camera that has just been opened has its torch off whatever this last asked for.
   */
  fun reset() {
    requested = null
    lastToggleNanos = 0L
  }

  private companion object {
    // `private` on each: a const val in a private companion is still emitted as a public
    // static field on the enclosing class. See docs/maint/HANDOFF.md, rule 3.
    private const val TOO_DARK_LUX = 45.0f
    private const val BRIGHT_ENOUGH_LUX = 450.0f

    /**
     * Three seconds. Long enough that the torch-raises-the-lux loop settles instead of
     * flapping, short enough that walking out of a dark room turns it off while you are
     * still looking at the screen.
     */
    private const val MIN_TOGGLE_INTERVAL_NANOS = 3_000_000_000L
  }
}
