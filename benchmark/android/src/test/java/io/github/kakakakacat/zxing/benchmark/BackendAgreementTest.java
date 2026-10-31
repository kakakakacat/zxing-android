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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.Result;

import org.junit.Test;

/**
 * The one difference between the backends that is allowed, and everything that is not.
 *
 * <p>This rule decides whether a backend A/B is comparing two speeds or two behaviours,
 * so the exception it grants is kept as narrow as it can be made.
 */
public final class BackendAgreementTest {

  private static Result result(BarcodeFormat format, String text) {
    return new Result(text, null, null, format);
  }

  @Test
  public void identicalResultsAgree() {
    assertNull(BackendAgreement.disagreement(
        result(BarcodeFormat.QR_CODE, "hello"), result(BarcodeFormat.QR_CODE, "hello")));
  }

  @Test
  public void twoMissesAgree() {
    assertNull(BackendAgreement.disagreement(null, null));
  }

  @Test
  public void aMissAgainstAHitIsADisagreement() {
    assertNotNull(BackendAgreement.disagreement(
        null, result(BarcodeFormat.QR_CODE, "hello")));
    assertNotNull(BackendAgreement.disagreement(
        result(BarcodeFormat.QR_CODE, "hello"), null));
  }

  @Test
  public void differentTextIsADisagreement() {
    assertNotNull(BackendAgreement.disagreement(
        result(BarcodeFormat.QR_CODE, "hello"), result(BarcodeFormat.QR_CODE, "goodbye")));
  }

  @Test
  public void aUpcASymbolReportedAsEan13WithALeadingZeroAgrees() {
    // What zxing-cpp actually returns for the 1D replay frame, citing ISO/IEC 15420.
    assertNull(BackendAgreement.disagreement(
        result(BarcodeFormat.UPC_A, "012345678905"),
        result(BarcodeFormat.EAN_13, "0012345678905")));
  }

  @Test
  public void theSameSymbolStillLabelledUpcAAlsoAgrees() {
    // With EAN-13 excluded from the search, zxing-cpp relabels it UPC_A but keeps the
    // 13-digit text.
    assertNull(BackendAgreement.disagreement(
        result(BarcodeFormat.UPC_A, "012345678905"),
        result(BarcodeFormat.UPC_A, "0012345678905")));
  }

  @Test
  public void theExceptionDoesNotRunInTheOtherDirection() {
    // A 13-digit reference against a 12-digit candidate is not the documented rendering
    // difference, and must not be waved through by symmetry.
    assertNotNull(BackendAgreement.disagreement(
        result(BarcodeFormat.EAN_13, "0012345678905"),
        result(BarcodeFormat.UPC_A, "012345678905")));
  }

  @Test
  public void aDifferentDigitIsNotARenderingDifference() {
    assertNotNull(BackendAgreement.disagreement(
        result(BarcodeFormat.UPC_A, "012345678905"),
        result(BarcodeFormat.EAN_13, "0012345678906")));
  }

  @Test
  public void aLeadingZeroOnSomethingThatIsNotUpcAIsNotExcused() {
    assertNotNull(BackendAgreement.disagreement(
        result(BarcodeFormat.CODE_128, "12345"),
        result(BarcodeFormat.CODE_128, "012345")));
  }

  @Test
  public void theExceptionDoesNotStretchToAnotherFormat() {
    assertNotNull(BackendAgreement.disagreement(
        result(BarcodeFormat.UPC_A, "012345678905"),
        result(BarcodeFormat.EAN_8, "0012345678905")));
  }

  @Test
  public void aDisagreementSaysWhatBothSidesSaw() {
    String why = BackendAgreement.disagreement(
        result(BarcodeFormat.QR_CODE, "hello"), result(BarcodeFormat.QR_CODE, "goodbye"));
    assertTrue(why, why.contains("hello") && why.contains("goodbye"));
  }
}
