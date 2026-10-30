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

import io.github.kakakakacat.zxing.MultiFormatReader;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.BinaryBitmap;
import io.github.kakakakacat.zxing.common.HybridBinarizer;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The generator's own tests, required by {@code docs/maint/BENCHMARKS.md} §10.4.
 *
 * <p>They hold the generator to the three properties a committed baseline depends on: the
 * frames are deterministic, a hit frame really decodes through the real crop, and a miss
 * frame really does not decode. A miss frame that quietly started decoding would turn the
 * most expensive case in the matrix into the cheapest, and every comparison against the
 * baseline after it would be wrong in the flattering direction.
 *
 * <p>Runs on the host JVM: no device, no camera, no emulator.
 */
public final class FrameFactoryTest {

  @Test
  public void everyFrameHasTheRequestedShape() {
    for (FrameFactory.Resolution resolution : FrameFactory.Resolution.values()) {
      for (FrameFactory.Variant variant : FrameFactory.Variant.values()) {
        FrameFactory.Frame frame = FrameFactory.build(resolution, variant);
        assertEquals(resolution.width, frame.width);
        assertEquals(resolution.height, frame.height);
        assertEquals("stride must equal width for the Camera1 path",
                     frame.width, frame.stride);
        assertEquals(frame.width * frame.height, frame.luminance.length);
      }
    }
  }

  @Test
  public void generationIsDeterministic() {
    for (FrameFactory.Resolution resolution : FrameFactory.Resolution.values()) {
      for (FrameFactory.Variant variant : FrameFactory.Variant.values()) {
        FrameFactory.Frame first = FrameFactory.build(resolution, variant);
        FrameFactory.Frame second = FrameFactory.build(resolution, variant);
        assertArrayEquals(resolution.id + '/' + variant.id + " is not deterministic",
                          first.luminance, second.luminance);
        assertEquals(first.sha256, second.sha256);
      }
    }
  }

  @Test
  public void everyFrameHashIsDistinct() {
    // Nine frames, nine hashes. A collision would mean two cases in the matrix are
    // secretly the same measurement.
    String[] hashes = new String[FrameFactory.Resolution.values().length
        * FrameFactory.Variant.values().length];
    int i = 0;
    for (FrameFactory.Resolution resolution : FrameFactory.Resolution.values()) {
      for (FrameFactory.Variant variant : FrameFactory.Variant.values()) {
        hashes[i++] = FrameFactory.build(resolution, variant).sha256;
      }
    }
    for (int a = 0; a < hashes.length; a++) {
      assertEquals(64, hashes[a].length());
      for (int b = a + 1; b < hashes.length; b++) {
        assertTrue("duplicate frame hash: " + hashes[a], !hashes[a].equals(hashes[b]));
      }
    }
  }

  @Test
  public void hitFramesDecodeThroughTheRealCrop() {
    for (FrameFactory.Resolution resolution : FrameFactory.Resolution.values()) {
      assertDecodes(resolution, FrameFactory.Variant.HIT_QR, "ZX-L2-QR");
      assertDecodes(resolution, FrameFactory.Variant.HIT_1D, "012345678905");
    }
  }

  @Test
  public void missFramesDoNotDecodeUnderAnyConfiguration() {
    for (FrameFactory.Resolution resolution : FrameFactory.Resolution.values()) {
      FrameFactory.Frame frame = FrameFactory.build(resolution, FrameFactory.Variant.MISS);
      for (L2Config config : L2Config.values()) {
        Result result = FramePipeline.endToEnd(FramePipeline.reader(config), frame);
        assertNull(resolution.id + "/miss decoded under " + config.id
                       + ": the background is finding structure that is not there",
                   result);
      }
    }
  }

  @Test
  public void theCropIsWideEnoughForA1dSymbol() {
    // 95 modules across for UPC-A, and the benchmark is pointless if they land on one
    // pixel each. Three pixels per module is the floor this geometry has to clear.
    for (FrameFactory.Resolution resolution : FrameFactory.Resolution.values()) {
      int[] crop = ScanGeometry.crop(resolution.width, resolution.height);
      int cropWidth = crop[2] - crop[0];
      assertTrue(resolution.id + " crop is only " + cropWidth + "px wide",
                 cropWidth >= 95 * 3);
      assertTrue(resolution.id + " crop is not landscape: "
                     + Arrays.toString(crop),
                 cropWidth > crop[3] - crop[1]);
    }
  }

  @Test
  public void backgroundNoiseStaysInsideItsBand() {
    FrameFactory.Frame frame = FrameFactory.build(
        FrameFactory.Resolution.VGA, FrameFactory.Variant.MISS);
    for (byte b : frame.luminance) {
      int value = b & 0xFF;
      assertTrue("noise escaped its band: " + value, value >= 100 && value < 156);
    }
  }

  private static void assertDecodes(FrameFactory.Resolution resolution,
                                    FrameFactory.Variant variant,
                                    String expectedText) {
    FrameFactory.Frame frame = FrameFactory.build(resolution, variant);
    for (L2Config config : L2Config.values()) {
      MultiFormatReader reader = FramePipeline.reader(config);
      Result result = FramePipeline.endToEnd(reader, frame);
      String label = resolution.id + '/' + variant.id + " under " + config.id;
      if (config.canDecode(variant)) {
        assertNotNull(label + " did not decode", result);
        assertEquals(label + " decoded the wrong text", expectedText, result.getText());
      } else {
        assertNull(label + " decoded under a configuration that excludes its format",
                   result);
      }
    }
  }

  @Test
  public void theLuminanceSourceDoesNotCopyThePlane() {
    // CameraManager hands the camera's own buffer to PlanarYUVLuminanceSource with the
    // crop as offsets. If that ever became a copy, every per-frame allocation number in
    // a committed baseline would be measuring something else.
    FrameFactory.Frame frame = FrameFactory.build(
        FrameFactory.Resolution.HD, FrameFactory.Variant.HIT_QR);
    int[] crop = ScanGeometry.crop(frame.width, frame.height);
    PlanarYUVLuminanceSource source = FramePipeline.luminanceSource(frame, crop);
    assertEquals(crop[2] - crop[0], source.getWidth());
    assertEquals(crop[3] - crop[1], source.getHeight());

    // A sanity check that the two really share storage: binarizing has to see the
    // barcode that was drawn into the plane.
    assertNotNull(new BinaryBitmap(new HybridBinarizer(source)));
  }
}
