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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Rect;
import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.DecodeHintType;
import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.android.Intents;
import io.github.kakakakacat.zxing.android.ViewfinderView;
import io.github.kakakakacat.zxing.android.camera.CameraManager;
import io.github.kakakakacat.zxing.android.camera.FrontLightMode;
import io.github.kakakakacat.zxing.android.camera.PreviewCoordinateMapper;
import io.github.kakakakacat.zxing.android.camera.YPlaneLuminanceSource;
import io.github.kakakakacat.zxing.android.camera.open.CameraFacing;
import io.github.kakakakacat.zxing.android.camera.open.OpenCameraInterface;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.junit.Test;

/**
 * This module is Kotlin and it is published as an AAR; most consumers are Java.
 *
 * <p>Until `:app` converts, that module was the live check: it is Java, it compiles against
 * this API, and anything that broke a Java caller broke the build. This test takes that job
 * over, and is written in Java for the same reason. Kotlin can change the Java-facing shape of
 * an API without changing a line of Kotlin that uses it: declaration-site variance turns
 * {@code Collection<BarcodeFormat>} into {@code Collection<? extends BarcodeFormat>}, a
 * {@code val} that is not {@code const} stops being usable in a {@code case} label, and a
 * method that is not {@code open} can no longer be overridden.
 *
 * <p>Most of this is compiled rather than run: {@link #surfaceCompiles} is never invoked and
 * exists so javac has to accept every public member the way a consumer would write it. These
 * are host JVM tests against the Android stub jar, so anything touching a real framework
 * object would throw; the assertions that do run cover values and pure geometry.
 */
public final class JavaSourceCompatibilityTest {

  @Test
  public void decoderBackendNamesAreCompileTimeConstants() {
    // A `case` label only accepts a constant expression, so this fails to compile if any of
    // these stops being `const val` in Kotlin.
    String name = FrameDecoders.NATIVE;
    int matched;
    switch (name) {
      case FrameDecoders.JAVA:
        matched = 1;
        break;
      case FrameDecoders.NATIVE:
        matched = 2;
        break;
      case FrameDecoders.JAVA_ALIAS:
      case FrameDecoders.NATIVE_ALIAS:
        matched = 3;
        break;
      default:
        matched = 0;
    }
    assertEquals(2, matched);
    assertEquals("zxing-java", FrameDecoders.JAVA);
    assertEquals("zxing-cpp", FrameDecoders.NATIVE);
  }

  @Test
  public void intentExtraKeysAreCompileTimeConstants() {
    int matched;
    switch (Intents.Scan.FORMATS) {
      case Intents.Scan.FORMATS:
        matched = 1;
        break;
      case Intents.Scan.MODE:
        matched = 2;
        break;
      default:
        matched = 0;
    }
    assertEquals(1, matched);
    assertEquals("com.google.zxing.client.android.SCAN", Intents.Scan.ACTION);
    assertEquals("com.google.zxing.client.android.ENCODE", Intents.Encode.ACTION);
    assertEquals("SCAN_RESULT", Intents.Scan.RESULT);
    assertEquals(-1, OpenCameraInterface.NO_REQUESTED_CAMERA);
  }

  @Test
  public void backendNamesResolveThroughTheirAliases() {
    assertEquals(FrameDecoders.JAVA, FrameDecoders.canonical("java"));
    assertEquals(FrameDecoders.NATIVE, FrameDecoders.canonical("native"));
    assertEquals("backend=zxing-java", FrameDecoders.buildInfo(FrameDecoders.JAVA));
    assertTrue(FrameDecoders.isAvailable(FrameDecoders.JAVA));
  }

  @Test
  public void optionsRoundTripThroughTheBuilder() {
    Collection<BarcodeFormat> formats = EnumSet.of(BarcodeFormat.QR_CODE, BarcodeFormat.EAN_13);
    Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

    ScannerOptions options = new ScannerOptions.Builder()
        .setDecoderBackend(ScannerOptions.DecoderBackend.JAVA)
        .setFormats(formats)
        .setBaseHints(hints)
        .setCharacterSet("UTF-8")
        .setCameraId(1)
        .setAutoFocus(false)
        .setContinuousFocusDisabled(true)
        .setInvertScan(true)
        .setBarcodeSceneModeDisabled(false)
        .setMeteringDisabled(false)
        .setExposureDisabled(false)
        .setFrontLightMode(FrontLightMode.AUTO)
        .setFramingRect(640, 480)
        .build();

    assertSame(ScannerOptions.DecoderBackend.JAVA, options.getDecoderBackend());
    assertEquals(2, options.getFormats().size());
    assertEquals(Boolean.TRUE, options.getBaseHints().get(DecodeHintType.TRY_HARDER));
    assertEquals("UTF-8", options.getCharacterSet());
    assertEquals(1, options.getCameraId());
    assertEquals(false, options.isAutoFocus());
    assertEquals(true, options.isContinuousFocusDisabled());
    assertEquals(true, options.isInvertScan());
    assertEquals(false, options.isBarcodeSceneModeDisabled());
    assertEquals(false, options.isMeteringDisabled());
    assertEquals(false, options.isExposureDisabled());
    assertSame(FrontLightMode.AUTO, options.getFrontLightMode());
    assertEquals(640, options.getFramingRectWidth());
    assertEquals(480, options.getFramingRectHeight());

    // buildUpon has to preserve every field; a setter left out of it would be silent.
    ScannerOptions copy = options.buildUpon().build();
    assertSame(options.getDecoderBackend(), copy.getDecoderBackend());
    assertEquals(options.getFormats().size(), copy.getFormats().size());
    assertEquals(options.getBaseHints(), copy.getBaseHints());
    assertEquals(options.getCharacterSet(), copy.getCharacterSet());
    assertEquals(options.getCameraId(), copy.getCameraId());
    assertEquals(options.isAutoFocus(), copy.isAutoFocus());
    assertEquals(options.isContinuousFocusDisabled(), copy.isContinuousFocusDisabled());
    assertEquals(options.isInvertScan(), copy.isInvertScan());
    assertEquals(options.isBarcodeSceneModeDisabled(), copy.isBarcodeSceneModeDisabled());
    assertEquals(options.isMeteringDisabled(), copy.isMeteringDisabled());
    assertEquals(options.isExposureDisabled(), copy.isExposureDisabled());
    assertSame(options.getFrontLightMode(), copy.getFrontLightMode());
    assertEquals(options.getFramingRectWidth(), copy.getFramingRectWidth());
    assertEquals(options.getFramingRectHeight(), copy.getFramingRectHeight());
  }

  @Test
  public void defaultsAreReachableAsAStaticMethod() {
    ScannerOptions defaults = ScannerOptions.defaults();
    assertSame(ScannerOptions.DecoderBackend.AUTO, defaults.getDecoderBackend());
    assertNull(defaults.getFormats());
    assertTrue(defaults.isAutoFocus());
    assertEquals(OpenCameraInterface.NO_REQUESTED_CAMERA, defaults.getCameraId());
  }

  @Test
  public void frontLightModeParsesFromJava() {
    assertSame(FrontLightMode.AUTO, FrontLightMode.parse("AUTO"));
    assertSame(FrontLightMode.OFF, FrontLightMode.parse(null));
    assertSame(FrontLightMode.OFF, FrontLightMode.parse("nonsense"));
  }

  @Test
  public void cameraFacingOrdinalsAreUnchanged() {
    // OpenCameraInterface indexes this with Camera.CameraInfo.facing, so the order is API.
    assertEquals(0, CameraFacing.BACK.ordinal());
    assertEquals(1, CameraFacing.FRONT.ordinal());
    assertEquals(2, CameraFacing.values().length);
  }

  @Test
  public void coordinateMappingIsCallableAndStillCorrect() {
    int[] box = PreviewCoordinateMapper.toFrameCoordinates(
        100, 100, 200, 200, 400, 400, 400, 400, 400, 400, 0, false);
    assertNotNull(box);
    assertEquals(4, box.length);
    assertEquals(100, box[0]);
    assertEquals(200, box[2]);
    assertEquals(0, PreviewCoordinateMapper.normalizeRotation(360));
    assertEquals(270, PreviewCoordinateMapper.normalizeRotation(-90));
  }

  @Test
  public void yPlaneSourceIsConstructibleAndCroppableFromJava() {
    byte[] bytes = new byte[16 * 16];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) i;
    }
    YPlaneLuminanceSource source =
        new YPlaneLuminanceSource(ByteBuffer.wrap(bytes), 16, 1, 2, 2, 8, 8);
    assertEquals(8, source.getWidth());
    assertEquals(8, source.getHeight());
    assertTrue(source.isCropSupported());
    byte[] row = source.getRow(0, null);
    assertEquals(8, row.length);
    assertEquals(bytes[2 * 16 + 2], row[0]);
    LuminanceSource cropped = source.crop(1, 1, 4, 4);
    assertEquals(4, cropped.getWidth());
    assertNotNull(source.detach());
    assertEquals(64, source.getMatrix().length);
  }

  /**
   * Never called. Its job is to fail compilation if any Java-facing signature changes; see the
   * class comment.
   */
  @SuppressWarnings("unused")
  private static void surfaceCompiles(Context context, ScanOutcome outcome) {
    ScannerView view = new ScannerView(context);
    view = new ScannerView(context, null);
    view = new ScannerView(context, null, 0);
    view.setOptions(ScannerOptions.defaults());
    Collection<BarcodeFormat> formats = EnumSet.of(BarcodeFormat.QR_CODE);
    view.setFormats(formats);
    view.setFormats(null);
    view.setThumbnailsEnabled(false);
    view.setTorch(true);
    view.resume();
    view.pause();
    view.resumeScanning();
    boolean scanning = view.isScanning();
    ViewfinderView viewfinder = view.getViewfinderView();
    CameraManager manager = view.getCameraManager();
    Rect rect = view.getFramingRect();

    view.setListener(new ScannerView.Listener() {
      @Override
      public void onScanned(ScanOutcome o) {
        Result result = o.getResult();
        android.graphics.Bitmap thumbnail = o.getThumbnail();
        float scale = o.getThumbnailScaleFactor();
      }

      @Override
      public void onScannerError(ScannerError error, Exception cause) {
        switch (error) {
          case PERMISSION_DENIED:
          case NO_CAMERA:
          case CAMERA_UNAVAILABLE:
          case PREVIEW_FAILED:
            break;
        }
      }
    });

    // The decoder seam is implementable from Java: the benchmark module does exactly this.
    //
    // This anonymous class is also where declaration-site variance actually gets caught.
    // A *call* like view.setFormats(formats) above compiles either way, because a
    // Collection<BarcodeFormat> is assignable to Collection<? extends BarcodeFormat>. An
    // override is not: drop @JvmSuppressWildcards from FrameDecoder.setFormats and javac
    // says "name clash: both have the same erasure, yet neither overrides the other".
    // Verified by doing exactly that.
    FrameDecoder decoder = new FrameDecoder() {
      @Override
      public Result decode(LuminanceSource source) {
        return null;
      }

      @Override
      public void setFormats(Collection<BarcodeFormat> formats) {
      }

      @Override
      public void close() {
      }

      @Override
      public String name() {
        return "test";
      }
    };
    decoder = FrameDecoders.create(FrameDecoders.JAVA, null, formats);
    decoder.setFormats(formats);
    decoder.close();

    viewfinder.setCameraManager(manager);
    viewfinder.drawViewfinder();
    viewfinder.drawResultBitmap(null);
    viewfinder.addPossibleResultPoint(new io.github.kakakakacat.zxing.ResultPoint(0, 0));
  }
}
