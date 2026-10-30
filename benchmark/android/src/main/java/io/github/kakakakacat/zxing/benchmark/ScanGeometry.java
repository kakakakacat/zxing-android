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

import io.github.kakakakacat.zxing.android.camera.PreviewCoordinateMapper;

/**
 * The fixed viewing scenario every L2 frame is built and cropped against.
 *
 * <p>A benchmark needs one geometry, pinned, or a crop that changed under it looks like a
 * decoder that changed. These numbers are therefore constants, not device queries.
 *
 * <p>The scenario is the Android 16 large-screen portrait case: a portrait display with a
 * landscape camera frame behind it, so the display-to-camera rotation is 90 and the crop
 * goes through {@link PreviewCoordinateMapper}'s rotation path rather than the identity
 * path that only ever worked by accident. That is also the case {@code A-13} fixed, so a
 * regression in it shows up here instead of on a user's large screen.
 *
 * <p>The viewfinder is a tall band in screen space, which rotates into a wide band in
 * frame space — the shape the scanner actually crops, and the only shape a 1D barcode can
 * be measured in.
 */
public final class ScanGeometry {

  /** Portrait display, in the current display orientation. */
  public static final int SCREEN_WIDTH = 1080;
  public static final int SCREEN_HEIGHT = 2400;

  /** The viewfinder rectangle in screen coordinates. */
  public static final int VIEWFINDER_LEFT = 340;
  public static final int VIEWFINDER_TOP = 400;
  public static final int VIEWFINDER_RIGHT = 740;
  public static final int VIEWFINDER_BOTTOM = 2000;

  /** Clockwise rotation applied to camera frames before they are displayed. */
  public static final int CW_ROTATION_DISPLAY_TO_CAMERA = 90;

  /** Back camera: the preview is not mirrored. */
  public static final boolean MIRRORED = false;

  private ScanGeometry() {
  }

  /**
   * The crop rectangle for a camera frame of this size, as {@code {left, top, right,
   * bottom}} in frame coordinates.
   *
   * <p>This is stage 1 of the pipeline, and it is exactly the call the scanner makes.
   *
   * @throws IllegalStateException if the geometry leaves nothing to crop, which would mean
   *         the constants above are wrong rather than that the device is unusual
   */
  public static int[] crop(int cameraWidth, int cameraHeight) {
    // The display is portrait and the frame is landscape, so the preview as displayed has
    // the camera's axes swapped.
    int previewOnScreenWidth = cameraHeight;
    int previewOnScreenHeight = cameraWidth;
    int[] box = PreviewCoordinateMapper.toFrameCoordinates(
        VIEWFINDER_LEFT, VIEWFINDER_TOP, VIEWFINDER_RIGHT, VIEWFINDER_BOTTOM,
        SCREEN_WIDTH, SCREEN_HEIGHT,
        previewOnScreenWidth, previewOnScreenHeight,
        cameraWidth, cameraHeight,
        CW_ROTATION_DISPLAY_TO_CAMERA, MIRRORED);
    if (box == null) {
      throw new IllegalStateException(
          "ScanGeometry produced no crop for " + cameraWidth + 'x' + cameraHeight);
    }
    return box;
  }
}
