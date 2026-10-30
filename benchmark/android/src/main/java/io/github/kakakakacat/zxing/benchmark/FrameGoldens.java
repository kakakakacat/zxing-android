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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads {@code benchmark/android/goldens/frames.golden.json}, the committed frame hashes
 * that make two result sets comparable (§10.4).
 *
 * <p>One file, three readers: the host unit test, the on-device run and the report
 * converter. The alternative — each side knowing the hashes separately — is how a baseline
 * quietly stops describing the frames that were actually measured.
 *
 * <p>The parser is deliberately a regular expression over the three fields this class
 * needs, not a JSON library: {@code org.json} exists on the device but not in a host unit
 * test, and pulling a parser into a benchmark module to read nine constants would be a
 * dependency bought for nothing. {@code FrameGoldensTest} holds it to the committed file.
 */
public final class FrameGoldens {

  /** System property the Gradle unit test task sets to the golden file's path. */
  public static final String GOLDEN_FILE_PROPERTY = "l2.goldenFile";

  /** Asset name the instrumentation run reads, from the {@code goldens/} source dir. */
  public static final String GOLDEN_ASSET = "frames.golden.json";

  private static final Pattern FRAME = Pattern.compile(
      "\"resolution\"\\s*:\\s*\"([a-z0-9]+)\"\\s*,\\s*"
          + "\"variant\"\\s*:\\s*\"([a-z0-9_]+)\"[^}]*?"
          + "\"sha256\"\\s*:\\s*\"([0-9a-f]{64})\"",
      Pattern.DOTALL);

  private FrameGoldens() {
  }

  /** @return {@code "<resolution>/<variant>"} to SHA-256, in file order */
  public static Map<String,String> parse(String json) {
    Map<String,String> goldens = new LinkedHashMap<>();
    Matcher matcher = FRAME.matcher(json);
    while (matcher.find()) {
      goldens.put(matcher.group(1) + '/' + matcher.group(2), matcher.group(3));
    }
    if (goldens.isEmpty()) {
      throw new IllegalStateException("no frame goldens found; the file format changed");
    }
    return goldens;
  }

  public static Map<String,String> load(InputStream in) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int read;
    while ((read = in.read(chunk)) != -1) {
      buffer.write(chunk, 0, read);
    }
    return parse(new String(buffer.toByteArray(), Charset.forName("UTF-8")));
  }

  /** The key a {@link FrameFactory.Frame} is recorded under. */
  public static String key(FrameFactory.Resolution resolution, FrameFactory.Variant variant) {
    return resolution.id + '/' + variant.id;
  }
}
