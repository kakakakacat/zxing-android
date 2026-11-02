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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rule behind defect A-12. Every case here is a thing the old code got wrong, or a
 * thing it got right that must not break on the way to fixing the rest.
 */
class TorchPolicyTest {

  private val second = 1_000_000_000L

  @Test
  fun darkTurnsItOnStraightAway() {
    val policy = TorchPolicy()
    assertEquals(true, policy.decide(10.0f, 0L))
  }

  @Test
  fun aSteadyDarkSceneAsksOnlyOnce() {
    val policy = TorchPolicy()
    assertEquals(true, policy.decide(10.0f, 0L))
    // Five readings a second for ten seconds, all dark. The old code called setTorch for
    // every one of them.
    for (i in 1..50) {
      assertNull("reading $i", policy.decide(10.0f, i * second / 5))
    }
  }

  @Test
  fun theDeadBandChangesNothingInEitherDirection() {
    val policy = TorchPolicy()
    assertNull(policy.decide(200.0f, 0L))
    assertEquals(true, policy.decide(10.0f, second))
    // Back into the band: not bright enough to turn it off again.
    assertNull(policy.decide(200.0f, 10 * second))
    assertNull(policy.decide(449.0f, 20 * second))
  }

  @Test
  fun theThresholdsThemselvesCount() {
    val policy = TorchPolicy()
    assertEquals(true, policy.decide(45.0f, 0L))
    assertEquals(false, policy.decide(450.0f, 10 * second))
  }

  @Test
  fun aFlipBackIsHeldOffUntilTheIntervalHasPassed() {
    val policy = TorchPolicy()
    assertEquals(true, policy.decide(10.0f, 0L))
    // This is the feedback loop: the torch's own light pushes the reading over the
    // bright threshold a fifth of a second later.
    assertNull(policy.decide(900.0f, second / 5))
    assertNull(policy.decide(900.0f, 2 * second))
    assertNull("one nanosecond early", policy.decide(900.0f, 3 * second - 1))
    assertEquals(false, policy.decide(900.0f, 3 * second))
  }

  @Test
  fun theIntervalIsBetweenChanges_notSinceTheFirstOne() {
    val policy = TorchPolicy()
    assertEquals(true, policy.decide(10.0f, 0L))
    assertEquals(false, policy.decide(900.0f, 3 * second))
    assertNull("too soon after the second change", policy.decide(10.0f, 4 * second))
    assertEquals(true, policy.decide(10.0f, 6 * second))
  }

  @Test
  fun resetMakesTheNextDecisionImmediateAgain() {
    val policy = TorchPolicy()
    assertEquals(true, policy.decide(10.0f, 0L))
    assertNull(policy.decide(900.0f, second))
    // A new camera: its torch is off, so "on" has to be asked for again, at once.
    policy.reset()
    assertEquals(true, policy.decide(10.0f, second))
  }

  @Test
  fun aShorterIntervalIsHonoured() {
    val policy = TorchPolicy(minToggleIntervalNanos = second / 2)
    assertEquals(true, policy.decide(10.0f, 0L))
    assertNull(policy.decide(900.0f, second / 4))
    assertEquals(false, policy.decide(900.0f, second / 2))
  }
}
