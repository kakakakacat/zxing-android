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

import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.android.scanner.FrameDecoder;
import io.github.kakakakacat.zxing.android.scanner.FrameDecoders;

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
 * One decoder backend, one frame, end to end — the comparable unit for phase 5 item 10.
 *
 * <p>Separate from {@link FramePipelineBenchmark} on purpose. That one measures five
 * stages of the Java pipeline and its results are the committed baseline; adding a
 * backend axis to it would change the shape of every case name and make the existing
 * baseline incomparable with anything taken afterwards.
 *
 * <p>The measured unit is the whole decoder, not a stage of one. A native backend brings
 * its own thresholding, so there is no shared {@code binarize} to hold constant;
 * measuring anything narrower would compare two halves of one decoder. Crop and luminance
 * source are identical on both sides and together cost under a microsecond.
 *
 * <p><strong>The case names carry no backend.</strong> The backend is the independent
 * variable of this comparison and is recorded in the result envelope instead, so that two
 * runs differing only by backend compare case for case rather than coming back as a
 * changed matrix.
 *
 * <p>Which backend runs is chosen by the {@code l2.backend} instrumentation argument:
 * {@code java} (the default) or {@code native}. One run per backend, two files, then
 * {@code benchmark/tools/compare.py}. Running both in one process would let the first
 * backend's allocations and cache state colour the second's numbers.
 */
@RunWith(Parameterized.class)
public final class DecoderBackendBenchmark {

  @Rule
  public final BenchmarkRule benchmarkRule = new BenchmarkRule();

  @Parameterized.Parameter(0)
  public FrameFactory.Resolution resolution;

  @Parameterized.Parameter(1)
  public FrameFactory.Variant variant;

  @Parameterized.Parameter(2)
  public L2Config config;

  private FrameFactory.Frame frame;
  private FrameDecoder decoder;

  @SuppressWarnings("unused")
  private volatile Object sink;

  static String requestedBackend() {
    Bundle arguments = InstrumentationRegistry.getArguments();
    return arguments.getString("l2.backend", FrameDecoders.JAVA);
  }

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
  public void buildTheFrameAndDecoder() {
    // An unknown name throws out of canonical() and fails the run. Only a real device
    // limitation -- no native build for this ABI -- is allowed to skip, and the two must
    // not be confused: a typo that skipped every case would look like a clean run that
    // measured nothing.
    String backend = FrameDecoders.canonical(requestedBackend());
    Assume.assumeTrue(
        "backend " + backend + " has no build for this device's ABI, so it is not "
            + "measured here rather than measured as something else",
        FrameDecoders.isAvailable(backend));

    frame = FrameFactory.build(resolution, variant);
    decoder = FramePipeline.decoder(backend, config);

    // A decoder that cannot see what the Java one sees would make the comparison a
    // comparison of two behaviours. Checked here so a disagreement fails the run that
    // produces the numbers, not a reader of them later.
    Result expected = FramePipeline.endToEnd(FramePipeline.reader(config), frame);
    Result actual = FramePipeline.endToEnd(decoder, frame);
    if (expected == null) {
      if (actual != null) {
        throw new IllegalStateException(backend + " found " + actual.getText()
            + " in " + frame.sha256 + " where the Java decoder found nothing");
      }
    } else {
      if (actual == null) {
        throw new IllegalStateException(backend + " found nothing in " + frame.sha256
            + " where the Java decoder found " + expected.getText());
      }
      if (!expected.getText().equals(actual.getText())
          || expected.getBarcodeFormat() != actual.getBarcodeFormat()) {
        throw new IllegalStateException(backend + " disagrees on " + frame.sha256
            + ": java=" + expected.getBarcodeFormat() + "/" + expected.getText()
            + " vs " + actual.getBarcodeFormat() + "/" + actual.getText());
      }
    }
  }

  @After
  public void releaseTheDecoder() {
    if (decoder != null) {
      decoder.close();
      decoder = null;
    }
  }

  /** Crop, luminance source and the whole decoder, as the decode thread runs them. */
  @Test
  public void endToEnd() {
    final BenchmarkState state = benchmarkRule.getState();
    final FrameDecoder localDecoder = decoder;
    final FrameFactory.Frame localFrame = frame;
    while (state.keepRunning()) {
      sink = FramePipeline.endToEnd(localDecoder, localFrame);
    }
  }
}
