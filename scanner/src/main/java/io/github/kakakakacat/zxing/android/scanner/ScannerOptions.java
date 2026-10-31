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

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.DecodeHintType;
import io.github.kakakakacat.zxing.android.camera.FrontLightMode;
import io.github.kakakakacat.zxing.android.camera.open.OpenCameraInterface;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Everything the scanner needs to know, passed in explicitly.
 *
 * <p>The upstream app read these out of {@code SharedPreferences} deep inside the camera
 * and decode classes, which made them unusable from a library. Callers now build an
 * instance and hand it over; where a caller wants the Barcode Scanner app's preference
 * behaviour, it reads the preferences itself and fills this in.
 *
 * <p>Instances are immutable and safe to share across threads.
 */
public final class ScannerOptions {

  /** Which decoder does the work. */
  public enum DecoderBackend {
    /**
     * The native zxing-cpp decoder where it can run, the Java decoder otherwise.
     *
     * <p>The default. On the reference device the native decoder is 56–82% faster on
     * every 2D case and on frames that decode nothing under all formats — which is what
     * live preview does almost every frame — and slower only on 1D hits, by about 1.7 ms
     * on the single frame of a session that succeeds. Measurements and method in
     * {@code docs/maint/BENCHMARKS.md} §10.6c.
     *
     * <p>Falls back to Java with no error when there is no native build for the device's
     * ABI.
     */
    AUTO,

    /** This fork's Java decoder, whatever the device supports. */
    JAVA,

    /**
     * The native decoder, or an error if it cannot load.
     *
     * <p>Use only when a caller would rather fail than silently get the Java decoder.
     */
    NATIVE
  }

  private final DecoderBackend decoderBackend;
  private final Collection<BarcodeFormat> formats;
  private final Map<DecodeHintType,Object> baseHints;
  private final String characterSet;
  private final int cameraId;
  private final boolean autoFocus;
  private final boolean continuousFocusDisabled;
  private final boolean invertScan;
  private final boolean barcodeSceneModeDisabled;
  private final boolean meteringDisabled;
  private final boolean exposureDisabled;
  private final FrontLightMode frontLightMode;
  private final int framingRectWidth;
  private final int framingRectHeight;

  private ScannerOptions(Builder b) {
    this.decoderBackend = b.decoderBackend;
    this.formats = b.formats == null ? null : Collections.unmodifiableCollection(b.formats);
    this.baseHints = Collections.unmodifiableMap(b.baseHints);
    this.characterSet = b.characterSet;
    this.cameraId = b.cameraId;
    this.autoFocus = b.autoFocus;
    this.continuousFocusDisabled = b.continuousFocusDisabled;
    this.invertScan = b.invertScan;
    this.barcodeSceneModeDisabled = b.barcodeSceneModeDisabled;
    this.meteringDisabled = b.meteringDisabled;
    this.exposureDisabled = b.exposureDisabled;
    this.frontLightMode = b.frontLightMode;
    this.framingRectWidth = b.framingRectWidth;
    this.framingRectHeight = b.framingRectHeight;
  }

  /** Defaults match the upstream app's out-of-the-box behaviour. */
  public static ScannerOptions defaults() {
    return new Builder().build();
  }

  /** Which decoder does the work. {@link DecoderBackend#AUTO} by default. */
  public DecoderBackend getDecoderBackend() {
    return decoderBackend;
  }

  /** Formats to look for, or {@code null} to let the reader try everything it supports. */
  public Collection<BarcodeFormat> getFormats() {
    return formats;
  }

  public Map<DecodeHintType,Object> getBaseHints() {
    return baseHints;
  }

  public String getCharacterSet() {
    return characterSet;
  }

  public int getCameraId() {
    return cameraId;
  }

  public boolean isAutoFocus() {
    return autoFocus;
  }

  public boolean isContinuousFocusDisabled() {
    return continuousFocusDisabled;
  }

  public boolean isInvertScan() {
    return invertScan;
  }

  public boolean isBarcodeSceneModeDisabled() {
    return barcodeSceneModeDisabled;
  }

  public boolean isMeteringDisabled() {
    return meteringDisabled;
  }

  public boolean isExposureDisabled() {
    return exposureDisabled;
  }

  public FrontLightMode getFrontLightMode() {
    return frontLightMode;
  }

  /** Requested scan rectangle width in pixels, or 0 to derive it from the screen. */
  public int getFramingRectWidth() {
    return framingRectWidth;
  }

  public int getFramingRectHeight() {
    return framingRectHeight;
  }

  public Builder buildUpon() {
    return new Builder(this);
  }

  public static final class Builder {

    private DecoderBackend decoderBackend = DecoderBackend.AUTO;
    private Collection<BarcodeFormat> formats;
    private Map<DecodeHintType,Object> baseHints = new EnumMap<>(DecodeHintType.class);
    private String characterSet;
    private int cameraId = OpenCameraInterface.NO_REQUESTED_CAMERA;
    private boolean autoFocus = true;
    private boolean continuousFocusDisabled;
    private boolean invertScan;
    private boolean barcodeSceneModeDisabled = true;
    private boolean meteringDisabled = true;
    private boolean exposureDisabled = true;
    private FrontLightMode frontLightMode = FrontLightMode.OFF;
    private int framingRectWidth;
    private int framingRectHeight;

    public Builder() {
    }

    private Builder(ScannerOptions o) {
      this.decoderBackend = o.decoderBackend;
      this.formats = o.formats;
      this.baseHints = new EnumMap<>(o.baseHints);
      this.characterSet = o.characterSet;
      this.cameraId = o.cameraId;
      this.autoFocus = o.autoFocus;
      this.continuousFocusDisabled = o.continuousFocusDisabled;
      this.invertScan = o.invertScan;
      this.barcodeSceneModeDisabled = o.barcodeSceneModeDisabled;
      this.meteringDisabled = o.meteringDisabled;
      this.exposureDisabled = o.exposureDisabled;
      this.frontLightMode = o.frontLightMode;
      this.framingRectWidth = o.framingRectWidth;
      this.framingRectHeight = o.framingRectHeight;
    }

    /** @param backend null is treated as {@link DecoderBackend#AUTO} */
    public Builder setDecoderBackend(DecoderBackend backend) {
      this.decoderBackend = backend == null ? DecoderBackend.AUTO : backend;
      return this;
    }

    public Builder setFormats(Collection<BarcodeFormat> formats) {
      this.formats = formats;
      return this;
    }

    public Builder setBaseHints(Map<DecodeHintType,?> hints) {
      this.baseHints = new EnumMap<>(DecodeHintType.class);
      if (hints != null) {
        this.baseHints.putAll(hints);
      }
      return this;
    }

    public Builder setCharacterSet(String characterSet) {
      this.characterSet = characterSet;
      return this;
    }

    /** @param cameraId a camera index, or {@link OpenCameraInterface#NO_REQUESTED_CAMERA} */
    public Builder setCameraId(int cameraId) {
      this.cameraId = cameraId;
      return this;
    }

    public Builder setAutoFocus(boolean autoFocus) {
      this.autoFocus = autoFocus;
      return this;
    }

    public Builder setContinuousFocusDisabled(boolean disabled) {
      this.continuousFocusDisabled = disabled;
      return this;
    }

    public Builder setInvertScan(boolean invertScan) {
      this.invertScan = invertScan;
      return this;
    }

    public Builder setBarcodeSceneModeDisabled(boolean disabled) {
      this.barcodeSceneModeDisabled = disabled;
      return this;
    }

    public Builder setMeteringDisabled(boolean disabled) {
      this.meteringDisabled = disabled;
      return this;
    }

    public Builder setExposureDisabled(boolean disabled) {
      this.exposureDisabled = disabled;
      return this;
    }

    public Builder setFrontLightMode(FrontLightMode mode) {
      this.frontLightMode = mode == null ? FrontLightMode.OFF : mode;
      return this;
    }

    /** Both values must be positive to take effect. */
    public Builder setFramingRect(int width, int height) {
      this.framingRectWidth = width;
      this.framingRectHeight = height;
      return this;
    }

    public ScannerOptions build() {
      return new ScannerOptions(this);
    }
  }
}
