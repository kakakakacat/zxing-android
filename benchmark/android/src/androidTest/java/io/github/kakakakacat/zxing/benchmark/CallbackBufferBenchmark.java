/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.benchmark;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import io.github.kakakakacat.zxing.android.camera.CallbackBufferPool;
import kotlin.Unit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Allocation/lease microbenchmark only: excludes the camera HAL, copying and decoding. */
@RunWith(Parameterized.class)
public final class CallbackBufferBenchmark {
  @Rule public final BenchmarkRule benchmarkRule = new BenchmarkRule();
  @Parameterized.Parameter public FrameFactory.Resolution resolution;
  @Parameterized.Parameters(name = "{0}")
  public static FrameFactory.Resolution[] resolutions() { return FrameFactory.Resolution.values(); }
  private CallbackBufferPool pool;
  private int bytes;
  private volatile Object sink;
  @Before public void prepare() {
    bytes = resolution.width * resolution.height * 3 / 2;
    pool = new CallbackBufferPool(bytes, 2, () -> Unit.INSTANCE);
  }
  @After public void close() { pool.close(); }
  @Test public void allocateBuffer() {
    BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) sink = new byte[bytes];
  }
  @Test public void leaseBuffer() {
    BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) {
      CallbackBufferPool.Lease lease = pool.acquire();
      if (lease == null) throw new IllegalStateException("pool exhausted");
      sink = lease.getBytes();
      lease.close();
    }
  }
}
