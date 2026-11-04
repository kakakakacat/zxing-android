/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
package io.github.kakakakacat.zxing.android.scanner

import io.github.kakakakacat.zxing.*
import org.junit.Assert.*
import org.junit.Test

class PerformanceOptionsTest {
  @Test fun defaultsPreserveExistingBehaviorAndCopiesPreserveOptIns() {
    val defaults = ScannerOptions.defaults()
    assertFalse(defaults.nativeDirectBuffer)
    assertFalse(defaults.progressiveRoi)
    assertFalse(defaults.javaBufferReuse)
    assertFalse(defaults.camera1BufferPool)
    assertFalse(defaults.directFrameScheduling)
    assertEquals(0, defaults.analysisWidth)
    assertEquals(0L, defaults.decodeIntervalMillis)
    assertEquals(0, defaults.previewWidth)
    assertEquals(ScannerOptions.DecoderBackend.AUTO, defaults.decoderBackend)
    val copy = defaults.buildUpon().setNativeDirectBuffer(true).setPreviewSize(1280, 720)
        .setAnalysisSize(640, 360).setProgressiveRoi(true).setJavaBufferReuse(true)
        .setCamera1BufferPool(true).setDirectFrameScheduling(true)
        .setDecodeIntervalMillis(50).setDecoderBackend(ScannerOptions.DecoderBackend.FORMAT_AWARE)
        .build().buildUpon().build()
    assertTrue(copy.nativeDirectBuffer)
    assertEquals(1280, copy.previewWidth)
    assertEquals(720, copy.previewHeight)
    assertEquals(640, copy.analysisWidth)
    assertEquals(360, copy.analysisHeight)
    assertTrue(copy.progressiveRoi)
    assertTrue(copy.javaBufferReuse)
    assertTrue(copy.camera1BufferPool)
    assertTrue(copy.directFrameScheduling)
    assertEquals(50L, copy.decodeIntervalMillis)
  }
  @Test fun optionsSnapshotMutableInputs() {
    val formats = arrayListOf(BarcodeFormat.QR_CODE)
    val hints = mutableMapOf<DecodeHintType, Any>(DecodeHintType.TRY_HARDER to true)
    val builder = ScannerOptions.Builder().setFormats(formats).setBaseHints(hints)
    val options = builder.build()
    formats.clear(); hints.clear()
    assertEquals(listOf(BarcodeFormat.QR_CODE), options.formats!!.toList())
    assertTrue(options.baseHints.containsKey(DecodeHintType.TRY_HARDER))
  }
  @Test fun routingUsesOnlyOneBackendAndReplacesItOnFormatChanges() {
    val created = arrayListOf<String>()
    val closed = arrayListOf<String>()
    fun fake(name: String): FrameDecoder {
      created.add(name)
      return object : FrameDecoder {
        override fun decode(source: LuminanceSource): Result? = null
        override fun setFormats(formats: Collection<BarcodeFormat>?) {}
        override fun close() { closed.add(name) }
        override fun name() = name
      }
    }
    val decoder = FormatAwareDecoder(emptyMap<DecodeHintType, Any>(),
        listOf(BarcodeFormat.CODE_128), { fake("java") }, { fake("auto") })
    assertEquals(listOf("java"), created)
    decoder.setFormats(listOf(BarcodeFormat.QR_CODE))
    assertEquals(listOf("java", "auto"), created)
    assertEquals(listOf("java"), closed)
    decoder.close()
    assertEquals(created, closed)
    assertTrue(FormatAwareDecoder.prefersJava(mapOf(DecodeHintType.CHARACTER_SET to "UTF-8"), null))
    assertFalse(FormatAwareDecoder.prefersJava(emptyMap<DecodeHintType, Any>(), null))
  }
  @Test(expected = IllegalArgumentException::class)
  fun rejectsHalfSpecifiedAnalysisSize() { ScannerOptions.Builder().setAnalysisSize(640, 0) }

  @Test fun namedBenchmarkStrategiesAreReachableAndRespectJavaHints() {
    assertEquals(FrameDecoders.JAVA_REUSE, FrameDecoders.canonical("java-reuse"))
    assertEquals(FrameDecoders.JAVA_PROGRESSIVE, FrameDecoders.canonical("java-progressive"))
    val decoder = FrameDecoders.create("format-aware", mapOf(DecodeHintType.CHARACTER_SET to "UTF-8"), null)
    try { assertEquals("format-aware:zxing-java", decoder.name()) } finally { decoder.close() }
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsHalfSpecifiedStreamSize() { ScannerOptions.Builder().setPreviewSize(640, 0) }
  @Test(expected = IllegalArgumentException::class)
  fun rejectsNegativeInterval() { ScannerOptions.Builder().setDecodeIntervalMillis(-1) }
}
