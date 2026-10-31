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

package io.github.kakakakacat.zxing.android.camera;

import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;

import java.nio.ByteBuffer;

/**
 * The luma plane of a {@code YUV_420_888} image, read where it already lies.
 *
 * <p>Built for CameraX's {@code ImageProxy}: pass {@code getPlanes()[0]}'s buffer,
 * {@code getRowStride()} and {@code getPixelStride()} together with the scan rectangle.
 * Nothing is copied to construct one, and the only copy that ever happens is the scan
 * rectangle itself, in {@link #getMatrix()}. A frame is typically far larger than the
 * region being decoded, so the usual "drain the buffer into a {@code byte[]} and wrap it
 * in {@link PlanarYUVLuminanceSource}" costs a full-frame copy per preview frame to
 * produce exactly the same result.
 *
 * <p>This class deliberately does not depend on CameraX, or on any camera API. It needs
 * a buffer and three integers, which keeps the AAR free of a camera-stack dependency
 * while the Camera1 to CameraX question is still open, and makes the whole thing testable
 * without a device.
 *
 * <h3>Two things the caller owns</h3>
 *
 * <p><strong>The buffer must outlive the decode.</strong> Nothing is copied, so closing
 * the {@code ImageProxy} while a decode is still running leaves this reading a buffer the
 * camera has taken back. Close the image only after the decode has returned — or call
 * {@link #detach()} first, which pays one scan-rectangle-sized copy and frees the caller
 * to close immediately.
 *
 * <p><strong>Mirroring is not applied here.</strong> {@link PlanarYUVLuminanceSource}
 * offers a {@code reverseHorizontal} flag that rewrites its input array in place; doing
 * that to a camera-owned buffer would corrupt a frame the camera still owns, and on a
 * read-only buffer it would simply throw. Front-camera mirroring belongs in the
 * display-to-frame mapping, which is what {@link PreviewCoordinateMapper} is for.
 *
 * <p>Instances are immutable and read the buffer with absolute gets, so they never touch
 * its position or limit and can be read from a thread other than the one that created
 * them.
 */
public final class YPlaneLuminanceSource extends LuminanceSource {

  private final ByteBuffer plane;
  private final int rowStride;
  private final int pixelStride;
  private final int left;
  private final int top;

  /**
   * @param plane       the luma plane's buffer, read but never modified or repositioned
   * @param rowStride   bytes between the starts of two rows; may exceed the image width
   * @param pixelStride bytes between two horizontally adjacent luma samples, usually 1
   * @param left        scan rectangle, in image coordinates
   * @param top         scan rectangle, in image coordinates
   * @param width       scan rectangle width
   * @param height      scan rectangle height
   */
  public YPlaneLuminanceSource(ByteBuffer plane,
                               int rowStride,
                               int pixelStride,
                               int left,
                               int top,
                               int width,
                               int height) {
    super(width, height);
    if (plane == null) {
      throw new IllegalArgumentException("plane is null");
    }
    if (rowStride <= 0 || pixelStride <= 0) {
      throw new IllegalArgumentException(
          "strides must be positive: rowStride=" + rowStride + " pixelStride=" + pixelStride);
    }
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException(
          "the scan rectangle is empty: " + width + "x" + height);
    }
    if (left < 0 || top < 0) {
      throw new IllegalArgumentException(
          "the scan rectangle starts outside the image: " + left + "," + top);
    }
    // The last byte the rectangle reads has to exist. Checking it here turns a
    // misconfigured crop into an exception at construction rather than a
    // BufferUnderflowException from somewhere inside the binarizer.
    long lastIndex = (long) (top + height - 1) * rowStride
        + (long) (left + width - 1) * pixelStride;
    if (lastIndex >= plane.limit()) {
      throw new IllegalArgumentException(
          "the scan rectangle runs past the end of the plane: needs " + (lastIndex + 1)
              + " bytes, buffer holds " + plane.limit());
    }
    this.plane = plane;
    this.rowStride = rowStride;
    this.pixelStride = pixelStride;
    this.left = left;
    this.top = top;
  }

  @Override
  public byte[] getRow(int y, byte[] row) {
    if (y < 0 || y >= getHeight()) {
      throw new IllegalArgumentException("Requested row is outside the image: " + y);
    }
    int width = getWidth();
    if (row == null || row.length < width) {
      row = new byte[width];
    }
    readRow(y, row, 0);
    return row;
  }

  @Override
  public byte[] getMatrix() {
    int width = getWidth();
    int height = getHeight();
    // The scan rectangle, and only the scan rectangle. Returning the plane itself is not
    // an option even when the rectangle covers the whole image: a direct buffer has no
    // accessible array, and padded rows would not be contiguous anyway.
    byte[] matrix = new byte[width * height];
    for (int y = 0; y < height; y++) {
      readRow(y, matrix, y * width);
    }
    return matrix;
  }

  /** One row of the scan rectangle into {@code out}, starting at {@code outOffset}. */
  private void readRow(int y, byte[] out, int outOffset) {
    int width = getWidth();
    int rowStart = (top + y) * rowStride + left * pixelStride;
    if (pixelStride == 1) {
      // Contiguous run: one bulk read, which is what almost every device produces.
      if (plane.hasArray()) {
        System.arraycopy(plane.array(), plane.arrayOffset() + rowStart, out, outOffset, width);
      } else {
        // Absolute bulk get, so the buffer's own position is left alone. Duplicating is
        // cheap -- it copies the buffer's cursor, not its contents -- and keeps this
        // readable from a thread that did not create the source.
        ByteBuffer cursor = plane.duplicate();
        cursor.position(rowStart);
        cursor.get(out, outOffset, width);
      }
      return;
    }
    // Interleaved luma. Legal, rare, and silently wrong if assumed away.
    for (int x = 0; x < width; x++) {
      out[outOffset + x] = plane.get(rowStart + x * pixelStride);
    }
  }

  @Override
  public boolean isCropSupported() {
    return true;
  }

  @Override
  public LuminanceSource crop(int left, int top, int width, int height) {
    return new YPlaneLuminanceSource(plane, rowStride, pixelStride,
                                     this.left + left, this.top + top, width, height);
  }

  /**
   * The same pixels, copied out of the camera's buffer so the image can be closed.
   *
   * <p>Use this when the decode cannot be finished before the {@code ImageProxy} has to
   * be released. It costs one copy of the scan rectangle — still not a full frame — and
   * the result no longer refers to the camera's memory at all.
   */
  public PlanarYUVLuminanceSource detach() {
    int width = getWidth();
    int height = getHeight();
    return new PlanarYUVLuminanceSource(getMatrix(), width, height, 0, 0, width, height, false);
  }
}
