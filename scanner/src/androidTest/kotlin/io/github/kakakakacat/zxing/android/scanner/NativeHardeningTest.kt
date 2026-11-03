/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.scanner

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kakakakacat.zxing.*
import io.github.kakakakacat.zxing.android.camera.ScanFrame
import io.github.kakakakacat.zxing.android.camera.YPlaneLuminanceSource
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class NativeHardeningTest {
  @Before fun requireNative() { assumeTrue(NativeFrameDecoder.isAvailable()) }

  @Test fun directAndArrayPathsAgreeWithPaddingCropAndPixelStride() {
    val matrix = MultiFormatWriter().encode("direct-ROI", BarcodeFormat.QR_CODE, 240, 240)
    for (pixelStride in listOf(1, 2)) {
      val rowStride = (matrix.width + 8) * pixelStride
      val buffer = ByteBuffer.allocateDirect(rowStride * (matrix.height + 6))
      for (i in 0 until buffer.capacity()) buffer.put(i, 0xff.toByte())
      for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
        buffer.put((y + 3) * rowStride + (x + 4) * pixelStride,
            if (matrix[x, y]) 0.toByte() else 0xff.toByte())
      }
      val source = YPlaneLuminanceSource(buffer, rowStride, pixelStride, 4, 3,
          matrix.width, matrix.height)
      val array = NativeFrameDecoder(null, listOf(BarcodeFormat.QR_CODE), true, false)
      val direct = NativeFrameDecoder(null, listOf(BarcodeFormat.QR_CODE), true, true)
      try {
        val expected = array.decode(source)
        val actual = direct.decode(source)
        assertNotNull(actual)
        assertEquals("direct-ROI", actual!!.text)
        assertEquals(expected!!.text, actual.text)
        assertEquals(expected.barcodeFormat, actual.barcodeFormat)
        assertArrayEquals(expected.rawBytes, actual.rawBytes)
        assertEquals(0, buffer.position())
      } finally { array.close(); direct.close() }
    }
  }

  @Test fun directOptionFallsBackForHeapBuffers() {
    val source = YPlaneLuminanceSource(ByteBuffer.wrap(ByteArray(64) { 0xff.toByte() }),
        8, 1, 0, 0, 8, 8)
    val decoder = NativeFrameDecoder(null, null, true, true)
    try { assertNull(decoder.decode(source)) } finally { decoder.close() }
  }

  @Test fun nativeWrapperFailuresReachTheSessionFailureBudget() {
    val replyThread = HandlerThread("native-budget-replies").apply { start() }
    val replies = LinkedBlockingQueue<Message>()
    val receiver = object : Handler(replyThread.looper) {
      override fun handleMessage(msg: Message) { replies.offer(Message.obtain(msg)) }
    }
    val closed = AtomicInteger()
    val inputSource = PlanarYUVLuminanceSource(ByteArray(64), 8, 8, 0, 0, 8, 8, false)
    val thread = DecoderThread(receiver, ScannerOptions.defaults(), null, null, false, 7,
        DecoderThread.DecoderFactory {
          NativeFrameDecoder(null, null, true, false, NativeFrameDecoder.DecodeCall { _, _, _, _ ->
            throw NativeDecodeException("injected native-call failure")
          })
        })
    try {
      thread.start()
      val target = thread.awaitReady(5000)
      assertNotNull(target)
      repeat(DecoderThread.MAX_CONSECUTIVE_DECODE_FAILURES) {
        val frame = object : ScanFrame {
          override val source: LuminanceSource get() = inputSource
          override fun close() { closed.incrementAndGet() }
        }
        target!!.obtainMessage(DecoderThread.MSG_DECODE, frame).sendToTarget()
      }
      var failure: Message? = null
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      while (System.nanoTime() < deadline && failure == null) {
        val message = replies.poll(1, TimeUnit.SECONDS)
        if (message?.what == ScannerView.MSG_DECODER_FAILED) failure = message
      }
      assertNotNull("native exceptions must not turn into ordinary misses", failure)
      assertTrue(failure!!.obj is NativeDecodeException)
      thread.join(5000)
      assertFalse(thread.isAlive)
      assertEquals(DecoderThread.MAX_CONSECUTIVE_DECODE_FAILURES, closed.get())
    } finally { thread.requestQuit(); thread.join(5000); replyThread.quitSafely() }
  }
}
