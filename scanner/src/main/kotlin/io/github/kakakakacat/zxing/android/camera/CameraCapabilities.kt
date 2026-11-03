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

/**
 * Which camera settings the running backend actually applies.
 *
 * [io.github.kakakakacat.zxing.android.scanner.ScannerOptions] is one set of options for two
 * camera stacks, and the two do not honour the same ones. The Camera1 path drives the
 * `Camera.Parameters` those options were written for; the CameraX path leaves focus,
 * metering, exposure and scene mode to CameraX's own 3A and binds the back camera whatever
 * `cameraId` says. Until this existed, a host that set one of those on the CameraX backend
 * got silence: the call succeeded, the setting did nothing, and nothing said so.
 *
 * So a setting is either honoured or it is reported here as not honoured — and the backend
 * logs, once per session, every option the caller set that it is going to ignore. Read it
 * through [ScannerCamera.capabilities] once the camera is open.
 *
 * "Honoured" is about the backend, not the device. A phone with no flash unit honours the
 * torch option and has nowhere to apply it; that is [torchAvailable], which is separate.
 */
class CameraCapabilities(

    /** Which stack this describes: `camera1` or `camerax`. */
    val backendName: String,

    /** [io.github.kakakakacat.zxing.android.scanner.ScannerOptions.cameraId]. */
    val cameraIdHonoured: Boolean,

    /**
     * `isAutoFocus` and `isContinuousFocusDisabled`.
     *
     * The CameraX path reports false: focus is CameraX's, through its default 3A, and
     * [AutoFocusManager] is not on that path at all.
     */
    val autoFocusHonoured: Boolean,

    /** `isMeteringDisabled`. */
    val meteringHonoured: Boolean,

    /** `isExposureDisabled`. */
    val exposureHonoured: Boolean,

    /** `isBarcodeSceneModeDisabled`. */
    val barcodeSceneModeHonoured: Boolean,

    /** `isInvertScan`. */
    val invertScanHonoured: Boolean,

    /** `framingRectWidth` / `framingRectHeight`, and the scan rectangle generally. */
    val framingRectHonoured: Boolean,

    /** `frontLightMode`, and [ScannerCamera.setTorch]. */
    val torchHonoured: Boolean,

    /** Whether this camera has a flash unit at all. False means [setTorch] can do nothing. */
    val torchAvailable: Boolean
) {

  /** The names of the options this backend will ignore, for a log line or a test. */
  fun unhonouredOptions(): List<String> {
    val out = ArrayList<String>(6)
    if (!cameraIdHonoured) out.add("cameraId")
    if (!autoFocusHonoured) out.add("autoFocus")
    if (!meteringHonoured) out.add("metering")
    if (!exposureHonoured) out.add("exposure")
    if (!barcodeSceneModeHonoured) out.add("barcodeSceneMode")
    if (!invertScanHonoured) out.add("invertScan")
    if (!framingRectHonoured) out.add("framingRect")
    if (!torchHonoured) out.add("frontLightMode")
    return out
  }

  override fun toString(): String =
      "$backendName: ignores ${unhonouredOptions()}, torchAvailable=$torchAvailable"
}
