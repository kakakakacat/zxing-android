/*
 * Copyright (C) 2008 ZXing authors
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

package io.github.kakakakacat.zxing.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import io.github.kakakakacat.zxing.ResultPoint
import io.github.kakakakacat.zxing.android.camera.ScannerCamera
import io.github.kakakakacat.zxing.android.scanner.R

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and
 * partial transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
// Resources#getColor(int): the theme-aware overload is API 23+ and minSdk here is 21.
@Suppress("DEPRECATION")
class ViewfinderView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

  private var camera: ScannerCamera? = null

  // Initialize these once for performance rather than calling them every time in onDraw().
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val maskColor: Int = resources.getColor(R.color.zxing_viewfinder_mask)
  private val resultColor: Int = resources.getColor(R.color.zxing_viewfinder_result_view)
  private val laserColor: Int = resources.getColor(R.color.zxing_viewfinder_laser)
  private val resultPointColor: Int = resources.getColor(R.color.zxing_possible_result_points)

  private var resultBitmap: Bitmap? = null
  private var scannerAlpha = 0
  private var possibleResultPoints: MutableList<ResultPoint> = ArrayList(5)
  private var lastPossibleResultPoints: MutableList<ResultPoint>? = null

  constructor(context: Context) : this(context, null)

  /**
   * The open camera to read the scan rectangle from, or null to stop drawing one.
   *
   * Was `setCameraManager(CameraManager)` until Phase 6 put a second camera stack behind the
   * scanner; the overlay never needed anything from `CameraManager` that [ScannerCamera]
   * does not carry.
   */
  fun setCamera(camera: ScannerCamera?) {
    this.camera = camera
  }

  @SuppressLint("DrawAllocation")
  override fun onDraw(canvas: Canvas) {
    // not ready yet, early draw before done configuring
    val manager = camera ?: return
    val frame = manager.getFramingRect() ?: return
    val previewFrame = manager.getFramingRectInPreview() ?: return
    val width = canvas.width
    val height = canvas.height

    // Draw the exterior (i.e. outside the framing rect) darkened
    val bitmap = resultBitmap
    paint.color = if (bitmap != null) resultColor else maskColor
    canvas.drawRect(0f, 0f, width.toFloat(), frame.top.toFloat(), paint)
    canvas.drawRect(0f, frame.top.toFloat(), frame.left.toFloat(),
                    (frame.bottom + 1).toFloat(), paint)
    canvas.drawRect((frame.right + 1).toFloat(), frame.top.toFloat(), width.toFloat(),
                    (frame.bottom + 1).toFloat(), paint)
    canvas.drawRect(0f, (frame.bottom + 1).toFloat(), width.toFloat(), height.toFloat(), paint)

    if (bitmap != null) {
      // Draw the opaque result bitmap over the scanning rectangle
      paint.alpha = CURRENT_POINT_OPACITY
      canvas.drawBitmap(bitmap, null, frame, paint)
    } else {

      // Draw a red "laser scanner" line through the middle to show decoding is active
      paint.color = laserColor
      paint.alpha = SCANNER_ALPHA[scannerAlpha]
      scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.size
      val middle = frame.height() / 2 + frame.top
      canvas.drawRect((frame.left + 2).toFloat(), (middle - 1).toFloat(),
                      (frame.right - 1).toFloat(), (middle + 2).toFloat(), paint)

      val scaleX = frame.width() / previewFrame.width().toFloat()
      val scaleY = frame.height() / previewFrame.height().toFloat()

      val currentPossible = possibleResultPoints
      val currentLast = lastPossibleResultPoints
      val frameLeft = frame.left
      val frameTop = frame.top
      if (currentPossible.isEmpty()) {
        lastPossibleResultPoints = null
      } else {
        possibleResultPoints = ArrayList(5)
        lastPossibleResultPoints = currentPossible
        paint.alpha = CURRENT_POINT_OPACITY
        paint.color = resultPointColor
        synchronized(currentPossible) {
          for (point in currentPossible) {
            canvas.drawCircle((frameLeft + (point.x * scaleX).toInt()).toFloat(),
                              (frameTop + (point.y * scaleY).toInt()).toFloat(),
                              POINT_SIZE.toFloat(), paint)
          }
        }
      }
      if (currentLast != null) {
        paint.alpha = CURRENT_POINT_OPACITY / 2
        paint.color = resultPointColor
        synchronized(currentLast) {
          val radius = POINT_SIZE / 2.0f
          for (point in currentLast) {
            canvas.drawCircle((frameLeft + (point.x * scaleX).toInt()).toFloat(),
                              (frameTop + (point.y * scaleY).toInt()).toFloat(),
                              radius, paint)
          }
        }
      }

      // Request another update at the animation interval, but only repaint the laser line,
      // not the entire viewfinder mask.
      postInvalidateDelayed(ANIMATION_DELAY,
                            frame.left - POINT_SIZE,
                            frame.top - POINT_SIZE,
                            frame.right + POINT_SIZE,
                            frame.bottom + POINT_SIZE)
    }
  }

  fun drawViewfinder() {
    val bitmap = resultBitmap
    resultBitmap = null
    bitmap?.recycle()
    invalidate()
  }

  /**
   * Draw a bitmap with the result points highlighted instead of the live scanning display.
   *
   * @param barcode An image of the decoded barcode.
   */
  fun drawResultBitmap(barcode: Bitmap?) {
    resultBitmap = barcode
    invalidate()
  }

  fun addPossibleResultPoint(point: ResultPoint) {
    val points = possibleResultPoints
    synchronized(points) {
      points.add(point)
      val size = points.size
      if (size > MAX_RESULT_POINTS) {
        // trim it
        points.subList(0, size - MAX_RESULT_POINTS / 2).clear()
      }
    }
  }

  private companion object {
    private val SCANNER_ALPHA = intArrayOf(0, 64, 128, 192, 255, 192, 128, 64)
    private const val ANIMATION_DELAY = 80L
    private const val CURRENT_POINT_OPACITY = 0xA0
    private const val MAX_RESULT_POINTS = 20
    private const val POINT_SIZE = 6
  }
}
