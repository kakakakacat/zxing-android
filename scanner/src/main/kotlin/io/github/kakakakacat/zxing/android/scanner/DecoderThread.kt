/*
 * Copyright (C) 2008 ZXing authors
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

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import io.github.kakakakacat.zxing.BarcodeFormat
import io.github.kakakakacat.zxing.DecodeHintType
import io.github.kakakakacat.zxing.LuminanceSource
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource
import io.github.kakakakacat.zxing.ResultPointCallback
import io.github.kakakakacat.zxing.android.camera.ScanFrame
import io.github.kakakakacat.zxing.android.camera.YPlaneLuminanceSource
import java.util.EnumMap
import java.util.concurrent.CountDownLatch

/**
 * Owns the single decode thread. One frame is decoded at a time: the next preview frame is only
 * requested once the current one has been answered, so there is no queue to grow and the frame
 * buffer handed over by the camera is never shared with a second decode.
 */
internal class DecoderThread(
    private val replyTo: Handler,
    options: ScannerOptions,
    private val initialFormats: Collection<@JvmSuppressWildcards BarcodeFormat>?,
    resultPointCallback: ResultPointCallback?,
    private val thumbnails: Boolean,
    private val sessionId: Int
) : Thread("zxing-decode-$sessionId") {

  private val backendPreference: ScannerOptions.DecoderBackend = options.decoderBackend

  /** Everything except `POSSIBLE_FORMATS`, which the host may change per scan. */
  private val fixedHints: Map<DecodeHintType, Any> = fixedHints(options, resultPointCallback)

  private val handlerInitLatch = CountDownLatch(1)

  @Volatile
  private var handler: Handler? = null

  /**
   * The decoder this thread will use.
   *
   * `AUTO` prefers the native backend and falls back to Java when there is no native build for
   * this device's ABI — a device that cannot run it should scan, not fail. `NATIVE` does not
   * fall back, because a caller that asked for it specifically would rather know.
   *
   * Two things change with the native backend, both deliberate and neither silent: the
   * viewfinder's animated candidate points stop, because zxing-cpp reports a symbol's corners
   * only once it has found one and has no equivalent of `NEED_RESULT_POINT_CALLBACK`; and hints
   * other than the format selection and `TRY_HARDER` are not honoured, because no mapping from
   * this fork's `DecodeHintType` to zxing-cpp's options exists. A caller that set one of those
   * should ask for [ScannerOptions.DecoderBackend.JAVA].
   */
  private fun createDecoder(): FrameDecoder {
    if (backendPreference == ScannerOptions.DecoderBackend.JAVA) {
      return JavaFrameDecoder(fixedHints, initialFormats)
    }
    if (backendPreference == ScannerOptions.DecoderBackend.NATIVE) {
      return NativeFrameDecoder(fixedHints, initialFormats, true)
    }
    if (!NativeFrameDecoder.isAvailable()) {
      Log.i(TAG, "No native decoder for this ABI; using the Java decoder",
            NativeFrameDecoder.loadFailure())
      return JavaFrameDecoder(fixedHints, initialFormats)
    }
    return try {
      NativeFrameDecoder(fixedHints, initialFormats, true)
    } catch (e: RuntimeException) {
      // The library loaded but would not produce a decoder. Scanning with the Java one
      // beats not scanning, and the reason is logged rather than swallowed.
      Log.w(TAG, "The native decoder could not be created; using the Java decoder", e)
      JavaFrameDecoder(fixedHints, initialFormats)
    } catch (e: UnsatisfiedLinkError) {
      Log.w(TAG, "The native decoder could not be created; using the Java decoder", e)
      JavaFrameDecoder(fixedHints, initialFormats)
    }
  }

  /** Blocks until the decode looper is up. Returns null if the thread was interrupted. */
  fun getHandler(): Handler? {
    try {
      handlerInitLatch.await()
    } catch (ie: InterruptedException) {
      Thread.currentThread().interrupt()
      return null
    }
    return handler
  }

  override fun run() {
    Looper.prepare()
    handler = DecodeHandler()
    handlerInitLatch.countDown()
    Looper.loop()
  }

  private inner class DecodeHandler : Handler(Looper.myLooper()!!) {

    private val decoder: FrameDecoder = createDecoder()
    private var running = true

    override fun handleMessage(message: Message) {
      if (!running) {
        // Still the owner of anything that arrives late, and on the CameraX backend a
        // frame nobody closes is a buffer slot the camera never gets back.
        (message.obj as? ScanFrame)?.close()
        return
      }
      if (message.what == MSG_DECODE) {
        decode(message.obj as ScanFrame)
      } else if (message.what == MSG_SET_FORMATS) {
        @Suppress("UNCHECKED_CAST")
        val formats = message.obj as Collection<BarcodeFormat>?
        decoder.setFormats(formats)
      } else if (message.what == MSG_QUIT) {
        running = false
        // The backend may hold memory the garbage collector will not take.
        decoder.close()
        // quitSafely, not quit: a frame can be posted between the camera being told to
        // stop and this message being handled, and `quit` would discard it unhandled --
        // taking the ImageProxy it carries with it. quitSafely delivers it, `running` is
        // already false, and the branch above hands it back.
        Looper.myLooper()?.quitSafely()
      }
    }

    /**
     * Decodes one frame and gives it back.
     *
     * The frame is closed in a `finally` and nowhere else. On the CameraX backend it holds a
     * slot in the camera's buffer queue, and a slot that is never returned stops frame
     * delivery altogether — with no exception to find it by.
     */
    private fun decode(frame: ScanFrame) {
      try {
        val source = frame.source
        val rawResult = if (source == null) null else decoder.decode(source)

        if (rawResult == null) {
          // arg1 carries the session id so a reply from a torn-down session can be dropped.
          replyTo.obtainMessage(ScannerView.MSG_DECODE_FAILED, sessionId, 0).sendToTarget()
          return
        }

        var thumbnail: Bitmap? = null
        var scaleFactor = 1.0f
        val planar = if (thumbnails && source != null) thumbnailSource(source) else null
        if (planar != null) {
          val pixels = planar.renderThumbnail()
          val tw = planar.thumbnailWidth
          val th = planar.thumbnailHeight
          // Mutable, so callers can draw result points straight onto it. Upstream JPEG
          // compressed this to cross a thread boundary inside one process and then made a
          // second copy to get a mutable bitmap back; both threads are here, so neither
          // step is needed.
          thumbnail = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
          thumbnail.setPixels(pixels, 0, tw, 0, 0, tw, th)
          scaleFactor = tw.toFloat() / planar.width
        }
        val reply = replyTo.obtainMessage(ScannerView.MSG_DECODE_SUCCEEDED, sessionId, 0,
            ScanOutcome.of(rawResult, thumbnail, scaleFactor))
        reply.sendToTarget()
      } finally {
        frame.close()
      }
    }

    /**
     * The thumbnail that goes out with a successful scan, which only
     * [PlanarYUVLuminanceSource] knows how to render.
     *
     * A CameraX frame is a [YPlaneLuminanceSource] reading the camera's buffer in place, so
     * it has to be copied out before the image is closed — [YPlaneLuminanceSource.detach]
     * pays one scan-rectangle-sized copy, not a frame. That copy is only made on the one
     * frame per session that decodes, and only when thumbnails are enabled.
     */
    private fun thumbnailSource(source: LuminanceSource): PlanarYUVLuminanceSource? =
        when (source) {
          is PlanarYUVLuminanceSource -> source
          is YPlaneLuminanceSource -> source.detach()
          else -> null
        }
  }

  companion object {
    const val MSG_DECODE = 1
    const val MSG_QUIT = 2

    /** `obj` is the new format collection, or null for every supported format. */
    const val MSG_SET_FORMATS = 3

    private const val TAG = "DecoderThread"

    /**
     * The hints that do not change for the life of the decode thread.
     *
     * Separated from `POSSIBLE_FORMATS` so that changing the formats mid-session cannot
     * silently drop the character set or the result point callback along with them.
     */
    private fun fixedHints(
        options: ScannerOptions,
        resultPointCallback: ResultPointCallback?
    ): Map<DecodeHintType, Any> {
      val hints: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)
      hints.putAll(options.baseHints)
      hints.remove(DecodeHintType.POSSIBLE_FORMATS)
      val characterSet = options.characterSet
      if (characterSet != null) {
        hints[DecodeHintType.CHARACTER_SET] = characterSet
      }
      if (resultPointCallback != null) {
        hints[DecodeHintType.NEED_RESULT_POINT_CALLBACK] = resultPointCallback
      }
      return hints
    }

    /**
     * The effective hint map for a format selection.
     *
     * An empty or null selection means "every format the reader supports", which is what
     * leaving `POSSIBLE_FORMATS` out of the map tells `MultiFormatReader`. Free of Android
     * types so the rule can be tested directly: it decides how much work every frame costs.
     */
    @JvmStatic
    fun hintsFor(
        fixedHints: Map<DecodeHintType, @JvmSuppressWildcards Any?>,
        formats: Collection<@JvmSuppressWildcards BarcodeFormat>?
    ): Map<DecodeHintType, Any?> {
      val hints: MutableMap<DecodeHintType, Any?> = EnumMap(DecodeHintType::class.java)
      hints.putAll(fixedHints)
      if (formats != null && !formats.isEmpty()) {
        hints[DecodeHintType.POSSIBLE_FORMATS] = formats
      }
      return hints
    }
  }
}
