/*
 * Copyright (C) 2010 ZXing authors
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

import android.content.Context
import android.graphics.Point
import android.hardware.Camera
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import io.github.kakakakacat.zxing.android.camera.open.CameraFacing
import io.github.kakakakacat.zxing.android.camera.open.OpenCamera
import io.github.kakakakacat.zxing.android.scanner.ScannerOptions

/**
 * A class which deals with reading, parsing, and setting the camera parameters which are used to
 * configure the camera hardware.
 */
@Suppress("DEPRECATION") // camera APIs
internal class CameraConfigurationManager(
    private val context: Context,
    private val options: ScannerOptions
) {

  /** Clockwise rotation from display to camera, compensated for a front-facing sensor. */
  var cwNeededRotation = 0
    private set

  /** Clockwise rotation applied to frames for display, as passed to setDisplayOrientation. */
  var cwRotationFromDisplayToCamera = 0
    private set

  var screenResolution: Point? = null
    private set

  var cameraResolution: Point? = null
    private set

  var bestPreviewSize: Point? = null
    private set

  var previewSizeOnScreen: Point? = null
    private set

  /** Front-facing previews are mirrored on screen, which the crop mapping has to undo. */
  var isMirrored = false
    private set

  /**
   * Reads, one time, values from the camera that are needed by the app.
   */
  fun initFromCameraParameters(camera: OpenCamera) {
    val parameters = camera.camera.parameters
    val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val display = manager.defaultDisplay

    val displayRotation = display.rotation
    val cwRotationFromNaturalToDisplay: Int = when (displayRotation) {
      Surface.ROTATION_0 -> 0
      Surface.ROTATION_90 -> 90
      Surface.ROTATION_180 -> 180
      Surface.ROTATION_270 -> 270
      else ->
        // Have seen this return incorrect values like -90
        if (displayRotation % 90 == 0) {
          (360 + displayRotation) % 360
        } else {
          throw IllegalArgumentException("Bad rotation: $displayRotation")
        }
    }
    Log.i(TAG, "Display at: $cwRotationFromNaturalToDisplay")

    var cwRotationFromNaturalToCamera = camera.orientation
    Log.i(TAG, "Camera at: $cwRotationFromNaturalToCamera")

    // Still not 100% sure about this. But acts like we need to flip this:
    if (camera.facing == CameraFacing.FRONT) {
      cwRotationFromNaturalToCamera = (360 - cwRotationFromNaturalToCamera) % 360
      Log.i(TAG, "Front camera overriden to: $cwRotationFromNaturalToCamera")
    }

    isMirrored = camera.facing == CameraFacing.FRONT
    cwRotationFromDisplayToCamera =
        (360 + cwRotationFromNaturalToCamera - cwRotationFromNaturalToDisplay) % 360
    Log.i(TAG, "Final display orientation: $cwRotationFromDisplayToCamera")
    cwNeededRotation = if (camera.facing == CameraFacing.FRONT) {
      Log.i(TAG, "Compensating rotation for front camera")
      (360 - cwRotationFromDisplayToCamera) % 360
    } else {
      cwRotationFromDisplayToCamera
    }
    Log.i(TAG, "Clockwise rotation from display to camera: $cwNeededRotation")

    val theScreenResolution = Point()
    display.getSize(theScreenResolution)
    screenResolution = theScreenResolution
    Log.i(TAG, "Screen resolution in current orientation: $screenResolution")
    cameraResolution =
        CameraConfigurationUtils.findBestPreviewSizeValue(parameters, theScreenResolution)
    Log.i(TAG, "Camera resolution: $cameraResolution")
    val preview =
        CameraConfigurationUtils.findBestPreviewSizeValue(parameters, theScreenResolution)
    bestPreviewSize = preview
    Log.i(TAG, "Best available preview size: $preview")

    val isScreenPortrait = theScreenResolution.x < theScreenResolution.y
    val isPreviewSizePortrait = preview.x < preview.y

    previewSizeOnScreen = if (isScreenPortrait == isPreviewSizePortrait) {
      preview
    } else {
      Point(preview.y, preview.x)
    }
    Log.i(TAG, "Preview size on screen: $previewSizeOnScreen")
  }

  fun setDesiredCameraParameters(camera: OpenCamera, safeMode: Boolean) {

    val theCamera = camera.camera
    val parameters = theCamera.parameters

    if (parameters == null) {
      Log.w(TAG,
            "Device error: no camera parameters are available. Proceeding without configuration.")
      return
    }

    Log.i(TAG, "Initial camera parameters: " + parameters.flatten())

    if (safeMode) {
      Log.w(TAG, "In camera config safe mode -- most settings will not be honored")
    }

    initializeTorch(parameters, safeMode)

    CameraConfigurationUtils.setFocus(
        parameters,
        options.isAutoFocus,
        options.isContinuousFocusDisabled,
        safeMode)

    if (!safeMode) {
      if (options.isInvertScan) {
        CameraConfigurationUtils.setInvertColor(parameters)
      }

      if (!options.isBarcodeSceneModeDisabled) {
        CameraConfigurationUtils.setBarcodeSceneMode(parameters)
      }

      if (!options.isMeteringDisabled) {
        CameraConfigurationUtils.setVideoStabilization(parameters)
        CameraConfigurationUtils.setFocusArea(parameters)
        CameraConfigurationUtils.setMetering(parameters)
      }

      // SetRecordingHint to true also a workaround for low framerate on Nexus 4
      // https://stackoverflow.com/questions/14131900/extreme-camera-lag-on-nexus-4
      parameters.setRecordingHint(true)
    }

    val preview = bestPreviewSize!!
    parameters.setPreviewSize(preview.x, preview.y)

    theCamera.parameters = parameters

    theCamera.setDisplayOrientation(cwRotationFromDisplayToCamera)

    val afterParameters = theCamera.parameters
    val afterSize = afterParameters.previewSize
    if (afterSize != null && (preview.x != afterSize.width || preview.y != afterSize.height)) {
      Log.w(TAG, "Camera said it supported preview size " + preview.x + 'x' + preview.y +
          ", but after setting it, preview size is " + afterSize.width + 'x' + afterSize.height)
      preview.x = afterSize.width
      preview.y = afterSize.height
    }
  }

  fun getTorchState(camera: Camera?): Boolean {
    if (camera != null) {
      val parameters = camera.parameters
      if (parameters != null) {
        val flashMode = parameters.flashMode
        return Camera.Parameters.FLASH_MODE_ON == flashMode ||
            Camera.Parameters.FLASH_MODE_TORCH == flashMode
      }
    }
    return false
  }

  fun setTorch(camera: Camera, newSetting: Boolean) {
    val parameters = camera.parameters
    doSetTorch(parameters, newSetting, false)
    camera.parameters = parameters
  }

  private fun initializeTorch(parameters: Camera.Parameters, safeMode: Boolean) {
    doSetTorch(parameters, options.frontLightMode == FrontLightMode.ON, safeMode)
  }

  private fun doSetTorch(
      parameters: Camera.Parameters,
      newSetting: Boolean,
      safeMode: Boolean
  ) {
    CameraConfigurationUtils.setTorch(parameters, newSetting)
    if (!safeMode && !options.isExposureDisabled) {
      CameraConfigurationUtils.setBestExposure(parameters, newSetting)
    }
  }

  private companion object {
    private const val TAG = "CameraConfiguration"
  }
}
