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

package io.github.kakakakacat.zxing.benchmark;

import android.os.Bundle;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.kakakakacat.zxing.BinaryBitmap;
import io.github.kakakakacat.zxing.MultiFormatReader;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.common.HybridBinarizer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * L2: the Android frame-pipeline benchmark of {@code docs/maint/BENCHMARKS.md} §10.
 *
 * <p>Five cases per parameter set — the four stages of §10.3 plus the whole path — over
 * the replay frames of §10.4 and the hint sets of §10.5, on the device's own ART.
 *
 * <p>What it measures: the cost of turning one preview buffer into a decode outcome. What
 * it does not measure: camera open, autofocus, exposure, preview frame rate, or anything a
 * user would call "how fast it scans". Those are L3, and an improvement here is not a
 * scanning improvement until L3 says so.
 *
 * <p>The matrix is selected by the {@code l2.matrix} instrumentation argument: {@code ci}
 * (the default) runs the 1280x720 / {@code default} slice, {@code full} runs all
 * 3x3x3 parameter sets. Run the full matrix with
 * {@code ./gradlew :benchmark-android:connectedReleaseAndroidTest -Pl2.matrix=full}.
 */
@RunWith(Parameterized.class)
public final class FramePipelineBenchmark {

  @Rule
  public final BenchmarkRule benchmarkRule = new BenchmarkRule();

  @Parameterized.Parameter(0)
  public FrameFactory.Resolution resolution;

  @Parameterized.Parameter(1)
  public FrameFactory.Variant variant;

  @Parameterized.Parameter(2)
  public L2Config config;

  private FrameFactory.Frame frame;
  private int[] crop;
  private PlanarYUVLuminanceSource source;
  private BinaryBitmap binarized;
  private MultiFormatReader reader;

  /**
   * Keeps the optimiser from deleting the work. A stage whose result is never read is a
   * stage the JIT is free to not perform, and a benchmark of nothing is very fast.
   */
  @SuppressWarnings("unused")
  private volatile Object sink;

  @Parameterized.Parameters(name = "{0}_{1}_{2}")
  public static Collection<Object[]> parameters() {
    Bundle arguments = InstrumentationRegistry.getArguments();
    boolean full = "full".equals(arguments.getString("l2.matrix", "ci"));

    List<Object[]> cases = new ArrayList<>();
    for (FrameFactory.Resolution resolution : FrameFactory.Resolution.values()) {
      if (!full && resolution != FrameFactory.Resolution.HD) {
        continue;
      }
      for (FrameFactory.Variant variant : FrameFactory.Variant.values()) {
        for (L2Config config : L2Config.values()) {
          if (!full && config != L2Config.DEFAULT) {
            continue;
          }
          cases.add(new Object[] { resolution, variant, config });
        }
      }
    }
    return cases;
  }

  @Before
  public void buildTheFrame() {
    frame = FrameFactory.build(resolution, variant);
    crop = FramePipeline.crop(frame.width, frame.height);
    source = FramePipeline.luminanceSource(frame, crop);
    reader = FramePipeline.reader(config);

    // Stage 4 has to measure the reader alone, so the black matrix is computed here,
    // once, and the cached bitmap is reused. See FramePipeline#decode.
    binarized = new BinaryBitmap(new HybridBinarizer(
        FramePipeline.luminanceSource(frame, crop)));
    try {
      binarized.getBlackMatrix();
    } catch (Exception e) {
      throw new IllegalStateException("could not binarize " + frame.sha256, e);
    }
  }

  /** Stage 1: the viewfinder rectangle through the display-to-camera rotation. */
  @Test
  public void crop() {
    final BenchmarkState state = benchmarkRule.getState();
    final int width = frame.width;
    final int height = frame.height;
    while (state.keepRunning()) {
      sink = FramePipeline.crop(width, height);
    }
  }

  /** Stage 2: the luminance source over the cropped region. */
  @Test
  public void luminanceSource() {
    final BenchmarkState state = benchmarkRule.getState();
    final FrameFactory.Frame localFrame = frame;
    final int[] localCrop = crop;
    while (state.keepRunning()) {
      sink = FramePipeline.luminanceSource(localFrame, localCrop);
    }
  }

  /**
   * Stage 3: the binarizer producing the black matrix.
   *
   * <p>A fresh {@code BinaryBitmap} per iteration on purpose: the class caches its matrix,
   * so reusing one would measure the cache rather than the binarizer.
   */
  @Test
  public void binarize() throws Exception {
    final BenchmarkState state = benchmarkRule.getState();
    final PlanarYUVLuminanceSource localSource = source;
    while (state.keepRunning()) {
      sink = new BinaryBitmap(new HybridBinarizer(localSource)).getBlackMatrix();
    }
  }

  /** Stage 4: one decode attempt on a reused reader, over an already-binarized bitmap. */
  @Test
  public void decode() {
    final BenchmarkState state = benchmarkRule.getState();
    final MultiFormatReader localReader = reader;
    final BinaryBitmap localBitmap = binarized;
    while (state.keepRunning()) {
      sink = FramePipeline.decode(localReader, localBitmap);
    }
  }

  /**
   * Stages 1-4 as the decode thread runs them. This is the headline number; the per-stage
   * cases exist so a change in it can be attributed instead of guessed at.
   */
  @Test
  public void endToEnd() {
    final BenchmarkState state = benchmarkRule.getState();
    final MultiFormatReader localReader = reader;
    final FrameFactory.Frame localFrame = frame;
    while (state.keepRunning()) {
      sink = FramePipeline.endToEnd(localReader, localFrame);
    }
  }
}
