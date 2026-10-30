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

package com.google.zxing.client.android.scanner;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Runs fixed frames through the same luminance-source and reader path the scanner uses on
 * live preview frames, on the device's own ART.
 *
 * <p>This is the correctness floor for the decode pipeline: it does not need a camera, a
 * physical barcode or a steady hand, so it can fail a change deterministically. It does
 * <em>not</em> say anything about autofocus, camera compatibility or scan latency.
 */
@RunWith(AndroidJUnit4.class)
public final class FrameDecodeTest {

  private static final Charset UTF8 = Charset.forName("UTF-8");

  @Test
  public void fixedFramesDecodeToTheExpectedContents() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getContext();
    AssetManager assets = context.getAssets();
    JSONArray samples = new JSONObject(readAsset(assets, "smoke-set.json"))
        .getJSONArray("samples");

    List<String> failures = new ArrayList<>();
    int checked = 0;

    for (int i = 0; i < samples.length(); i++) {
      JSONObject sample = samples.getJSONObject(i);
      String sampleId = sample.getString("sample_id");
      String expectedFormat = sample.isNull("expected_format")
          ? null : sample.getString("expected_format");
      String expectedText = sample.isNull("expected_text")
          ? null : stripTrailingNewline(sample.getString("expected_text"));

      PlanarYUVLuminanceSource source = loadAsLuminance(assets, sample.getString("file"));
      Result result;
      try {
        // A fresh reader per sample: decodeWithState carries hints between frames, and
        // this test is about one frame at a time.
        result = new MultiFormatReader()
            .decode(new BinaryBitmap(new HybridBinarizer(source)));
      } catch (NotFoundException | RuntimeException e) {
        result = null;
      }
      checked++;

      if (expectedFormat == null) {
        // Negative sample: anything decoded here is a false positive.
        if (result != null) {
          failures.add(sampleId + ": expected no result, got "
              + result.getBarcodeFormat() + " \"" + result.getText() + '"');
        }
        continue;
      }
      if (result == null) {
        failures.add(sampleId + ": expected " + expectedFormat + ", decoded nothing");
        continue;
      }
      if (result.getBarcodeFormat() != BarcodeFormat.valueOf(expectedFormat)) {
        failures.add(sampleId + ": expected format " + expectedFormat
            + ", got " + result.getBarcodeFormat());
        continue;
      }
      if (expectedText != null && !expectedText.equals(result.getText())) {
        failures.add(sampleId + ": expected text \"" + expectedText
            + "\", got \"" + result.getText() + '"');
      }
    }

    assertTrue("checked " + checked + " samples, " + failures.size() + " failed:\n"
        + join(failures), failures.isEmpty());
  }

  /**
   * Turns a test image into the kind of buffer the camera hands over: an 8-bit luminance
   * plane with stride == width, wrapped exactly as CameraManager wraps a preview frame.
   */
  private static PlanarYUVLuminanceSource loadAsLuminance(AssetManager assets, String name)
      throws IOException {
    Bitmap bitmap;
    try (InputStream in = assets.open("frames/" + name)) {
      bitmap = BitmapFactory.decodeStream(in);
    }
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    int[] argb = new int[width * height];
    bitmap.getPixels(argb, 0, width, 0, 0, width, height);
    bitmap.recycle();

    byte[] luminance = new byte[width * height];
    for (int i = 0; i < argb.length; i++) {
      int pixel = argb[i];
      int r = (pixel >> 16) & 0xFF;
      int g = (pixel >> 8) & 0xFF;
      int b = pixel & 0xFF;
      // Same integer luma weights zxing's own RGBLuminanceSource uses.
      luminance[i] = (byte) ((r + 2 * g + b) >> 2);
    }
    return new PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false);
  }

  private static String readAsset(AssetManager assets, String name) throws IOException {
    try (InputStream in = assets.open(name)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
      }
      return out.toString(UTF8.name());
    }
  }

  private static String stripTrailingNewline(String s) {
    int end = s.length();
    while (end > 0 && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) {
      end--;
    }
    return s.substring(0, end);
  }

  private static String join(List<String> lines) {
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
      sb.append("  ").append(line).append('\n');
    }
    return sb.toString();
  }
}
