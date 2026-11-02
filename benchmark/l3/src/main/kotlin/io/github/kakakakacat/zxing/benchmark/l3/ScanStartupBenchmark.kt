/*
 * Copyright (C) 2026 ZXing Android maintenance fork contributors
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

package io.github.kakakakacat.zxing.benchmark.l3

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * L3, as `docs/maint/BENCHMARKS.md` §11 specifies it: launch to preview, preview to a camera
 * that is ready, and camera-ready to a first successful scan.
 *
 * ### What each metric is, and is not
 *
 * - **`timeToInitialDisplay`** — the window's first frame. §11 calls this "preview visible"
 *   and that is a convenient name rather than an accurate one: the first frame contains the
 *   viewfinder and an empty `SurfaceView`, not camera pixels. Camera pixels are what
 *   [FIRST_FRAME] measures, and the two are hundreds of milliseconds apart.
 * - **`zxing.camera.open`** — the scanner asking its backend to open, to the backend
 *   reporting ready. This is the interval the Phase 6 camera spike measured from outside;
 *   here it is measured through the shipping `ScannerView` on both backends.
 * - **`zxing.first.frame`** — camera-ready to the first frame arriving at the decode thread.
 * - **`zxing.first.scan`** — camera-ready to the first successful decode. **Scene
 *   dependent**, and therefore not admissible until the scene is fixed and photographed
 *   per §11. The benchmark below collects it anyway and the harness reports it as absent
 *   when there was nothing to scan, which is the honest outcome rather than a zero.
 *
 * ### Why the whole app and not a harness activity
 *
 * Because the Phase 6 spike already measured a harness activity, and the question L3 asks
 * is about the product. The app is driven through the same `preferences_camerax` setting a
 * user would toggle, so the backend under test is reached the way it is actually reached.
 *
 * That preference is written **before the run**, by `tools/maint/l3-run.sh`, and not from
 * here: the target of a Macrobenchmark is not debuggable — deliberately — so neither
 * `run-as` nor the instrumentation's own shell can reach its `shared_prefs`. Which backend
 * actually ran is therefore read back out of the trace rather than assumed.
 */
// TraceSectionMetric is still @ExperimentalMetricApi in benchmark 1.5.0. Opting in here
// rather than project-wide: it is the one API this module needs and the one that will tell
// us if it changes under us.
@OptIn(androidx.benchmark.macro.ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class ScanStartupBenchmark {

  @get:Rule
  val rule = MacrobenchmarkRule()

  @Test
  fun coldStartToCameraReady() {
    val arguments = InstrumentationRegistry.getArguments()
    val iterations = arguments.getString("l3.iterations")?.toIntOrNull() ?: 10

    rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            // Mode.Sum over one occurrence per iteration is Mode.First with a count the
            // report can check. A span that never appeared sums to zero, and zero and
            // "absent" are different claims -- §8 -- so the harness reads the count.
            TraceSectionMetric(CAMERA_OPEN, TraceSectionMetric.Mode.Sum),
            TraceSectionMetric(FIRST_FRAME, TraceSectionMetric.Mode.Sum),
            TraceSectionMetric(FIRST_SCAN, TraceSectionMetric.Mode.Sum),
            // Not a timing. It is how the result proves which backend it measured: the
            // preference is written outside this process, and "the preference did not
            // take" is indistinguishable from "the two backends are the same speed"
            // unless the trace says so itself. l3_report.py refuses a mismatch.
            TraceSectionMetric(BACKEND_CAMERA1, TraceSectionMetric.Mode.Sum),
            TraceSectionMetric(BACKEND_CAMERAX, TraceSectionMetric.Mode.Sum)),
        iterations = iterations,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() }) {
      startActivityAndWait()
      // The camera spans close after the window's first frame, so the iteration has to
      // outlive them or the trace ends mid-span and the metric silently reads zero.
      device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5_000)
      Thread.sleep(SETTLE_MS)
    }
  }

  companion object {
    private const val TARGET_PACKAGE = "io.github.kakakakacat.zxing.android"

    private const val CAMERA_OPEN = "zxing.camera.open"
    private const val FIRST_FRAME = "zxing.first.frame"
    private const val FIRST_SCAN = "zxing.first.scan"
    private const val BACKEND_CAMERA1 = "zxing.backend.camera1"
    private const val BACKEND_CAMERAX = "zxing.backend.camerax"

    /**
     * Long enough for a camera to open, deliver a frame and decode it on the slowest
     * device here, and it is a sleep rather than a wait on a condition because the thing
     * being waited for is invisible from this process -- that is the whole reason
     * `ScannerTrace` exists. Measured: `zxing.camera.open` finishes inside 1.1 s on
     * DN-ENG41 on either backend.
     */
    private const val SETTLE_MS = 3_000L
  }
}
