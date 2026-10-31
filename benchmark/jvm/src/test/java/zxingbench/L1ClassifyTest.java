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

package zxingbench;

import static org.junit.Assert.assertEquals;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.NotFoundException;
import io.github.kakakakacat.zxing.Result;
import org.junit.Test;

/**
 * How a decode is scored against its expectation.
 *
 * <p>These rules decide every number in an L1 result file, so they are worth testing
 * without a 1168-sample corpus run behind them. One of them was wrong: the expectation
 * had its trailing newline stripped and the decoded text did not, which scored nine
 * samples INCORRECT whose payload genuinely ends in a newline.
 */
public final class L1ClassifyTest {

  private static Manifest.Sample sample(String format, String text) {
    Manifest.Sample s = new Manifest.Sample();
    s.sampleId = "test/1";
    s.expectedFormat = format;
    s.expectedText = text;
    return s;
  }

  private static Result result(BarcodeFormat format, String text) {
    return new Result(text, null, null, format);
  }

  @Test
  public void matchingFormatAndTextIsCorrect() {
    assertEquals(L1Runner.Status.CORRECT,
        L1Runner.classify(sample("QR_CODE", "hello"),
                          result(BarcodeFormat.QR_CODE, "hello"), null));
  }

  @Test
  public void aTrailingNewlineIsPartOfThePayloadOnBothSides() {
    assertEquals(L1Runner.Status.CORRECT,
        L1Runner.classify(sample("QR_CODE", "hello\n"),
                          result(BarcodeFormat.QR_CODE, "hello\n"), null));
  }

  @Test
  public void aTrailingNewlineIsNotNormalisedAwayOnEitherSide() {
    // The decoded text really is one character shorter than the expectation. That is a
    // mismatch, not something to tidy up -- the sidecar carries the payload byte for byte.
    assertEquals(L1Runner.Status.INCORRECT,
        L1Runner.classify(sample("QR_CODE", "hello\n"),
                          result(BarcodeFormat.QR_CODE, "hello"), null));
    assertEquals(L1Runner.Status.INCORRECT,
        L1Runner.classify(sample("QR_CODE", "hello"),
                          result(BarcodeFormat.QR_CODE, "hello\n"), null));
  }

  @Test
  public void aCarriageReturnPairIsNotNormalisedEither() {
    assertEquals(L1Runner.Status.CORRECT,
        L1Runner.classify(sample("QR_CODE", "a\r\nb\r\n"),
                          result(BarcodeFormat.QR_CODE, "a\r\nb\r\n"), null));
  }

  @Test
  public void aDifferentFormatIsIncorrectHoweverTheTextCompares() {
    assertEquals(L1Runner.Status.INCORRECT,
        L1Runner.classify(sample("EAN_13", "9780441014989"),
                          result(BarcodeFormat.UPC_E, "9780441014989"), null));
  }

  @Test
  public void aMissOnAPositiveSampleIsNotFound() {
    assertEquals(L1Runner.Status.NOT_FOUND,
        L1Runner.classify(sample("QR_CODE", "hello"), null, NotFoundException.getNotFoundInstance()));
  }

  @Test
  public void aMissOnANegativeSampleIsTheCorrectAnswer() {
    assertEquals(L1Runner.Status.CORRECT_NO_RESULT,
        L1Runner.classify(sample(null, null), null, NotFoundException.getNotFoundInstance()));
  }

  @Test
  public void aReadOnANegativeSampleIsAFalsePositive() {
    assertEquals(L1Runner.Status.FALSE_POSITIVE,
        L1Runner.classify(sample(null, null), result(BarcodeFormat.EAN_8, "12345670"), null));
  }

  @Test
  public void anUnexpectedExceptionIsAnErrorNotAMiss() {
    assertEquals(L1Runner.Status.ERROR,
        L1Runner.classify(sample("QR_CODE", "hello"), null, new IllegalStateException("boom")));
  }
}
