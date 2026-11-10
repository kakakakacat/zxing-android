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
import io.github.kakakakacat.zxing.android.camera.LumaFrameData;
import io.github.kakakakacat.zxing.android.scanner.FrameDecoder;
import io.github.kakakakacat.zxing.android.scanner.FrameDecoders;
import io.github.kakakakacat.zxing.android.scanner.StagedFrameDecoder;
import io.github.kakakakacat.zxing.common.HybridBinarizer;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Where a frame's time goes inside one decoder: thresholding, or detection.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@link DecoderBackendBenchmark} measures a whole decoder and says why: a native
 * backend brings its own thresholding, so there is no stage the two backends
 * <em>share</em>. That rules out a comparable stage, not a measured one — and without
 * one, the largest number this project has is unattributed. An FHD all-format miss costs
 * 18.677 ms on the native backend (BENCHMARKS.md §10.6c) and nothing says whether that is
 * mostly the binarizer or mostly the detectors. Every decision downstream — hand-written
 * NEON for the binarizer, a different binarizer, {@code -O3}, LTO, a smaller analysis
 * resolution — is a different decision depending on the answer, and all of them are
 * currently guesses.
 *
 * <h2>What the two cases are</h2>
 *
 * <ul>
 *   <li>{@code binarize} — a fresh binarizer over the frame, forced to produce its black
 *       matrix. Allocation included, because the live path pays it per frame too.
 *   <li>{@code detect} — the readers over a matrix built once, before timing.
 * </ul>
 *
 * <p>Both backends run the same two case names, with the backend in the result envelope
 * rather than in the names — the convention {@link DecoderBackendBenchmark} established,
 * and the reason two runs differing only by backend compare case for case. So this does
 * answer "is the native binarizer faster than the Java one", which the end-to-end case
 * cannot.
 *
 * <h2>What it is not</h2>
 *
 * <p><strong>{@code binarize + detect} is a floor for {@code endToEnd}, not a partition
 * of it.</strong> The matrix {@code detect} reads is warm in cache, which it is not on a
 * live frame; the crop, the luminance source and — on the native side — the JNI
 * transition and the array pin sit outside both halves. Read the two against the
 * end-to-end case for the same frame and resolution, which {@link
 * DecoderBackendBenchmark} already produces. A split that falls well short of it is a
 * finding about those excluded costs, not a broken measurement.
 *
 * <p><strong>For a 1D hit, {@code binarize} measures work the decode never does — do not
 * add the halves.</strong> Only the 2D readers consult the matrix; both decoders threshold
 * a row at a time for 1D symbols. And with {@code TRY_HARDER} off, which is what this fork
 * ships, zxing-cpp's {@code MultiFormatReader} runs the 1D reader ahead of every 2D one,
 * so a 1D symbol ends the read before anything asks for a matrix. The 1D cases are kept
 * anyway: the gap between {@code binarize} and the end-to-end figure for the same frame is
 * how that short circuit is observed, and a configuration that lost it would show up here
 * as a 1D hit that suddenly costs a matrix.
 *
 * <p>Which backend runs is the {@code l2.backend} instrumentation argument, as elsewhere.
 * A backend that cannot be split skips rather than reporting a zero.
 */
@RunWith(Parameterized.class)
public final class DecoderStageBenchmark {

  @Rule
  public final BenchmarkRule benchmarkRule = new BenchmarkRule();

  @Parameterized.Parameter(0)
  public FrameFactory.Resolution resolution;

  @Parameterized.Parameter(1)
  public FrameFactory.Variant variant;

  @Parameterized.Parameter(2)
  public L2Config config;

  private FrameDecoder decoder;
  private StagedFrameDecoder staged;

  /** The Java side of the split, which needs no seam: a warm bitmap is the whole trick. */
  private MultiFormatReader reader;
  private PlanarYUVLuminanceSource source;
  private BinaryBitmap warmBitmap;

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
  public void prepareTheStages() {
    String backend = FrameDecoders.canonical(DecoderBackendBenchmark.requestedBackend());
    Assume.assumeTrue(
        "backend " + backend + " has no build for this device's ABI, so it is not "
            + "measured here rather than measured as something else",
        FrameDecoders.isAvailable(backend));

    FrameFactory.Frame frame = FrameFactory.build(resolution, variant);
    int[] crop = FramePipeline.crop(frame.width, frame.height);
    source = FramePipeline.luminanceSource(frame, crop);

    if (FrameDecoders.JAVA.equals(backend)) {
      reader = FramePipeline.reader(config);
      // Warmed here, so the detect case times the readers and not the binarizer. The
      // matrix is cached on first use and BinaryBitmap hands out the same one after.
      warmBitmap = new BinaryBitmap(new HybridBinarizer(source));
      try {
        warmBitmap.getBlackMatrix();
      } catch (io.github.kakakakacat.zxing.NotFoundException e) {
        throw new IllegalStateException(
            "the Java binarizer refused " + frame.sha256 + ", so neither stage is "
                + "measurable on it", e);
      }
      return;
    }

    decoder = FramePipeline.decoder(backend, config);
    staged = FramePipeline.staged(decoder);
    Assume.assumeTrue(
        "backend " + backend + " cannot run its stages apart, so there is nothing to "
            + "attribute here",
        staged != null);
    LumaFrameData data = FramePipeline.frameData(frame, crop);
    // Throws rather than skips when the configuration is one the split would misdescribe:
    // a run that silently measured a different decode path is the failure this guards.
    staged.prepareStages(data);
  }

  @After
  public void releaseTheStages() {
    if (staged != null) {
      staged.releaseStages();
      staged = null;
    }
    if (decoder != null) {
      decoder.close();
      decoder = null;
    }
    reader = null;
    source = null;
    warmBitmap = null;
  }

  /** Thresholding alone: one black matrix from the frame, built fresh each iteration. */
  @Test
  public void binarize() {
    final BenchmarkState state = benchmarkRule.getState();
    if (staged != null) {
      final StagedFrameDecoder localStaged = staged;
      while (state.keepRunning()) {
        sink = localStaged.binarizeOnly();
      }
      return;
    }
    final PlanarYUVLuminanceSource localSource = source;
    while (state.keepRunning()) {
      try {
        sink = FramePipeline.binarize(localSource);
      } catch (io.github.kakakakacat.zxing.NotFoundException e) {
        throw new IllegalStateException("the binarizer refused a frame it accepted in "
            + "setup, which is not something a measurement should see", e);
      }
    }
  }

  /** Detection alone: the readers over a matrix that was built before timing started. */
  @Test
  public void detect() {
    final BenchmarkState state = benchmarkRule.getState();
    if (staged != null) {
      final StagedFrameDecoder localStaged = staged;
      while (state.keepRunning()) {
        sink = localStaged.detectOnly();
      }
      return;
    }
    final MultiFormatReader localReader = reader;
    final BinaryBitmap localBitmap = warmBitmap;
    while (state.keepRunning()) {
      sink = FramePipeline.decode(localReader, localBitmap);
    }
  }
}
