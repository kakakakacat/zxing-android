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

package zxingbench;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.BinaryBitmap;
import io.github.kakakakacat.zxing.DecodeHintType;
import io.github.kakakakacat.zxing.MultiFormatReader;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.common.HybridBinarizer;

import zxingbench.corpus.L1Classifier;
import zxingbench.corpus.L1Report;
import zxingbench.corpus.L1Status;
import zxingbench.corpus.Manifest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * L1: runs the fixed dataset through the decoder and reports correctness and per-sample
 * decode time.
 *
 * <p>Images are decoded to a luminance plane once, up front; the timed section covers the
 * binarizer and reader only, so file reading and PNG/JPEG decompression are not counted.
 *
 * <p>Host JVM timings are a diagnostic aid. They are not an Android performance result and
 * must never be reported as one.
 *
 * <pre>
 *   ./gradlew :benchmark-jvm:run --args="--manifest benchmark/dataset/manifest.json \
 *       --out build/l1.json --config default --warmup 3 --measure 10"
 * </pre>
 */
public final class L1Runner {

  private L1Runner() {
  }

  /** A named set of decode hints, so results from different configurations never mix. */
  private enum Config {
    /** Everything the reader supports, no extra effort. Matches the app's default. */
    DEFAULT(null, false),
    QR_ONLY(new BarcodeFormat[] { BarcodeFormat.QR_CODE }, false),
    ONED_ONLY(new BarcodeFormat[] {
        BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.EAN_8, BarcodeFormat.EAN_13,
        BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODE_128,
        BarcodeFormat.ITF, BarcodeFormat.CODABAR, BarcodeFormat.RSS_14,
        BarcodeFormat.RSS_EXPANDED }, false),
    TRY_HARDER(null, true);

    final BarcodeFormat[] formats;
    final boolean tryHarder;

    Config(BarcodeFormat[] formats, boolean tryHarder) {
      this.formats = formats;
      this.tryHarder = tryHarder;
    }

    Map<DecodeHintType,Object> hints() {
      Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
      if (formats != null) {
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(formats));
      }
      if (tryHarder) {
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
      }
      return hints;
    }
  }

  public static void main(String[] args) throws Exception {
    Map<String,String> opts = parseArgs(args);
    Path repo = Paths.get(opts.getOrDefault("repo", ".")).toAbsolutePath().normalize();
    Path manifestPath = repo.resolve(
        opts.getOrDefault("manifest", "benchmark/dataset/manifest.json"));
    Path out = repo.resolve(opts.getOrDefault("out", "build/benchmark/l1.json"));
    Config config = Config.valueOf(
        opts.getOrDefault("config", "default").toUpperCase(Locale.ROOT));
    int warmup = Integer.parseInt(opts.getOrDefault("warmup", "3"));
    int measure = Integer.parseInt(opts.getOrDefault("measure", "10"));
    String runId = opts.getOrDefault("run-id", "local-" + System.currentTimeMillis());
    String commit = opts.getOrDefault("commit", "unknown");
    // Off by default; see the device runner for what a luminance hash settles.
    boolean lumaHash = Boolean.parseBoolean(opts.getOrDefault("luma-hash", "false"));

    Manifest manifest = Manifest.parse(
        new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8));
    System.out.printf("L1 %s: %d samples, dataset %s%n",
        config, manifest.samples.size(), manifest.datasetHash.substring(0, 16));

    Map<DecodeHintType,Object> hints = config.hints();
    List<Map<String,Object>> rows = new ArrayList<>();
    Map<L1Status,Integer> totals = new EnumMap<>(L1Status.class);
    for (L1Status s : L1Status.values()) {
      totals.put(s, 0);
    }

    int done = 0;
    for (Manifest.Sample sample : manifest.samples) {
      Frame frame;
      try {
        frame = Frame.load(repo.resolve(sample.path).toFile());
      } catch (IOException ioe) {
        System.err.println("skipping unreadable " + sample.sampleId + ": " + ioe);
        continue;
      }

      // Warm up this sample, then measure it repeatedly. Every repetition decodes the
      // same preloaded buffer, so only the decode path is timed.
      for (int i = 0; i < warmup; i++) {
        try {
          decodeOnce(frame, hints);
        } catch (Throwable ignored) {
          // A sample that does not decode still has to be warmed up and timed.
        }
      }
      long[] nanos = new long[measure];
      Result last = null;
      Throwable error = null;
      for (int i = 0; i < measure; i++) {
        long t0 = System.nanoTime();
        try {
          last = decodeOnce(frame, hints);
        } catch (Throwable t) {
          error = t;
          last = null;
        }
        nanos[i] = System.nanoTime() - t0;
      }
      Arrays.sort(nanos);

      L1Status status = classify(sample, last, error);
      totals.put(status, totals.get(status) + 1);

      Map<String,Object> row = new LinkedHashMap<>();
      row.put("sample_id", sample.sampleId);
      row.put("scene_tags", String.join("|", sample.sceneTags));
      row.put("expected_format", sample.expectedFormat);
      row.put("actual_format", last == null ? null : last.getBarcodeFormat().toString());
      row.put("status", status.name());
      row.put("width", sample.width);
      row.put("height", sample.height);
      row.put("decode_ns_p50", nanos[nanos.length / 2]);
      row.put("decode_ns_min", nanos[0]);
      row.put("decode_ns_max", nanos[nanos.length - 1]);
      row.put("repeats", measure);
      if (lumaHash) {
        row.put("luma_sha256", sha256(frame.luminance));
      }
      rows.add(row);

      if (++done % 200 == 0) {
        System.out.printf("  %d/%d%n", done, manifest.samples.size());
      }
    }

    writeResults(out, runId, commit, config.name(), manifest, warmup, measure, totals, rows);

    System.out.println("--- status counts");
    for (Map.Entry<L1Status,Integer> e : totals.entrySet()) {
      if (e.getValue() > 0) {
        System.out.printf("  %-18s %d%n", e.getKey(), e.getValue());
      }
    }
    System.out.println("wrote " + out);
  }

  /** The hash of the plane handed to the decoder; see the device runner. */
  private static String sha256(byte[] bytes) throws java.security.NoSuchAlgorithmException {
    byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
    StringBuilder hex = new StringBuilder(digest.length * 2);
    for (byte b : digest) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16));
      hex.append(Character.forDigit(b & 0xF, 16));
    }
    return hex.toString();
  }

  private static Result decodeOnce(Frame frame, Map<DecodeHintType,Object> hints)
      throws io.github.kakakakacat.zxing.NotFoundException {
    PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
        frame.luminance, frame.width, frame.height, 0, 0, frame.width, frame.height, false);
    MultiFormatReader reader = new MultiFormatReader();
    reader.setHints(hints);
    return reader.decode(new BinaryBitmap(new HybridBinarizer(source)));
  }

  /**
   * Scores one decode. The rules live in {@link L1Classifier}, shared with the device
   * runner: two L1 files are only subtractable if the same code scored both.
   */
  private static L1Status classify(Manifest.Sample sample, Result result, Throwable error) {
    boolean hardError =
        error != null && !(error instanceof io.github.kakakakacat.zxing.NotFoundException);
    return L1Classifier.classify(sample,
        result == null ? null : result.getBarcodeFormat().toString(),
        result == null ? null : result.getText(),
        hardError);
  }

  private static void writeResults(Path out,
                                   String runId,
                                   String commit,
                                   String config,
                                   Manifest manifest,
                                   int warmup,
                                   int measure,
                                   Map<L1Status,Integer> totals,
                                   List<Map<String,Object>> rows) throws IOException {
    Files.createDirectories(out.getParent());
    Map<String,Object> envelope = new LinkedHashMap<>();
    envelope.put("run_id", runId);
    envelope.put("commit", commit);
    envelope.put("runner", "host-jvm");
    envelope.put("backend", "zxing-java");
    envelope.put("config", config);
    envelope.put("dataset_hash", manifest.datasetHash);
    envelope.put("sample_count", manifest.samples.size());
    envelope.put("warmup", warmup);
    envelope.put("measure", measure);
    envelope.put("jvm", System.getProperty("java.vm.name") + " "
        + System.getProperty("java.version"));
    envelope.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
    envelope.put("note",
        "Host JVM result. Diagnostic only; not an Android performance result.");
    try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
      L1Report.write(w, envelope, totals, rows);
    }
  }

  private static Map<String,String> parseArgs(String[] args) {
    Map<String,String> opts = new LinkedHashMap<>();
    for (int i = 0; i < args.length; i++) {
      if (!args[i].startsWith("--")) {
        throw new IllegalArgumentException("unexpected argument: " + args[i]);
      }
      String key = args[i].substring(2);
      if (i + 1 >= args.length) {
        throw new IllegalArgumentException("missing value for --" + key);
      }
      opts.put(key, args[++i]);
    }
    return opts;
  }

  /** A preloaded 8-bit luminance plane, stride == width. */
  private static final class Frame {
    final byte[] luminance;
    final int width;
    final int height;

    private Frame(byte[] luminance, int width, int height) {
      this.luminance = luminance;
      this.width = width;
      this.height = height;
    }

    static Frame load(File file) throws IOException {
      BufferedImage image = ImageIO.read(file);
      if (image == null) {
        throw new IOException("no image reader for " + file);
      }
      int width = image.getWidth();
      int height = image.getHeight();
      int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
      byte[] luminance = new byte[width * height];
      for (int i = 0; i < argb.length; i++) {
        int pixel = argb[i];
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        // Same integer luma weights as zxing's RGBLuminanceSource.
        luminance[i] = (byte) ((r + 2 * g + b) >> 2);
      }
      return new Frame(luminance, width, height);
    }
  }
}
