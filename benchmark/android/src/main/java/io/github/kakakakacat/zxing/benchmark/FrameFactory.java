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

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.MultiFormatWriter;
import io.github.kakakakacat.zxing.WriterException;
import io.github.kakakakacat.zxing.common.BitMatrix;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Builds the fixed replay frames of {@code docs/maint/BENCHMARKS.md} §10.4.
 *
 * <p>Frames are generated rather than committed. Nine raw luminance planes at these
 * resolutions is about ten megabytes of binary in the repository, and a committed blob is
 * no more reproducible than a generator that is deterministic and tested — it is only
 * harder to review. The generator is therefore the artefact, {@link #build} is pure, and
 * every frame carries the SHA-256 of its own bytes so a result set can state exactly what
 * was measured. §10.4's comparability rule applies to that hash: a result set whose frame
 * hashes differ from the baseline's is not comparable, however it was produced.
 *
 * <p>The barcode is drawn into the crop rectangle that {@link ScanGeometry} produces, so a
 * hit frame decodes through the real crop rather than only when the whole frame is read.
 */
public final class FrameFactory {

  /** The three preview sizes of §10.4. */
  public enum Resolution {
    VGA("vga", 640, 480),
    HD("hd", 1280, 720),
    FHD("fhd", 1920, 1080);

    public final String id;
    public final int width;
    public final int height;

    Resolution(String id, int width, int height) {
      this.id = id;
      this.width = width;
      this.height = height;
    }
  }

  /**
   * The three frame variants of §10.4. The hit/miss split is explicit because a miss costs
   * more than a hit — the reader exhausts every enabled format before giving up — so a
   * pooled average is really a statement about the hit rate of whatever frames were used.
   */
  public enum Variant {
    HIT_QR("hit_qr"),
    HIT_1D("hit_1d"),
    MISS("miss");

    public final String id;

    Variant(String id) {
      this.id = id;
    }
  }

  /** A generated preview buffer: an 8-bit luminance plane, stride == width. */
  public static final class Frame {
    public final byte[] luminance;
    public final int width;
    public final int height;
    public final int stride;
    /** The crop rectangle this frame's barcode was drawn into, {left, top, right, bottom}. */
    public final int[] crop;
    /** SHA-256 over the dimensions and the plane. Travels with every result set. */
    public final String sha256;

    Frame(byte[] luminance, int width, int height, int[] crop, String sha256) {
      this.luminance = luminance;
      this.width = width;
      this.height = height;
      this.stride = width;
      this.crop = crop;
      this.sha256 = sha256;
    }
  }

  /** Short, fixed payloads: the point is the pipeline, not the decoder's capacity limits. */
  private static final String QR_PAYLOAD = "ZX-L2-QR";
  /** A valid UPC-A, check digit included; the writer rejects anything else. */
  private static final String UPCA_PAYLOAD = "012345678905";

  /** Luminance of the quiet zone and the light modules. */
  private static final byte WHITE = (byte) 0xFF;
  /** Luminance of the dark modules. */
  private static final byte BLACK = 0x00;

  /**
   * Background noise band. Narrow and mid-grey on purpose: a real empty preview is sensor
   * noise rather than flat grey, but high-contrast noise invites the binarizer to find
   * structure that is not there, and a miss frame that accidentally decodes is not a miss
   * frame. {@code FrameFactoryTest} holds this to it.
   */
  private static final int NOISE_MIN = 100;
  private static final int NOISE_SPAN = 56;

  /** White margin around the barcode, in frame pixels, on top of the writer's own. */
  private static final int QUIET_ZONE = 12;

  private FrameFactory() {
  }

  /**
   * Builds one frame. Pure: the same arguments always produce the same bytes and the same
   * {@link Frame#sha256}, on any device and on the host.
   */
  public static Frame build(Resolution resolution, Variant variant) {
    int width = resolution.width;
    int height = resolution.height;
    int[] crop = ScanGeometry.crop(width, height);

    byte[] plane = new byte[width * height];
    fillNoise(plane, seedFor(resolution, variant));

    if (variant != Variant.MISS) {
      drawBarcode(plane, width, crop, variant);
    }

    return new Frame(plane, width, height, crop, sha256(plane, width, height));
  }

  /**
   * A fixed seed per frame, so the noise is reproducible and two variants never share a
   * background by accident.
   */
  private static long seedFor(Resolution resolution, Variant variant) {
    return 0x5A58494E47L * (resolution.ordinal() + 1L) + variant.ordinal() * 31L;
  }

  /**
   * xorshift64*, written out here rather than taken from {@code java.util.Random} so the
   * stream is defined by this file and cannot change under a JDK or ART update. The frame
   * hashes in a committed baseline depend on it.
   */
  private static void fillNoise(byte[] plane, long seed) {
    long state = seed == 0 ? 1 : seed;
    for (int i = 0; i < plane.length; i++) {
      state ^= state >>> 12;
      state ^= state << 25;
      state ^= state >>> 27;
      long value = state * 0x2545F4914F6CDD1DL;
      // Take a high bit range; the low bits of this generator are the weakest.
      int noise = (int) ((value >>> 33) % NOISE_SPAN);
      plane[i] = (byte) (NOISE_MIN + noise);
    }
  }

  private static void drawBarcode(byte[] plane, int stride, int[] crop, Variant variant) {
    int cropWidth = crop[2] - crop[0];
    int cropHeight = crop[3] - crop[1];

    BarcodeFormat format;
    String payload;
    int targetWidth;
    int targetHeight;
    if (variant == Variant.HIT_QR) {
      format = BarcodeFormat.QR_CODE;
      payload = QR_PAYLOAD;
      // Square, as large as the short side of the crop allows once the quiet zone is cut.
      int side = Math.min(cropWidth, cropHeight) - 2 * QUIET_ZONE;
      targetWidth = side;
      targetHeight = side;
    } else {
      format = BarcodeFormat.UPC_A;
      payload = UPCA_PAYLOAD;
      // Wide and short, the shape a 1D symbol is actually printed in. 95 modules have to
      // fit across, so the crop's long side is the one that matters.
      targetWidth = cropWidth - 2 * QUIET_ZONE;
      targetHeight = Math.max(1, Math.min(cropHeight - 2 * QUIET_ZONE, cropHeight / 2));
    }
    if (targetWidth <= 0 || targetHeight <= 0) {
      throw new IllegalStateException("crop too small for a barcode: "
          + cropWidth + 'x' + cropHeight);
    }

    BitMatrix matrix;
    try {
      // No MARGIN hint: each writer's default quiet zone is part of the symbol's
      // specification — nine modules for UPC/EAN — and it scales with the module size,
      // which a fixed pixel margin here does not. Suppressing it made the 1920x1080 hit
      // frame undecodable while the 640x480 one still decoded, which is exactly the kind
      // of silent resolution-dependent difference a baseline must not carry.
      matrix = new MultiFormatWriter().encode(payload, format, targetWidth, targetHeight);
    } catch (WriterException we) {
      // Fixed payloads and sizes: this would be a programming error, not a runtime case.
      throw new IllegalStateException("could not encode the " + variant.id + " frame", we);
    }

    int matrixWidth = matrix.getWidth();
    int matrixHeight = matrix.getHeight();
    int originX = crop[0] + (cropWidth - matrixWidth) / 2;
    int originY = crop[1] + (cropHeight - matrixHeight) / 2;

    // The quiet zone goes down first: a symbol drawn straight onto noise has no margin and
    // is a test of the binarizer's patience rather than of the pipeline.
    fillRect(plane, stride, originX - QUIET_ZONE, originY - QUIET_ZONE,
             matrixWidth + 2 * QUIET_ZONE, matrixHeight + 2 * QUIET_ZONE, WHITE);

    for (int y = 0; y < matrixHeight; y++) {
      int rowStart = (originY + y) * stride + originX;
      for (int x = 0; x < matrixWidth; x++) {
        plane[rowStart + x] = matrix.get(x, y) ? BLACK : WHITE;
      }
    }
  }

  private static void fillRect(byte[] plane, int stride, int left, int top,
                               int width, int height, byte value) {
    int planeHeight = plane.length / stride;
    int x0 = Math.max(0, left);
    int y0 = Math.max(0, top);
    int x1 = Math.min(stride, left + width);
    int y1 = Math.min(planeHeight, top + height);
    for (int y = y0; y < y1; y++) {
      int rowStart = y * stride;
      for (int x = x0; x < x1; x++) {
        plane[rowStart + x] = value;
      }
    }
  }

  private static String sha256(byte[] plane, int width, int height) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException nsae) {
      throw new IllegalStateException("SHA-256 is required on every supported platform", nsae);
    }
    // Dimensions first, so two planes of equal bytes but different shapes cannot collide.
    digest.update(new byte[] {
        (byte) (width >>> 8), (byte) width, (byte) (height >>> 8), (byte) height });
    byte[] hash = digest.digest(plane);
    StringBuilder hex = new StringBuilder(hash.length * 2);
    for (byte b : hash) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16));
      hex.append(Character.forDigit(b & 0xF, 16));
    }
    return hex.toString();
  }
}
