/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.benchmark;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.android.camera.YPlaneLuminanceSource;
import io.github.kakakakacat.zxing.android.scanner.FrameDecoder;
import io.github.kakakakacat.zxing.android.scanner.FrameDecoders;
import java.nio.ByteBuffer;
import java.util.Collection;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Same pixels/ROI on a direct camera-like plane; staging is outside the timed loop. */
@RunWith(Parameterized.class)
public final class DirectPlaneBenchmark {
  @Rule public final BenchmarkRule benchmarkRule = new BenchmarkRule();
  @Parameterized.Parameter(0) public FrameFactory.Resolution resolution;
  @Parameterized.Parameter(1) public FrameFactory.Variant variant;
  @Parameterized.Parameter(2) public L2Config config;
  private FrameFactory.Frame frame;
  private ByteBuffer plane;
  private FrameDecoder decoder;
  private volatile Object sink;

  @Parameterized.Parameters(name = "{0}_{1}_{2}")
  public static Collection<Object[]> parameters() { return DecoderBackendBenchmark.parameters(); }

  private Result decode() {
    int[] crop = FramePipeline.crop(frame.width, frame.height);
    return decoder.decode(new YPlaneLuminanceSource(plane, frame.width, 1,
        crop[0], crop[1], crop[2] - crop[0], crop[3] - crop[1]));
  }

  @Before public void prepare() {
    String backend = FrameDecoders.canonical(DecoderBackendBenchmark.requestedBackend());
    Assume.assumeTrue(FrameDecoders.isAvailable(backend));
    frame = FrameFactory.build(resolution, variant);
    plane = ByteBuffer.allocateDirect(frame.luminance.length);
    plane.put(frame.luminance).flip();
    decoder = FramePipeline.decoder(backend, config);
    Result expected = FramePipeline.endToEnd(FramePipeline.reader(config), frame);
    String disagreement = BackendAgreement.disagreement(expected, decode());
    if (disagreement != null) throw new IllegalStateException(disagreement);
  }

  @After public void close() { if (decoder != null) decoder.close(); }

  @Test public void directPlaneEndToEnd() {
    BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) sink = decode();
  }
}
