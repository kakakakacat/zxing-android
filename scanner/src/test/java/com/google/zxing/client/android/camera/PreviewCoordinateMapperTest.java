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

package com.google.zxing.client.android.camera;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class PreviewCoordinateMapperTest {

  /**
   * The case upstream was locked to: landscape screen, landscape sensor, no rotation.
   * The new mapping must agree with the old independent-ratio scaling here, or this is a
   * regression rather than a fix.
   */
  @Test
  public void rotation0MatchesTheOldIndependentRatioScaling() {
    int screenW = 1920;
    int screenH = 1080;
    int camW = 1280;
    int camH = 720;
    int l = 460;
    int t = 202;
    int r = 1460;
    int b = 877;

    int[] box = PreviewCoordinateMapper.toFrameCoordinates(
        l, t, r, b, screenW, screenH, camW, camH, camW, camH, 0, false);

    assertArrayEquals(new int[] {
        Math.round(l * camW / (float) screenW),
        Math.round(t * camH / (float) screenH),
        Math.round(r * camW / (float) screenW),
        Math.round(b * camH / (float) screenH),
    }, box);
  }

  /**
   * The Android 16 large-screen case, reproduced on an API 36 emulator at
   * 1600x2560 @240dpi: the system ignores screenOrientation, the activity runs portrait,
   * and the camera still delivers a 1280x720 landscape frame rotated 90 degrees for
   * display.
   *
   * <p>The viewfinder is 1000x675 centred on screen. Under the old mapping it became
   * (240,265)-(1040,455) in frame coordinates: a wide, short band that is not the region
   * the user is aiming at. The mapping must instead produce a region whose aspect ratio
   * matches the on-screen rectangle once the rotation is undone.
   */
  @Test
  public void portraitScreenWithLandscapeSensorMapsThroughTheRotation() {
    int screenW = 1600;
    int screenH = 2560;
    int camW = 1280;
    int camH = 720;
    // Preview shown in display orientation: the frame rotated 90 degrees.
    int previewOnScreenW = camH;
    int previewOnScreenH = camW;

    int[] box = PreviewCoordinateMapper.toFrameCoordinates(
        300, 942, 1300, 1617,
        screenW, screenH, previewOnScreenW, previewOnScreenH, camW, camH, 90, false);

    // Screen rect 1000 wide x 675 tall scales to 450 x 337.5 on the displayed preview,
    // and a 90-degree rotation swaps the axes, so the frame crop is 338 x 450 once the
    // half pixel is rounded. Under the old mapping it was 800 x 190: a band of the frame
    // that has nothing to do with what the user is aiming at.
    assertEquals("crop width in frame coordinates", 338, box[2] - box[0]);
    assertEquals("crop height in frame coordinates", 450, box[3] - box[1]);
    // And it is inside the frame.
    assertEquals(true, box[0] >= 0 && box[2] <= camW && box[1] >= 0 && box[3] <= camH);
  }

  /** 270 degrees is the other portrait case, on devices whose sensor is mounted the other way. */
  @Test
  public void rotation270SwapsAxesTheOtherWay() {
    int[] box = PreviewCoordinateMapper.toFrameCoordinates(
        0, 0, 360, 640,
        720, 1280, 720, 1280, 1280, 720, 270, false);

    // Top-left quarter-width strip on screen maps to the bottom-left of the frame.
    assertArrayEquals(new int[] { 1280 - 640, 0, 1280 - 0, 360 }, box);
  }

  @Test
  public void rotation180FlipsBothAxes() {
    int[] box = PreviewCoordinateMapper.toFrameCoordinates(
        100, 50, 300, 150,
        400, 200, 400, 200, 400, 200, 180, false);

    assertArrayEquals(new int[] { 100, 50, 300, 150 }, box);
  }

  @Test
  public void rotation180IsNotTheIdentityForAnOffCentreRect() {
    int[] box = PreviewCoordinateMapper.toFrameCoordinates(
        0, 0, 100, 40,
        400, 200, 400, 200, 400, 200, 180, false);

    assertArrayEquals(new int[] { 300, 160, 400, 200 }, box);
  }

  /** A front camera's preview is mirrored on screen; the crop must be un-mirrored. */
  @Test
  public void frontCameraCropIsUnmirrored() {
    int[] box = PreviewCoordinateMapper.toFrameCoordinates(
        0, 0, 100, 40,
        400, 200, 400, 200, 400, 200, 0, true);

    assertArrayEquals(new int[] { 300, 0, 400, 40 }, box);
  }

  /** A rect that scales past the frame edge is clamped, not passed on to crash the source. */
  @Test
  public void cropIsClampedToTheFrame() {
    int[] box = PreviewCoordinateMapper.toFrameCoordinates(
        -50, -50, 5000, 5000,
        400, 200, 400, 200, 400, 200, 0, false);

    assertArrayEquals(new int[] { 0, 0, 400, 200 }, box);
  }

  @Test
  public void degenerateInputsReturnNullRatherThanAnEmptyCrop() {
    assertNull(PreviewCoordinateMapper.toFrameCoordinates(
        0, 0, 10, 10, 0, 0, 100, 100, 100, 100, 0, false));
    assertNull(PreviewCoordinateMapper.toFrameCoordinates(
        0, 0, 10, 10, 100, 100, 100, 100, 0, 0, 0, false));
    // Entirely off-frame: nothing left to decode.
    assertNull(PreviewCoordinateMapper.toFrameCoordinates(
        -100, -100, -10, -10, 400, 200, 400, 200, 400, 200, 0, false));
  }

  /** Drivers have been seen reporting rotations that are not multiples of 90. */
  @Test
  public void nonsenseRotationsFallBackToNone() {
    assertEquals(0, PreviewCoordinateMapper.normalizeRotation(-17));
    assertEquals(90, PreviewCoordinateMapper.normalizeRotation(-270));
    assertEquals(270, PreviewCoordinateMapper.normalizeRotation(630));
    assertEquals(0, PreviewCoordinateMapper.normalizeRotation(360));
  }
}
