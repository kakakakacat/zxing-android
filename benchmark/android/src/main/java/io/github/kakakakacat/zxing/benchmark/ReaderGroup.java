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

package io.github.kakakakacat.zxing.benchmark;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.DecodeHintType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The six readers an all-format decode runs, as a format set each.
 *
 * <p>The stage split attributed a frame to thresholding or to detection and found
 * detection carrying 90-93% of it (BENCHMARKS.md §10.3c). It cannot say <em>which</em>
 * reader that is, because both backends hide the loop: zxing-cpp's
 * {@code MultiFormatReader} keeps {@code _readers} private and this fork's Java one keeps
 * {@code readers} private, so there is no seam inside either to time.
 *
 * <p>These groups reach the same decomposition from outside. Each one is the format set
 * that causes exactly one of the six readers to be constructed, so a decode restricted to
 * it runs that reader and no other. Both decoders build the same six, in the same order:
 *
 * <ul>
 *   <li>{@code ONED} — {@code OneD::Reader} / {@code MultiFormatOneDReader}
 *   <li>{@code QRCODE} — {@code QRCode::Reader} / {@code QRCodeReader}
 *   <li>{@code DATAMATRIX}, {@code AZTEC}, {@code PDF417}, {@code MAXICODE} — likewise
 * </ul>
 *
 * <h2>Why each group is a whole symbology and never less</h2>
 *
 * <p>The readers branch on their own format set. {@code QRReader} skips Micro-QR and rMQR
 * detection unless those formats are in it; {@code MultiFormatOneDReader} and
 * {@code ODReader} build one sub-reader per linear symbology. A group narrower than the
 * symbology would therefore time a reader doing <em>less</em> than it does in the
 * all-format run, and the attribution would understate it. So {@link #ONED} names every
 * linear format rather than one, and the 2D groups name the symbology rather than a
 * variant of it.
 *
 * <p>{@link #PDF417} is the one group that covers two readers on the native side:
 * zxing-cpp builds {@code Pdf417::Reader} and {@code MicroPdf417::Reader} separately, and
 * no format set selects the first without the second. Reported together, because an
 * attribution that split them would be describing a decode nobody runs.
 *
 * <h2>When the groups sum, and when they do not</h2>
 *
 * <p>Both reader loops return at the first symbol found. <strong>On a miss every reader
 * runs to the end, so the groups sum to the whole detection; on a hit they do not</strong>
 * — a hit charges nothing to the readers behind it, and under this fork's shipping
 * default (no {@code TRY_HARDER}) the 1D reader is in front of all of them. The miss is
 * the frame a live preview decodes thousands of, and the one these groups exist to
 * attribute. That the six add up on a miss, and fall short of the sum on a hit, is the
 * measurement's own check on itself.
 */
public enum ReaderGroup {

  /** Every linear symbology: the eight sub-readers the default 1D reader builds. */
  ONED("oned", "AllLinear",
      BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.EAN_8, BarcodeFormat.EAN_13,
      BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODE_128,
      BarcodeFormat.ITF, BarcodeFormat.CODABAR,
      BarcodeFormat.RSS_14, BarcodeFormat.RSS_EXPANDED),

  /** QR and, on the native side, its Micro-QR and rMQR variants. */
  QRCODE("qrcode", "QRCode", BarcodeFormat.QR_CODE),

  DATAMATRIX("datamatrix", "DataMatrix", BarcodeFormat.DATA_MATRIX),

  AZTEC("aztec", "Aztec", BarcodeFormat.AZTEC),

  /** PDF417 and MicroPDF417: two native readers no format set separates. */
  PDF417("pdf417", "PDF417", BarcodeFormat.PDF_417),

  MAXICODE("maxicode", "MaxiCode", BarcodeFormat.MAXICODE);

  public final String id;

  /** The zxing-cpp format list {@code StagedFrameDecoder.detectOnlyGroup} takes. */
  public final String nativeFormats;

  private final List<BarcodeFormat> javaFormats;

  ReaderGroup(String id, String nativeFormats, BarcodeFormat... javaFormats) {
    this.id = id;
    this.nativeFormats = nativeFormats;
    this.javaFormats = Collections.unmodifiableList(Arrays.asList(javaFormats));
  }

  /**
   * This group narrowed to what {@code config} already restricted the decoder to.
   *
   * <p>Empty when the configuration builds no reader for this group at all — a 1D group
   * under a QR-only scanner is not a cheap measurement, it is a measurement of a reader
   * that configuration never constructs.
   */
  public List<BarcodeFormat> javaFormats(L2Config config) {
    Collection<BarcodeFormat> allowed = config.formats();
    if (allowed == null) {
      return javaFormats;
    }
    List<BarcodeFormat> narrowed = new ArrayList<>();
    for (BarcodeFormat format : javaFormats) {
      if (allowed.contains(format)) {
        narrowed.add(format);
      }
    }
    return Collections.unmodifiableList(narrowed);
  }

  /** Whether {@code config} builds this group's reader at all. */
  public boolean appliesTo(L2Config config) {
    return !javaFormats(config).isEmpty();
  }

  /** Hints that select this group and nothing else, for the Java reader. */
  public Map<DecodeHintType,Object> hints(L2Config config) {
    Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.POSSIBLE_FORMATS, javaFormats(config));
    return hints;
  }
}
