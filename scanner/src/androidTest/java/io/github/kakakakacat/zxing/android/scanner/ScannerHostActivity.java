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

package io.github.kakakakacat.zxing.android.scanner;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;

/**
 * A host that embeds the scanner rather than being one.
 *
 * <p>The scanner is half the window's width and half its height, offset from the top left
 * by a quarter of each — the shape every assumption about "the scanner fills the screen"
 * breaks on. A standalone scanner application never produces it, which is why the scan
 * rectangle was computed from the display's size for as long as it was.
 */
public final class ScannerHostActivity extends Activity {

  private ScannerView scannerView;
  private FrameLayout root;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    root = new FrameLayout(this);
    scannerView = new ScannerView(this);
    root.addView(scannerView, new FrameLayout.LayoutParams(0, 0, Gravity.TOP | Gravity.START));
    setContentView(root);
    root.post(this::layoutScannerAtHalfSize);
  }

  private void layoutScannerAtHalfSize() {
    FrameLayout.LayoutParams params =
        (FrameLayout.LayoutParams) scannerView.getLayoutParams();
    params.width = root.getWidth() / 2;
    params.height = root.getHeight() / 2;
    params.leftMargin = root.getWidth() / 4;
    params.topMargin = root.getHeight() / 4;
    scannerView.setLayoutParams(params);
  }

  /**
   * What a host is supposed to do, and what this activity did not do at first.
   *
   * <p>Without it the camera stayed open past the activity, and the next test could not
   * open one. That is the contract {@link ScannerView} documents rather than a quirk of
   * the test: the view does not observe a lifecycle, the host drives it.
   */
  @Override
  protected void onPause() {
    scannerView.pause();
    super.onPause();
  }

  public ScannerView getScannerView() {
    return scannerView;
  }

  public FrameLayout getRoot() {
    return root;
  }
}
