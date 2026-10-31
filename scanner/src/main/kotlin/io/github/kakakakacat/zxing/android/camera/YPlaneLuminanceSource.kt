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

package io.github.kakakakacat.zxing.android.camera

import io.github.kakakakacat.zxing.LuminanceSource
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource
import java.nio.ByteBuffer

/**
 * The luma plane of a `YUV_420_888` image, read where it already lies.
 *
 * Built for CameraX's `ImageProxy`: pass `getPlanes()[0]`'s buffer, `getRowStride()` and
 * `getPixelStride()` together with the scan rectangle. Nothing is copied to construct one,
 * and the only copy that ever happens is the scan rectangle itself, in [getMatrix]. A frame
 * is typically far larger than the region being decoded, so the usual "drain the buffer into
 * a `byte[]` and wrap it in [PlanarYUVLuminanceSource]" costs a full-frame copy per preview
 * frame to produce exactly the same result.
 *
 * This class deliberately does not depend on CameraX, or on any camera API. It needs a buffer
 * and three integers, which keeps the AAR free of a camera-stack dependency while the Camera1
 * to CameraX question is still open, and makes the whole thing testable without a device.
 *
 * ### Two things the caller owns
 *
 * **The buffer must outlive the decode.** Nothing is copied, so closing the `ImageProxy`
 * while a decode is still running leaves this reading a buffer the camera has taken back.
 * Close the image only after the decode has returned — or call [detach] first, which pays one
 * scan-rectangle-sized copy and frees the caller to close immediately.
 *
 * **Mirroring is not applied here.** [PlanarYUVLuminanceSource] offers a `reverseHorizontal`
 * flag that rewrites its input array in place; doing that to a camera-owned buffer would
 * corrupt a frame the camera still owns, and on a read-only buffer it would simply throw.
 * Front-camera mirroring belongs in the display-to-frame mapping, which is what
 * [PreviewCoordinateMapper] is for.
 *
 * Instances are immutable and read the buffer with absolute gets, so they never touch its
 * position or limit and can be read from a thread other than the one that created them.
 *
 * @param plane       the luma plane's buffer, read but never modified or repositioned
 * @param rowStride   bytes between the starts of two rows; may exceed the image width
 * @param pixelStride bytes between two horizontally adjacent luma samples, usually 1
 * @param left        scan rectangle, in image coordinates
 * @param top         scan rectangle, in image coordinates
 * @param width       scan rectangle width
 * @param height      scan rectangle height
 */
class YPlaneLuminanceSource(
    private val plane: ByteBuffer,
    private val rowStride: Int,
    private val pixelStride: Int,
    private val left: Int,
    private val top: Int,
    width: Int,
    height: Int
) : LuminanceSource(width, height) {

  init {
    // `plane` is non-null in Kotlin, but the Java constructor threw IllegalArgumentException
    // for a null one and a Java caller can still pass null. Kotlin's own check throws NPE, so
    // the explicit one stays and runs first for anyone who was catching it.
    @Suppress("SENSELESS_COMPARISON")
    if (plane == null) {
      throw IllegalArgumentException("plane is null")
    }
    if (rowStride <= 0 || pixelStride <= 0) {
      throw IllegalArgumentException(
          "strides must be positive: rowStride=$rowStride pixelStride=$pixelStride")
    }
    if (width <= 0 || height <= 0) {
      throw IllegalArgumentException("the scan rectangle is empty: ${width}x$height")
    }
    if (left < 0 || top < 0) {
      throw IllegalArgumentException("the scan rectangle starts outside the image: $left,$top")
    }
    // The last byte the rectangle reads has to exist. Checking it here turns a
    // misconfigured crop into an exception at construction rather than a
    // BufferUnderflowException from somewhere inside the binarizer.
    val lastIndex = (top + height - 1).toLong() * rowStride +
        (left + width - 1).toLong() * pixelStride
    if (lastIndex >= plane.limit()) {
      throw IllegalArgumentException(
          "the scan rectangle runs past the end of the plane: needs " + (lastIndex + 1) +
              " bytes, buffer holds " + plane.limit())
    }
  }

  override fun getRow(y: Int, row: ByteArray?): ByteArray {
    if (y < 0 || y >= height) {
      throw IllegalArgumentException("Requested row is outside the image: $y")
    }
    val width = width
    var out = row
    if (out == null || out.size < width) {
      out = ByteArray(width)
    }
    readRow(y, out, 0)
    return out
  }

  override fun getMatrix(): ByteArray {
    val width = width
    val height = height
    // The scan rectangle, and only the scan rectangle. Returning the plane itself is not
    // an option even when the rectangle covers the whole image: a direct buffer has no
    // accessible array, and padded rows would not be contiguous anyway.
    val matrix = ByteArray(width * height)
    for (y in 0 until height) {
      readRow(y, matrix, y * width)
    }
    return matrix
  }

  /** One row of the scan rectangle into [out], starting at [outOffset]. */
  private fun readRow(y: Int, out: ByteArray, outOffset: Int) {
    val width = width
    val rowStart = (top + y) * rowStride + left * pixelStride
    if (pixelStride == 1) {
      // Contiguous run: one bulk read, which is what almost every device produces.
      if (plane.hasArray()) {
        System.arraycopy(plane.array(), plane.arrayOffset() + rowStart, out, outOffset, width)
      } else {
        // Absolute bulk get, so the buffer's own position is left alone. Duplicating is
        // cheap -- it copies the buffer's cursor, not its contents -- and keeps this
        // readable from a thread that did not create the source.
        val cursor = plane.duplicate()
        cursor.position(rowStart)
        cursor.get(out, outOffset, width)
      }
      return
    }
    // Interleaved luma. Legal, rare, and silently wrong if assumed away.
    for (x in 0 until width) {
      out[outOffset + x] = plane.get(rowStart + x * pixelStride)
    }
  }

  override fun isCropSupported(): Boolean = true

  override fun crop(left: Int, top: Int, width: Int, height: Int): LuminanceSource =
      YPlaneLuminanceSource(plane, rowStride, pixelStride,
                            this.left + left, this.top + top, width, height)

  /**
   * The same pixels, copied out of the camera's buffer so the image can be closed.
   *
   * Use this when the decode cannot be finished before the `ImageProxy` has to be released.
   * It costs one copy of the scan rectangle — still not a full frame — and the result no
   * longer refers to the camera's memory at all.
   */
  fun detach(): PlanarYUVLuminanceSource {
    val width = width
    val height = height
    return PlanarYUVLuminanceSource(matrix, width, height, 0, 0, width, height, false)
  }
}
