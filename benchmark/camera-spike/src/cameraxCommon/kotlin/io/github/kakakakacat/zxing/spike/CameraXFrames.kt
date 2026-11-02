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

package io.github.kakakakacat.zxing.spike

import android.os.SystemClock
import android.util.Size
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import io.github.kakakakacat.zxing.android.camera.YPlaneLuminanceSource
import io.github.kakakakacat.zxing.android.scanner.FrameDecoder

/**
 * Everything the two CameraX flavors do identically.
 *
 * They differ only in how the preview reaches the screen — `camerax` uses `PreviewView` from
 * `camera-view`, `cameraxlean` hands CameraX a plain `SurfaceView` — so the analysis path,
 * the resolution request and the decode have to be literally the same code or the size
 * comparison between them would be measuring two programs instead of two dependency sets.
 */
object CameraXFrames {

  /** `am start ... --ez backCameraOnly true`. */
  const val EXTRA_BACK_CAMERA_ONLY = "backCameraOnly"

  // The size the Camera1 path negotiates on the reference device, asked for explicitly so
  // every flavor decodes the same number of pixels.
  private const val TARGET_WIDTH = 1920
  private const val TARGET_HEIGHT = 1080

  // FramingRectSizer's rule, restated. It is `internal` to :scanner and this module is
  // Kotlin, so it cannot be called from here at all -- the cross-module `internal` rule the
  // Kotlin conversion settled, rule 9 in the handoff. Copying three lines of arithmetic into
  // a spike beats widening the published AAR's API for a module that is never shipped.
  private const val MIN_FRAME = 240
  private const val MAX_ALONG_LONG_AXIS = 1200
  private const val MAX_ALONG_SHORT_AXIS = 675

  /** `configureInstance` may be called once per process and throws on the second call. */
  private var configured = false

  /**
   * Tells CameraX up front that only the back camera matters.
   *
   * Without this, CameraX's `CameraValidator` checks that both a front-facing and a
   * back-facing camera exist and, when one is missing, **retries initialisation for about
   * five seconds** before carrying on. A device with one camera is not exotic — the Android
   * 16 AVD used here is configured `hw.camera.front=none` — and a barcode scanner has no use
   * for the front camera anyway. Measured both ways; the decision record has the numbers.
   */
  @Synchronized
  fun configureBackCameraOnly() {
    if (configured) {
      return
    }
    configured = true
    try {
      ProcessCameraProvider.configureInstance(
          CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
              .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
              .build())
    } catch (ise: IllegalStateException) {
      // Already configured in this process. Only possible if the activity is recreated,
      // which the spike's one-shot runs do not do.
    }
  }

  fun buildAnalysis(): ImageAnalysis {
    // Asked for explicitly so the flavors decode comparable frames. Whatever the camera
    // actually gives back is printed in the result line, because a request is not a
    // guarantee and a timing against an unstated frame size says nothing.
    val resolution = ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(Size(TARGET_WIDTH, TARGET_HEIGHT),
                               ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
        .build()
    return ImageAnalysis.Builder()
        .setResolutionSelector(resolution)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .build()
  }

  /**
   * Reports the first frame and decodes it once.
   *
   * The image is closed only after the decode returns, which is the ownership rule
   * [YPlaneLuminanceSource] documents: nothing is copied, so releasing the buffer first would
   * leave the decoder reading memory the camera has taken back.
   *
   * @return true if this call was the first frame
   */
  fun onFirstFrame(
      image: ImageProxy,
      decoder: FrameDecoder?,
      events: (String, Long, String) -> Unit
  ) {
    image.use {
      val arrivedAt = SystemClock.uptimeMillis()
      val plane = image.planes[0]
      events(SpikeBackend.EVENT_FIRST_FRAME, arrivedAt,
             "frame=${image.width}x${image.height} rowStride=${plane.rowStride}" +
                 " pixelStride=${plane.pixelStride} ${SpikeDecoder.buildInfo()}")

      if (decoder == null) {
        return@use
      }
      val regionWidth = scanDimension(image.width, MAX_ALONG_LONG_AXIS)
      val regionHeight = scanDimension(image.height, MAX_ALONG_SHORT_AXIS)
      val startedAt = SystemClock.uptimeMillis()
      val source = YPlaneLuminanceSource(
          plane.buffer, plane.rowStride, plane.pixelStride,
          (image.width - regionWidth) / 2, (image.height - regionHeight) / 2,
          regionWidth, regionHeight)
      val result = decoder.decode(source)
      val finishedAt = SystemClock.uptimeMillis()
      events(SpikeBackend.EVENT_FIRST_DECODE, finishedAt,
             "decodeMs=${finishedAt - startedAt} region=${regionWidth}x${regionHeight}" +
                 " found=${result != null}")
    }
  }

  private fun scanDimension(resolution: Int, hardMax: Int): Int =
      minOf(maxOf(5 * resolution / 8, MIN_FRAME), hardMax)
}
