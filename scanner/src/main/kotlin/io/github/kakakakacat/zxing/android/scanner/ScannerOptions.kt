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

package io.github.kakakakacat.zxing.android.scanner

import io.github.kakakakacat.zxing.BarcodeFormat
import io.github.kakakakacat.zxing.DecodeHintType
import io.github.kakakakacat.zxing.android.camera.FrontLightMode
import io.github.kakakakacat.zxing.android.camera.open.OpenCameraInterface
import java.util.Collections
import java.util.EnumMap

/**
 * Everything the scanner needs to know, passed in explicitly.
 *
 * The upstream app read these out of `SharedPreferences` deep inside the camera and decode
 * classes, which made them unusable from a library. Callers now build an instance and hand it
 * over; where a caller wants the Barcode Scanner app's preference behaviour, it reads the
 * preferences itself and fills this in.
 *
 * Instances are immutable and safe to share across threads.
 */
class ScannerOptions private constructor(
    /** Which camera stack delivers frames. [CameraBackend.CAMERA1] by default. */
    val cameraBackend: CameraBackend,
    /** Which decoder does the work. [DecoderBackend.AUTO] by default. */
    val decoderBackend: DecoderBackend,
    /** Formats to look for, or `null` to let the reader try everything it supports. */
    val formats: Collection<@JvmSuppressWildcards BarcodeFormat>?,
    val baseHints: Map<DecodeHintType, @JvmSuppressWildcards Any>,
    val characterSet: String?,
    val cameraId: Int,
    val isAutoFocus: Boolean,
    val isContinuousFocusDisabled: Boolean,
    val isInvertScan: Boolean,
    val isBarcodeSceneModeDisabled: Boolean,
    val isMeteringDisabled: Boolean,
    val isExposureDisabled: Boolean,
    val frontLightMode: FrontLightMode,
    /** Requested scan rectangle width in pixels, or 0 to derive it from the screen. */
    val framingRectWidth: Int,
    val framingRectHeight: Int,
    /**
     * Whether the scanner may configure CameraX for the whole process. Off by default.
     *
     * `ProcessCameraProvider.configureInstance` is process-wide and can be called once:
     * the scanner would be telling CameraX that only the back camera exists, for every
     * part of the host application, from a library the host embedded to read barcodes.
     * That is the host's decision, so it is off unless asked for.
     *
     * It is worth asking for in an application whose only camera use is this scanner, and
     * on hardware with one camera. Without the limiter CameraX's validator insists both
     * lens facings exist and retries initialisation for about five seconds when one does
     * not: 5,987 ms against 114 ms, measured on a one-camera emulator, and no difference
     * at all on a two-camera phone. A host that wants the limiter and its own CameraX
     * configuration should set `CameraXConfig.Builder.setAvailableCamerasLimiter` itself
     * through `CameraXConfig.Provider` and leave this off.
     *
     * Ignored by [CameraBackend.CAMERA1], which does not use CameraX at all.
     */
    val cameraXProcessConfiguration: Boolean,
    /** Experimental direct Y-plane JNI input; off until measured on the host device. */
    val nativeDirectBuffer: Boolean,
    /** Requested camera stream size; 0/0 keeps the existing negotiation. */
    val previewWidth: Int,
    val previewHeight: Int,
    /** Minimum delay between decode requests, not a camera frame-rate guarantee. */
    val decodeIntervalMillis: Long
) {

  /**
   * Which camera stack delivers frames.
   *
   * Phase 6 adopted CameraX and kept the original `android.hardware.Camera` path reachable
   * rather than deleting it, so a device CameraX handles badly has somewhere to go. The
   * comparison that led to that — two physical devices, time to a decodable frame, APK size
   * and what each stack costs to maintain — is `docs/maint/decisions/0001-camera-backend.md`.
   */
  enum class CameraBackend {

    /**
     * `android.hardware.Camera`, deprecated since API 21, and this fork's 1,285 lines of
     * quirk handling for it.
     *
     * Still the default: CameraX does not become the default until the verification the
     * decision record asks for has been done on a device, not before.
     */
    CAMERA1,

    /**
     * CameraX, without `camera-view`.
     *
     * Frames reach the decoder as `ImageProxy` luma planes read in place, so a frame is not
     * copied on its way to being decoded. Only the back camera is considered.
     */
    CAMERAX
  }

  /** Which decoder does the work. */
  enum class DecoderBackend {
    /**
     * The native zxing-cpp decoder where it can run, the Java decoder otherwise.
     *
     * The default. On the reference device the native decoder is 56–82% faster on every 2D
     * case and on frames that decode nothing under all formats — which is what live preview
     * does almost every frame — and slower only on 1D hits, by about 1.7 ms on the single
     * frame of a session that succeeds. Measurements and method in
     * `docs/maint/BENCHMARKS.md` §10.6c.
     *
     * Falls back to Java with no error when there is no native build for the device's ABI.
     */
    AUTO,

    /** This fork's Java decoder, whatever the device supports. */
    JAVA,

    /**
     * The native decoder, or an error if it cannot load.
     *
     * Use only when a caller would rather fail than silently get the Java decoder.
     */
    NATIVE,

    /** Opt-in: Java for 1D-only requests or Java-only hints, AUTO otherwise. */
    FORMAT_AWARE
  }

  /**
   * A builder holding everything this instance holds.
   *
   * Built through the public setters rather than through a copy constructor. Java's was
   * private and Kotlin has no way for the outer class to reach a nested class's private
   * constructor, so keeping it would have meant making it `internal` -- which is public in
   * bytecode and would have handed callers a second, undocumented way to build a Builder.
   */
  fun buildUpon(): Builder = Builder()
      .setCameraBackend(cameraBackend)
      .setNativeDirectBuffer(nativeDirectBuffer)
      .setPreviewSize(previewWidth, previewHeight)
      .setDecodeIntervalMillis(decodeIntervalMillis)
      .setCameraXProcessConfiguration(cameraXProcessConfiguration)
      .setDecoderBackend(decoderBackend)
      .setFormats(formats)
      .setBaseHints(baseHints)
      .setCharacterSet(characterSet)
      .setCameraId(cameraId)
      .setAutoFocus(isAutoFocus)
      .setContinuousFocusDisabled(isContinuousFocusDisabled)
      .setInvertScan(isInvertScan)
      .setBarcodeSceneModeDisabled(isBarcodeSceneModeDisabled)
      .setMeteringDisabled(isMeteringDisabled)
      .setExposureDisabled(isExposureDisabled)
      .setFrontLightMode(frontLightMode)
      .setFramingRect(framingRectWidth, framingRectHeight)

  class Builder {

    private var nativeDirectBuffer = false
    private var previewWidth = 0
    private var previewHeight = 0
    private var decodeIntervalMillis = 0L

    /** Enables an experimental path. Image ownership still lasts through decode. */
    fun setNativeDirectBuffer(enabled: Boolean): Builder {
      nativeDirectBuffer = enabled
      return this
    }

    /** 0/0 restores legacy negotiation; both nonzero dimensions must be positive. */
    fun setPreviewSize(width: Int, height: Int): Builder {
      require((width == 0 && height == 0) || (width > 0 && height > 0))
      previewWidth = width
      previewHeight = height
      return this
    }

    /** Delays the next request after completion; may increase first-scan latency. */
    fun setDecodeIntervalMillis(interval: Long): Builder {
      require(interval >= 0)
      decodeIntervalMillis = interval
      return this
    }

    private var cameraBackend = CameraBackend.CAMERA1
    private var cameraXProcessConfiguration = false
    private var decoderBackend = DecoderBackend.AUTO
    private var formats: Collection<BarcodeFormat>? = null
    private var baseHints: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)
    private var characterSet: String? = null
    private var cameraId = OpenCameraInterface.NO_REQUESTED_CAMERA
    private var autoFocus = true
    private var continuousFocusDisabled = false
    private var invertScan = false
    private var barcodeSceneModeDisabled = true
    private var meteringDisabled = true
    private var exposureDisabled = true
    private var frontLightMode = FrontLightMode.OFF
    private var framingRectWidth = 0
    private var framingRectHeight = 0

    /** See [ScannerOptions.cameraXProcessConfiguration]. Off by default. */
    fun setCameraXProcessConfiguration(enabled: Boolean): Builder {
      this.cameraXProcessConfiguration = enabled
      return this
    }

    /** @param backend null is treated as [CameraBackend.CAMERA1] */
    fun setCameraBackend(backend: CameraBackend?): Builder {
      this.cameraBackend = backend ?: CameraBackend.CAMERA1
      return this
    }

    /** @param backend null is treated as [DecoderBackend.AUTO] */
    fun setDecoderBackend(backend: DecoderBackend?): Builder {
      this.decoderBackend = backend ?: DecoderBackend.AUTO
      return this
    }

    fun setFormats(formats: Collection<@JvmSuppressWildcards BarcodeFormat>?): Builder {
      this.formats = formats
      return this
    }

    fun setBaseHints(hints: Map<DecodeHintType, *>?): Builder {
      val fresh: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)
      if (hints != null) {
        @Suppress("UNCHECKED_CAST")
        fresh.putAll(hints as Map<DecodeHintType, Any>)
      }
      this.baseHints = fresh
      return this
    }

    fun setCharacterSet(characterSet: String?): Builder {
      this.characterSet = characterSet
      return this
    }

    /** @param cameraId a camera index, or [OpenCameraInterface.NO_REQUESTED_CAMERA] */
    fun setCameraId(cameraId: Int): Builder {
      this.cameraId = cameraId
      return this
    }

    fun setAutoFocus(autoFocus: Boolean): Builder {
      this.autoFocus = autoFocus
      return this
    }

    fun setContinuousFocusDisabled(disabled: Boolean): Builder {
      this.continuousFocusDisabled = disabled
      return this
    }

    fun setInvertScan(invertScan: Boolean): Builder {
      this.invertScan = invertScan
      return this
    }

    fun setBarcodeSceneModeDisabled(disabled: Boolean): Builder {
      this.barcodeSceneModeDisabled = disabled
      return this
    }

    fun setMeteringDisabled(disabled: Boolean): Builder {
      this.meteringDisabled = disabled
      return this
    }

    fun setExposureDisabled(disabled: Boolean): Builder {
      this.exposureDisabled = disabled
      return this
    }

    fun setFrontLightMode(mode: FrontLightMode?): Builder {
      this.frontLightMode = mode ?: FrontLightMode.OFF
      return this
    }

    /** Both values must be positive to take effect. */
    fun setFramingRect(width: Int, height: Int): Builder {
      this.framingRectWidth = width
      this.framingRectHeight = height
      return this
    }

    // The values go across one by one rather than the Builder itself: Kotlin will not let
    // the outer class read a nested class's private fields, and the alternative -- `internal`
    // -- puts a mangled getter and setter per field into the published API. The API diff
    // showed 28 of them.
    fun build(): ScannerOptions = ScannerOptions(
        cameraBackend,
        decoderBackend,
        formats?.let { Collections.unmodifiableCollection(ArrayList(it)) },
        // Snapshot the hints so later builder mutations cannot change a built instance.
        Collections.unmodifiableMap(EnumMap(baseHints)),
        characterSet,
        cameraId,
        autoFocus,
        continuousFocusDisabled,
        invertScan,
        barcodeSceneModeDisabled,
        meteringDisabled,
        exposureDisabled,
        frontLightMode,
        framingRectWidth,
        framingRectHeight,
        cameraXProcessConfiguration,
        nativeDirectBuffer, previewWidth, previewHeight, decodeIntervalMillis)
  }

  companion object {
    /** Defaults match the upstream app's out-of-the-box behaviour. */
    @JvmStatic
    fun defaults(): ScannerOptions = Builder().build()
  }
}
