/*
 * Copyright (C) 2014 ZXing authors
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

import android.graphics.Point
import android.graphics.Rect
import android.hardware.Camera
import android.os.Build
import android.util.Log
import java.util.Arrays
import java.util.regex.Pattern

/**
 * Utility methods for configuring the Android camera.
 *
 * @author Sean Owen
 */
@Suppress("DEPRECATION") // camera APIs
object CameraConfigurationUtils {

  private const val TAG = "CameraConfiguration"

  private val SEMICOLON: Pattern = Pattern.compile(";")

  private const val MIN_PREVIEW_PIXELS = 480 * 320 // normal screen
  private const val MAX_EXPOSURE_COMPENSATION = 1.5f
  private const val MIN_EXPOSURE_COMPENSATION = 0.0f
  private const val MAX_ASPECT_DISTORTION = 0.15
  private const val MIN_FPS = 10
  private const val MAX_FPS = 20
  private const val AREA_PER_1000 = 400

  @JvmStatic
  fun setFocus(
      parameters: Camera.Parameters,
      autoFocus: Boolean,
      disableContinuous: Boolean,
      safeMode: Boolean
  ) {
    val supportedFocusModes = parameters.supportedFocusModes
    var focusMode: String? = null
    if (autoFocus) {
      focusMode = if (safeMode || disableContinuous) {
        findSettableValue("focus mode", supportedFocusModes, Camera.Parameters.FOCUS_MODE_AUTO)
      } else {
        findSettableValue("focus mode",
                          supportedFocusModes,
                          Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
                          Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
                          Camera.Parameters.FOCUS_MODE_AUTO)
      }
    }
    // Maybe selected auto-focus but not available, so fall through here:
    if (!safeMode && focusMode == null) {
      focusMode = findSettableValue("focus mode",
                                    supportedFocusModes,
                                    Camera.Parameters.FOCUS_MODE_MACRO,
                                    Camera.Parameters.FOCUS_MODE_EDOF)
    }
    if (focusMode != null) {
      if (focusMode == parameters.focusMode) {
        Log.i(TAG, "Focus mode already set to $focusMode")
      } else {
        parameters.focusMode = focusMode
      }
    }
  }

  @JvmStatic
  fun setTorch(parameters: Camera.Parameters, on: Boolean) {
    val supportedFlashModes = parameters.supportedFlashModes
    val flashMode = if (on) {
      findSettableValue("flash mode",
                        supportedFlashModes,
                        Camera.Parameters.FLASH_MODE_TORCH,
                        Camera.Parameters.FLASH_MODE_ON)
    } else {
      findSettableValue("flash mode", supportedFlashModes, Camera.Parameters.FLASH_MODE_OFF)
    }
    if (flashMode != null) {
      if (flashMode == parameters.flashMode) {
        Log.i(TAG, "Flash mode already set to $flashMode")
      } else {
        Log.i(TAG, "Setting flash mode to $flashMode")
        parameters.flashMode = flashMode
      }
    }
  }

  @JvmStatic
  fun setBestExposure(parameters: Camera.Parameters, lightOn: Boolean) {
    val minExposure = parameters.minExposureCompensation
    val maxExposure = parameters.maxExposureCompensation
    val step = parameters.exposureCompensationStep
    if ((minExposure != 0 || maxExposure != 0) && step > 0.0f) {
      // Set low when light is on
      val targetCompensation =
          if (lightOn) MIN_EXPOSURE_COMPENSATION else MAX_EXPOSURE_COMPENSATION
      var compensationSteps = Math.round(targetCompensation / step)
      val actualCompensation = step * compensationSteps
      // Clamp value:
      compensationSteps = Math.max(Math.min(compensationSteps, maxExposure), minExposure)
      if (parameters.exposureCompensation == compensationSteps) {
        Log.i(TAG, "Exposure compensation already set to $compensationSteps / $actualCompensation")
      } else {
        Log.i(TAG, "Setting exposure compensation to $compensationSteps / $actualCompensation")
        parameters.exposureCompensation = compensationSteps
      }
    } else {
      Log.i(TAG, "Camera does not support exposure compensation")
    }
  }

  @JvmStatic
  fun setBestPreviewFPS(parameters: Camera.Parameters) {
    setBestPreviewFPS(parameters, MIN_FPS, MAX_FPS)
  }

  @JvmStatic
  fun setBestPreviewFPS(parameters: Camera.Parameters, minFPS: Int, maxFPS: Int) {
    val supportedPreviewFpsRanges = parameters.supportedPreviewFpsRange
    Log.i(TAG, "Supported FPS ranges: " + fpsRangesToString(supportedPreviewFpsRanges))
    if (supportedPreviewFpsRanges != null && !supportedPreviewFpsRanges.isEmpty()) {
      var suitableFPSRange: IntArray? = null
      for (fpsRange in supportedPreviewFpsRanges) {
        val thisMin = fpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
        val thisMax = fpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
        if (thisMin >= minFPS * 1000 && thisMax <= maxFPS * 1000) {
          suitableFPSRange = fpsRange
          break
        }
      }
      if (suitableFPSRange == null) {
        Log.i(TAG, "No suitable FPS range?")
      } else {
        val currentFpsRange = IntArray(2)
        parameters.getPreviewFpsRange(currentFpsRange)
        if (Arrays.equals(currentFpsRange, suitableFPSRange)) {
          Log.i(TAG, "FPS range already set to " + Arrays.toString(suitableFPSRange))
        } else {
          Log.i(TAG, "Setting FPS range to " + Arrays.toString(suitableFPSRange))
          parameters.setPreviewFpsRange(suitableFPSRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                                        suitableFPSRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX])
        }
      }
    }
  }

  @JvmStatic
  fun setFocusArea(parameters: Camera.Parameters) {
    if (parameters.maxNumFocusAreas > 0) {
      Log.i(TAG, "Old focus areas: " + areasToString(parameters.focusAreas))
      val middleArea = buildMiddleArea()
      Log.i(TAG, "Setting focus area to : " + areasToString(middleArea))
      parameters.focusAreas = middleArea
    } else {
      Log.i(TAG, "Device does not support focus areas")
    }
  }

  @JvmStatic
  fun setMetering(parameters: Camera.Parameters) {
    if (parameters.maxNumMeteringAreas > 0) {
      Log.i(TAG, "Old metering areas: " + parameters.meteringAreas)
      val middleArea = buildMiddleArea()
      Log.i(TAG, "Setting metering area to : " + areasToString(middleArea))
      parameters.meteringAreas = middleArea
    } else {
      Log.i(TAG, "Device does not support metering areas")
    }
  }

  private fun buildMiddleArea(): List<Camera.Area> = listOf(
      Camera.Area(Rect(-AREA_PER_1000, -AREA_PER_1000, AREA_PER_1000, AREA_PER_1000), 1))

  @JvmStatic
  fun setVideoStabilization(parameters: Camera.Parameters) {
    if (parameters.isVideoStabilizationSupported) {
      if (parameters.videoStabilization) {
        Log.i(TAG, "Video stabilization already enabled")
      } else {
        Log.i(TAG, "Enabling video stabilization...")
        parameters.videoStabilization = true
      }
    } else {
      Log.i(TAG, "This device does not support video stabilization")
    }
  }

  @JvmStatic
  fun setBarcodeSceneMode(parameters: Camera.Parameters) {
    if (Camera.Parameters.SCENE_MODE_BARCODE == parameters.sceneMode) {
      Log.i(TAG, "Barcode scene mode already set")
      return
    }
    val sceneMode = findSettableValue("scene mode",
                                      parameters.supportedSceneModes,
                                      Camera.Parameters.SCENE_MODE_BARCODE)
    if (sceneMode != null) {
      parameters.sceneMode = sceneMode
    }
  }

  @JvmStatic
  fun setZoom(parameters: Camera.Parameters, targetZoomRatio: Double) {
    if (parameters.isZoomSupported) {
      val zoom = indexOfClosestZoom(parameters, targetZoomRatio) ?: return
      if (parameters.zoom == zoom) {
        Log.i(TAG, "Zoom is already set to $zoom")
      } else {
        Log.i(TAG, "Setting zoom to $zoom")
        parameters.zoom = zoom
      }
    } else {
      Log.i(TAG, "Zoom is not supported")
    }
  }

  private fun indexOfClosestZoom(parameters: Camera.Parameters, targetZoomRatio: Double): Int? {
    val ratios: List<Int>? = parameters.zoomRatios
    Log.i(TAG, "Zoom ratios: $ratios")
    val maxZoom = parameters.maxZoom
    if (ratios == null || ratios.isEmpty() || ratios.size != maxZoom + 1) {
      Log.w(TAG, "Invalid zoom ratios!")
      return null
    }
    val target100 = 100.0 * targetZoomRatio
    var smallestDiff = Double.POSITIVE_INFINITY
    var closestIndex = 0
    for (i in ratios.indices) {
      val diff = Math.abs(ratios[i] - target100)
      if (diff < smallestDiff) {
        smallestDiff = diff
        closestIndex = i
      }
    }
    Log.i(TAG, "Chose zoom ratio of " + (ratios[closestIndex] / 100.0))
    return closestIndex
  }

  @JvmStatic
  fun setInvertColor(parameters: Camera.Parameters) {
    if (Camera.Parameters.EFFECT_NEGATIVE == parameters.colorEffect) {
      Log.i(TAG, "Negative effect already set")
      return
    }
    val colorMode = findSettableValue("color effect",
                                      parameters.supportedColorEffects,
                                      Camera.Parameters.EFFECT_NEGATIVE)
    if (colorMode != null) {
      parameters.colorEffect = colorMode
    }
  }

  @JvmStatic
  fun findBestPreviewSizeValue(parameters: Camera.Parameters, screenResolution: Point): Point {

    val rawSupportedSizes: List<Camera.Size>? = parameters.supportedPreviewSizes
    if (rawSupportedSizes == null) {
      Log.w(TAG, "Device returned no supported preview sizes; using default")
      val defaultSize = parameters.previewSize
          ?: throw IllegalStateException("Parameters contained no preview size!")
      return Point(defaultSize.width, defaultSize.height)
    }

    if (Log.isLoggable(TAG, Log.INFO)) {
      val previewSizesString = StringBuilder()
      for (size in rawSupportedSizes) {
        previewSizesString.append(size.width).append('x').append(size.height).append(' ')
      }
      Log.i(TAG, "Supported preview sizes: $previewSizesString")
    }

    val screenAspectRatio = screenResolution.x / screenResolution.y.toDouble()

    // Find a suitable size, with max resolution
    var maxResolution = 0
    var maxResPreviewSize: Camera.Size? = null
    for (size in rawSupportedSizes) {
      val realWidth = size.width
      val realHeight = size.height
      val resolution = realWidth * realHeight
      if (resolution < MIN_PREVIEW_PIXELS) {
        continue
      }

      val isCandidatePortrait = realWidth < realHeight
      val maybeFlippedWidth = if (isCandidatePortrait) realHeight else realWidth
      val maybeFlippedHeight = if (isCandidatePortrait) realWidth else realHeight
      val aspectRatio = maybeFlippedWidth / maybeFlippedHeight.toDouble()
      val distortion = Math.abs(aspectRatio - screenAspectRatio)
      if (distortion > MAX_ASPECT_DISTORTION) {
        continue
      }

      if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
        val exactPoint = Point(realWidth, realHeight)
        Log.i(TAG, "Found preview size exactly matching screen size: $exactPoint")
        return exactPoint
      }

      // Resolution is suitable; record the one with max resolution
      if (resolution > maxResolution) {
        maxResolution = resolution
        maxResPreviewSize = size
      }
    }

    // If no exact match, use largest preview size. This was not a great idea on older devices
    // because of the additional computation needed. We're likely to get here on newer Android
    // 4+ devices, where the CPU is much more powerful.
    if (maxResPreviewSize != null) {
      val largestSize = Point(maxResPreviewSize.width, maxResPreviewSize.height)
      Log.i(TAG, "Using largest suitable preview size: $largestSize")
      return largestSize
    }

    // If there is nothing at all suitable, return current preview size
    val defaultPreview = parameters.previewSize
        ?: throw IllegalStateException("Parameters contained no preview size!")
    val defaultSize = Point(defaultPreview.width, defaultPreview.height)
    Log.i(TAG, "No suitable preview sizes, using default: $defaultSize")
    return defaultSize
  }

  private fun findSettableValue(
      name: String,
      supportedValues: Collection<String>?,
      vararg desiredValues: String
  ): String? {
    Log.i(TAG, "Requesting $name value from among: " + Arrays.toString(desiredValues))
    Log.i(TAG, "Supported $name values: $supportedValues")
    if (supportedValues != null) {
      for (desiredValue in desiredValues) {
        if (supportedValues.contains(desiredValue)) {
          Log.i(TAG, "Can set $name to: $desiredValue")
          return desiredValue
        }
      }
    }
    Log.i(TAG, "No supported values match")
    return null
  }

  // Named rather than overloaded toString(): on a Kotlin object those two private overloads
  // would be competing with Any.toString().
  private fun fpsRangesToString(arrays: Collection<IntArray>?): String {
    if (arrays == null || arrays.isEmpty()) {
      return "[]"
    }
    return arrays.joinToString(", ", "[", "]") { Arrays.toString(it) }
  }

  private fun areasToString(areas: Iterable<Camera.Area>?): String? {
    if (areas == null) {
      return null
    }
    val result = StringBuilder()
    for (area in areas) {
      result.append(area.rect).append(':').append(area.weight).append(' ')
    }
    return result.toString()
  }

  @JvmStatic
  fun collectStats(parameters: Camera.Parameters): String = collectStats(parameters.flatten())

  @JvmStatic
  fun collectStats(flattenedParams: CharSequence?): String {
    val result = StringBuilder(1000)
    appendStat(result, "BOARD", Build.BOARD)
    appendStat(result, "BRAND", Build.BRAND)
    appendStat(result, "CPU_ABI", Build.CPU_ABI)
    appendStat(result, "DEVICE", Build.DEVICE)
    appendStat(result, "DISPLAY", Build.DISPLAY)
    appendStat(result, "FINGERPRINT", Build.FINGERPRINT)
    appendStat(result, "HOST", Build.HOST)
    appendStat(result, "ID", Build.ID)
    appendStat(result, "MANUFACTURER", Build.MANUFACTURER)
    appendStat(result, "MODEL", Build.MODEL)
    appendStat(result, "PRODUCT", Build.PRODUCT)
    appendStat(result, "TAGS", Build.TAGS)
    appendStat(result, "TIME", Build.TIME)
    appendStat(result, "TYPE", Build.TYPE)
    appendStat(result, "USER", Build.USER)
    appendStat(result, "VERSION.CODENAME", Build.VERSION.CODENAME)
    appendStat(result, "VERSION.INCREMENTAL", Build.VERSION.INCREMENTAL)
    appendStat(result, "VERSION.RELEASE", Build.VERSION.RELEASE)
    appendStat(result, "VERSION.SDK_INT", Build.VERSION.SDK_INT)

    if (flattenedParams != null) {
      val params = SEMICOLON.split(flattenedParams)
      Arrays.sort(params)
      for (param in params) {
        result.append(param).append('\n')
      }
    }

    return result.toString()
  }

  private fun appendStat(builder: StringBuilder, stat: String, value: Any?) {
    builder.append(stat).append('=').append(value).append('\n')
  }
}
