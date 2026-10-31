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
import io.github.kakakakacat.zxing.Result;

/**
 * Whether two decoder backends found the same barcode.
 *
 * <p>A speed comparison between two decoders is only meaningful if they are reading the
 * same thing, so every A/B case checks this before it is timed. Exact equality is the
 * rule, with exactly one documented exception.
 *
 * <h3>The exception: UPC-A rendered as EAN-13</h3>
 *
 * <p>This fork's Java decoder reports a UPC-A symbol as {@code UPC_A} with its 12 digits.
 * zxing-cpp reports the same symbol as 13 digits with a leading zero, and as
 * {@code EAN_13} unless EAN-13 was explicitly excluded from the search. That is not a
 * misread by either: zxing-cpp does it deliberately, citing ISO/IEC 15420:2009 and GS1
 * General Specifications 5.1.3, which say the content behind symbology identifier
 * {@code ]E0} should be 13 digits. GS1 prefix 0 is reserved for UPC-A, so the two
 * renderings denote the same symbol without ambiguity.
 *
 * <p>Treating this as a disagreement would make every 1D hit case unmeasurable.
 * Normalising it away inside the decoder would be worse: it would hide, from the only
 * place that currently notices, a difference that reaches callers. So it is allowed here,
 * named, and written down as a product consequence in {@code docs/maint/BENCHMARKS.md}
 * §10.6b — a host that switched backends would start receiving 13 digits where it used
 * to receive 12.
 */
public final class BackendAgreement {

  private BackendAgreement() {
  }

  /** Why two results differ, or null if they describe the same symbol. */
  public static String disagreement(Result reference, Result candidate) {
    if (reference == null && candidate == null) {
      return null;
    }
    if (reference == null) {
      return "the reference decoder found nothing, the candidate found "
          + describe(candidate);
    }
    if (candidate == null) {
      return "the reference decoder found " + describe(reference)
          + ", the candidate found nothing";
    }
    if (reference.getText().equals(candidate.getText())
        && reference.getBarcodeFormat() == candidate.getBarcodeFormat()) {
      return null;
    }
    if (isUpcARenderedAsEan13(reference, candidate)) {
      return null;
    }
    return "reference " + describe(reference) + " vs candidate " + describe(candidate);
  }

  /**
   * Whether the candidate is the reference's UPC-A symbol in its EAN-13 rendering.
   *
   * <p>Deliberately narrow: the reference must be a 12-digit {@code UPC_A}, and the
   * candidate must be exactly that string with a leading zero, reported as either
   * {@code EAN_13} or {@code UPC_A}. Anything else is a real disagreement.
   */
  static boolean isUpcARenderedAsEan13(Result reference, Result candidate) {
    if (reference.getBarcodeFormat() != BarcodeFormat.UPC_A) {
      return false;
    }
    BarcodeFormat candidateFormat = candidate.getBarcodeFormat();
    if (candidateFormat != BarcodeFormat.EAN_13 && candidateFormat != BarcodeFormat.UPC_A) {
      return false;
    }
    String referenceText = reference.getText();
    if (referenceText.length() != 12) {
      return false;
    }
    return candidate.getText().equals("0" + referenceText);
  }

  private static String describe(Result result) {
    return result.getBarcodeFormat() + "/" + result.getText();
  }
}
