/*
 * Copyright (C) 2008 ZXing authors
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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import io.github.kakakakacat.zxing.BarcodeFormat;
import io.github.kakakakacat.zxing.PlanarYUVLuminanceSource;
import io.github.kakakakacat.zxing.ResultPoint;
import io.github.kakakakacat.zxing.ResultPointCallback;
import io.github.kakakakacat.zxing.android.AmbientLightManager;
import io.github.kakakakacat.zxing.android.ViewfinderView;
import io.github.kakakakacat.zxing.android.camera.CameraManager;

import java.io.IOException;
import java.util.Collection;

/**
 * A drop-in scanning surface: add it to a layout, give it a {@link Listener}, call
 * {@link #resume()} and {@link #pause()} from the host's lifecycle, and it reports
 * decoded barcodes.
 *
 * <p>It owns the camera, the preview surface, the viewfinder overlay and the decode
 * thread. It deliberately does <em>not</em> request the CAMERA permission: the host owns
 * that conversation with the user. If the permission is missing, {@link #resume()}
 * reports {@link ScannerError#PERMISSION_DENIED} instead of opening the camera.
 *
 * <p>All callbacks arrive on the main thread. Results carry the id of the scanning
 * session that produced them, so a decode that finishes after {@link #pause()} is
 * dropped rather than delivered into a torn-down screen.
 */
public final class ScannerView extends FrameLayout implements SurfaceHolder.Callback {

  static final int MSG_DECODE_SUCCEEDED = 10;
  static final int MSG_DECODE_FAILED = 11;

  private static final String TAG = "ScannerView";

  /** Receives scanner events. Always called on the main thread. */
  public interface Listener {
    /**
     * A barcode was decoded. Scanning is paused until {@link #resumeScanning()} is
     * called, so the host can show the result without the next frame overwriting it.
     */
    void onScanned(ScanOutcome outcome);

    /** The scanner could not start, or the camera went away. Scanning is stopped. */
    void onScannerError(ScannerError error, Exception cause);
  }

  private final SurfaceView surfaceView;
  private final ViewfinderView viewfinderView;
  private final Handler mainHandler;

  private ScannerOptions options = ScannerOptions.defaults();
  private Collection<BarcodeFormat> formats;
  private Listener listener;
  private boolean thumbnailsEnabled = true;

  private CameraManager cameraManager;
  private AmbientLightManager ambientLightManager;
  private DecoderThread decoderThread;
  private boolean hasSurface;
  private boolean resumed;
  private boolean scanning;
  /** Bumped on every start, so replies from an older session can be identified. */
  private int sessionId;

  public ScannerView(Context context) {
    this(context, null);
  }

  public ScannerView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ScannerView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    surfaceView = new SurfaceView(context);
    addView(surfaceView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                          ViewGroup.LayoutParams.MATCH_PARENT));
    viewfinderView = new ViewfinderView(context);
    addView(viewfinderView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                             ViewGroup.LayoutParams.MATCH_PARENT));

    mainHandler = new Handler(Looper.getMainLooper()) {
      @Override
      public void handleMessage(Message msg) {
        ScannerView.this.handleDecodeReply(msg);
      }
    };
  }

  /** Must be set before {@link #resume()}; changing it later takes effect on the next resume. */
  public void setOptions(ScannerOptions options) {
    this.options = options == null ? ScannerOptions.defaults() : options;
  }

  /**
   * Formats to decode, or {@code null} for every format the reader supports. Kept
   * separate from {@link ScannerOptions} because hosts commonly derive it per scan
   * request (for example from an Intent) while the rest of the options are fixed.
   */
  public void setFormats(Collection<BarcodeFormat> formats) {
    this.formats = formats;
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  /** Thumbnails cost an allocation and a pixel copy per successful scan. Default on. */
  public void setThumbnailsEnabled(boolean enabled) {
    this.thumbnailsEnabled = enabled;
  }

  public ViewfinderView getViewfinderView() {
    return viewfinderView;
  }

  /** Null until the camera is open. */
  public CameraManager getCameraManager() {
    return cameraManager;
  }

  public boolean isScanning() {
    return scanning;
  }

  /** Call from the host's onResume. Opens the camera and starts decoding. */
  public void resume() {
    if (resumed) {
      return;
    }
    resumed = true;
    if (!hasCameraPermission()) {
      resumed = false;
      reportError(ScannerError.PERMISSION_DENIED, null);
      return;
    }
    cameraManager = new CameraManager(getContext().getApplicationContext(), options);
    ambientLightManager = new AmbientLightManager(getContext(), options.getFrontLightMode());
    viewfinderView.setCameraManager(cameraManager);
    SurfaceHolder holder = surfaceView.getHolder();
    if (hasSurface) {
      openCamera(holder);
    } else {
      holder.addCallback(this);
    }
  }

  /** Call from the host's onPause. Releases the camera and stops the decode thread. */
  public void pause() {
    if (!resumed) {
      return;
    }
    resumed = false;
    stopScanning();
    if (ambientLightManager != null) {
      ambientLightManager.stop();
      ambientLightManager = null;
    }
    if (cameraManager != null) {
      cameraManager.closeDriver();
      cameraManager = null;
    }
    if (!hasSurface) {
      surfaceView.getHolder().removeCallback(this);
    }
  }

  /** After a result has been shown, ask for the next frame. */
  public void resumeScanning() {
    if (!resumed || cameraManager == null || decoderThread == null || scanning) {
      return;
    }
    scanning = true;
    viewfinderView.drawViewfinder();
    requestFrame();
  }

  /** Turns the torch on or off. No-op if the camera is not open. */
  public void setTorch(boolean on) {
    if (cameraManager != null) {
      cameraManager.setTorch(on);
    }
  }

  private boolean hasCameraPermission() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        getContext().checkSelfPermission(Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED;
  }

  private void openCamera(SurfaceHolder holder) {
    CameraManager manager = cameraManager;
    if (manager == null || manager.isOpen()) {
      return;
    }
    try {
      manager.openDriver(holder);
    } catch (IOException ioe) {
      // openDriver reports "no camera object from the driver" and a failed
      // setPreviewDisplay through the same exception type; isOpen() tells them apart.
      Log.w(TAG, "Could not open the camera or attach the preview", ioe);
      reportError(manager.isOpen() ? ScannerError.PREVIEW_FAILED
                                   : cameraUnavailableOrMissing(manager), ioe);
      return;
    } catch (RuntimeException re) {
      // Camera.open() throws RuntimeException for "no camera", "camera in use" and
      // "service died" alike; distinguish what we can rather than blaming the device.
      Log.w(TAG, "Could not open the camera", re);
      reportError(cameraUnavailableOrMissing(manager), re);
      return;
    }

    sessionId++;
    final int thisSession = sessionId;
    DecoderThread thread = new DecoderThread(
        new DecoderThread.FrameSource() {
          @Override
          public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
            CameraManager cm = cameraManager;
            return cm == null ? null : cm.buildLuminanceSource(data, width, height);
          }
        },
        mainHandler, options, formats,
        new ResultPointCallback() {
          @Override
          public void foundPossibleResultPoint(ResultPoint point) {
            viewfinderView.addPossibleResultPoint(point);
          }
        },
        thumbnailsEnabled, thisSession);
    thread.start();
    decoderThread = thread;

    try {
      manager.startPreview();
    } catch (RuntimeException re) {
      Log.w(TAG, "Could not start the preview", re);
      stopScanning();
      reportError(ScannerError.PREVIEW_FAILED, re);
      return;
    }
    if (ambientLightManager != null) {
      ambientLightManager.start(manager);
    }
    scanning = true;
    viewfinderView.drawViewfinder();
    requestFrame();
  }

  private static ScannerError cameraUnavailableOrMissing(CameraManager manager) {
    return manager.hasAnyCamera() ? ScannerError.CAMERA_UNAVAILABLE : ScannerError.NO_CAMERA;
  }

  private void requestFrame() {
    CameraManager manager = cameraManager;
    DecoderThread thread = decoderThread;
    if (manager == null || thread == null) {
      return;
    }
    Handler decodeHandler = thread.getHandler();
    if (decodeHandler != null) {
      manager.requestPreviewFrame(decodeHandler, DecoderThread.MSG_DECODE);
    }
  }

  private void stopScanning() {
    scanning = false;
    // Invalidate the session first: anything still in flight is now stale.
    sessionId++;
    DecoderThread thread = decoderThread;
    decoderThread = null;
    if (cameraManager != null) {
      cameraManager.stopPreview();
    }
    if (thread != null) {
      Handler decodeHandler = thread.getHandler();
      if (decodeHandler != null) {
        decodeHandler.obtainMessage(DecoderThread.MSG_QUIT).sendToTarget();
      }
      try {
        thread.join(500L);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
    mainHandler.removeMessages(MSG_DECODE_SUCCEEDED);
    mainHandler.removeMessages(MSG_DECODE_FAILED);
  }

  private void handleDecodeReply(Message msg) {
    if (msg.arg1 != sessionId) {
      // A reply from a session that has already been torn down. Dropping it is the
      // whole point of the session id: upstream could deliver a decode from the previous
      // camera session into a freshly resumed screen.
      return;
    }
    if (msg.what == MSG_DECODE_FAILED) {
      if (scanning) {
        requestFrame();
      }
      return;
    }
    if (msg.what == MSG_DECODE_SUCCEEDED) {
      scanning = false;
      Listener l = listener;
      if (l != null) {
        l.onScanned((ScanOutcome) msg.obj);
      }
    }
  }

  private void reportError(ScannerError error, Exception cause) {
    Listener l = listener;
    if (l != null) {
      l.onScannerError(error, cause);
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    if (!hasSurface) {
      hasSurface = true;
      if (resumed) {
        openCamera(holder);
      }
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    // Preview size is negotiated when the camera opens; nothing to do here.
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    hasSurface = false;
    // The camera cannot keep drawing into a surface that is going away.
    if (resumed) {
      stopScanning();
      if (cameraManager != null) {
        cameraManager.closeDriver();
      }
    }
  }

  /** The scan rectangle in view coordinates, or null before the camera is configured. */
  public Rect getFramingRect() {
    CameraManager manager = cameraManager;
    return manager == null ? null : manager.getFramingRect();
  }
}
