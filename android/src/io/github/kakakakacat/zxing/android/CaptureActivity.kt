/*
 * Copyright (C) 2008 ZXing authors
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

package io.github.kakakakacat.zxing.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.provider.Browser
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import io.github.kakakakacat.zxing.BarcodeFormat
import io.github.kakakakacat.zxing.DecodeHintType
import io.github.kakakakacat.zxing.Result
import io.github.kakakakacat.zxing.ResultMetadataType
import io.github.kakakakacat.zxing.ResultPoint
import io.github.kakakakacat.zxing.android.camera.FrontLightMode
import io.github.kakakakacat.zxing.android.clipboard.ClipboardInterface
import io.github.kakakakacat.zxing.android.history.HistoryActivity
import io.github.kakakakacat.zxing.android.history.HistoryManager
import io.github.kakakakacat.zxing.android.result.ResultButtonListener
import io.github.kakakakacat.zxing.android.result.ResultHandler
import io.github.kakakakacat.zxing.android.result.ResultHandlerFactory
import io.github.kakakakacat.zxing.android.result.supplement.SupplementalInfoRetriever
import io.github.kakakakacat.zxing.android.scanner.ScanOutcome
import io.github.kakakakacat.zxing.android.scanner.ScannerError
import io.github.kakakakacat.zxing.android.scanner.ScannerOptions
import io.github.kakakakacat.zxing.android.scanner.ScannerView
import io.github.kakakakacat.zxing.android.share.ShareActivity
import java.text.DateFormat
import java.util.EnumSet

/**
 * This activity opens the camera and does the actual scanning on a background thread. It draws a
 * viewfinder to help the user place the barcode correctly, shows feedback as the image processing
 * is happening, and then overlays the results when a scan is successful.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
@Suppress("DEPRECATION") // android.preference.PreferenceManager, getDefaultDisplay, getColor
class CaptureActivity : Activity(), ScannerView.Listener {

  private lateinit var scannerView: ScannerView
  private lateinit var uiHandler: Handler
  private lateinit var statusView: TextView
  private lateinit var resultView: View
  private var lastResult: Result? = null
  private var copyToClipboard = false
  private var source: IntentSource = IntentSource.NONE
  private var sourceUrl: String? = null
  private var scanFromWebPageManager: ScanFromWebPageManager? = null
  private var decodeFormats: Collection<BarcodeFormat>? = null
  private var decodeHints: Map<DecodeHintType, *>? = null
  private var characterSet: String? = null
  private var historyManager: HistoryManager? = null
  private lateinit var inactivityTimer: InactivityTimer
  private lateinit var beepManager: BeepManager

  /** True between asking for CAMERA and the user answering, so we ask exactly once. */
  private var awaitingCameraPermission = false

  /** Set when the user answered "no", so onResume explains instead of asking again. */
  private var cameraPermissionDenied = false

  override fun onCreate(icicle: Bundle?) {
    super.onCreate(icicle)

    val window: Window = getWindow()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.capture)

    uiHandler = Handler(Looper.getMainLooper())
    scannerView = findViewById(R.id.scanner_view)
    scannerView.setListener(this)
    applySystemBarInsets()
    inactivityTimer = InactivityTimer(this)
    beepManager = BeepManager(this)

    PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
  }

  /**
   * Keeps the text and result panels clear of the system bars while the camera preview
   * stays edge to edge.
   *
   * Android 15 (API 35) enforces edge-to-edge for apps targeting 35+, so the status
   * hint -- laid out at bottom|center -- ended up underneath the gesture navigation bar and
   * was simply not visible. Verified on API 35 and 36 emulators: the inset frame starts
   * at y=1017 of 1080 and the text occupied 1029..1080.
   */
  private fun applySystemBarInsets() {
    val statusHint = findViewById<View>(R.id.status_view)
    val resultPanel = findViewById<View>(R.id.result_view)
    val root = findViewById<View>(android.R.id.content)
    root.setOnApplyWindowInsetsListener { _, insets ->
      val left: Int
      val top: Int
      val right: Int
      val bottom: Int
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bars = insets.getInsets(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        left = bars.left
        top = bars.top
        right = bars.right
        bottom = bars.bottom
      } else {
        left = insets.systemWindowInsetLeft
        top = insets.systemWindowInsetTop
        right = insets.systemWindowInsetRight
        bottom = insets.systemWindowInsetBottom
      }
      statusHint.setPadding(left, statusHint.paddingTop, right, bottom)
      resultPanel.setPadding(left, top, right, bottom)
      insets
    }
    root.requestApplyInsets()
  }

  override fun onResume() {
    super.onResume()

    // historyManager must be initialized here to update the history preference
    val historyManager = HistoryManager(this)
    this.historyManager = historyManager
    historyManager.trimHistory()

    // CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
    // want to open the camera driver and measure the screen size if we're going to show the help on
    // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
    // off screen.
    resultView = findViewById(R.id.result_view)
    statusView = findViewById(R.id.status_view)

    lastResult = null

    val prefs = PreferenceManager.getDefaultSharedPreferences(this)

    requestedOrientation =
        if (prefs.getBoolean(PreferencesActivity.KEY_DISABLE_AUTO_ORIENTATION, true)) {
          getCurrentOrientation()
        } else {
          ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

    resetStatusView()

    beepManager.updatePrefs()
    inactivityTimer.onResume()

    val intent = getIntent()

    copyToClipboard = prefs.getBoolean(PreferencesActivity.KEY_COPY_TO_CLIPBOARD, true) &&
        (intent == null || intent.getBooleanExtra(Intents.Scan.SAVE_HISTORY, true))

    source = IntentSource.NONE
    sourceUrl = null
    scanFromWebPageManager = null
    decodeFormats = null
    decodeHints = null
    characterSet = null
    val optionsBuilder = scannerOptionsFromPreferences(prefs)

    if (intent != null) {

      val action = intent.action
      val dataString = intent.dataString

      if (Intents.Scan.ACTION == action) {

        // Scan the formats the intent requested, and return the result to the calling activity.
        source = IntentSource.NATIVE_APP_INTENT
        decodeFormats = DecodeFormatManager.parseDecodeFormats(intent)
        decodeHints = DecodeHintManager.parseDecodeHints(intent)

        if (intent.hasExtra(Intents.Scan.WIDTH) && intent.hasExtra(Intents.Scan.HEIGHT)) {
          val width = intent.getIntExtra(Intents.Scan.WIDTH, 0)
          val height = intent.getIntExtra(Intents.Scan.HEIGHT, 0)
          if (width > 0 && height > 0) {
            optionsBuilder.setFramingRect(width, height)
          }
        }

        if (intent.hasExtra(Intents.Scan.CAMERA_ID)) {
          val cameraId = intent.getIntExtra(Intents.Scan.CAMERA_ID, -1)
          if (cameraId >= 0) {
            optionsBuilder.setCameraId(cameraId)
          }
        }

        val customPromptMessage = intent.getStringExtra(Intents.Scan.PROMPT_MESSAGE)
        if (customPromptMessage != null) {
          statusView.text = customPromptMessage
        }

      } else if (dataString != null &&
                 (dataString.contains("https://www.google") ||
                  dataString.contains("http://www.google")) &&
                 dataString.contains("/m/products/scan")) {

        // Scan only products and send the result to mobile Product Search.
        source = IntentSource.PRODUCT_SEARCH_LINK
        sourceUrl = dataString
        decodeFormats = DecodeFormatManager.PRODUCT_FORMATS

      } else if (isZXingURL(dataString)) {

        // Scan formats requested in query string (all formats if none specified).
        // If a return URL is specified, send the results there. Otherwise, handle it ourselves.
        source = IntentSource.ZXING_LINK
        sourceUrl = dataString
        val inputUri = Uri.parse(dataString)
        scanFromWebPageManager = ScanFromWebPageManager(inputUri)
        decodeFormats = DecodeFormatManager.parseDecodeFormats(inputUri)
        // Allow a sub-set of the hints to be specified by the caller.
        decodeHints = DecodeHintManager.parseDecodeHints(inputUri)

      }

      characterSet = intent.getStringExtra(Intents.Scan.CHARACTER_SET)
    }

    optionsBuilder.setCharacterSet(characterSet).setBaseHints(decodeHints)
    scannerView.setOptions(optionsBuilder.build())
    scannerView.setFormats(decodeFormats)

    maybeStartScanner()
  }

  /**
   * Translates this app's preferences into the library's options. The library takes no
   * interest in SharedPreferences, which is what lets it be embedded by other apps.
   */
  private fun scannerOptionsFromPreferences(prefs: SharedPreferences): ScannerOptions.Builder =
      ScannerOptions.Builder()
          .setAutoFocus(prefs.getBoolean(PreferencesActivity.KEY_AUTO_FOCUS, true))
          .setContinuousFocusDisabled(
              prefs.getBoolean(PreferencesActivity.KEY_DISABLE_CONTINUOUS_FOCUS, false))
          .setInvertScan(prefs.getBoolean(PreferencesActivity.KEY_INVERT_SCAN, false))
          .setBarcodeSceneModeDisabled(
              prefs.getBoolean(PreferencesActivity.KEY_DISABLE_BARCODE_SCENE_MODE, true))
          .setMeteringDisabled(prefs.getBoolean(PreferencesActivity.KEY_DISABLE_METERING, true))
          .setExposureDisabled(prefs.getBoolean(PreferencesActivity.KEY_DISABLE_EXPOSURE, true))
          .setFrontLightMode(FrontLightMode.parse(
              prefs.getString(PreferencesActivity.KEY_FRONT_LIGHT_MODE, FrontLightMode.OFF.toString())))

  private fun hasCameraPermission(): Boolean =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
          checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

  /**
   * Starts the scanner if we are allowed to. Otherwise asks for CAMERA once, and explains
   * the situation if the user says no. Without this the app used to open the camera
   * regardless, get a RuntimeException from the camera service, and tell the user to
   * restart their device.
   */
  private fun maybeStartScanner() {
    if (hasCameraPermission()) {
      scannerView.resume()
      return
    }
    if (cameraPermissionDenied) {
      cameraPermissionDenied = false
      displayCameraPermissionDeniedAndExit()
      return
    }
    if (!awaitingCameraPermission) {
      awaitingCameraPermission = true
      requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int,
                                          permissions: Array<out String>,
                                          grantResults: IntArray) {
    if (requestCode != CAMERA_PERMISSION_REQUEST_CODE) {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults)
      return
    }
    awaitingCameraPermission = false
    // Remember the refusal only; onResume runs next and drives the camera either way.
    cameraPermissionDenied =
        grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED
  }

  /**
   * Distinguishes "not now" from "don't ask again": in the latter case the only way back
   * is the system settings screen, so offer it rather than leaving a dead end.
   */
  private fun displayCameraPermissionDeniedAndExit() {
    val canAskAgain = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
    val builder = AlertDialog.Builder(this)
    builder.setTitle(getString(R.string.app_name))
    builder.setMessage(if (canAskAgain) R.string.msg_camera_permission_denied
                       else R.string.msg_camera_permission_denied_permanently)
    if (canAskAgain) {
      builder.setPositiveButton(R.string.button_ok, FinishListener(this))
    } else {
      builder.setPositiveButton(R.string.button_open_settings) { _, _ ->
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
          startActivity(intent)
        } catch (anfe: ActivityNotFoundException) {
          Log.w(TAG, "No settings activity to open", anfe)
        }
        finish()
      }
      builder.setNegativeButton(R.string.button_cancel, FinishListener(this))
    }
    builder.setOnCancelListener(FinishListener(this))
    builder.show()
  }

  private fun getCurrentOrientation(): Int {
    val rotation = windowManager.defaultDisplay.rotation
    return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      when (rotation) {
        Surface.ROTATION_0, Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
      }
    } else {
      when (rotation) {
        Surface.ROTATION_0, Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
      }
    }
  }

  override fun onPause() {
    uiHandler.removeCallbacksAndMessages(null)
    scannerView.pause()
    inactivityTimer.onPause()
    beepManager.close()
    // historyManager = null; // Keep for onActivityResult
    super.onPause()
  }

  override fun onDestroy() {
    inactivityTimer.shutdown()
    super.onDestroy()
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    when (keyCode) {
      KeyEvent.KEYCODE_BACK -> {
        if (source == IntentSource.NATIVE_APP_INTENT) {
          setResult(RESULT_CANCELED)
          finish()
          return true
        }
        if ((source == IntentSource.NONE || source == IntentSource.ZXING_LINK) && lastResult != null) {
          restartPreviewAfterDelay(0L)
          return true
        }
      }
      // Handle these events so they don't launch the Camera app
      KeyEvent.KEYCODE_FOCUS, KeyEvent.KEYCODE_CAMERA -> return true
      // Use volume up/down to turn on light
      KeyEvent.KEYCODE_VOLUME_DOWN -> {
        scannerView.setTorch(false)
        return true
      }
      KeyEvent.KEYCODE_VOLUME_UP -> {
        scannerView.setTorch(true)
        return true
      }
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.capture, menu)
    return super.onCreateOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.addFlags(Intents.FLAG_NEW_DOC)
    when (item.itemId) {
      R.id.menu_share -> {
        intent.setClassName(this, ShareActivity::class.java.name)
        startActivity(intent)
      }
      R.id.menu_history -> {
        intent.setClassName(this, HistoryActivity::class.java.name)
        startActivityForResult(intent, HISTORY_REQUEST_CODE)
      }
      R.id.menu_settings -> {
        intent.setClassName(this, PreferencesActivity::class.java.name)
        startActivity(intent)
      }
      R.id.menu_help -> {
        intent.setClassName(this, HelpActivity::class.java.name)
        startActivity(intent)
      }
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
    val historyManager = this.historyManager
    if (resultCode == RESULT_OK && requestCode == HISTORY_REQUEST_CODE && historyManager != null) {
      val itemNumber = intent?.getIntExtra(Intents.History.ITEM_NUMBER, -1) ?: -1
      if (itemNumber >= 0) {
        val historyItem = historyManager.buildHistoryItem(itemNumber)
        handleDecode(historyItem.getResult()!!, null, 1.0f)
      }
    }
  }

  override fun onScanned(outcome: ScanOutcome) {
    handleDecode(outcome.result, outcome.thumbnail, outcome.thumbnailScaleFactor)
  }

  override fun onScannerError(error: ScannerError, cause: Exception?) {
    Log.w(TAG, "Scanner error: $error", cause)
    val message = when (error) {
      // maybeStartScanner() owns the permission conversation; nothing to say here.
      ScannerError.PERMISSION_DENIED -> return
      ScannerError.NO_CAMERA -> R.string.msg_camera_none
      ScannerError.CAMERA_UNAVAILABLE -> R.string.msg_camera_unavailable
      else -> R.string.msg_camera_framework_bug
    }
    displayCameraErrorAndExit(message)
  }

  /**
   * A valid barcode has been found, so give an indication of success and show the results.
   *
   * @param rawResult The contents of the barcode.
   * @param barcode A greyscale bitmap of the camera data which was decoded.
   * @param scaleFactor amount by which thumbnail was scaled
   */
  fun handleDecode(rawResult: Result, barcode: Bitmap?, scaleFactor: Float) {
    inactivityTimer.onActivity()
    lastResult = rawResult
    val resultHandler = ResultHandlerFactory.makeResultHandler(this, rawResult)

    val fromLiveScan = barcode != null
    if (fromLiveScan) {
      historyManager?.addHistoryItem(rawResult, resultHandler)
      // Then not from history, so beep/vibrate and we have an image to draw on
      beepManager.playBeepSoundAndVibrate()
      drawResultPoints(barcode!!, scaleFactor, rawResult)
    }

    when (source) {
      IntentSource.NATIVE_APP_INTENT, IntentSource.PRODUCT_SEARCH_LINK ->
        handleDecodeExternally(rawResult, resultHandler, barcode)
      IntentSource.ZXING_LINK -> {
        val manager = scanFromWebPageManager
        if (manager == null || !manager.isScanFromWebPage()) {
          handleDecodeInternally(rawResult, resultHandler, barcode)
        } else {
          handleDecodeExternally(rawResult, resultHandler, barcode)
        }
      }
      IntentSource.NONE -> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (fromLiveScan && prefs.getBoolean(PreferencesActivity.KEY_BULK_MODE, false)) {
          Toast.makeText(applicationContext,
                         resources.getString(R.string.msg_bulk_mode_scanned) + " (" + rawResult.text + ')',
                         Toast.LENGTH_SHORT).show()
          maybeSetClipboard(resultHandler)
          // Wait a moment or else it will scan the same barcode continuously about 3 times
          restartPreviewAfterDelay(BULK_MODE_SCAN_DELAY_MS)
        } else {
          handleDecodeInternally(rawResult, resultHandler, barcode)
        }
      }
    }
  }

  /**
   * Superimpose a line for 1D or dots for 2D to highlight the key features of the barcode.
   *
   * @param barcode A bitmap of the captured image.
   * @param scaleFactor amount by which thumbnail was scaled
   * @param rawResult The decoded results which contains the points to draw.
   */
  private fun drawResultPoints(barcode: Bitmap, scaleFactor: Float, rawResult: Result) {
    val points = rawResult.resultPoints
    if (points != null && points.isNotEmpty()) {
      val canvas = Canvas(barcode)
      val paint = Paint()
      paint.color = resources.getColor(R.color.result_points)
      if (points.size == 2) {
        paint.strokeWidth = 4.0f
        drawLine(canvas, paint, points[0], points[1], scaleFactor)
      } else if (points.size == 4 &&
                 (rawResult.barcodeFormat == BarcodeFormat.UPC_A ||
                  rawResult.barcodeFormat == BarcodeFormat.EAN_13)) {
        // Hacky special case -- draw two lines, for the barcode and metadata
        drawLine(canvas, paint, points[0], points[1], scaleFactor)
        drawLine(canvas, paint, points[2], points[3], scaleFactor)
      } else {
        paint.strokeWidth = 10.0f
        for (point in points) {
          if (point != null) {
            canvas.drawPoint(scaleFactor * point.x, scaleFactor * point.y, paint)
          }
        }
      }
    }
  }

  // Put up our own UI for how to handle the decoded contents.
  private fun handleDecodeInternally(rawResult: Result, resultHandler: ResultHandler, barcode: Bitmap?) {

    maybeSetClipboard(resultHandler)

    val prefs = PreferenceManager.getDefaultSharedPreferences(this)

    val defaultButtonID = resultHandler.getDefaultButtonID()
    if (defaultButtonID != null && prefs.getBoolean(PreferencesActivity.KEY_AUTO_OPEN_WEB, false)) {
      resultHandler.handleButtonPress(defaultButtonID)
      return
    }

    statusView.visibility = View.GONE
    scannerView.getViewfinderView().visibility = View.GONE
    resultView.visibility = View.VISIBLE

    val barcodeImageView = findViewById<ImageView>(R.id.barcode_image_view)
    if (barcode == null) {
      barcodeImageView.setImageBitmap(
          BitmapFactory.decodeResource(resources, R.drawable.launcher_icon))
    } else {
      barcodeImageView.setImageBitmap(barcode)
    }

    findViewById<TextView>(R.id.format_text_view).text = rawResult.barcodeFormat.toString()

    findViewById<TextView>(R.id.type_text_view).text = resultHandler.getType().toString()

    val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    findViewById<TextView>(R.id.time_text_view).text = formatter.format(rawResult.timestamp)

    val metaTextView = findViewById<TextView>(R.id.meta_text_view)
    val metaTextViewLabel = findViewById<View>(R.id.meta_text_view_label)
    metaTextView.visibility = View.GONE
    metaTextViewLabel.visibility = View.GONE
    val metadata = rawResult.resultMetadata
    if (metadata != null) {
      val metadataText = StringBuilder(20)
      for ((key, value) in metadata) {
        if (DISPLAYABLE_METADATA_TYPES.contains(key)) {
          metadataText.append(value).append('\n')
        }
      }
      if (metadataText.isNotEmpty()) {
        metadataText.setLength(metadataText.length - 1)
        metaTextView.text = metadataText
        metaTextView.visibility = View.VISIBLE
        metaTextViewLabel.visibility = View.VISIBLE
      }
    }

    val displayContents = resultHandler.getDisplayContents()
    val contentsTextView = findViewById<TextView>(R.id.contents_text_view)
    contentsTextView.text = displayContents
    val scaledSize = maxOf(22, 32 - displayContents.length / 4)
    contentsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize.toFloat())

    val supplementTextView = findViewById<TextView>(R.id.contents_supplement_text_view)
    supplementTextView.text = ""
    supplementTextView.setOnClickListener(null)
    if (PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(PreferencesActivity.KEY_SUPPLEMENTAL, true)) {
      SupplementalInfoRetriever.maybeInvokeRetrieval(supplementTextView,
                                                     resultHandler.getResult(),
                                                     historyManager,
                                                     this)
    }

    val buttonCount = resultHandler.getButtonCount()
    val buttonView = findViewById<ViewGroup>(R.id.result_button_view)
    buttonView.requestFocus()
    for (x in 0 until ResultHandler.MAX_BUTTON_COUNT) {
      val button = buttonView.getChildAt(x) as TextView
      if (x < buttonCount) {
        button.visibility = View.VISIBLE
        button.setText(resultHandler.getButtonText(x))
        button.setOnClickListener(ResultButtonListener(resultHandler, x))
      } else {
        button.visibility = View.GONE
      }
    }
  }

  // Briefly show the contents of the barcode, then handle the result outside Barcode Scanner.
  private fun handleDecodeExternally(rawResult: Result, resultHandler: ResultHandler, barcode: Bitmap?) {

    if (barcode != null) {
      scannerView.getViewfinderView().drawResultBitmap(barcode)
    }

    val resultDurationMS = if (getIntent() == null) {
      DEFAULT_INTENT_RESULT_DURATION_MS
    } else {
      getIntent().getLongExtra(Intents.Scan.RESULT_DISPLAY_DURATION_MS,
                               DEFAULT_INTENT_RESULT_DURATION_MS)
    }

    if (resultDurationMS > 0) {
      var rawResultString = rawResult.toString()
      if (rawResultString.length > 32) {
        rawResultString = rawResultString.substring(0, 32) + " ..."
      }
      statusView.text = getString(resultHandler.getDisplayTitle()) + " : " + rawResultString
    }

    maybeSetClipboard(resultHandler)

    when (source) {
      IntentSource.NATIVE_APP_INTENT -> {
        // Hand back whatever action they requested - this can be changed to Intents.Scan.ACTION when
        // the deprecated intent is retired.
        val intent = Intent(getIntent().action)
        intent.addFlags(Intents.FLAG_NEW_DOC)
        intent.putExtra(Intents.Scan.RESULT, rawResult.toString())
        intent.putExtra(Intents.Scan.RESULT_FORMAT, rawResult.barcodeFormat.toString())
        val rawBytes = rawResult.rawBytes
        if (rawBytes != null && rawBytes.isNotEmpty()) {
          intent.putExtra(Intents.Scan.RESULT_BYTES, rawBytes)
        }
        val metadata = rawResult.resultMetadata
        if (metadata != null) {
          if (metadata.containsKey(ResultMetadataType.UPC_EAN_EXTENSION)) {
            intent.putExtra(Intents.Scan.RESULT_UPC_EAN_EXTENSION,
                            metadata[ResultMetadataType.UPC_EAN_EXTENSION].toString())
          }
          val orientation = metadata[ResultMetadataType.ORIENTATION] as Number?
          if (orientation != null) {
            intent.putExtra(Intents.Scan.RESULT_ORIENTATION, orientation.toInt())
          }
          val ecLevel = metadata[ResultMetadataType.ERROR_CORRECTION_LEVEL] as String?
          if (ecLevel != null) {
            intent.putExtra(Intents.Scan.RESULT_ERROR_CORRECTION_LEVEL, ecLevel)
          }
          @Suppress("UNCHECKED_CAST")
          val byteSegments = metadata[ResultMetadataType.BYTE_SEGMENTS] as Iterable<ByteArray>?
          if (byteSegments != null) {
            var i = 0
            for (byteSegment in byteSegments) {
              intent.putExtra(Intents.Scan.RESULT_BYTE_SEGMENTS_PREFIX + i, byteSegment)
              i++
            }
          }
        }
        postAfterResultDisplay(resultDurationMS) {
          setResult(Activity.RESULT_OK, intent)
          finish()
        }
      }

      IntentSource.PRODUCT_SEARCH_LINK -> {
        // Reformulate the URL which triggered us into a query, so that the request goes to the same
        // TLD as the scan URL.
        val url = sourceUrl!!
        val end = url.lastIndexOf("/scan")
        val productReplyURL = url.substring(0, end) + "?q=" +
            resultHandler.getDisplayContents() + "&source=zxing"
        postAfterResultDisplay(resultDurationMS) { launchProductQuery(productReplyURL) }
      }

      IntentSource.ZXING_LINK -> {
        val manager = scanFromWebPageManager
        if (manager != null && manager.isScanFromWebPage()) {
          val linkReplyURL = manager.buildReplyURL(rawResult, resultHandler)
          scanFromWebPageManager = null
          postAfterResultDisplay(resultDurationMS) { launchProductQuery(linkReplyURL) }
        }
      }

      IntentSource.NONE -> {}
    }
  }

  private fun maybeSetClipboard(resultHandler: ResultHandler) {
    if (copyToClipboard && !resultHandler.areContentsSecure()) {
      ClipboardInterface.setText(resultHandler.getDisplayContents(), this)
    }
  }

  /** Shows the result for the configured duration, then runs the reply. */
  private fun postAfterResultDisplay(delayMS: Long, action: Runnable) {
    if (delayMS > 0L) {
      uiHandler.postDelayed(action, delayMS)
    } else {
      action.run()
    }
  }

  /**
   * Opens a product-search URL. Moved here from CaptureActivityHandler, which no longer
   * exists now that the decode loop lives in the scanner library.
   */
  private fun launchProductQuery(url: String) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.addFlags(Intents.FLAG_NEW_DOC)
    intent.data = Uri.parse(url)

    val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    val browserPackageName = resolveInfo?.activityInfo?.packageName

    // Needed for default Android browser / Chrome only apparently
    when (browserPackageName) {
      "com.android.browser", "com.android.chrome" -> {
        intent.setPackage(browserPackageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, browserPackageName)
      }
    }

    try {
      startActivity(intent)
    } catch (ignored: ActivityNotFoundException) {
      Log.w(TAG, "Can't find anything to handle VIEW of URI")
    }
  }

  private fun displayCameraErrorAndExit(messageId: Int) {
    val builder = AlertDialog.Builder(this)
    builder.setTitle(getString(R.string.app_name))
    builder.setMessage(getString(messageId))
    builder.setPositiveButton(R.string.button_ok, FinishListener(this))
    builder.setOnCancelListener(FinishListener(this))
    builder.show()
  }

  fun restartPreviewAfterDelay(delayMS: Long) {
    uiHandler.postDelayed({ scannerView.resumeScanning() }, maxOf(0L, delayMS))
    resetStatusView()
  }

  private fun resetStatusView() {
    resultView.visibility = View.GONE
    statusView.setText(R.string.msg_default_status)
    statusView.visibility = View.VISIBLE
    scannerView.getViewfinderView().visibility = View.VISIBLE
    lastResult = null
  }

  fun drawViewfinder() {
    scannerView.getViewfinderView().drawViewfinder()
  }

  private companion object {

    private val TAG = CaptureActivity::class.java.simpleName

    private const val DEFAULT_INTENT_RESULT_DURATION_MS = 1500L
    private const val BULK_MODE_SCAN_DELAY_MS = 1000L

    private val ZXING_URLS = arrayOf("http://zxing.appspot.com/scan", "zxing://scan/")

    private const val HISTORY_REQUEST_CODE = 0x0000bacc
    private const val CAMERA_PERMISSION_REQUEST_CODE = 0x0000cafe

    private val DISPLAYABLE_METADATA_TYPES: Collection<ResultMetadataType> =
        EnumSet.of(ResultMetadataType.ISSUE_NUMBER,
                   ResultMetadataType.SUGGESTED_PRICE,
                   ResultMetadataType.ERROR_CORRECTION_LEVEL,
                   ResultMetadataType.POSSIBLE_COUNTRY)

    private fun isZXingURL(dataString: String?): Boolean {
      if (dataString == null) {
        return false
      }
      return ZXING_URLS.any { dataString.startsWith(it) }
    }

    private fun drawLine(canvas: Canvas, paint: Paint, a: ResultPoint?, b: ResultPoint?, scaleFactor: Float) {
      if (a != null && b != null) {
        canvas.drawLine(scaleFactor * a.x,
                        scaleFactor * a.y,
                        scaleFactor * b.x,
                        scaleFactor * b.y,
                        paint)
      }
    }
  }
}
