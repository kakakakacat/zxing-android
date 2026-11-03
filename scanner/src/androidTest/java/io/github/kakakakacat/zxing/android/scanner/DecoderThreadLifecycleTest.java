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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.LuminanceSource;
import io.github.kakakakacat.zxing.Result;
import io.github.kakakakacat.zxing.android.camera.ScanFrame;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What the decode thread does when things go wrong, which is the half of its life no real
 * decoder can be asked to demonstrate.
 *
 * <p>Every case here was unreachable before: the decoder was built inside the
 * {@code Handler} constructor and the latch was counted down after it, so an
 * initialisation that threw left the thread dead and every waiter blocked for ever on a
 * latch with no timeout. A decode that threw took the process with it through the
 * uncaught-exception handler. A format change that could not be applied was dropped and
 * scanning went on under the old selection.
 *
 * <p>The decoder is injected, so each of those is a lambda rather than a device
 * configuration nobody has.
 */
@RunWith(AndroidJUnit4.class)
public final class DecoderThreadLifecycleTest {

  private static final long TIMEOUT_SECONDS = 10;

  private HandlerThread replyThread;
  private Handler replyTo;
  private final BlockingQueue<Message> replies = new ArrayBlockingQueue<>(64);
  private final List<DecoderThread> started = new ArrayList<>();

  /** A decoder whose every move is recorded and can be made to fail on demand. */
  private static final class FakeDecoder implements FrameDecoder {
    final AtomicInteger decodes = new AtomicInteger();
    final AtomicInteger closes = new AtomicInteger();
    final List<Collection<BarcodeFormat>> formatCalls = new ArrayList<>();
    volatile boolean throwOnDecode;
    volatile boolean throwOnSetFormats;
    volatile long decodeMillis;

    @Override
    public Result decode(LuminanceSource source) {
      decodes.incrementAndGet();
      if (decodeMillis > 0) {
        try {
          Thread.sleep(decodeMillis);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }
      if (throwOnDecode) {
        throw new IllegalStateException("the decoder is broken");
      }
      return null;
    }

    @Override
    public void setFormats(Collection<BarcodeFormat> formats) {
      if (throwOnSetFormats) {
        throw new UnsupportedOperationException("this backend cannot be asked for that");
      }
      synchronized (formatCalls) {
        formatCalls.add(formats);
      }
    }

    @Override
    public void close() {
      closes.incrementAndGet();
    }

    @Override
    public String name() {
      return "fake";
    }

    Collection<BarcodeFormat> lastFormats() {
      synchronized (formatCalls) {
        return formatCalls.isEmpty() ? null : formatCalls.get(formatCalls.size() - 1);
      }
    }
  }

  /** A frame that counts its own closes, because "exactly once" is the contract. */
  private static final class CountingFrame implements ScanFrame {
    final AtomicInteger closes = new AtomicInteger();

    /**
     * A real source, however small.
     *
     * <p>Not null: the decode thread answers a frame with no crop as a miss without ever
     * calling the decoder, and a test whose decoder is never reached proves nothing about
     * what happens when it throws. Two of these tests passed for that reason before this
     * line existed.
     */
    @Override
    public LuminanceSource getSource() {
      return new LuminanceSource(1, 1) {
        private final byte[] pixel = new byte[1];

        @Override
        public byte[] getRow(int y, byte[] row) {
          return pixel;
        }

        @Override
        public byte[] getMatrix() {
          return pixel;
        }
      };
    }

    @Override
    public void close() {
      closes.incrementAndGet();
    }
  }

  @Before
  public void setUp() {
    replyThread = new HandlerThread("decoder-lifecycle-reply");
    replyThread.start();
    replyTo = new Handler(replyThread.getLooper()) {
      @Override
      public void handleMessage(Message msg) {
        replies.offer(Message.obtain(msg));
      }
    };
  }

  @After
  public void tearDown() throws Exception {
    for (DecoderThread thread : started) {
      thread.requestQuit();
      thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    }
    started.clear();
    if (replyThread != null) {
      replyThread.quitSafely();
    }
  }

  private DecoderThread start(DecoderThread.DecoderFactory factory,
                              Collection<BarcodeFormat> formats) {
    DecoderThread thread = new DecoderThread(
        replyTo, ScannerOptions.defaults(), formats, null, false, 1, factory);
    started.add(thread);
    thread.start();
    return thread;
  }

  private Message awaitReply(int what) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
    while (System.nanoTime() < deadline) {
      Message msg = replies.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (msg == null) {
        break;
      }
      if (msg.what == what) {
        return msg;
      }
    }
    throw new AssertionError("no message " + what + " arrived within "
                             + TIMEOUT_SECONDS + "s");
  }

  @Test
  public void anInitialisationThatThrowsIsAnsweredRatherThanWaitedFor() throws Exception {
    DecoderThread thread = start(formats -> {
      throw new IllegalStateException("libzxingnative is not loaded");
    }, null);

    // The whole defect in one line: this used to never return.
    assertNull("a failed initialisation must not hand back a handler",
               thread.awaitReady(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)));
    assertEquals(DecoderThread.State.FAILED, thread.decoderState());
    assertNotNull("the reason must survive", thread.startFailure());
    assertEquals("libzxingnative is not loaded", thread.startFailure().getMessage());

    Message failure = awaitReply(ScannerView.MSG_DECODER_FAILED);
    assertEquals("the reply must name its session", 1, failure.arg1);
    assertTrue(failure.obj instanceof IllegalStateException);
    thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    assertFalse("the thread must not be left running", thread.isAlive());
  }

  @Test
  public void anInitialisationThatIsSlowTimesOutInsteadOfBlockingForever() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    DecoderThread thread = start(formats -> {
      awaitQuietly(release);
      return new FakeDecoder();
    }, null);

    long startedAt = System.nanoTime();
    assertNull("a wait that runs out must say so", thread.awaitReady(200L));
    long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    assertTrue("the wait should have been bounded, took " + waitedMillis + " ms",
               waitedMillis < TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    assertEquals(DecoderThread.State.STARTING, thread.decoderState());

    release.countDown();
    assertNotNull("it should still come up once it can",
                  thread.awaitReady(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)));
    assertEquals(DecoderThread.State.READY, thread.decoderState());
  }

  @Test
  public void quittingDuringInitialisationLeavesNothingRunning() throws Exception {
    // The host paused while the decoder was still being built. Nothing has been scanned
    // and nothing will be, so the decoder has to be closed on the way out rather than
    // left to a looper that is never entered.
    CountDownLatch release = new CountDownLatch(1);
    FakeDecoder decoder = new FakeDecoder();
    DecoderThread thread = start(formats -> {
      awaitQuietly(release);
      return decoder;
    }, null);

    thread.requestQuit();
    release.countDown();
    thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

    assertFalse(thread.isAlive());
    assertEquals(DecoderThread.State.STOPPED, thread.decoderState());
    assertEquals("the decoder must be closed exactly once", 1, decoder.closes.get());
    assertTrue("nothing should have been decoded", decoder.decodes.get() == 0);
  }

  @Test
  public void aFormatChangeThatArrivesBeforeTheDecoderIsNotLost() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    FakeDecoder decoder = new FakeDecoder();
    List<Collection<BarcodeFormat>> created = new ArrayList<>();
    Collection<BarcodeFormat> initial = Arrays.asList(BarcodeFormat.QR_CODE);
    DecoderThread thread = start(formats -> {
      created.add(formats);
      entered.countDown();
      awaitQuietly(release);
      return decoder;
    }, initial);

    // Waited for, not raced: the change has to arrive after the thread has read the
    // selection it is building with, or it is not the case this test is about.
    assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    assertSame(initial, created.get(0));
    Collection<BarcodeFormat> wanted = Arrays.asList(BarcodeFormat.EAN_13);
    thread.setFormats(wanted);  // while the decoder is still being built
    release.countDown();
    Handler handler = thread.awaitReady(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    assertNotNull(handler);
    drain(handler);

    assertSame("the selection in force must be the last one asked for",
               wanted, decoder.lastFormats());
  }

  @Test
  public void aDecodeThatThrowsLosesOneFrameAndKeepsScanning() throws Exception {
    FakeDecoder decoder = new FakeDecoder();
    DecoderThread thread = start(formats -> decoder, null);
    Handler handler = thread.awaitReady(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    assertNotNull(handler);

    decoder.throwOnDecode = true;
    CountingFrame frame = new CountingFrame();
    handler.obtainMessage(DecoderThread.MSG_DECODE, frame).sendToTarget();

    Message reply = awaitReply(ScannerView.MSG_DECODE_FAILED);
    assertEquals(1, reply.arg1);
    // The reply is posted before finally closes the frame. Await handler completion,
    // not a scheduler race between two threads (no sleep or relaxed assertion).
    drain(handler);
    assertEquals("a lost frame is still returned, exactly once", 1, frame.closes.get());
    assertEquals("one bad frame must not stop the session",
                 DecoderThread.State.READY, thread.decoderState());
    assertEquals("the decoder must not be closed for one bad frame", 0, decoder.closes.get());
  }

  @Test
  public void aDecoderThatThrowsOnEveryFrameIsGivenUpOnRatherThanSpunOn() throws Exception {
    FakeDecoder decoder = new FakeDecoder();
    DecoderThread thread = start(formats -> decoder, null);
    Handler handler = thread.awaitReady(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    assertNotNull(handler);

    decoder.throwOnDecode = true;
    List<CountingFrame> frames = new ArrayList<>();
    for (int i = 0; i < DecoderThread.MAX_CONSECUTIVE_DECODE_FAILURES; i++) {
      CountingFrame frame = new CountingFrame();
      frames.add(frame);
      handler.obtainMessage(DecoderThread.MSG_DECODE, frame).sendToTarget();
    }

    Message failure = awaitReply(ScannerView.MSG_DECODER_FAILED);
    assertTrue(failure.obj instanceof IllegalStateException);
    thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    assertEquals(DecoderThread.State.STOPPED, thread.decoderState());
    assertEquals("the decoder must be closed on the way out", 1, decoder.closes.get());
    for (CountingFrame frame : frames) {
      assertEquals("every frame is returned exactly once", 1, frame.closes.get());
    }
  }

  @Test
  public void aFormatUpdateThatThrowsStopsTheSessionRatherThanScanningOnTheOldSelection()
      throws Exception {
    FakeDecoder decoder = new FakeDecoder();
    DecoderThread thread = start(formats -> decoder, null);
    Handler handler = thread.awaitReady(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    assertNotNull(handler);

    decoder.throwOnSetFormats = true;
    thread.setFormats(Arrays.asList(BarcodeFormat.AZTEC));

    Message failure = awaitReply(ScannerView.MSG_DECODER_FAILED);
    assertTrue(failure.obj instanceof UnsupportedOperationException);
    thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    assertEquals(DecoderThread.State.STOPPED, thread.decoderState());
    assertEquals(1, decoder.closes.get());
  }

  @Test
  public void quittingDuringALongDecodeStillReturnsEveryFrameExactlyOnce() throws Exception {
    FakeDecoder decoder = new FakeDecoder();
    decoder.decodeMillis = 600;  // longer than the 500 ms join this used to do on the caller
    DecoderThread thread = start(formats -> decoder, null);
    Handler handler = thread.awaitReady(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    assertNotNull(handler);

    CountingFrame inFlight = new CountingFrame();
    CountingFrame late = new CountingFrame();
    handler.obtainMessage(DecoderThread.MSG_DECODE, inFlight).sendToTarget();
    Thread.sleep(50);  // let the first decode start
    handler.obtainMessage(DecoderThread.MSG_DECODE, late).sendToTarget();
    thread.requestQuit();

    thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    assertEquals(DecoderThread.State.STOPPED, thread.decoderState());
    assertEquals("the frame being decoded", 1, inFlight.closes.get());
    // quitSafely delivers what is already queued, and a frame that arrives after the
    // decoder has stopped is handed straight back rather than dropped with the message.
    assertEquals("the frame that arrived during the quit", 1, late.closes.get());
    assertEquals(1, decoder.closes.get());
  }

  @Test
  public void aHundredStartStopCyclesLeaveNothingBehind() throws Exception {
    // Fast pause/resume, which is where a lifecycle defect shows up one time in a
    // hundred rather than every time. See the CameraX LifecycleRegistry crash in
    // docs/maint/HANDOFF.md for why that number and not three.
    for (int i = 0; i < 100; i++) {
      FakeDecoder decoder = new FakeDecoder();
      DecoderThread thread = new DecoderThread(
          replyTo, ScannerOptions.defaults(), null, null, false, i, formats -> decoder);
      thread.start();
      if ((i % 2) == 0) {
        // Half the cycles wait for the decoder; half quit while it is still coming up.
        assertNotNull(thread.awaitReady(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)));
      }
      thread.requestQuit();
      thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
      assertFalse("cycle " + i + " left the thread running", thread.isAlive());
      assertEquals("cycle " + i + " did not close its decoder exactly once",
                   1, decoder.closes.get());
      assertEquals(DecoderThread.State.STOPPED, thread.decoderState());
    }
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException("the test never released the initialisation");
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ie);
    }
  }

  /** Waits until everything already posted to a looper has been handled. */
  private static void drain(Handler handler) throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    handler.post(done::countDown);
    assertTrue("the decode looper did not drain",
               done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
  }
}
