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

import android.content.res.AssetManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.kakakakacat.zxing.Result;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The gate in front of the benchmark: the frames this device generated are the frames the
 * baseline was taken on, and they still behave the way the matrix assumes.
 *
 * <p>Not a benchmark. It runs first so that a run which would have produced an
 * incomparable or meaningless number fails loudly instead. In particular a {@code miss}
 * frame that started decoding would turn the most expensive case in the matrix into the
 * cheapest, and every later comparison would be wrong in the flattering direction.
 */
@RunWith(AndroidJUnit4.class)
public final class FrameIntegrityTest {

  @Test
  public void generatedFramesMatchTheCommittedGoldens() throws IOException {
    Map<String,String> goldens = loadGoldens();
    assertEquals(9, goldens.size());

    for (FrameFactory.Resolution resolution : FrameFactory.Resolution.values()) {
      for (FrameFactory.Variant variant : FrameFactory.Variant.values()) {
        String key = FrameGoldens.key(resolution, variant);
        assertEquals(
            key + " generated different bytes on this device than the committed golden. "
                + "The result of this run is not comparable with any committed baseline.",
            goldens.get(key), FrameFactory.build(resolution, variant).sha256);
      }
    }
  }

  @Test
  public void hitFramesHitAndMissFramesMissOnThisDevice() {
    for (FrameFactory.Resolution resolution : FrameFactory.Resolution.values()) {
      for (FrameFactory.Variant variant : FrameFactory.Variant.values()) {
        FrameFactory.Frame frame = FrameFactory.build(resolution, variant);
        for (L2Config config : L2Config.values()) {
          Result result = FramePipeline.endToEnd(FramePipeline.reader(config), frame);
          String label = resolution.id + '/' + variant.id + " under " + config.id;
          if (config.canDecode(variant)) {
            assertNotNull(label + " did not decode", result);
          } else {
            assertNull(label + " decoded when the matrix says it cannot", result);
          }
        }
      }
    }
  }

  private static Map<String,String> loadGoldens() throws IOException {
    AssetManager assets =
        InstrumentationRegistry.getInstrumentation().getContext().getAssets();
    try (InputStream in = assets.open(FrameGoldens.GOLDEN_ASSET)) {
      return FrameGoldens.load(in);
    }
  }
}
