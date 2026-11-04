package io.github.kakakakacat.zxing.benchmark;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.android.scanner.FrameDecoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Same Java decoder and same pixels; only the ROI packer changes. */
@RunWith(Parameterized.class)
public final class RoiPackingDecodeBenchmark {
  @Rule public final BenchmarkRule benchmarkRule = new BenchmarkRule();
  @Parameterized.Parameter(0) public FrameFactory.Resolution resolution;
  @Parameterized.Parameter(1) public FrameFactory.Variant variant;

  private FrameFactory.Frame frame;
  private ByteBuffer plane;
  private byte[] packed;
  private FrameDecoder decoder;
  private String packBackend;
  private int left, top, width, height;
  private volatile Object sink;

  @Parameterized.Parameters(name = "{0}_{1}")
  public static Collection<Object[]> parameters() {
    ArrayList<Object[]> result = new ArrayList<>();
    for (FrameFactory.Resolution size : FrameFactory.Resolution.values())
      for (FrameFactory.Variant kind : FrameFactory.Variant.values())
        result.add(new Object[] {size, kind});
    return result;
  }

  @Before public void prepare() {
    packBackend = InstrumentationRegistry.getArguments().getString("roi.decodeBackend", "java");
    org.junit.Assume.assumeTrue(packBackend.equals("java") || packBackend.equals("jni_cpp"));
    frame = FrameFactory.build(resolution, variant);
    left = frame.crop[0];
    top = frame.crop[1];
    width = frame.crop[2] - left;
    height = frame.crop[3] - top;
    plane = ByteBuffer.allocateDirect(frame.luminance.length);
    plane.put(frame.luminance).clear();
    packed = new byte[width * height];
    decoder = FramePipeline.decoder("java", L2Config.DEFAULT);
    Result expected = FramePipeline.endToEnd(FramePipeline.reader(L2Config.DEFAULT), frame);
    if (!pack()) throw new IllegalStateException("packer rejected fixed frame");
    Result actual = decodePacked();
    String disagreement = BackendAgreement.disagreement(expected, actual);
    if (disagreement != null) throw new IllegalStateException(disagreement);
  }

  private boolean pack() {
    return packBackend.equals("java")
        ? RoiPacking.packJava(plane, packed, 0, frame.width, frame.height,
            left, top, width, height, frame.width, 1)
        : RoiPacking.packNative(plane, packed, 0, frame.width, frame.height,
            left, top, width, height, frame.width, 1);
  }

  private Result decodePacked() {
    LuminanceSource source = new PlanarYUVLuminanceSource(
        packed, width, height, 0, 0, width, height, false);
    return decoder.decode(source);
  }

  @After public void close() { if (decoder != null) decoder.close(); }

  @Test public void roiPackPlusSameJavaDecoder() {
    BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) {
      if (!pack()) throw new AssertionError("fixed ROI rejected");
      sink = decodePacked();
    }
  }
}
