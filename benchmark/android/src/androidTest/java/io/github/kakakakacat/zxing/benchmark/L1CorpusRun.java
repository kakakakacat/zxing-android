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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.kakakakacat.zxing.DecodeHintType;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.android.scanner.FrameDecoder;
import io.github.kakakakacat.zxing.android.scanner.FrameDecoders;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import zxingbench.corpus.L1Classifier;
import zxingbench.corpus.L1Report;
import zxingbench.corpus.L1Status;
import zxingbench.corpus.Manifest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * L1 on a device: the 1168-sample corpus through one decoder backend.
 *
 * <p>Why this exists. L1 has only ever run on the host JVM against the Java decoder, and
 * L2's frames are nine clean generated images. A decoder that is faster on a clean frame
 * can be worse on a blurred or an angled one, and the native backend was made the default
 * on speed alone. This run is the missing number: <em>correctness</em>, on the corpus that
 * has the hard images, on the device that ships the native code.
 *
 * <p><strong>Not a benchmark, and deliberately times nothing.</strong> It lives in the
 * benchmark module because that is where the dataset tooling is, but it carries no
 * {@code BenchmarkRule}, no locked clocks and no thermal protocol, so there is no number
 * in its output that could be misread as one. §2 of {@code docs/maint/BENCHMARKS.md}
 * forbids quoting a timing without a protocol; the way to make that impossible is not to
 * record one. Speed is L2's question and has its own files.
 *
 * <p>The corpus is pushed to the device rather than packed into the APK: 109 MB of PNGs
 * in an androidTest asset directory would be built into every test run of the module,
 * including the benchmark's. {@code benchmark/tools/l1_device.sh} pushes it, runs this,
 * and pulls the result.
 *
 * <p>Every sample's bytes are hashed against the manifest before it is decoded. A corpus
 * that arrived incomplete or truncated would otherwise be scored as a decoder that misses
 * a lot, which is the one failure that would look exactly like the finding being
 * searched for.
 */
@RunWith(AndroidJUnit4.class)
public final class L1CorpusRun {

  /** Images are decoded one at a time and recycled; this bounds nothing but a mistake. */
  private static final int PROGRESS_EVERY = 200;

  @Test
  public void runTheCorpusThroughTheSelectedBackend() throws Exception {
    Bundle arguments = InstrumentationRegistry.getArguments();
    String backend = FrameDecoders.canonical(arguments.getString("l1.backend", "java"));
    // The same two configurations the host runner names, and for the same reason: a file
    // scored under one of them cannot be compared with a file scored under the other, so
    // the configuration travels in the envelope rather than in whoever typed the command.
    String config = arguments.getString("l1.config", "DEFAULT").toUpperCase(Locale.ROOT);
    Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
    if ("TRY_HARDER".equals(config)) {
      hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    } else if (!"DEFAULT".equals(config)) {
      throw new IllegalArgumentException("unknown l1.config: " + config
          + " (expected DEFAULT or TRY_HARDER)");
    }

    // An unknown backend name already threw above. Only a real device limitation is
    // allowed to skip: a typo that skipped the run would look like a clean pass that
    // decoded nothing.
    Assume.assumeTrue(
        "backend " + backend + " has no build for this device's ABI",
        FrameDecoders.isAvailable(backend));

    File corpus = arguments.containsKey("l1.corpus")
        ? new File(arguments.getString("l1.corpus")) : externalDir("l1");
    File manifestFile = new File(corpus, "manifest.json");
    assertTrue("no corpus at " + manifestFile.getAbsolutePath()
        + " -- run benchmark/tools/l1_device.sh, which pushes it",
        manifestFile.isFile());

    Manifest manifest = Manifest.parse(readUtf8(manifestFile));
    // Off by default: 1168 more hashes in the file, for a question that is only asked
    // when two runs disagree about a sample. See lumaHash below for what it settles.
    boolean lumaHash = Boolean.parseBoolean(arguments.getString("l1.lumaHash", "false"));

    Map<L1Status,Integer> totals = new EnumMap<>(L1Status.class);
    for (L1Status status : L1Status.values()) {
      totals.put(status, 0);
    }
    List<Map<String,Object>> rows = new ArrayList<>(manifest.samples.size());
    List<String> corrupt = new ArrayList<>();

    FrameDecoder decoder = FrameDecoders.create(backend, hints, null);
    try {
      int done = 0;
      for (Manifest.Sample sample : manifest.samples) {
        File image = new File(corpus, sample.path);
        if (!image.isFile()) {
          corrupt.add(sample.sampleId + ": missing");
          continue;
        }
        if (!sha256(image).equals(sample.sha256)) {
          corrupt.add(sample.sampleId + ": sha256 does not match the manifest");
          continue;
        }

        Frame frame = Frame.load(image);
        if (frame == null) {
          corrupt.add(sample.sampleId + ": no image decoder on this device");
          continue;
        }
        if (frame.width != sample.width || frame.height != sample.height) {
          corrupt.add(sample.sampleId + ": decoded " + frame.width + 'x' + frame.height
              + ", manifest says " + sample.width + 'x' + sample.height);
          continue;
        }

        Result result = null;
        Throwable error = null;
        try {
          // The same luminance source and the same whole-image crop as the host runner,
          // so the backends differ in the decoder and in nothing else.
          result = decoder.decode(new PlanarYUVLuminanceSource(
              frame.luminance, frame.width, frame.height,
              0, 0, frame.width, frame.height, false));
        } catch (Throwable t) {
          error = t;
        }

        L1Status status = L1Classifier.classify(sample,
            result == null ? null : result.getBarcodeFormat().toString(),
            result == null ? null : result.getText(),
            error != null);
        totals.put(status, totals.get(status) + 1);
        Map<String,Object> row = row(sample, result, status, error);
        if (lumaHash) {
          row.put("luma_sha256", sha256(frame.luminance));
        }
        rows.add(row);

        if (++done % PROGRESS_EVERY == 0) {
          android.util.Log.i("L1", done + "/" + manifest.samples.size());
        }
      }
    } finally {
      decoder.close();
    }

    File out = arguments.containsKey("l1.out")
        ? new File(arguments.getString("l1.out"))
        : new File(externalDir("l1-out"), "l1-" + backend + ".json");
    write(out, manifest, backend, config, arguments, totals, rows);

    // The corpus gate comes after the file is written: a partial result that says why it
    // is partial is more useful than no file at all.
    assertEquals("corpus samples that could not be used: " + corrupt,
        0, corrupt.size());
    assertEquals("every sample should have been scored",
        manifest.samples.size(), rows.size());
  }

  private static Map<String,Object> row(Manifest.Sample sample,
                                        Result result,
                                        L1Status status,
                                        Throwable error) {
    Map<String,Object> row = new LinkedHashMap<>();
    row.put("sample_id", sample.sampleId);
    row.put("scene_tags", join(sample.sceneTags));
    row.put("expected_format", sample.expectedFormat);
    row.put("actual_format", result == null ? null : result.getBarcodeFormat().toString());
    row.put("status", status.name());
    row.put("width", sample.width);
    row.put("height", sample.height);
    // What a disagreement actually said, where a disagreement is what this run is for.
    // Carried only for the two statuses where the text is the finding, so that a 1168-row
    // file stays diffable.
    if (status == L1Status.INCORRECT || status == L1Status.FALSE_POSITIVE) {
      row.put("actual_text", result == null ? null : result.getText());
    }
    if (error != null) {
      row.put("error", error.getClass().getName() + ": " + error.getMessage());
    }
    return row;
  }

  private static void write(File out,
                            Manifest manifest,
                            String backend,
                            String config,
                            Bundle arguments,
                            Map<L1Status,Integer> totals,
                            List<Map<String,Object>> rows) throws IOException {
    File parent = out.getParentFile();
    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
      throw new IOException("could not create " + parent);
    }
    Map<String,Object> envelope = new LinkedHashMap<>();
    envelope.put("run_id", arguments.getString("l1.runId", "device-" + System.currentTimeMillis()));
    envelope.put("commit", arguments.getString("l1.commit", "unknown"));
    envelope.put("runner", "device");
    envelope.put("backend", backend);
    envelope.put("backend_build", FrameDecoders.buildInfo(backend));
    envelope.put("config", config);
    envelope.put("dataset_hash", manifest.datasetHash);
    envelope.put("sample_count", manifest.samples.size());
    envelope.put("device", Build.MODEL);
    envelope.put("fingerprint", Build.FINGERPRINT);
    envelope.put("android_release", Build.VERSION.RELEASE);
    envelope.put("android_sdk", Build.VERSION.SDK_INT);
    envelope.put("abi", Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0]);
    envelope.put("note", "Correctness only. No timing is recorded here on purpose; "
        + "this run has no clock or thermal protocol, so it could not produce one that "
        + "BENCHMARKS.md section 2 would admit.");
    try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(out), Charset.forName("UTF-8")))) {
      L1Report.write(w, envelope, totals, rows);
    }
    android.util.Log.i("L1", "wrote " + out.getAbsolutePath());
  }

  /**
   * Where the corpus is expected, and where the result is written.
   *
   * <p>{@code Android/media/<package>} rather than the more obvious
   * {@code getExternalFilesDir}. Both are the app's own directories, but what {@code adb
   * push} writes into the second one stays owned by the shell user and the app cannot
   * read it back — the corpus appears to be missing while {@code adb shell ls} shows
   * every file of it. The media directory hands the pushed files to the app, which is
   * why androidx.benchmark puts its own output there too.
   */
  private static File externalDir(String name) {
    File[] media =
        InstrumentationRegistry.getInstrumentation().getContext().getExternalMediaDirs();
    if (media.length == 0 || media[0] == null) {
      throw new IllegalStateException("this device exposes no external media directory");
    }
    return new File(media[0], name);
  }

  private static String join(List<String> parts) {
    StringBuilder joined = new StringBuilder();
    for (String part : parts) {
      if (joined.length() > 0) {
        joined.append('|');
      }
      joined.append(part);
    }
    return joined.toString();
  }

  private static String readUtf8(File file) throws IOException {
    byte[] bytes = new byte[(int) file.length()];
    try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
      in.readFully(bytes);
    }
    return new String(bytes, Charset.forName("UTF-8"));
  }

  /**
   * The hash of the plane that was handed to the decoder.
   *
   * <p>What it settles: whether a host run and a device run that disagree about a sample
   * disagree about the decoder or about the image. The file hashes already match — the
   * corpus gate above refuses to score a sample whose bytes differ — so anything left is
   * in how the PNG became pixels, and {@code ImageIO} and {@code BitmapFactory} are two
   * different PNG decoders.
   */
  private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
    return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static String sha256(File file) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] buffer = new byte[64 * 1024];
    try (InputStream in = new java.io.FileInputStream(file)) {
      int read;
      while ((read = in.read(buffer)) > 0) {
        digest.update(buffer, 0, read);
      }
    }
    return hex(digest.digest());
  }

  private static String hex(byte[] bytes) {
    StringBuilder hex = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16));
      hex.append(Character.forDigit(b & 0xF, 16));
    }
    return hex.toString();
  }

  /** An 8-bit luminance plane, stride == width, built the way the host runner builds one. */
  private static final class Frame {
    final byte[] luminance;
    final int width;
    final int height;

    private Frame(byte[] luminance, int width, int height) {
      this.luminance = luminance;
      this.width = width;
      this.height = height;
    }

    static Frame load(File file) {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inPreferredConfig = Bitmap.Config.ARGB_8888;
      // Density scaling would silently resize the corpus. The size check against the
      // manifest would catch it, but it should not be possible in the first place.
      options.inScaled = false;
      Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
      if (bitmap == null) {
        return null;
      }
      try {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);
        byte[] luminance = new byte[width * height];
        for (int i = 0; i < argb.length; i++) {
          int pixel = argb[i];
          int r = (pixel >> 16) & 0xFF;
          int g = (pixel >> 8) & 0xFF;
          int b = pixel & 0xFF;
          // The same integer luma weights as the host runner and as RGBLuminanceSource.
          // The corpus is PNG, so the pixels either side of this are bit-identical and
          // the two runs really do decode the same plane.
          luminance[i] = (byte) ((r + 2 * g + b) >> 2);
        }
        return new Frame(luminance, width, height);
      } finally {
        bitmap.recycle();
      }
    }
  }
}
