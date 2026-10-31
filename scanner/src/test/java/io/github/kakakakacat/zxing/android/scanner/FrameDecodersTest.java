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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Naming a decoder backend.
 *
 * <p>A benchmark run selects its backend by name. Getting that wrong once already cost a
 * full-matrix run on a physical device: {@code -Pl2.backend=java} did not match the
 * constant {@code zxing-java}, {@code isAvailable} answered false, and all 27 cases
 * skipped as though the device could not run them. A skipped run looks like a clean one.
 */
public final class FrameDecodersTest {

  @Test
  public void shortAndCanonicalNamesBothResolve() {
    assertEquals(FrameDecoders.JAVA, FrameDecoders.canonical("java"));
    assertEquals(FrameDecoders.JAVA, FrameDecoders.canonical(FrameDecoders.JAVA));
    assertEquals(FrameDecoders.NATIVE, FrameDecoders.canonical("native"));
    assertEquals(FrameDecoders.NATIVE, FrameDecoders.canonical(FrameDecoders.NATIVE));
  }

  @Test
  public void anUnknownNameThrowsRatherThanReportingUnavailable() {
    // The distinction that matters: "no build for this ABI" may skip a benchmark case,
    // but a name nobody recognises is a mistake in the request and has to be loud.
    for (String bad : new String[] {"cpp", "zxing", "JAVA", "", "zxing-java "}) {
      try {
        FrameDecoders.canonical(bad);
        fail("expected an unknown backend name to throw: '" + bad + "'");
      } catch (IllegalArgumentException expected) {
        assertTrue(expected.getMessage(), expected.getMessage().contains("unknown decoder backend"));
      }
    }
  }

  @Test
  public void isAvailableThrowsOnAnUnknownNameInsteadOfAnsweringFalse() {
    try {
      FrameDecoders.isAvailable("nonsense");
      fail("expected an unknown backend name to throw");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void theJavaBackendIsAlwaysAvailableUnderEitherName() {
    assertTrue(FrameDecoders.isAvailable("java"));
    assertTrue(FrameDecoders.isAvailable(FrameDecoders.JAVA));
  }

  @Test
  public void buildInfoNamesTheBackendItDescribes() {
    assertTrue(FrameDecoders.buildInfo("java").contains("backend=zxing-java"));
  }

  @Test
  public void theCanonicalNamesAreWhatGetsRecorded() {
    // These strings end up in result files and in comparison reports. Changing one
    // silently makes old results incomparable with new ones.
    assertEquals("zxing-java", FrameDecoders.JAVA);
    assertEquals("zxing-cpp", FrameDecoders.NATIVE);
  }
}
