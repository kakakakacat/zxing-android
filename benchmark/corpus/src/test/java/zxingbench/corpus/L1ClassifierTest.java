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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * How a decode is scored against its expectation.
 *
 * <p>These rules decide every number in an L1 result file, so they are worth testing
 * without a 1168-sample corpus run behind them. One of them was wrong: the expectation
 * had its trailing newline stripped and the decoded text did not, which scored nine
 * samples INCORRECT whose payload genuinely ends in a newline.
 */
public final class L1ClassifierTest {

  private static Manifest.Sample sample(String format, String text) {
    Manifest.Sample s = new Manifest.Sample();
    s.sampleId = "test/1";
    s.expectedFormat = format;
    s.expectedText = text;
    return s;
  }

  private static L1Status classify(Manifest.Sample sample, String format, String text) {
    return L1Classifier.classify(sample, format, text, false);
  }

  @Test
  public void matchingFormatAndTextIsCorrect() {
    assertEquals(L1Status.CORRECT,
        classify(sample("QR_CODE", "hello"), "QR_CODE", "hello"));
  }

  @Test
  public void aTrailingNewlineIsPartOfThePayloadOnBothSides() {
    assertEquals(L1Status.CORRECT,
        classify(sample("QR_CODE", "hello\n"), "QR_CODE", "hello\n"));
  }

  @Test
  public void aTrailingNewlineIsNotNormalisedAwayOnEitherSide() {
    // The decoded text really is one character shorter than the expectation. That is a
    // mismatch, not something to tidy up -- the sidecar carries the payload byte for byte.
    assertEquals(L1Status.INCORRECT,
        classify(sample("QR_CODE", "hello\n"), "QR_CODE", "hello"));
    assertEquals(L1Status.INCORRECT,
        classify(sample("QR_CODE", "hello"), "QR_CODE", "hello\n"));
  }

  @Test
  public void aCarriageReturnPairIsNotNormalisedEither() {
    assertEquals(L1Status.CORRECT,
        classify(sample("QR_CODE", "a\r\nb\r\n"), "QR_CODE", "a\r\nb\r\n"));
  }

  @Test
  public void aDifferentFormatIsIncorrectHoweverTheTextCompares() {
    assertEquals(L1Status.INCORRECT,
        classify(sample("EAN_13", "9780441014989"), "UPC_E", "9780441014989"));
  }

  @Test
  public void aMissOnAPositiveSampleIsNotFound() {
    assertEquals(L1Status.NOT_FOUND, classify(sample("QR_CODE", "hello"), null, null));
  }

  @Test
  public void aMissOnANegativeSampleIsTheCorrectAnswer() {
    assertEquals(L1Status.CORRECT_NO_RESULT, classify(sample(null, null), null, null));
  }

  @Test
  public void aReadOnANegativeSampleIsAFalsePositive() {
    assertEquals(L1Status.FALSE_POSITIVE,
        classify(sample(null, null), "EAN_8", "12345670"));
  }

  @Test
  public void aFailureThatIsNotAMissIsAnError() {
    // The distinction is the point: a backend that throws on every frame would otherwise
    // be scored as a decoder that simply finds nothing, and the corpus would come back
    // looking merely unlucky rather than broken.
    assertEquals(L1Status.ERROR,
        L1Classifier.classify(sample("QR_CODE", "hello"), null, null, true));
    assertEquals(L1Status.ERROR,
        L1Classifier.classify(sample(null, null), null, null, true));
  }
}
