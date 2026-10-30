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

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The three hint sets of {@code docs/maint/BENCHMARKS.md} §10.5.
 *
 * <p>Each one is run and reported separately. Pooling them would average a scan that has
 * to try sixteen formats with one that tries a single 1D symbology, which describes no
 * real workload at all.
 */
public enum L2Config {

  /** Every format the reader supports, no {@code TRY_HARDER}. What the app ships. */
  DEFAULT("default", null),

  /** The common library-consumer case: a QR-only scanner. */
  QR_ONLY("qr_only", new BarcodeFormat[] { BarcodeFormat.QR_CODE }),

  /** A single specified 1D format — the cheapest realistic hint set. */
  ONED_ONLY_UPCA("oned_only_upca", new BarcodeFormat[] { BarcodeFormat.UPC_A });

  public final String id;
  private final BarcodeFormat[] formats;

  L2Config(String id, BarcodeFormat[] formats) {
    this.id = id;
    this.formats = formats;
  }

  /** A fresh hint map; {@code MultiFormatReader} keeps a reference to what it is given. */
  public Map<DecodeHintType,Object> hints() {
    Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
    if (formats != null) {
      hints.put(DecodeHintType.POSSIBLE_FORMATS,
                Collections.unmodifiableList(Arrays.asList(formats)));
    }
    return hints;
  }

  /**
   * Whether this configuration can decode this variant at all.
   *
   * <p>The matrix is a full cross product, so {@code hit_qr} under {@code oned_only_upca}
   * is a legitimate case — it measures a miss, and an expensive one. Naming that here
   * keeps the result file honest about which cases were hits.
   */
  public boolean canDecode(FrameFactory.Variant variant) {
    switch (variant) {
      case HIT_QR:
        return this != ONED_ONLY_UPCA;
      case HIT_1D:
        return this != QR_ONLY;
      default:
        return false;
    }
  }

  public static L2Config fromId(String id) {
    for (L2Config config : values()) {
      if (config.id.equals(id)) {
        return config;
      }
    }
    throw new IllegalArgumentException("unknown L2 configuration: " + id);
  }
}
