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
import static org.junit.Assert.assertFalse;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.MultiFormatWriter;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.android.camera.Camera1ScanFrame;
import io.github.kakakakacat.zxing.common.BitMatrix;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Changing the decoded formats takes effect on a running decode thread.
 *
 * <p>Before this, {@code ScannerView.setFormats} only reached the reader when the next
 * decode thread was constructed, so calling it on a live scanner silently did nothing.
 * A test that only checked hint construction would not have caught that: the hints were
 * built correctly and never delivered.
 *
 * <p>No camera is involved. The decode thread is driven directly with a generated frame,
 * so the test is deterministic and runs on an emulator.
 */
@RunWith(AndroidJUnit4.class)
public final class LiveFormatSwitchTest {

  private static final String PAYLOAD = "ZX-LIVE-SWITCH";
  private static final int SIZE = 400;
  private static final long TIMEOUT_SECONDS = 10;

  private HandlerThread replyThread;
  private DecoderThread decoder;
  private Handler decodeHandler;
  private final BlockingQueue<Integer> replies = new ArrayBlockingQueue<>(16);

  /** A frame holding a QR code, as a full-frame greyscale plane. */
  private static byte[] qrFrame() throws Exception {
    BitMatrix matrix = new MultiFormatWriter()
        .encode(PAYLOAD, BarcodeFormat.QR_CODE, SIZE, SIZE);
    byte[] luminance = new byte[SIZE * SIZE];
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        luminance[y * SIZE + x] = (byte) (matrix.get(x, y) ? 0x00 : (byte) 0xFF);
      }
    }
    return luminance;
  }

  @Before
  public void setUp() throws Exception {
    replyThread = new HandlerThread("live-format-switch-reply");
    replyThread.start();
    Handler replyTo = new Handler(replyThread.getLooper()) {
      @Override
      public void handleMessage(Message msg) {
        replies.offer(msg.what);
      }
    };

    decoder = new DecoderThread(replyTo, ScannerOptions.defaults(), null, null, false, 1);
    decoder.start();
    decodeHandler = decoder.awaitReady(5000L);
    assertNotNull("decode looper never came up", decodeHandler);
  }

  @After
  public void tearDown() throws InterruptedException {
    if (decoder != null) {
      decoder.requestQuit();
      decoder.join(5000L);
      assertFalse("decoder thread did not terminate", decoder.isAlive());
    }
    if (replyThread != null) {
      replyThread.quitSafely();
      replyThread.join(5000L);
    }
  }

  private int decodeOnce(byte[] frame) throws InterruptedException {
    // The decode thread takes a ScanFrame now, already cropped: the crop belongs to the
    // camera backend, which knows the frame geometry, and not to the decoder.
    Camera1ScanFrame scanFrame = new Camera1ScanFrame(
        new PlanarYUVLuminanceSource(frame, SIZE, SIZE, 0, 0, SIZE, SIZE, false));
    decodeHandler.obtainMessage(DecoderThread.MSG_DECODE, scanFrame).sendToTarget();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
    while (true) {
      long remaining = deadline - System.nanoTime();
      Integer what = remaining > 0 ? replies.poll(remaining, TimeUnit.NANOSECONDS) : null;
      assertNotNull("the decode thread never answered", what);
      // awaitReady publishes its latch before READY reaches this separate reply looper.
      // A late READY is a lifecycle notification, never the answer to this frame.
      if (what != ScannerView.MSG_DECODER_READY) {
        return what; // Includes failures: an unexpected terminal error still fails the test.
      }
    }
  }

  private void setFormats(Collection<BarcodeFormat> formats) throws InterruptedException {
    decodeHandler.obtainMessage(DecoderThread.MSG_SET_FORMATS, formats).sendToTarget();
    // The decode handler is a single looper, so a frame posted after this message is
    // necessarily handled after it. Nothing to wait for beyond ordering.
  }

  @Test
  public void aFormatChangeTakesEffectOnARunningDecodeThread() throws Exception {
    byte[] frame = qrFrame();

    assertEquals("a QR frame should decode when every format is enabled",
        ScannerView.MSG_DECODE_SUCCEEDED, decodeOnce(frame));

    setFormats(Arrays.asList(BarcodeFormat.EAN_13));
    assertEquals("the same frame must not decode once QR has been switched off",
        ScannerView.MSG_DECODE_FAILED, decodeOnce(frame));

    setFormats(Arrays.asList(BarcodeFormat.QR_CODE));
    assertEquals("switching QR back on must decode it again",
        ScannerView.MSG_DECODE_SUCCEEDED, decodeOnce(frame));

    setFormats(null);
    assertEquals("a null selection means every format, so it still decodes",
        ScannerView.MSG_DECODE_SUCCEEDED, decodeOnce(frame));
  }
}
