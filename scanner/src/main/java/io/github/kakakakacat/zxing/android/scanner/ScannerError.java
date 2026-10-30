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

package io.github.kakakakacat.zxing.android.scanner;

/**
 * Why the scanner could not start. The upstream app collapsed every one of these into a
 * single "the Android camera encountered a problem, restart your device" dialog, which
 * told users to reboot when the camera was merely busy or not permitted.
 */
public enum ScannerError {

  /** The CAMERA permission is not granted. The caller is responsible for requesting it. */
  PERMISSION_DENIED,
  /** The device reports no camera the scanner can use. */
  NO_CAMERA,
  /** A camera exists but could not be opened — typically held by another app. */
  CAMERA_UNAVAILABLE,
  /** The camera opened but the preview could not be attached to the surface. */
  PREVIEW_FAILED
}
