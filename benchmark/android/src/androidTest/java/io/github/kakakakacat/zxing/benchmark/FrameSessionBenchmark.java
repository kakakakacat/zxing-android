/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.benchmark;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.android.scanner.FrameDecoder;
import io.github.kakakakacat.zxing.android.scanner.FrameDecoders;
import java.util.Collection;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Replay CPU cost: two misses followed by up to five target frames, not camera latency. */
@RunWith(Parameterized.class)
public final class FrameSessionBenchmark {
  @Rule public final BenchmarkRule benchmarkRule = new BenchmarkRule();
  @Parameterized.Parameter(0) public FrameFactory.Resolution resolution;
  @Parameterized.Parameter(1) public FrameFactory.Variant variant;
  @Parameterized.Parameter(2) public L2Config config;
  private FrameFactory.Frame miss;
  private FrameFactory.Frame target;
  private FrameDecoder decoder;
  private volatile Object sink;

  @Parameterized.Parameters(name = "{0}_{1}_{2}")
  public static Collection<Object[]> parameters() { return DecoderBackendBenchmark.parameters(); }

  private Result session() {
    decoder.setFormats(config.formats());
    for (int i = 0; i < 2; i++) {
      Result unexpected = FramePipeline.endToEnd(decoder, miss);
      if (unexpected != null) throw new IllegalStateException("false positive on the miss prelude");
    }
    for (int i = 0; i < 5; i++) {
      Result result = FramePipeline.endToEnd(decoder, target);
      if (result != null) return result;
    }
    return null;
  }

  @Before public void prepare() {
    String backend = FrameDecoders.canonical(DecoderBackendBenchmark.requestedBackend());
    Assume.assumeTrue(FrameDecoders.isAvailable(backend));
    miss = FrameFactory.build(resolution, FrameFactory.Variant.MISS);
    target = FrameFactory.build(resolution, variant);
    decoder = FramePipeline.decoder(backend, config);
    Result expected = FramePipeline.endToEnd(FramePipeline.reader(config), target);
    String disagreement = BackendAgreement.disagreement(expected, session());
    if (disagreement != null) throw new IllegalStateException(disagreement);
  }

  @After public void close() { if (decoder != null) decoder.close(); }

  @Test public void sessionEndToEnd() {
    BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) sink = session();
  }
}
