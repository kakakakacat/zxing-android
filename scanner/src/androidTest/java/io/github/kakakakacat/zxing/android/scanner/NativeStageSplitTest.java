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

package io.github.kakakakacat.zxing.android.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.MultiFormatWriter;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.android.camera.LumaFrameData;
import io.github.kakakakacat.zxing.common.BitMatrix;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * The measurement-only stage split, checked for the properties a benchmark relies on.
 *
 * <p><strong>No timing is taken here.</strong> This exists because
 * {@code DecoderStageBenchmark} will report two numbers that nothing else can
 * cross-check: if the split silently decoded a different frame, or detected nothing
 * because the matrix was never built, the benchmark would still produce a clean-looking
 * pair of medians. These are the invariants that make those medians mean something.
 */
@RunWith(AndroidJUnit4.class)
public final class NativeStageSplitTest {

  private static final int SIZE = 200;

  /** Padding, so the crop is a real offset into a strided plane rather than the frame. */
  private static final int LEFT = 7;
  private static final int TOP = 9;

  private static final class Frame {
    final byte[] pixels;
    final int imageWidth;
    final int imageHeight;
    final int cropWidth;
    final int cropHeight;

    Frame(byte[] pixels, int imageWidth, int imageHeight, int cropWidth, int cropHeight) {
      this.pixels = pixels;
      this.imageWidth = imageWidth;
      this.imageHeight = imageHeight;
      this.cropWidth = cropWidth;
      this.cropHeight = cropHeight;
    }

    LumaFrameData data() {
      return LumaFrameData.Companion.array(pixels, imageWidth, imageHeight, imageWidth, 1,
          LEFT, TOP, cropWidth, cropHeight, 0, 0L);
    }

    LuminanceSource source() {
      return new PlanarYUVLuminanceSource(pixels, imageWidth, imageHeight, LEFT, TOP,
          cropWidth, cropHeight, false);
    }
  }

  @Before
  public void requireTheNativeLibrary() {
    Assume.assumeTrue(
        "libzxingnative is not loadable on this ABI (" + Arrays.toString(Build.SUPPORTED_ABIS)
            + "), which is the documented fallback case",
        NativeFrameDecoder.isAvailable());
  }

  private static Frame frameWith(String payload, BarcodeFormat format) throws Exception {
    BitMatrix bits = new MultiFormatWriter().encode(payload, format, SIZE, SIZE);
    int imageWidth = bits.getWidth() + 19;
    int imageHeight = bits.getHeight() + 17;
    byte[] pixels = new byte[imageWidth * imageHeight];
    Arrays.fill(pixels, (byte) 0xff);
    for (int y = 0; y < bits.getHeight(); y++) {
      for (int x = 0; x < bits.getWidth(); x++) {
        if (bits.get(x, y)) {
          pixels[(y + TOP) * imageWidth + x + LEFT] = 0;
        }
      }
    }
    return new Frame(pixels, imageWidth, imageHeight, bits.getWidth(), bits.getHeight());
  }

  private static Frame blankFrame() {
    int imageWidth = SIZE + 19;
    int imageHeight = SIZE + 17;
    byte[] pixels = new byte[imageWidth * imageHeight];
    Arrays.fill(pixels, (byte) 0xff);
    return new Frame(pixels, imageWidth, imageHeight, SIZE, SIZE);
  }

  /**
   * The split sees the frame the ordinary decode sees.
   *
   * <p>The point of the whole exercise: {@code detectOnly} has to agree with
   * {@code decode} about whether this frame holds a symbol. It would not if
   * {@code prepareStages} copied the wrong span, lost a stride, or thresholded a
   * differently cropped region — and every one of those produces a plausible timing.
   */
  @Test
  public void theSplitDetectsWhatAnOrdinaryDecodeDetects() throws Exception {
    Frame hit = frameWith("STAGE-SPLIT", BarcodeFormat.QR_CODE);
    Frame miss = blankFrame();
    NativeFrameDecoder decoder = new NativeFrameDecoder(null, null, true);
    try {
      Result decoded = ((FrameDataDecoder) decoder).decode(hit.data(), hit.source());
      assertNotNull("the ordinary decode found nothing in a clean QR frame", decoded);
      assertEquals("STAGE-SPLIT", decoded.getText());

      decoder.prepareStages(hit.data());
      assertTrue("the split missed a symbol the same frame decodes", decoder.detectOnly());

      decoder.prepareStages(miss.data());
      assertFalse("the split found a symbol in a blank frame", decoder.detectOnly());
      assertNull("the ordinary decode found a symbol in a blank frame",
          ((FrameDataDecoder) decoder).decode(miss.data(), miss.source()));
    } finally {
      decoder.close();
    }
  }

  /** Detection repeats: the cached matrix is not consumed by the first read. */
  @Test
  public void detectionRepeatsOverTheSameHeldFrame() throws Exception {
    Frame hit = frameWith("STAGE-REPEAT", BarcodeFormat.QR_CODE);
    NativeFrameDecoder decoder = new NativeFrameDecoder(null, null, true);
    try {
      decoder.prepareStages(hit.data());
      // A benchmark calls this thousands of times and reports the median. If the first
      // call consumed the matrix, the median would be the cost of finding nothing.
      for (int i = 0; i < 64; i++) {
        assertTrue("detection stopped finding the symbol at iteration " + i,
            decoder.detectOnly());
      }
    } finally {
      decoder.close();
    }
  }

  /**
   * Thresholding reports the matrix it built, and reports the crop rather than the frame.
   *
   * <p>The cell count is what {@code binarizeOnly} returns so the call has a result that
   * cannot be optimised away; checking it against the crop also catches a split that
   * binarized the whole padded plane, which would be a larger number and a slower one.
   */
  @Test
  public void thresholdingCoversTheCropAndRepeats() throws Exception {
    Frame hit = frameWith("STAGE-MATRIX", BarcodeFormat.QR_CODE);
    NativeFrameDecoder decoder = new NativeFrameDecoder(null, null, true);
    try {
      decoder.prepareStages(hit.data());
      long cells = (long) hit.cropWidth * hit.cropHeight;
      for (int i = 0; i < 8; i++) {
        assertEquals("thresholding covered something other than the crop", cells,
            decoder.binarizeOnly());
      }
    } finally {
      decoder.close();
    }
  }

  /**
   * A group finds what the whole reader loop finds, and the other groups do not.
   *
   * <p>The attribution's whole claim is that a group is one of the readers the
   * all-format loop runs. A group that silently decoded through a different path, or
   * through every reader, would still produce a plausible median -- this is what
   * distinguishes the two.
   */
  @Test
  public void oneGroupFindsTheSymbolAndTheOthersDoNot() throws Exception {
    Frame hit = frameWith("STAGE-GROUP", BarcodeFormat.QR_CODE);
    NativeFrameDecoder decoder = new NativeFrameDecoder(null, null, true);
    try {
      decoder.prepareStages(hit.data());
      assertTrue("the whole loop missed a symbol the same frame decodes",
          decoder.detectOnly());
      assertTrue("the QR group missed the QR symbol the whole loop finds",
          decoder.detectOnlyGroup("QRCode"));
      assertFalse("the 1D group found a QR symbol", decoder.detectOnlyGroup("AllLinear"));
      assertFalse(decoder.detectOnlyGroup("DataMatrix"));
      assertFalse(decoder.detectOnlyGroup("Aztec"));
      assertFalse(decoder.detectOnlyGroup("PDF417"));
      assertFalse(decoder.detectOnlyGroup("MaxiCode"));
      // Repeats, because the benchmark calls one group thousands of times against the
      // reader it cached on the first call.
      for (int i = 0; i < 32; i++) {
        assertTrue("the QR group stopped finding the symbol at iteration " + i,
            decoder.detectOnlyGroup("QRCode"));
      }
    } finally {
      decoder.close();
    }
  }

  /** Every group runs over a frame that holds nothing, which is the attributed case. */
  @Test
  public void everyGroupRunsOverAMissFrame() {
    Frame miss = blankFrame();
    NativeFrameDecoder decoder = new NativeFrameDecoder(null, null, true);
    try {
      decoder.prepareStages(miss.data());
      for (String group : new String[] {
          "AllLinear", "QRCode", "DataMatrix", "Aztec", "PDF417", "MaxiCode" }) {
        assertFalse("group " + group + " found a symbol in a blank frame",
            decoder.detectOnlyGroup(group));
      }
    } finally {
      decoder.close();
    }
  }

  /**
   * A group the decoder's own formats build no reader for is refused, not measured.
   *
   * <p>Timing the 1D reader under a QR-only scanner would attribute work that
   * configuration never does. Refusing is the only answer that cannot be mistaken for a
   * cheap reader.
   */
  @Test
  public void aGroupOutsideTheConfiguredFormatsIsRefused() throws Exception {
    Frame hit = frameWith("STAGE-NARROW", BarcodeFormat.QR_CODE);
    NativeFrameDecoder decoder =
        new NativeFrameDecoder(null, Arrays.asList(BarcodeFormat.QR_CODE), true);
    try {
      decoder.prepareStages(hit.data());
      assertTrue("the QR group is inside a QR-only format set and should still run",
          decoder.detectOnlyGroup("QRCode"));
      try {
        decoder.detectOnlyGroup("AllLinear");
        fail("a QR-only decoder timed the 1D reader it never builds");
      } catch (IllegalStateException expected) {
        // The only safe answer.
      }
    } finally {
      decoder.close();
    }
  }

  /** An unparsable group is a mistake in the caller, not a zero in the results. */
  @Test
  public void anUnknownGroupIsRefused() {
    NativeFrameDecoder decoder = new NativeFrameDecoder(null, null, true);
    try {
      decoder.prepareStages(blankFrame().data());
      try {
        decoder.detectOnlyGroup("NotAFormatName");
        fail("an unparsable format group was measured as a reader");
      } catch (IllegalStateException expected) {
        // Expected.
      }
      try {
        decoder.detectOnlyGroup("");
        fail("an empty format group was measured, which is every reader at once");
      } catch (IllegalStateException expected) {
        // Expected: an empty format set means "all formats" to ReaderOptions.
      }
    } finally {
      decoder.close();
    }
  }

  /** Neither stage invents a number when no frame is held. */
  @Test
  public void bothStagesRefuseWithoutAFrame() {
    NativeFrameDecoder decoder = new NativeFrameDecoder(null, null, true);
    try {
      assertEquals("thresholding reported a matrix with no frame held", -1L,
          decoder.binarizeOnly());
      assertFalse("detection reported a hit with no frame held", decoder.detectOnly());
      // And again after a release, because the benchmark releases in @After and a stale
      // bitmap would make the next class's first iterations measure the previous frame.
      decoder.releaseStages();
      assertEquals(-1L, decoder.binarizeOnly());
      assertFalse(decoder.detectOnly());
      try {
        decoder.detectOnlyGroup("QRCode");
        fail("a group reported an outcome with no frame held");
      } catch (IllegalStateException expected) {
        // The matrix is gone; there is nothing to run a reader over.
      }
    } finally {
      decoder.close();
    }
  }

  /** A closed decoder refuses rather than reaching through a dangling handle. */
  @Test
  public void aClosedDecoderRefusesTheStages() throws Exception {
    Frame hit = frameWith("STAGE-CLOSED", BarcodeFormat.QR_CODE);
    NativeFrameDecoder decoder = new NativeFrameDecoder(null, null, true);
    decoder.prepareStages(hit.data());
    decoder.close();
    try {
      decoder.binarizeOnly();
      fail("thresholding ran on a closed decoder");
    } catch (IllegalStateException expected) {
      // The handle is gone; this is the only safe answer.
    }
    try {
      decoder.detectOnly();
      fail("detection ran on a closed decoder");
    } catch (IllegalStateException expected) {
      // Likewise.
    }
    try {
      decoder.detectOnlyGroup("QRCode");
      fail("a group ran on a closed decoder");
    } catch (IllegalStateException expected) {
      // Likewise.
    }
    // Releasing twice, and after close, has to stay harmless: @After does exactly this.
    decoder.releaseStages();
    decoder.releaseStages();
  }
}
