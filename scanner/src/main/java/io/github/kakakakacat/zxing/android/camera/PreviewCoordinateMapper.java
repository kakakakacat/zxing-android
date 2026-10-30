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

/**
 * Maps the on-screen viewfinder rectangle into the camera's frame coordinates.
 *
 * <p>Upstream scaled the rectangle with independent x and y ratios straight from screen
 * size to camera resolution. That happens to be right in the one case the app was locked
 * to — a landscape screen with a landscape sensor, where the display-to-camera rotation
 * is 0 — and wrong in every other. Android 16 made the difference reachable: on a large
 * screen it ignores {@code android:screenOrientation}, so the activity runs portrait
 * against a landscape preview and the decoded region bears no relation to the rectangle
 * the user is aiming with.
 *
 * <p>Deliberately free of android.graphics types: the mapping is plain integer geometry,
 * so it can be unit tested on the host JVM rather than only on a device.
 */
public final class PreviewCoordinateMapper {

  private PreviewCoordinateMapper() {
  }

  /**
   * @param frameLeft/Top/Right/Bottom the viewfinder rectangle, in screen coordinates
   * @param screenWidth/Height   the screen size, in the current display orientation
   * @param previewOnScreenWidth/Height the preview size expressed in the display's
   *                             orientation (camera resolution with the axes swapped when
   *                             the display-to-camera rotation is 90 or 270)
   * @param cameraWidth/Height   the preview size in the camera's own frame coordinates
   * @param cwRotationFromDisplayToCamera clockwise rotation applied to frames for display;
   *                             0, 90, 180 or 270
   * @param mirrored             true for a front-facing camera, whose preview is mirrored
   * @return {@code {left, top, right, bottom}} to crop out of the frame buffer, clamped
   *         to the frame, or null if nothing usable remains
   */
  public static int[] toFrameCoordinates(int frameLeft, int frameTop,
                                         int frameRight, int frameBottom,
                                         int screenWidth, int screenHeight,
                                         int previewOnScreenWidth, int previewOnScreenHeight,
                                         int cameraWidth, int cameraHeight,
                                         int cwRotationFromDisplayToCamera,
                                         boolean mirrored) {
    if (screenWidth <= 0 || screenHeight <= 0 || cameraWidth <= 0 || cameraHeight <= 0) {
      return null;
    }

    // The SurfaceView is MATCH_PARENT and the preview is stretched over it, so screen
    // coordinates scale linearly onto the preview as displayed.
    float scaleX = previewOnScreenWidth / (float) screenWidth;
    float scaleY = previewOnScreenHeight / (float) screenHeight;
    int left = Math.round(frameLeft * scaleX);
    int top = Math.round(frameTop * scaleY);
    int right = Math.round(frameRight * scaleX);
    int bottom = Math.round(frameBottom * scaleY);

    if (mirrored) {
      // A front camera's preview is flipped horizontally in display space; undo that
      // before rotating back into frame space.
      int flippedLeft = previewOnScreenWidth - right;
      right = previewOnScreenWidth - left;
      left = flippedLeft;
    }

    int[] box = rotateIntoFrame(left, top, right, bottom, cameraWidth, cameraHeight,
                                normalizeRotation(cwRotationFromDisplayToCamera));

    // Clamp: rounding and a stretched preview can push a corner just outside the frame,
    // and PlanarYUVLuminanceSource rejects a crop that does not fit.
    box[0] = clamp(box[0], 0, cameraWidth);
    box[1] = clamp(box[1], 0, cameraHeight);
    box[2] = clamp(box[2], 0, cameraWidth);
    box[3] = clamp(box[3], 0, cameraHeight);
    if (box[2] - box[0] <= 0 || box[3] - box[1] <= 0) {
      return null;
    }
    return box;
  }

  /** Rotation the preview is displayed with, as a value in {0, 90, 180, 270}. */
  static int normalizeRotation(int degrees) {
    int r = ((degrees % 360) + 360) % 360;
    if (r % 90 != 0) {
      // Some drivers have been seen reporting nonsense; treat it as no rotation rather
      // than producing a silently wrong crop.
      return 0;
    }
    return r;
  }

  /**
   * Undoes the clockwise rotation that {@code Camera.setDisplayOrientation} applies, so a
   * rectangle measured on the displayed preview becomes a rectangle in the frame buffer.
   */
  private static int[] rotateIntoFrame(int left, int top, int right, int bottom,
                                       int frameWidth, int frameHeight, int rotation) {
    switch (rotation) {
      case 90:
        // A frame point (fx, fy) is drawn at (frameHeight - fy, fx).
        return new int[] { top, frameHeight - right, bottom, frameHeight - left };
      case 180:
        return new int[] { frameWidth - right, frameHeight - bottom,
                           frameWidth - left, frameHeight - top };
      case 270:
        // A frame point (fx, fy) is drawn at (fy, frameWidth - fx).
        return new int[] { frameWidth - bottom, left, frameWidth - top, right };
      default:
        return new int[] { left, top, right, bottom };
    }
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
