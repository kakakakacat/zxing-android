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

package io.github.kakakakacat.zxing.android.share

import android.app.ListActivity
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListAdapter
import io.github.kakakakacat.zxing.android.R

/**
 * Loads a list of packages installed on the device asynchronously.
 *
 * @author Sean Owen
 */
@Suppress("DEPRECATION") // AsyncTask, android.app.ListActivity
internal class LoadPackagesAsyncTask(private val activity: ListActivity) :
    AsyncTask<Any, Any, List<AppInfo>>() {

  // Defect A-10: getInstalledApplications returns almost nothing from Android 11 on without a
  // <queries> element in the manifest. Carried forward. See docs/maint/HANDOFF.md, Phase 3.
  override fun doInBackground(vararg objects: Any): List<AppInfo> {
    val labelsPackages = ArrayList<AppInfo>()
    val packageManager: PackageManager = activity.packageManager
    for (appInfo in packageManager.getInstalledApplications(0)) {
      val packageName: String? = appInfo.packageName
      if (!isHidden(packageName)) {
        val label = appInfo.loadLabel(packageManager)
        val icon = appInfo.loadIcon(packageManager)
        if (label != null) {
          labelsPackages.add(AppInfo(packageName!!, label.toString(), icon))
        }
      }
    }
    labelsPackages.sort()
    return labelsPackages
  }

  override fun onPostExecute(results: List<AppInfo>) {
    val listAdapter: ListAdapter = object : ArrayAdapter<AppInfo>(
        activity, R.layout.app_picker_list_item, R.id.app_picker_list_item_label, results) {
      override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val icon = results[position].getIcon()
        if (icon != null) {
          view.findViewById<ImageView>(R.id.app_picker_list_item_icon).setImageDrawable(icon)
        }
        return view
      }
    }
    activity.listAdapter = listAdapter
  }

  private companion object {

    private val PKG_PREFIX_SAFELIST = arrayOf(
        "com.google.android.apps.")

    private val PKG_PREFIX_BLOCKLIST = arrayOf(
        "com.android.",
        "android",
        "com.google.android.",
        "com.htc")

    private fun isHidden(packageName: String?): Boolean {
      if (packageName == null) {
        return true
      }
      if (PKG_PREFIX_SAFELIST.any { packageName.startsWith(it) }) {
        return false
      }
      return PKG_PREFIX_BLOCKLIST.any { packageName.startsWith(it) }
    }
  }
}
