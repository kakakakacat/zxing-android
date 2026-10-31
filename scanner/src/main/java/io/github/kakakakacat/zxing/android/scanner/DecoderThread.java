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

package io.github.kakakakacat.zxing.android.scanner;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.DecodeHintType;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.ResultPointCallback;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Owns the single decode thread. One frame is decoded at a time: the next preview frame is
 * only requested once the current one has been answered, so there is no queue to grow and
 * the frame buffer handed over by the camera is never shared with a second decode.
 */
final class DecoderThread extends Thread {

  static final int MSG_DECODE = 1;
  static final int MSG_QUIT = 2;
  /** {@code obj} is the new format collection, or null for every supported format. */
  static final int MSG_SET_FORMATS = 3;

  private static final String TAG = "DecoderThread";

  private final FrameSource frameSource;
  private final Handler replyTo;
  private final ScannerOptions.DecoderBackend backendPreference;
  /** Everything except {@code POSSIBLE_FORMATS}, which the host may change per scan. */
  private final Map<DecodeHintType,Object> fixedHints;
  private final Collection<BarcodeFormat> initialFormats;
  private final boolean thumbnails;
  private final int sessionId;
  private final CountDownLatch handlerInitLatch = new CountDownLatch(1);
  private volatile Handler handler;

  /** Lets the decode thread ask for the crop rectangle without knowing about the camera. */
  interface FrameSource {
    PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height);
  }

  DecoderThread(FrameSource frameSource,
                Handler replyTo,
                ScannerOptions options,
                Collection<BarcodeFormat> formats,
                ResultPointCallback resultPointCallback,
                boolean thumbnails,
                int sessionId) {
    super("zxing-decode-" + sessionId);
    this.frameSource = frameSource;
    this.replyTo = replyTo;
    this.thumbnails = thumbnails;
    this.sessionId = sessionId;

    this.initialFormats = formats;
    this.fixedHints = fixedHints(options, resultPointCallback);
    this.backendPreference = options.getDecoderBackend();
  }

  /**
   * The decoder this thread will use.
   *
   * <p>{@code AUTO} prefers the native backend and falls back to Java when there is no
   * native build for this device's ABI — a device that cannot run it should scan, not
   * fail. {@code NATIVE} does not fall back, because a caller that asked for it
   * specifically would rather know.
   *
   * <p>Two things change with the native backend, both deliberate and neither silent:
   * the viewfinder's animated candidate points stop, because zxing-cpp reports a symbol's
   * corners only once it has found one and has no equivalent of
   * {@code NEED_RESULT_POINT_CALLBACK}; and hints other than the format selection are
   * not honoured, because no mapping from this fork's {@code DecodeHintType} to
   * zxing-cpp's options exists. A caller that set either should ask for
   * {@link ScannerOptions.DecoderBackend#JAVA}.
   */
  private FrameDecoder createDecoder() {
    if (backendPreference == ScannerOptions.DecoderBackend.JAVA) {
      return new JavaFrameDecoder(fixedHints, initialFormats);
    }
    if (backendPreference == ScannerOptions.DecoderBackend.NATIVE) {
      return new NativeFrameDecoder(initialFormats);
    }
    if (!NativeFrameDecoder.isAvailable()) {
      Log.i(TAG, "No native decoder for this ABI; using the Java decoder",
            NativeFrameDecoder.loadFailure());
      return new JavaFrameDecoder(fixedHints, initialFormats);
    }
    try {
      return new NativeFrameDecoder(initialFormats);
    } catch (RuntimeException | UnsatisfiedLinkError e) {
      // The library loaded but would not produce a decoder. Scanning with the Java one
      // beats not scanning, and the reason is logged rather than swallowed.
      Log.w(TAG, "The native decoder could not be created; using the Java decoder", e);
      return new JavaFrameDecoder(fixedHints, initialFormats);
    }
  }

  /**
   * The hints that do not change for the life of the decode thread.
   *
   * <p>Separated from {@code POSSIBLE_FORMATS} so that changing the formats mid-session
   * cannot silently drop the character set or the result point callback along with them.
   */
  private static Map<DecodeHintType,Object> fixedHints(ScannerOptions options,
                                                       ResultPointCallback resultPointCallback) {
    Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.putAll(options.getBaseHints());
    hints.remove(DecodeHintType.POSSIBLE_FORMATS);
    if (options.getCharacterSet() != null) {
      hints.put(DecodeHintType.CHARACTER_SET, options.getCharacterSet());
    }
    if (resultPointCallback != null) {
      hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
    }
    return hints;
  }

  /**
   * The effective hint map for a format selection.
   *
   * <p>An empty or null selection means "every format the reader supports", which is what
   * leaving {@code POSSIBLE_FORMATS} out of the map tells {@link MultiFormatReader}.
   * Package-private and free of Android types so the rule can be tested directly: it
   * decides how much work every frame costs.
   */
  static Map<DecodeHintType,Object> hintsFor(Map<DecodeHintType,Object> fixedHints,
                                             Collection<BarcodeFormat> formats) {
    Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.putAll(fixedHints);
    if (formats != null && !formats.isEmpty()) {
      hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
    }
    return hints;
  }

  /** Blocks until the decode looper is up. Returns null if the thread was interrupted. */
  Handler getHandler() {
    try {
      handlerInitLatch.await();
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      return null;
    }
    return handler;
  }

  @Override
  public void run() {
    Looper.prepare();
    handler = new DecodeHandler();
    handlerInitLatch.countDown();
    Looper.loop();
  }

  private final class DecodeHandler extends Handler {

    private final FrameDecoder decoder;
    private boolean running = true;

    DecodeHandler() {
      super(Looper.myLooper());
      decoder = createDecoder();
    }

    @Override
    public void handleMessage(Message message) {
      if (!running) {
        return;
      }
      if (message.what == MSG_DECODE) {
        decode((byte[]) message.obj, message.arg1, message.arg2);
      } else if (message.what == MSG_SET_FORMATS) {
        @SuppressWarnings("unchecked")
        Collection<BarcodeFormat> formats = (Collection<BarcodeFormat>) message.obj;
        decoder.setFormats(formats);
      } else if (message.what == MSG_QUIT) {
        running = false;
        // The backend may hold memory the garbage collector will not take.
        decoder.close();
        Looper looper = Looper.myLooper();
        if (looper != null) {
          looper.quit();
        }
      }
    }

    private void decode(byte[] data, int width, int height) {
      PlanarYUVLuminanceSource source = frameSource.buildLuminanceSource(data, width, height);
      Result rawResult = source == null ? null : decoder.decode(source);

      if (rawResult == null) {
        // arg1 carries the session id so a reply from a torn-down session can be dropped.
        replyTo.obtainMessage(ScannerView.MSG_DECODE_FAILED, sessionId, 0).sendToTarget();
        return;
      }

      Bitmap thumbnail = null;
      float scaleFactor = 1.0f;
      if (thumbnails && source != null) {
        int[] pixels = source.renderThumbnail();
        int tw = source.getThumbnailWidth();
        int th = source.getThumbnailHeight();
        // Mutable, so callers can draw result points straight onto it. Upstream JPEG
        // compressed this to cross a thread boundary inside one process and then made a
        // second copy to get a mutable bitmap back; both threads are here, so neither
        // step is needed.
        thumbnail = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        thumbnail.setPixels(pixels, 0, tw, 0, 0, tw, th);
        scaleFactor = (float) tw / source.getWidth();
      }
      Message reply = replyTo.obtainMessage(ScannerView.MSG_DECODE_SUCCEEDED, sessionId, 0,
          new ScanOutcome(rawResult, thumbnail, scaleFactor));
      reply.sendToTarget();
    }
  }
}
