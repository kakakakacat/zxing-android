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
 * Which of the six readers the detection half is spent in.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@link DecoderStageBenchmark} split a frame into thresholding and detection and
 * found detection carrying 90-93% of an all-format miss on both backends (BENCHMARKS.md
 * §10.3c). That retired one candidate -- hand-written NEON for the binarizer, which sits
 * on 6-8% of the native path -- and left the large half unattributed. "Detection" is six
 * readers in a loop, and a 15.150 ms FHD miss could be one of them or all six evenly; the
 * two cases call for completely different work.
 *
 * <p>Neither reader loop can be timed from inside: zxing-cpp's {@code MultiFormatReader}
 * keeps {@code _readers} private and this fork's Java one keeps {@code readers} private.
 * {@link ReaderGroup} reaches the decomposition from outside instead -- a format set per
 * reader, so that a decode restricted to it constructs that reader and no other -- and
 * this class times one group per case over a matrix built before timing starts, exactly
 * as the {@code detect} case does.
 *
 * <h2>Reading the result</h2>
 *
 * <p><strong>On a miss the six groups sum to the {@code detect} case for the same frame;
 * on a hit they do not.</strong> Both loops return at the first symbol, so a hit charges
 * nothing to the readers behind the one that found it. The miss is the frame a live
 * preview decodes thousands of and the one being attributed here; the hits are kept
 * because the shortfall against {@code detect} is how the short circuit is observed.
 *
 * <p>Two independent checks come free. The six miss groups must add up to {@code detect};
 * and {@code QRCODE} under {@code DEFAULT} must land near the whole {@code detect} figure
 * under {@code QR_ONLY}, which {@link DecoderStageBenchmark} already measured separately
 * -- the same reader reached two different ways.
 *
 * <p>A group the configuration builds no reader for is skipped, not measured as a cheap
 * one: under a QR-only scanner the 1D reader does not exist, and timing a reader the
 * decode never constructs would be attribution of imaginary work. The backend is the
 * {@code l2.backend} instrumentation argument and lives in the result envelope rather
 * than in the case names, as elsewhere.
 */
@RunWith(Parameterized.class)
public final class DetectorAttributionBenchmark {

  @Rule
  public final BenchmarkRule benchmarkRule = new BenchmarkRule();

  @Parameterized.Parameter(0)
  public FrameFactory.Resolution resolution;

  @Parameterized.Parameter(1)
  public FrameFactory.Variant variant;

  @Parameterized.Parameter(2)
  public L2Config config;

  @Parameterized.Parameter(3)
  public ReaderGroup group;

  private FrameDecoder decoder;
  private StagedFrameDecoder staged;

  private MultiFormatReader reader;
  private BinaryBitmap warmBitmap;

  @SuppressWarnings("unused")
  private volatile Object sink;

  @Parameterized.Parameters(name = "{0}_{1}_{2}_{3}")
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
          for (ReaderGroup group : ReaderGroup.values()) {
            // Dropped here rather than skipped in @Before: a configuration that builds no
            // reader for this group has no case, and a result file full of skips would
            // read as a run that measured less than it did.
            if (group.appliesTo(config)) {
              cases.add(new Object[] { resolution, variant, config, group });
            }
          }
        }
      }
    }
    return cases;
  }

  @Before
  public void prepareTheGroup() {
    String backend = FrameDecoders.canonical(DecoderBackendBenchmark.requestedBackend());
    Assume.assumeTrue(
        "backend " + backend + " has no build for this device's ABI, so it is not "
            + "measured here rather than measured as something else",
        FrameDecoders.isAvailable(backend));

    FrameFactory.Frame frame = FrameFactory.build(resolution, variant);
    int[] crop = FramePipeline.crop(frame.width, frame.height);
    PlanarYUVLuminanceSource source = FramePipeline.luminanceSource(frame, crop);

    if (FrameDecoders.JAVA.equals(backend)) {
      reader = FramePipeline.reader(config, group);
      warmBitmap = new BinaryBitmap(new HybridBinarizer(source));
      try {
        warmBitmap.getBlackMatrix();
      } catch (io.github.kakakakacat.zxing.NotFoundException e) {
        throw new IllegalStateException(
            "the Java binarizer refused " + frame.sha256 + ", so no group over it is "
                + "measurable", e);
      }
      return;
    }

    decoder = FramePipeline.decoder(backend, config);
    staged = FramePipeline.staged(decoder);
    Assume.assumeTrue(
        "backend " + backend + " cannot run its readers apart, so there is nothing to "
            + "attribute here",
        staged != null);
    staged.prepareStages(FramePipeline.frameData(frame, crop));
    // Builds this group's reader now, outside the measurement, and fails the case here
    // rather than inside the loop if the decoder refuses the group.
    sink = staged.detectOnlyGroup(group.nativeFormats);
  }

  @After
  public void releaseTheGroup() {
    if (staged != null) {
      staged.releaseStages();
      staged = null;
    }
    if (decoder != null) {
      decoder.close();
      decoder = null;
    }
    reader = null;
    warmBitmap = null;
  }

  /** One reader over a matrix that was built before timing started. */
  @Test
  public void detectGroup() {
    final BenchmarkState state = benchmarkRule.getState();
    if (staged != null) {
      final StagedFrameDecoder localStaged = staged;
      final String formats = group.nativeFormats;
      while (state.keepRunning()) {
        sink = localStaged.detectOnlyGroup(formats);
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
