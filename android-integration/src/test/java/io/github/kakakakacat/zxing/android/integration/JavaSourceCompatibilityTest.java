/*
 * Copyright 2026 zxing-android maintenance fork
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

package io.github.kakakakacat.zxing.android.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * This module's classes are Kotlin; its consumers are Java. Kotlin can compile a class whose
 * Java-facing shape is subtly different from the one it replaced -- a method that is no longer
 * overridable, a {@code Map<String,?>} that became {@code Map<String,Object>}, a constant that
 * stopped being a compile-time constant -- and nothing in a Kotlin-only build would notice.
 *
 * <p>So this test is written in Java deliberately, and most of it is compiled rather than run:
 * {@link #surfaceCompiles} is never invoked, and exists so that javac has to accept every public
 * member of the integration API exactly as a consumer would write it. The assertions that do run
 * cover the parts that have values rather than behaviour.
 *
 * <p>Nothing here touches a real {@link Activity}: these are host JVM tests against the Android
 * stub jar, where every framework method throws.
 */
public final class JavaSourceCompatibilityTest {

  @Test
  public void constantsKeepTheirValues() {
    assertEquals(0x0000c0de, IntentIntegrator.REQUEST_CODE);
    assertEquals("Install Barcode Scanner?", IntentIntegrator.DEFAULT_TITLE);
    assertEquals("This application requires Barcode Scanner. Would you like to install it?",
                 IntentIntegrator.DEFAULT_MESSAGE);
    assertEquals("Yes", IntentIntegrator.DEFAULT_YES);
    assertEquals("No", IntentIntegrator.DEFAULT_NO);
  }

  /**
   * The four public constants have to stay compile-time constants: a {@code switch} over a scan
   * request code is the documented way to use {@link IntentIntegrator#REQUEST_CODE}, and a
   * {@code case} label only accepts a constant expression. A Kotlin {@code val} instead of a
   * {@code const val} would compile everywhere except here.
   */
  @Test
  public void requestCodeIsUsableAsACaseLabel() {
    int handled;
    switch (IntentIntegrator.REQUEST_CODE) {
      case IntentIntegrator.REQUEST_CODE:
        handled = 1;
        break;
      default:
        handled = 0;
    }
    assertEquals(1, handled);
  }

  @Test
  public void formatCollectionsAreUnchanged() {
    assertTrue(IntentIntegrator.PRODUCT_CODE_TYPES.containsAll(
        Arrays.asList("UPC_A", "UPC_E", "EAN_8", "EAN_13", "RSS_14")));
    assertEquals(5, IntentIntegrator.PRODUCT_CODE_TYPES.size());
    assertEquals(10, IntentIntegrator.ONE_D_CODE_TYPES.size());
    assertEquals(java.util.Collections.singleton("QR_CODE"), IntentIntegrator.QR_CODE_TYPES);
    assertEquals(java.util.Collections.singleton("DATA_MATRIX"),
                 IntentIntegrator.DATA_MATRIX_TYPES);
    // "All types" is expressed as a null format list, and callers pass it straight through.
    assertNull(IntentIntegrator.ALL_CODE_TYPES);
  }

  @Test
  public void targetApplicationListsAreUnchanged() {
    assertEquals(java.util.Collections.singletonList("io.github.kakakakacat.zxing.android"),
                 IntentIntegrator.TARGET_MAINTENANCE_FORK_ONLY);
    assertEquals(java.util.Collections.singletonList("com.google.zxing.client.android"),
                 IntentIntegrator.TARGET_BARCODE_SCANNER_ONLY);
    assertEquals(Arrays.asList("io.github.kakakakacat.zxing.android",
                               "com.srowen.bs.android",
                               "com.srowen.bs.android.simple",
                               "com.google.zxing.client.android"),
                 IntentIntegrator.TARGET_ALL_KNOWN);
  }

  @Test
  public void parseActivityResultIgnoresAForeignRequestCode() {
    assertNull(IntentIntegrator.parseActivityResult(
        IntentIntegrator.REQUEST_CODE + 1, Activity.RESULT_OK, new Intent()));
  }

  /**
   * Never called. Its job is to fail compilation if the Java-facing signature of anything here
   * changes; see the class comment.
   */
  @SuppressWarnings({"unused", "deprecation"})
  private static void surfaceCompiles(Activity activity, android.app.Fragment fragment) {
    IntentIntegrator fromActivity = new IntentIntegrator(activity);
    IntentIntegrator fromFragment = new IntentIntegrator(fragment);

    String title = fromActivity.getTitle();
    fromActivity.setTitle(title);
    fromActivity.setTitleByID(0);
    String message = fromActivity.getMessage();
    fromActivity.setMessage(message);
    fromActivity.setMessageByID(0);
    String yes = fromActivity.getButtonYes();
    fromActivity.setButtonYes(yes);
    fromActivity.setButtonYesByID(0);
    String no = fromActivity.getButtonNo();
    fromActivity.setButtonNo(no);
    fromActivity.setButtonNoByID(0);

    Collection<String> targets = fromActivity.getTargetApplications();
    fromActivity.setTargetApplications(IntentIntegrator.TARGET_ALL_KNOWN);
    fromActivity.setSingleTargetApplication("com.example");
    fromActivity.addExtra("KEY", 1);
    // Declared as a wildcard, exactly as before: widening it to Map<String,Object> would let
    // callers put into a map the integrator owns.
    Map<String, ?> extras = fromActivity.getMoreExtras();

    AlertDialog a = fromActivity.initiateScan();
    AlertDialog b = fromActivity.initiateScan(0);
    AlertDialog c = fromActivity.initiateScan(IntentIntegrator.QR_CODE_TYPES);
    AlertDialog d = fromActivity.initiateScan(IntentIntegrator.ALL_CODE_TYPES, -1);
    AlertDialog e = fromFragment.shareText("text");
    AlertDialog f = fromFragment.shareText("text", "TEXT_TYPE");

    IntentResult result =
        IntentIntegrator.parseActivityResult(IntentIntegrator.REQUEST_CODE, -1, new Intent());
    String contents = result.getContents();
    String formatName = result.getFormatName();
    byte[] rawBytes = result.getRawBytes();
    Integer orientation = result.getOrientation();
    String ecLevel = result.getErrorCorrectionLevel();
    String asText = result.toString();
  }

  /**
   * Also never called. The class is subclassable and {@code startActivityForResult} is the
   * documented extension point; everything the Java class left non-final has to stay
   * overridable, which Kotlin does not do by default.
   */
  @SuppressWarnings("unused")
  private static final class StillSubclassable extends IntentIntegrator {
    StillSubclassable(Activity activity) {
      super(activity);
    }

    @Override
    protected void startActivityForResult(Intent intent, int code) {
      // no-op
    }

    @Override
    public void setTitle(String title) {
      super.setTitle(title);
    }

    @Override
    public void setTitleByID(int titleID) {
      super.setTitleByID(titleID);
    }

    @Override
    public Collection<String> getTargetApplications() {
      return super.getTargetApplications();
    }

    @Override
    public void setSingleTargetApplication(String targetApplication) {
      super.setSingleTargetApplication(targetApplication);
    }

    @Override
    public Map<String, ?> getMoreExtras() {
      return super.getMoreExtras();
    }
  }

  /** Unused reference so the import of {@link List} is not flagged. */
  @SuppressWarnings("unused")
  private static final List<String> KNOWN = IntentIntegrator.TARGET_ALL_KNOWN;
}
