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
import java.util.concurrent.TimeUnit

/**
 * Owns the single decode thread. One frame is decoded at a time: the next preview frame is only
 * requested once the current one has been answered, so there is no queue to grow and the frame
 * buffer handed over by the camera is never shared with a second decode.
 *
 * ### Four states, and a waiter always gets an answer
 *
 * `STARTING` until the decoder exists, then `READY` or `FAILED`, then `STOPPED`. The two
 * terminal starting states are both announced, to [replyTo] and to the latch alike, which is
 * the fix for the shape this had before: the decoder was built inside the `Handler`
 * constructor and `countDown()` came after it, so an initialisation that threw — a `NATIVE`
 * backend on an ABI with no native build, a format selection zxing-cpp cannot be asked for —
 * never reached the notification. The thread died, and every caller of `getHandler()` waited
 * on a latch nothing would ever count down. There was no timeout.
 *
 * **Readiness is announced rather than waited for.** [ScannerView] starts this thread from the
 * main thread and used to block it on that latch until the decoder was built. Nothing here
 * blocks the main thread now: the host is told when the decoder is up, and asks for the first
 * frame then. [awaitReady] remains for a caller that genuinely has to wait — a test, or a host
 * driving this off the main thread — and it takes a timeout.
 */
internal class DecoderThread @JvmOverloads constructor(
    private val replyTo: Handler,
    options: ScannerOptions,
    initialFormats: Collection<@JvmSuppressWildcards BarcodeFormat>?,
    resultPointCallback: ResultPointCallback?,
    private val thumbnails: Boolean,
    private val sessionId: Int,
    /**
     * Builds the decoder. Null means the backend the options asked for.
     *
     * The seam the lifecycle tests drive: an initialisation that throws, one that is slow, a
     * decode that throws and a format change that throws are all states this thread has to
     * have an answer for, and none of them can be produced on demand by a real decoder.
     */
    private val decoderFactory: DecoderFactory? = null
) : Thread("zxing-decode-$sessionId") {

  /**
   * Builds the decoder for a format selection.
   *
   * A `fun interface` rather than a Kotlin function type so the tests, which are written
   * in Java on purpose, can pass a lambda.
   */
  internal fun interface DecoderFactory {
    fun create(formats: Collection<@JvmSuppressWildcards BarcodeFormat>?): FrameDecoder
  }

  /** Where this thread is in its life. Read by [state]; never goes backwards. */
  enum class State {
    /** Started, no decoder yet. */
    STARTING,

    /** The decoder exists and the looper is running. */
    READY,

    /** The decoder could not be built. [startFailure] says why. Nothing will be decoded. */
    FAILED,

    /** Quit, by request or after a failure. The decoder is closed. */
    STOPPED
  }

  private val backendPreference: ScannerOptions.DecoderBackend = options.decoderBackend

  /** Everything except `POSSIBLE_FORMATS`, which the host may change per scan. */
  private val fixedHints: Map<DecodeHintType, Any> = fixedHints(options, resultPointCallback)

  private val readyLatch = CountDownLatch(1)

  /** Guards [handler], [currentState], [requestedFormats] and [quitRequested] together. */
  private val lock = Any()

  private var handler: Handler? = null

  @Volatile
  private var currentState: State = State.STARTING

  @Volatile
  private var startFailure: Throwable? = null

  private var requestedFormats: Collection<BarcodeFormat>? = initialFormats

  private var quitRequested = false

  /**
   * Where this thread is. A reader of this never blocks.
   *
   * Not named `state`: `Thread` already has a `getState()` and it answers a different
   * question — whether the thread is runnable — which is exactly the question that cannot
   * tell a decoder that failed to build from one that is still building.
   */
  fun decoderState(): State = currentState

  /** Why [State.FAILED], or null. */
  fun startFailure(): Throwable? = startFailure

  /**
   * The decode handler, or null if the decoder is not up yet or never will be.
   *
   * Does not block, and so does not answer "is it coming?" — that is what the
   * `MSG_DECODER_READY` and `MSG_DECODER_FAILED` replies are for.
   */
  fun handlerIfReady(): Handler? = synchronized(lock) { handler }

  /**
   * Blocks until the decoder is up or the wait runs out.
   *
   * Not for the main thread. Returns the handler, or null if initialisation failed, timed
   * out or the wait was interrupted — three outcomes a caller can tell apart through
   * [state] and [startFailure], which is the point: before this, a failed initialisation
   * and a slow one were the same unbounded wait.
   */
  fun awaitReady(timeoutMillis: Long): Handler? {
    try {
      if (!readyLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
        Log.w(TAG, "The decoder was still starting after $timeoutMillis ms")
        return null
      }
    } catch (ie: InterruptedException) {
      Thread.currentThread().interrupt()
      return null
    }
    return handlerIfReady()
  }

  /**
   * Restricts what is looked for, now or as soon as the decoder exists.
   *
   * A format change that arrives before the decoder is built used to be dropped: there was
   * no handler to post it to, and `getHandler()` would have blocked the caller until there
   * was. It is recorded here instead and applied by whichever of the two happens second,
   * under the lock that publishes the handler, so neither can be lost to the other.
   */
  fun setFormats(formats: Collection<@JvmSuppressWildcards BarcodeFormat>?) {
    val target = synchronized(lock) {
      requestedFormats = formats
      handler
    }
    target?.obtainMessage(MSG_SET_FORMATS, formats)?.sendToTarget()
  }

  /**
   * Stops the thread. Safe at any point in its life and safe to call twice.
   *
   * Does not wait. A caller that needs the thread gone before it goes on — and on this
   * library's teardown path nothing does, because both backends own their buffers past the
   * decode — can [join] on its own terms.
   */
  fun requestQuit() {
    val target = synchronized(lock) {
      quitRequested = true
      handler
    }
    // If there is no handler yet, run() will find quitRequested set as it publishes one
    // and quit without ever entering the loop.
    target?.obtainMessage(MSG_QUIT)?.sendToTarget()
  }

  /**
   * The decoder this thread will use.
   *
   * `AUTO` prefers the native backend and falls back to Java when there is no native build for
   * this device's ABI — a device that cannot run it should scan, not fail. `NATIVE` does not
   * fall back, because a caller that asked for it specifically would rather know: that is now
   * a reported [ScannerError.DECODER_UNAVAILABLE] rather than a thread that dies quietly.
   *
   * Two things change with the native backend, both deliberate and neither silent: the
   * viewfinder's animated candidate points stop, because zxing-cpp reports a symbol's corners
   * only once it has found one and has no equivalent of `NEED_RESULT_POINT_CALLBACK`; and hints
   * other than the format selection and `TRY_HARDER` are not honoured, because no mapping from
   * this fork's `DecodeHintType` to zxing-cpp's options exists. A caller that set one of those
   * should ask for [ScannerOptions.DecoderBackend.JAVA].
   */
  private fun createDecoder(formats: Collection<BarcodeFormat>?): FrameDecoder {
    decoderFactory?.let { return it.create(formats) }
    if (backendPreference == ScannerOptions.DecoderBackend.JAVA) {
      return JavaFrameDecoder(fixedHints, formats)
    }
    if (backendPreference == ScannerOptions.DecoderBackend.NATIVE) {
      // Deliberately not caught: an explicit NATIVE that cannot be honoured is reported to
      // the host rather than quietly answered with a different decoder.
      return NativeFrameDecoder(fixedHints, formats, true)
    }
    if (!NativeFrameDecoder.isAvailable()) {
      Log.i(TAG, "No native decoder for this ABI; using the Java decoder",
            NativeFrameDecoder.loadFailure())
      return JavaFrameDecoder(fixedHints, formats)
    }
    return try {
      NativeFrameDecoder(fixedHints, formats, true)
    } catch (e: RuntimeException) {
      // The library loaded but would not produce a decoder. Scanning with the Java one
      // beats not scanning, and the reason is logged rather than swallowed. The format
      // selection is carried over unchanged, so nothing the caller asked for is dropped
      // along with the backend.
      Log.w(TAG, "The native decoder could not be created; using the Java decoder", e)
      JavaFrameDecoder(fixedHints, formats)
    } catch (e: UnsatisfiedLinkError) {
      Log.w(TAG, "The native decoder could not be created; using the Java decoder", e)
      JavaFrameDecoder(fixedHints, formats)
    }
  }

  override fun run() {
    Looper.prepare()
    val formatsAtStart = synchronized(lock) { requestedFormats }
    val decoder = try {
      createDecoder(formatsAtStart)
    } catch (t: Throwable) {
      // Throwable, not Exception: an UnsatisfiedLinkError out of an explicit NATIVE
      // backend is the likeliest failure of all, and an Error that reaches the thread's
      // uncaught handler takes the process with it. Every waiter is told instead.
      fail(t)
      return
    }
    val decodeHandler = DecodeHandler(decoder)
    val quitBeforeStarting = synchronized(lock) {
      handler = decodeHandler
      currentState = State.READY
      if (requestedFormats !== formatsAtStart) {
        // A format change arrived while the decoder was being built. Applied here, under
        // the same lock that publishes the handler, so it cannot fall between the two.
        decodeHandler.obtainMessage(MSG_SET_FORMATS, requestedFormats).sendToTarget()
      }
      quitRequested
    }
    readyLatch.countDown()
    if (quitBeforeStarting) {
      // Paused while the decoder was being built. Nothing has been scanned and nothing
      // will be; close the decoder here rather than entering a loop to be told again.
      decoder.close()
      currentState = State.STOPPED
      return
    }
    replyTo.obtainMessage(ScannerView.MSG_DECODER_READY, sessionId, 0).sendToTarget()
    Looper.loop()
    currentState = State.STOPPED
  }

  /** Initialisation failed: every waiter is told, and the thread ends. */
  private fun fail(cause: Throwable) {
    Log.w(TAG, "The decode thread could not start", cause)
    startFailure = cause
    currentState = State.FAILED
    readyLatch.countDown()
    replyTo.obtainMessage(ScannerView.MSG_DECODER_FAILED, sessionId, 0, cause).sendToTarget()
  }

  private inner class DecodeHandler(
      private val decoder: FrameDecoder
  ) : Handler(Looper.myLooper()!!) {

    private var running = true

    /** L3's FIRST_FRAME span ends here, where the frame actually arrives, and ends once. */
    private var firstFrameTraced = false

    /**
     * Frames the decoder threw on, in a row.
     *
     * One is a lost frame and scanning goes on. A decoder that throws on every frame is
     * not going to start working, and a scanner that spins on it forever reports nothing
     * at all — so after [MAX_CONSECUTIVE_DECODE_FAILURES] the host is told and this
     * thread stops. Reset by any frame that is answered, hit or miss.
     */
    private var consecutiveFailures = 0

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
        try {
          decoder.setFormats(formats)
        } catch (e: RuntimeException) {
          // A selection this backend cannot honour. Carrying on under the previous one
          // would be scanning for formats the host did not ask for and did not refuse.
          stop(e)
        }
      } else if (message.what == MSG_QUIT) {
        stop(null)
      }
    }

    /**
     * Ends the session. The decoder is closed here and nowhere else.
     *
     * @param failure the reason, if this is a failure rather than an ordinary quit
     */
    private fun stop(failure: Throwable?) {
      running = false
      // The backend may hold memory the garbage collector will not take.
      decoder.close()
      if (failure != null) {
        Log.w(TAG, "The decoder stopped on an error", failure)
        replyTo.obtainMessage(ScannerView.MSG_DECODER_FAILED, sessionId, 0, failure)
            .sendToTarget()
      }
      // quitSafely, not quit: a frame can be posted between the camera being told to
      // stop and this message being handled, and `quit` would discard it unhandled --
      // taking the ImageProxy it carries with it. quitSafely delivers it, `running` is
      // already false, and the branch above hands it back.
      Looper.myLooper()?.quitSafely()
    }

    /**
     * Decodes one frame and gives it back.
     *
     * The frame is closed in a `finally` and nowhere else. On the CameraX backend it holds a
     * slot in the camera's buffer queue, and a slot that is never returned stops frame
     * delivery altogether — with no exception to find it by.
     */
    private fun decode(frame: ScanFrame) {
      if (!firstFrameTraced) {
        firstFrameTraced = true
        // Before the decode, not after: the question is when a frame reached the decoder,
        // and folding the first decode into that would answer a different one.
        ScannerTrace.end(ScannerTrace.FIRST_FRAME, sessionId)
      }
      try {
        val source = frame.source
        val rawResult = try {
          if (source == null) null else decoder.decode(source)
        } catch (e: RuntimeException) {
          // A frame the decoder threw on. Not a miss -- a miss is a frame that was read
          // and held nothing -- so it is counted and reported rather than hidden among
          // them, and the next frame is still asked for.
          consecutiveFailures++
          Log.w(TAG, "The decoder threw on a frame (" + consecutiveFailures +
                " in a row); that frame is lost", e)
          if (consecutiveFailures >= MAX_CONSECUTIVE_DECODE_FAILURES) {
            stop(e)
            return
          }
          replyTo.obtainMessage(ScannerView.MSG_DECODE_FAILED, sessionId, 0).sendToTarget()
          return
        }
        consecutiveFailures = 0

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

    /**
     * How many frames in a row the decoder may throw on before the session is given up.
     *
     * One is bad luck. Ten in a row is a decoder that is not going to recover, and a
     * scanner that keeps feeding it reports nothing for as long as the host leaves the
     * screen open.
     */
    const val MAX_CONSECUTIVE_DECODE_FAILURES = 10

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
