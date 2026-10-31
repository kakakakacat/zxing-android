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

package zxingbench.corpus;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

/**
 * The L1 result file, written the same way wherever the run happened.
 *
 * <p>The envelope is §8 of {@code docs/maint/BENCHMARKS.md}: what ran, on what, against
 * which dataset. Fields that only one runner can know — a JVM version, a device model, a
 * decoder backend — are passed in rather than guessed at here, but the layout, the key
 * order and the escaping are shared, so a host file and a device file can be diffed
 * directly instead of through a reader that knows about both.
 */
public final class L1Report {

  private L1Report() {
  }

  /**
   * @param envelope run metadata, written in iteration order; values may be String,
   *     Number or null
   * @param counts   every status, including the zeroes: a status missing from one file
   *     and present in another reads as a schema change rather than as a zero
   * @param rows     one map per sample, again written in iteration order
   */
  public static void write(Writer w,
                           Map<String,Object> envelope,
                           Map<L1Status,Integer> counts,
                           List<Map<String,Object>> rows) throws IOException {
    w.write("{\n");
    w.write("  \"layer\": \"L1\",\n");
    for (Map.Entry<String,Object> e : envelope.entrySet()) {
      w.write("  " + Json.quote(e.getKey()) + ": " + render(e.getValue()) + ",\n");
    }
    w.write("  \"status_counts\": {");
    boolean first = true;
    for (L1Status status : L1Status.values()) {
      Integer count = counts.get(status);
      if (!first) {
        w.write(",");
      }
      first = false;
      w.write("\n    " + Json.quote(status.name()) + ": " + (count == null ? 0 : count));
    }
    w.write("\n  },\n");
    w.write("  \"samples\": [\n");
    for (int i = 0; i < rows.size(); i++) {
      w.write("    {");
      boolean firstField = true;
      for (Map.Entry<String,Object> e : rows.get(i).entrySet()) {
        if (!firstField) {
          w.write(", ");
        }
        firstField = false;
        w.write(Json.quote(e.getKey()) + ": " + render(e.getValue()));
      }
      w.write(i == rows.size() - 1 ? "}\n" : "},\n");
    }
    w.write("  ]\n}\n");
  }

  private static String render(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Number) {
      return value.toString();
    }
    return Json.quote(value.toString());
  }
}
