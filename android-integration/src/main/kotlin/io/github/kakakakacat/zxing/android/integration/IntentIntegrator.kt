/*
 * Copyright 2009 ZXing authors
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

package io.github.kakakakacat.zxing.android.integration

import android.app.Activity
import android.app.AlertDialog
import android.app.Fragment
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * A utility class which helps ease integration with Barcode Scanner via [Intent]s. This is a
 * simple way to invoke barcode scanning and receive the result, without any need to integrate,
 * modify, or learn the project's source code.
 *
 * ## Initiating a barcode scan
 *
 * To integrate, create an instance of `IntentIntegrator` and call [initiateScan] and wait for the
 * result in your app.
 *
 * It does require that the Barcode Scanner (or work-alike) application is installed. The
 * [initiateScan] method will prompt the user to download the application, if needed.
 *
 * There are a few steps to using this integration. First, your [Activity] must implement the
 * method [Activity.onActivityResult] and include a line of code like this:
 *
 * ```
 * public void onActivityResult(int requestCode, int resultCode, Intent intent) {
 *   IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
 *   if (scanResult != null) {
 *     // handle scan result
 *   }
 *   // else continue with any other code you need in the method
 *   ...
 * }
 * ```
 *
 * This is where you will handle a scan result.
 *
 * Second, just call this in response to a user action somewhere to begin the scan process:
 *
 * ```
 * IntentIntegrator integrator = new IntentIntegrator(yourActivity);
 * integrator.initiateScan();
 * ```
 *
 * Note that [initiateScan] returns an [AlertDialog] which is non-null if the user was prompted to
 * download the application. This lets the calling app potentially manage the dialog. In
 * particular, ideally, the app dismisses the dialog if it is still active in its
 * [Activity.onPause] method.
 *
 * You can use [setTitle] to customize the title of this download prompt dialog (or, use
 * [setTitleByID] to set the title by string resource ID.) Likewise, the prompt message, and
 * yes/no button labels can be changed.
 *
 * Finally, you can use [addExtra] to add more parameters to the Intent used to invoke the
 * scanner. This can be used to set additional options not directly exposed by this simplified API.
 *
 * By default, this will only allow applications that are known to respond to this intent correctly
 * do so. The apps that are allowed to respond can be set with [setTargetApplications]. For
 * example, set to [TARGET_BARCODE_SCANNER_ONLY] to only target the Barcode Scanner app itself.
 *
 * ## Sharing text via barcode
 *
 * To share text, encoded as a QR Code on-screen, similarly, see [shareText].
 *
 * Some code, particularly download integration, was contributed from the Anobiit application.
 *
 * ## Enabling experimental barcode formats
 *
 * Some formats are not enabled by default even when scanning with [ALL_CODE_TYPES], such as
 * PDF417. Use [initiateScan] with a collection containing the names of formats to scan for
 * explicitly, like "PDF_417", to use such formats.
 *
 * @author Sean Owen
 * @author Fred Lin
 * @author Isaac Potoczny-Jones
 * @author Brad Drehmer
 * @author gcstang
 */
@Suppress("DEPRECATION") // android.app.Fragment: part of the frozen public signature.
open class IntentIntegrator {

  private val activity: Activity
  private val fragment: Fragment?

  /** Title of the "install Barcode Scanner?" prompt. */
  open var title: String = DEFAULT_TITLE
  /** Body of the "install Barcode Scanner?" prompt. */
  open var message: String = DEFAULT_MESSAGE
  /** Label of the prompt's confirm button. */
  open var buttonYes: String = DEFAULT_YES
  /** Label of the prompt's dismiss button. */
  open var buttonNo: String = DEFAULT_NO

  // `open` exactly where the Java original was overridable, and final exactly where it was
  // declared final. Kotlin's default is the opposite of Java's, so every one of these is a
  // deliberate choice rather than an omission: a subclass that overrode setTitle() against the
  // Java class has to keep compiling against this one.
  private var targetApplications: List<String> = TARGET_ALL_KNOWN
  private val moreExtras: MutableMap<String, Any> = HashMap(3)

  /**
   * @param activity [Activity] invoking the integration
   */
  constructor(activity: Activity) {
    this.activity = activity
    this.fragment = null
  }

  /**
   * @param fragment [Fragment] invoking the integration. [startActivityForResult] will be called
   *   on the [Fragment] instead of an [Activity]
   */
  constructor(fragment: Fragment) {
    this.activity = fragment.activity
    this.fragment = fragment
  }

  open fun setTitleByID(titleID: Int) {
    title = activity.getString(titleID)
  }

  open fun setMessageByID(messageID: Int) {
    message = activity.getString(messageID)
  }

  open fun setButtonYesByID(buttonYesID: Int) {
    buttonYes = activity.getString(buttonYesID)
  }

  open fun setButtonNoByID(buttonNoID: Int) {
    buttonNo = activity.getString(buttonNoID)
  }

  open fun getTargetApplications(): Collection<String> = targetApplications

  fun setTargetApplications(targetApplications: List<String>) {
    require(targetApplications.isNotEmpty()) { "No target applications" }
    this.targetApplications = targetApplications
  }

  open fun setSingleTargetApplication(targetApplication: String) {
    this.targetApplications = listOf(targetApplication)
  }

  open fun getMoreExtras(): Map<String, *> = moreExtras

  fun addExtra(key: String, value: Any) {
    moreExtras[key] = value
  }

  /**
   * Initiates a scan for all known barcode types with the default camera.
   *
   * @return the [AlertDialog] that was shown to the user prompting them to download the app if a
   *   prompt was needed, or null otherwise.
   */
  fun initiateScan(): AlertDialog? = initiateScan(ALL_CODE_TYPES, -1)

  /**
   * Initiates a scan for all known barcode types with the specified camera.
   *
   * @param cameraId camera ID of the camera to use. A negative value means "no preference".
   * @return the [AlertDialog] that was shown to the user prompting them to download the app if a
   *   prompt was needed, or null otherwise.
   */
  fun initiateScan(cameraId: Int): AlertDialog? = initiateScan(ALL_CODE_TYPES, cameraId)

  /**
   * Initiates a scan, using the default camera, only for a certain set of barcode types, given as
   * strings corresponding to their names in ZXing's `BarcodeFormat` class like "UPC_A". You can
   * supply constants like [PRODUCT_CODE_TYPES] for example.
   *
   * @param desiredBarcodeFormats names of `BarcodeFormat`s to scan for
   * @return the [AlertDialog] that was shown to the user prompting them to download the app if a
   *   prompt was needed, or null otherwise.
   */
  fun initiateScan(desiredBarcodeFormats: Collection<String>?): AlertDialog? =
      initiateScan(desiredBarcodeFormats, -1)

  /**
   * Initiates a scan, using the specified camera, only for a certain set of barcode types, given
   * as strings corresponding to their names in ZXing's `BarcodeFormat` class like "UPC_A". You can
   * supply constants like [PRODUCT_CODE_TYPES] for example.
   *
   * @param desiredBarcodeFormats names of `BarcodeFormat`s to scan for
   * @param cameraId camera ID of the camera to use. A negative value means "no preference".
   * @return the [AlertDialog] that was shown to the user prompting them to download the app if a
   *   prompt was needed, or null otherwise
   */
  fun initiateScan(desiredBarcodeFormats: Collection<String>?, cameraId: Int): AlertDialog? {
    val intentScan = Intent("$ACTION_NAMESPACE.SCAN")
    intentScan.addCategory(Intent.CATEGORY_DEFAULT)

    // check which types of codes to scan for
    if (desiredBarcodeFormats != null) {
      // set the desired barcode types
      intentScan.putExtra("SCAN_FORMATS", desiredBarcodeFormats.joinToString(","))
    }

    // check requested camera ID
    if (cameraId >= 0) {
      intentScan.putExtra("SCAN_CAMERA_ID", cameraId)
    }

    val targetAppPackage = findTargetAppPackage(intentScan) ?: return showDownloadDialog()
    intentScan.setPackage(targetAppPackage)
    intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    intentScan.addFlags(FLAG_NEW_DOC)
    attachMoreExtras(intentScan)
    startActivityForResult(intentScan, REQUEST_CODE)
    return null
  }

  /**
   * Start an activity. This method is defined to allow different methods of activity starting for
   * newer versions of Android and for compatibility library.
   *
   * @param intent Intent to start.
   * @param code Request code for the activity
   * @see Activity.startActivityForResult
   * @see Fragment.startActivityForResult
   */
  protected open fun startActivityForResult(intent: Intent, code: Int) {
    if (fragment == null) {
      activity.startActivityForResult(intent, code)
    } else {
      fragment.startActivityForResult(intent, code)
    }
  }

  private fun findTargetAppPackage(intent: Intent): String? {
    val pm = activity.packageManager
    val availableApps: List<ResolveInfo>? =
        pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    if (availableApps != null) {
      return targetApplications.firstOrNull { targetApp -> contains(availableApps, targetApp) }
    }
    return null
  }

  private fun showDownloadDialog(): AlertDialog {
    val downloadDialog = AlertDialog.Builder(activity)
    downloadDialog.setTitle(title)
    downloadDialog.setMessage(message)
    downloadDialog.setPositiveButton(buttonYes) { _, _ ->
      // Prefer to suggest download of BS if it is anywhere in the list; otherwise, first option.
      val packageName =
          if (targetApplications.contains(BS_PACKAGE)) BS_PACKAGE else targetApplications[0]
      val uri = Uri.parse("market://details?id=$packageName")
      val intent = Intent(Intent.ACTION_VIEW, uri)
      try {
        if (fragment == null) {
          activity.startActivity(intent)
        } else {
          fragment.startActivity(intent)
        }
      } catch (anfe: ActivityNotFoundException) {
        // Hmm, market is not installed
        Log.w(TAG, "Google Play is not installed; cannot install $packageName", anfe)
      }
    }
    downloadDialog.setNegativeButton(buttonNo, null)
    downloadDialog.setCancelable(true)
    return downloadDialog.show()
  }

  /**
   * Defaults to type "TEXT_TYPE".
   *
   * @param text the text string to encode as a barcode
   * @return the [AlertDialog] that was shown to the user prompting them to download the app if a
   *   prompt was needed, or null otherwise
   * @see shareText
   */
  fun shareText(text: CharSequence): AlertDialog? = shareText(text, "TEXT_TYPE")

  /**
   * Shares the given text by encoding it as a barcode, such that another user can scan the text
   * off the screen of the device.
   *
   * @param text the text string to encode as a barcode
   * @param type type of data to encode. See
   *   `io.github.kakakakacat.zxing.android.Contents.Type` constants.
   * @return the [AlertDialog] that was shown to the user prompting them to download the app if a
   *   prompt was needed, or null otherwise
   */
  fun shareText(text: CharSequence, type: CharSequence): AlertDialog? {
    val intent = Intent()
    intent.addCategory(Intent.CATEGORY_DEFAULT)
    intent.action = "$ACTION_NAMESPACE.ENCODE"
    intent.putExtra("ENCODE_TYPE", type)
    intent.putExtra("ENCODE_DATA", text)
    val targetAppPackage = findTargetAppPackage(intent) ?: return showDownloadDialog()
    intent.setPackage(targetAppPackage)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    intent.addFlags(FLAG_NEW_DOC)
    attachMoreExtras(intent)
    if (fragment == null) {
      activity.startActivity(intent)
    } else {
      fragment.startActivity(intent)
    }
    return null
  }

  private fun attachMoreExtras(intent: Intent) {
    for ((key, value) in moreExtras) {
      // Kind of hacky
      when (value) {
        is Int -> intent.putExtra(key, value)
        is Long -> intent.putExtra(key, value)
        is Boolean -> intent.putExtra(key, value)
        is Double -> intent.putExtra(key, value)
        is Float -> intent.putExtra(key, value)
        is Bundle -> intent.putExtra(key, value)
        else -> intent.putExtra(key, value.toString())
      }
    }
  }

  companion object {

    const val REQUEST_CODE: Int = 0x0000c0de // Only use bottom 16 bits

    private val TAG = IntentIntegrator::class.java.simpleName

    const val DEFAULT_TITLE: String = "Install Barcode Scanner?"
    const val DEFAULT_MESSAGE: String =
        "This application requires Barcode Scanner. Would you like to install it?"
    const val DEFAULT_YES: String = "Yes"
    const val DEFAULT_NO: String = "No"

    /**
     * Namespace of the SCAN/ENCODE action names. This is the public Intent contract and stays on
     * the original namespace regardless of which app handles it; it is not a package name to
     * launch.
     */
    private const val ACTION_NAMESPACE = "com.google.zxing.client.android"

    /** This fork's own Barcode Scanner build. */
    private const val MAINT_PACKAGE = "io.github.kakakakacat.zxing.android"
    /** The ZXing project's published Barcode Scanner. */
    private const val BS_PACKAGE = "com.google.zxing.client.android"
    private const val BSPLUS_PACKAGE = "com.srowen.bs.android"

    // supported barcode formats
    @JvmField
    val PRODUCT_CODE_TYPES: Collection<String> =
        list("UPC_A", "UPC_E", "EAN_8", "EAN_13", "RSS_14")
    @JvmField
    val ONE_D_CODE_TYPES: Collection<String> =
        list("UPC_A", "UPC_E", "EAN_8", "EAN_13", "CODE_39", "CODE_93", "CODE_128",
             "ITF", "RSS_14", "RSS_EXPANDED")
    @JvmField
    val QR_CODE_TYPES: Collection<String> = setOf("QR_CODE")
    @JvmField
    val DATA_MATRIX_TYPES: Collection<String> = setOf("DATA_MATRIX")

    /** Null means "no restriction", which is how the scanner reads a missing SCAN_FORMATS. */
    @JvmField
    val ALL_CODE_TYPES: Collection<String>? = null

    /** Only this fork's build. */
    @JvmField
    val TARGET_MAINTENANCE_FORK_ONLY: List<String> = listOf(MAINT_PACKAGE)
    /** Only the ZXing project's published app. */
    @JvmField
    val TARGET_BARCODE_SCANNER_ONLY: List<String> = listOf(BS_PACKAGE)
    @JvmField
    val TARGET_ALL_KNOWN: List<String> = list(
        MAINT_PACKAGE,              // this maintenance fork
        BSPLUS_PACKAGE,             // Barcode Scanner+
        "$BSPLUS_PACKAGE.simple",   // Barcode Scanner+ Simple
        BS_PACKAGE                  // Barcode Scanner
        // What else supports this intent?
    )

    // Should be FLAG_ACTIVITY_NEW_DOCUMENT in API 21+.
    // Defined once here because the current value is deprecated, so generates just one warning
    @Suppress("DEPRECATION")
    private const val FLAG_NEW_DOC = Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET

    /**
     * Call this from your [Activity]'s [Activity.onActivityResult] method.
     *
     * @param requestCode request code from `onActivityResult()`
     * @param resultCode result code from `onActivityResult()`
     * @param intent [Intent] from `onActivityResult()`
     * @return null if the event handled here was not related to this class, or else an
     *   [IntentResult] containing the result of the scan. If the user cancelled scanning, the
     *   fields will be null.
     */
    @JvmStatic
    fun parseActivityResult(requestCode: Int, resultCode: Int, intent: Intent): IntentResult? {
      if (requestCode == REQUEST_CODE) {
        if (resultCode == Activity.RESULT_OK) {
          val contents = intent.getStringExtra("SCAN_RESULT")
          val formatName = intent.getStringExtra("SCAN_RESULT_FORMAT")
          val rawBytes = intent.getByteArrayExtra("SCAN_RESULT_BYTES")
          val intentOrientation = intent.getIntExtra("SCAN_RESULT_ORIENTATION", Int.MIN_VALUE)
          val orientation = if (intentOrientation == Int.MIN_VALUE) null else intentOrientation
          val errorCorrectionLevel = intent.getStringExtra("SCAN_RESULT_ERROR_CORRECTION_LEVEL")
          return IntentResult.of(
              contents, formatName, rawBytes, orientation, errorCorrectionLevel)
        }
        return IntentResult.cancelled()
      }
      return null
    }

    private fun contains(availableApps: Iterable<ResolveInfo>, targetApp: String): Boolean =
        availableApps.any { targetApp == it.activityInfo.packageName }

    private fun list(vararg values: String): List<String> =
        java.util.Collections.unmodifiableList(values.asList())
  }
}
