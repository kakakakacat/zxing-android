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

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Holds the committed golden file and the generator to each other.
 *
 * <p>This is the test that makes a baseline mean something: if a change to
 * {@link FrameFactory} alters a single pixel of a replay frame, this fails on the host
 * before anyone gets as far as a device, and the failure says to re-baseline rather than
 * letting an incomparable comparison be published.
 */
public final class FrameGoldensTest {

  @Test
  public void everyGeneratedFrameMatchesTheCommittedHash() throws IOException {
    Map<String,String> goldens = loadGoldens();
    assertEquals("the golden file should describe all nine frames of §10.4",
                 9, goldens.size());

    for (FrameFactory.Resolution resolution : FrameFactory.Resolution.values()) {
      for (FrameFactory.Variant variant : FrameFactory.Variant.values()) {
        String key = FrameGoldens.key(resolution, variant);
        String expected = goldens.get(key);
        assertNotNull("no golden hash committed for " + key, expected);
        assertEquals(
            key + " changed. The frames are the comparability key (BENCHMARKS.md §10.4): "
                + "either this is an unintended change to FrameFactory, or the goldens and "
                + "every committed baseline have to be regenerated together.",
            expected, FrameFactory.build(resolution, variant).sha256);
      }
    }
  }

  @Test
  public void theParserIgnoresFormattingItDoesNotCareAbout() {
    Map<String,String> parsed = FrameGoldens.parse(
        "{\"frames\":[{\"resolution\":\"vga\",\"variant\":\"miss\",\"width\":640,"
            + "\"crop\":[1,2,3,4],\"sha256\":\""
            + "0000000000000000000000000000000000000000000000000000000000000001\"}]}");
    assertEquals(1, parsed.size());
    assertTrue(parsed.containsKey("vga/miss"));
  }

  private static Map<String,String> loadGoldens() throws IOException {
    String path = System.getProperty(FrameGoldens.GOLDEN_FILE_PROPERTY);
    assertNotNull("the test task must set -D" + FrameGoldens.GOLDEN_FILE_PROPERTY, path);
    try (InputStream in = Files.newInputStream(Paths.get(path))) {
      return FrameGoldens.load(in);
    }
  }
}
