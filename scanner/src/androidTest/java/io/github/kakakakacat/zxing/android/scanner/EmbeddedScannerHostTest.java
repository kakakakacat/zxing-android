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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import java.io.InputStream;
import java.io.IOException;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.kakakakacat.zxing.android.camera.CameraCapabilities;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The scanner as something a host embeds, rather than as the whole screen.
 *
 * <p>Two assumptions held here for as long as the only caller was a full-screen scanning
 * app. The scan rectangle was computed from {@code WindowManager}'s display size and drawn
 * by {@code ViewfinderView} in the view's own coordinates, which is the same rectangle only
 * when the two are the same size. And the CameraX backend called
 * {@code ProcessCameraProvider.unbindAll()}, which takes down every use case in the
 * process — a host's own preview or capture included.
 */
@RunWith(AndroidJUnit4.class)
public final class EmbeddedScannerHostTest {

  private static final long TIMEOUT_SECONDS = 15;

  @Before
  public void grantCameraAndRequireOne() throws IOException {
    String packageName = ApplicationProvider.getApplicationContext().getPackageName();
    // UiAutomation.grantRuntimePermission is newer than our API 23 minimum. The shell
    // permission command works on every supported API; drain it before opening a camera.
    ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
        .getUiAutomation().executeShellCommand("pm grant " + packageName + " android.permission.CAMERA");
    try (InputStream output = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
      byte[] buffer = new byte[256];
      while (output.read(buffer) != -1) { /* wait for pm to complete */ }
    }
    assertEquals("camera permission was not granted", android.content.pm.PackageManager.PERMISSION_GRANTED,
        ApplicationProvider.getApplicationContext().getPackageManager().checkPermission(
            android.Manifest.permission.CAMERA, packageName));
    Assume.assumeTrue("no camera on this device",
                      ApplicationProvider.getApplicationContext().getPackageManager()
                          .hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY));
    requireAnAwakeScreen();
  }

  /**
   * Fails immediately, and says why, when the device has gone to sleep.
   *
   * A SurfaceView gets no surface while the screen is off, so the scanner opens nothing and
   * reports nothing, and every test in this class fails fifteen seconds later with "the
   * camera neither opened nor reported an error". That message describes the symptom of a
   * product defect, so a device that merely dozed off between runs reads as a regression --
   * it did once here, across all six tests including the two Camera1 ones that touch none
   * of this. Said plainly it costs a second instead of ninety, and names the fix.
   *
   * This asserts rather than assumes. A screen that is off is a fixable state of the
   * harness, not a property of the device like having no camera, and skipping on it would
   * let a whole run go quietly green having scanned nothing.
   */
  private static void requireAnAwakeScreen() {
    PowerManager power = (PowerManager) ApplicationProvider.getApplicationContext()
        .getSystemService(Context.POWER_SERVICE);
    assertNotNull("no PowerManager", power);
    assertTrue("the device screen is off, so the scanner's SurfaceView gets no surface and "
               + "every test here would time out blaming the camera. Wake it and keep it "
               + "awake: adb shell input keyevent KEYCODE_WAKEUP && adb shell svc power "
               + "stayon usb", power.isInteractive());
  }

  /** Resumes the scanner and waits for the camera to report one way or the other. */
  private static void resumeAndWait(ActivityScenario<ScannerHostActivity> scenario,
                                    ScannerOptions options) throws Exception {
    // The host sizes the scanner in a post, and a SurfaceView with no size is given no
    // surface -- so resuming before the layout settles opens nothing and waits for ever.
    awaitLayout(scenario);
    CountDownLatch settled = new CountDownLatch(1);
    AtomicReference<ScannerError> error = new AtomicReference<>();
    AtomicReference<Exception> cause = new AtomicReference<>();
    scenario.onActivity(activity -> {
      ScannerView view = activity.getScannerView();
      view.setOptions(options);
      view.setListener(new ScannerView.Listener() {
        @Override
        public void onScanned(ScanOutcome outcome) {
          // Not what this test is about; a decode may or may not happen off a desk.
        }

        @Override
        public void onScannerError(ScannerError scannerError, Exception why) {
          error.set(scannerError);
          cause.set(why);
          settled.countDown();
        }
      });
      view.resume();
    });
    // The camera opening is asynchronous on both backends and there is no success
    // callback on ScannerView, so the framing rect appearing is what "open" looks like
    // from out here.
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
    while (System.nanoTime() < deadline) {
      if (settled.getCount() == 0) {
        break;
      }
      AtomicReference<Rect> rect = new AtomicReference<>();
      scenario.onActivity(activity -> rect.set(activity.getScannerView().getFramingRect()));
      if (rect.get() != null) {
        return;
      }
      Thread.sleep(100);
    }
    ScannerError reported = error.get();
    Exception why = cause.get();
    // The scanner's own invariants failing is not a device limitation, and must not be
    // skipped over. `bind` reports an IllegalStateException from its own `check` as
    // CAMERA_UNAVAILABLE, which is indistinguishable here from a camera another app is
    // holding -- so for a while a real defect read as a skip and the run stayed green.
    // That is exactly what happened to the separate preview/analysis sizes below: CameraX
    // negotiated 864x480 against 640x360, an exact aspect comparison refused the pair, and
    // every run of this class "skipped" on a device whose camera five other tests opened.
    if (why instanceof IllegalStateException) {
      throw new AssertionError(
          "the scanner failed its own invariant rather than the camera being unavailable: "
          + why, why);
    }
    // A device that cannot open a camera skips. A device that reports nothing at all and
    // never produces a rectangle fails: that is the shape a vacuous pass has, and three
    // of these tests would otherwise assert their way through a scanner that never ran.
    Assume.assumeTrue("the camera could not be opened on this device: " + reported,
                      reported == null);
    throw new AssertionError("the camera neither opened nor reported an error within "
                             + TIMEOUT_SECONDS + "s");
  }

  private static void awaitLayout(ActivityScenario<ScannerHostActivity> scenario)
      throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
    while (System.nanoTime() < deadline) {
      AtomicReference<int[]> size = new AtomicReference<>();
      scenario.onActivity(activity -> size.set(new int[] {
          activity.getScannerView().getWidth(), activity.getScannerView().getHeight()}));
      if (size.get()[0] > 0 && size.get()[1] > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("the embedded scanner never got a size");
  }

  @Test
  public void aCamera1HostCanPauseAndReopenWithBufferedDirectScheduling() throws Exception {
    try (ActivityScenario<ScannerHostActivity> scenario = ActivityScenario.launch(ScannerHostActivity.class)) {
      ScannerOptions options = new ScannerOptions.Builder()
          .setCamera1BufferPool(true).setDirectFrameScheduling(true).build();
      resumeAndWait(scenario, options);
      scenario.onActivity(activity -> activity.getScannerView().pause());
      resumeAndWait(scenario, options);
      scenario.onActivity(activity -> assertNotNull(activity.getScannerView().getFramingRect()));
    }
  }

  @Test
  public void aCameraXHostCanRequestSeparatePreviewAndAnalysisSizes() throws Exception {
    try (ActivityScenario<ScannerHostActivity> scenario =
             ActivityScenario.launch(ScannerHostActivity.class)) {
      resumeAndWait(scenario, new ScannerOptions.Builder()
          .setCameraBackend(ScannerOptions.CameraBackend.CAMERAX)
          .setPreviewSize(1280, 720).setAnalysisSize(640, 360)
          .setProgressiveRoi(true).setJavaBufferReuse(true)
          .setDecoderBackend(ScannerOptions.DecoderBackend.JAVA).build());
      scenario.onActivity(activity -> {
        ScannerView view = activity.getScannerView();
        Rect rect = view.getFramingRect();
        assertNotNull(rect);
        assertTrue(rect.left >= 0 && rect.top >= 0);
        assertTrue(rect.right <= view.getWidth() && rect.bottom <= view.getHeight());
      });
    }
  }

  @Test
  public void theScanRectangleIsInTheViewAndNotOnTheDisplay() throws Exception {
    try (ActivityScenario<ScannerHostActivity> scenario =
             ActivityScenario.launch(ScannerHostActivity.class)) {
      resumeAndWait(scenario, ScannerOptions.defaults());

      AtomicReference<Rect> rect = new AtomicReference<>();
      AtomicReference<int[]> size = new AtomicReference<>();
      scenario.onActivity(activity -> {
        ScannerView view = activity.getScannerView();
        rect.set(view.getFramingRect());
        size.set(new int[] {view.getWidth(), view.getHeight(),
                            activity.getRoot().getWidth(), activity.getRoot().getHeight()});
      });

      Rect framing = rect.get();
      int[] bounds = size.get();
      assertNotNull("no scan rectangle after the camera opened", framing);
      assertTrue("the view should be half the host, got " + bounds[0] + "x" + bounds[1]
                 + " of " + bounds[2] + "x" + bounds[3],
                 bounds[0] > 0 && bounds[0] < bounds[2]);

      // ViewfinderView draws this rectangle in its own coordinates, so a rectangle
      // measured against the display lands partly or wholly outside the view.
      assertTrue("the scan rectangle starts outside the view: " + framing,
                 framing.left >= 0 && framing.top >= 0);
      assertTrue("the scan rectangle runs past the view: " + framing
                 + " in " + bounds[0] + "x" + bounds[1],
                 framing.right <= bounds[0] && framing.bottom <= bounds[1]);

      // And it is centred on the view, within the rounding of an integer halving.
      int horizontalSlack = Math.abs(framing.left - (bounds[0] - framing.width()) / 2);
      int verticalSlack = Math.abs(framing.top - (bounds[1] - framing.height()) / 2);
      assertTrue("not centred horizontally: " + framing, horizontalSlack <= 1);
      assertTrue("not centred vertically: " + framing, verticalSlack <= 1);
    }
  }

  @Test
  public void theCameraXBackendLeavesAHostsOwnUseCaseBound() throws Exception {
    ProcessCameraProvider provider = ProcessCameraProvider.getInstance(
        ApplicationProvider.getApplicationContext()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    ImageAnalysis hostUseCase = new ImageAnalysis.Builder().build();
    HostLifecycleOwner owner = new HostLifecycleOwner();

    CountDownLatch bound = new CountDownLatch(1);
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      owner.start();
      provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, hostUseCase);
      bound.countDown();
    });
    assertTrue(bound.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    Assume.assumeTrue("the host use case could not be bound on this device",
                      provider.isBound(hostUseCase));

    try (ActivityScenario<ScannerHostActivity> scenario =
             ActivityScenario.launch(ScannerHostActivity.class)) {
      resumeAndWait(scenario, ScannerOptions.defaults().buildUpon()
          .setCameraBackend(ScannerOptions.CameraBackend.CAMERAX)
          .build());
      assertTrue("the scanner unbound the host's use case while opening",
                 provider.isBound(hostUseCase));
      scenario.onActivity(activity -> activity.getScannerView().pause());
      assertTrue("the scanner unbound the host's use case while closing",
                 provider.isBound(hostUseCase));
    } finally {
      InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
        provider.unbind(hostUseCase);
        owner.stop();
      });
    }
  }

  @Test
  public void theBackendSaysWhichOptionsItWillIgnore() throws Exception {
    try (ActivityScenario<ScannerHostActivity> scenario =
             ActivityScenario.launch(ScannerHostActivity.class)) {
      resumeAndWait(scenario, ScannerOptions.defaults().buildUpon()
          .setCameraBackend(ScannerOptions.CameraBackend.CAMERAX)
          .setCameraId(1)
          .build());

      AtomicReference<CameraCapabilities> caps = new AtomicReference<>();
      scenario.onActivity(
          activity -> caps.set(activity.getScannerView().getCameraCapabilities()));
      CameraCapabilities capabilities = caps.get();
      assertNotNull("no capabilities after the camera opened", capabilities);
      assertEquals("camerax", capabilities.getBackendName());
      // The CameraX path binds the back camera whatever cameraId says, and leaves focus,
      // metering, exposure and scene mode to CameraX's own 3A. Saying so is the point:
      // setting one of those used to succeed and do nothing.
      assertFalse(capabilities.getCameraIdHonoured());
      assertFalse(capabilities.getAutoFocusHonoured());
      assertTrue(capabilities.getFramingRectHonoured());
      assertTrue("the ignored options should be named: " + capabilities,
                 capabilities.unhonouredOptions().contains("cameraId"));
    }
  }

  @Test
  public void theCamera1BackendHonoursEverythingTheOptionsOffer() throws Exception {
    try (ActivityScenario<ScannerHostActivity> scenario =
             ActivityScenario.launch(ScannerHostActivity.class)) {
      resumeAndWait(scenario, ScannerOptions.defaults());

      AtomicReference<CameraCapabilities> caps = new AtomicReference<>();
      scenario.onActivity(
          activity -> caps.set(activity.getScannerView().getCameraCapabilities()));
      CameraCapabilities capabilities = caps.get();
      assertNotNull(capabilities);
      assertEquals("camera1", capabilities.getBackendName());
      assertTrue("the Camera1 path applies every option it was given: " + capabilities,
                 capabilities.unhonouredOptions().isEmpty());
    }
  }

  /** A lifecycle a host would own, standing in for an activity with its own camera use. */
  private static final class HostLifecycleOwner implements LifecycleOwner {
    private final LifecycleRegistry registry = new LifecycleRegistry(this);

    @Override
    public Lifecycle getLifecycle() {
      return registry;
    }

    void start() {
      registry.setCurrentState(Lifecycle.State.RESUMED);
    }

    void stop() {
      registry.setCurrentState(Lifecycle.State.DESTROYED);
    }
  }
}
