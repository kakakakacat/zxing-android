/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.scanner

import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kakakakacat.zxing.*
import io.github.kakakakacat.zxing.android.camera.Camera1ScanFrame
import io.github.kakakakacat.zxing.android.camera.LumaFrameData
import io.github.kakakakacat.zxing.android.camera.ScanFrame
import io.github.kakakakacat.zxing.android.camera.Camera1Worker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DirectFrameSchedulingTest {
  @Test fun backendNeutralFrameMetadataReachesCapableDecoder() {
    val replies = HandlerThread("frame-data-replies").apply { start() }
    val pixels = ByteArray(64)
    val source = PlanarYUVLuminanceSource(pixels, 8, 8, 0, 0, 8, 8, false)
    val data = LumaFrameData.array(pixels, 8, 8, 8, 1, 0, 0, 8, 8, 0, 123L)
    val decoded = CountDownLatch(1)
    val fake = object : FrameDecoder, FrameDataDecoder {
      override fun decode(source: LuminanceSource): Result? {
        fail("legacy source path used despite frame metadata")
        return null
      }
      override fun decode(data: LumaFrameData, fallback: LuminanceSource): Result? {
        assertEquals(123L, data.receivedAtNanos)
        assertSame(source, fallback)
        decoded.countDown()
        return null
      }
      override fun setFormats(formats: Collection<BarcodeFormat>?) {}
      override fun close() {}
      override fun name() = "frame-data-fake"
    }
    val thread = DecoderThread(Handler(replies.looper), ScannerOptions.defaults(), null, null,
        false, 2, DecoderThread.DecoderFactory { fake })
    try {
      thread.start()
      val handler = thread.awaitReady(5000)!!
      handler.obtainMessage(DecoderThread.MSG_DECODE,
          Camera1ScanFrame(source, data, null)).sendToTarget()
      assertTrue("frame metadata was not decoded", decoded.await(5, TimeUnit.SECONDS))
    } finally {
      thread.requestQuit(); thread.join(5000)
      replies.quitSafely(); replies.join(5000)
    }
  }

  @Test fun cameraWorkersAreSharedUntilTheLastCleanupAndThenTerminate() {
    val first = Camera1Worker.acquire()
    val second = Camera1Worker.acquire()
    val thread = second.handler.looper.thread
    assertSame(first.handler, second.handler)
    first.close(); first.close()
    assertTrue(thread.isAlive)
    second.close()
    thread.join(5000)
    assertFalse("last camera worker lease leaked its thread", thread.isAlive)
  }

  @Test fun missesKeepFlowingWhileTheReplyLooperIsBlockedAndOnlyAfterRelease() {
    val replies = HandlerThread("blocked-frame-replies").apply { start() }
    val blocked = CountDownLatch(1)
    val unblock = CountDownLatch(1)
    val reply = Handler(replies.looper)
    reply.post { blocked.countDown(); unblock.await(10, TimeUnit.SECONDS) }
    assertTrue(blocked.await(5, TimeUnit.SECONDS))
    val closed = AtomicInteger()
    val requests = AtomicInteger()
    val done = CountDownLatch(1)
    fun frame(): ScanFrame = Camera1ScanFrame(
        PlanarYUVLuminanceSource(ByteArray(64), 8, 8, 0, 0, 8, 8, false)) { closed.incrementAndGet() }
    val fake = object : FrameDecoder {
      override fun decode(source: LuminanceSource): Result? = null
      override fun setFormats(formats: Collection<BarcodeFormat>?) {}
      override fun close() {}
      override fun name() = "fake"
    }
    val gate = FrameRequestGate { handler ->
      val count = requests.incrementAndGet()
      assertEquals("next request ran before frame.close", count, closed.get())
      if (count < 3) handler.obtainMessage(DecoderThread.MSG_DECODE, frame()).sendToTarget()
      else done.countDown()
    }
    val thread = DecoderThread(reply, ScannerOptions.defaults(), null, null, false, 1,
        DecoderThread.DecoderFactory { fake }, gate)
    try {
      thread.start()
      val handler = thread.awaitReady(5000)!!
      handler.obtainMessage(DecoderThread.MSG_DECODE, frame()).sendToTarget()
      assertTrue("miss scheduling waited for the blocked reply looper", done.await(5, TimeUnit.SECONDS))
      assertEquals(3, closed.get())
      gate.cancel()
      gate.request(handler)
      assertEquals(3, requests.get())
    } finally {
      gate.cancel(); thread.requestQuit(); thread.join(5000)
      unblock.countDown(); replies.quitSafely(); replies.join(5000)
    }
  }
}
