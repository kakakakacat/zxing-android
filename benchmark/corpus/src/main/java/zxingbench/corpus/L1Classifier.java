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

package zxingbench.corpus;

/**
 * How a decode is scored against its expectation.
 *
 * <p>These rules decide every number in an L1 result file. They are stated once, in terms
 * of the decoded format name and text rather than of any decoder's {@code Result} type,
 * so that the host run against {@code :core} and the device run against a backend behind
 * the {@code FrameDecoder} seam are scored by the same code and can be subtracted from
 * each other.
 */
public final class L1Classifier {

  private L1Classifier() {
  }

  /**
   * @param sample       the expectation from the manifest
   * @param actualFormat the decoded format's name, or null if nothing was decoded.
   *     Finding no barcode is the ordinary answer here, not a failure.
   * @param actualText   the decoded text; ignored when {@code actualFormat} is null
   * @param hardError    whether the decoder failed in a way that is not "nothing here".
   *     A miss must never arrive as an error: the two are counted separately on purpose.
   */
  public static L1Status classify(Manifest.Sample sample,
                                  String actualFormat,
                                  String actualText,
                                  boolean hardError) {
    if (hardError) {
      return L1Status.ERROR;
    }
    if (sample.isNegative()) {
      return actualFormat == null ? L1Status.CORRECT_NO_RESULT : L1Status.FALSE_POSITIVE;
    }
    if (actualFormat == null) {
      return L1Status.NOT_FOUND;
    }
    if (!actualFormat.equals(sample.expectedFormat)) {
      return L1Status.INCORRECT;
    }
    // Compared raw, exactly as upstream's AbstractBlackBoxTestCase does. An earlier
    // revision stripped a trailing newline from the expectation but not from the decoded
    // text, which marked nine samples INCORRECT whose payload genuinely ends in one: the
    // sidecar carries the barcode's content byte for byte, newline included, so there is
    // nothing to normalise away. Normalising only one side can only invent mismatches.
    if (sample.expectedText != null && !sample.expectedText.equals(actualText)) {
      return L1Status.INCORRECT;
    }
    return L1Status.CORRECT;
  }
}
