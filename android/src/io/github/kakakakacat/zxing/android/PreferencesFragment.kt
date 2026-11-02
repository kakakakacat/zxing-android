/*
 * Copyright (C) 2015 ZXing authors
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

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceScreen
import java.net.URI
import java.net.URISyntaxException

/**
 * Implements support for barcode scanning preferences.
 *
 * @see PreferencesActivity
 */
@Suppress("DEPRECATION") // android.preference.*
class PreferencesFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

  private lateinit var checkBoxPrefs: Array<CheckBoxPreference>

  override fun onCreate(icicle: Bundle?) {
    super.onCreate(icicle)
    addPreferencesFromResource(R.xml.preferences)

    val preferences = preferenceScreen
    preferences.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    checkBoxPrefs = findDecodePrefs(preferences,
                                    PreferencesActivity.KEY_DECODE_1D_PRODUCT,
                                    PreferencesActivity.KEY_DECODE_1D_INDUSTRIAL,
                                    PreferencesActivity.KEY_DECODE_QR,
                                    PreferencesActivity.KEY_DECODE_DATA_MATRIX,
                                    PreferencesActivity.KEY_DECODE_AZTEC,
                                    PreferencesActivity.KEY_DECODE_PDF417)
    disableLastCheckedPref()

    val customProductSearch =
        preferences.findPreference(PreferencesActivity.KEY_CUSTOM_PRODUCT_SEARCH) as EditTextPreference
    customProductSearch.onPreferenceChangeListener = CustomSearchURLValidator()
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    disableLastCheckedPref()
  }

  private fun disableLastCheckedPref() {
    val checked = checkBoxPrefs.filter { it.isChecked }
    val disable = checked.size <= 1
    for (pref in checkBoxPrefs) {
      pref.isEnabled = !(disable && checked.contains(pref))
    }
  }

  private inner class CustomSearchURLValidator : Preference.OnPreferenceChangeListener {

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
      if (!isValid(newValue)) {
        val builder = AlertDialog.Builder(this@PreferencesFragment.activity)
        builder.setTitle(R.string.msg_error)
        builder.setMessage(R.string.msg_invalid_value)
        builder.setCancelable(true)
        builder.show()
        return false
      }
      return true
    }

    private fun isValid(newValue: Any?): Boolean {
      // Allow empty/null value
      if (newValue == null) {
        return true
      }
      var valueString = newValue.toString()
      if (valueString.isEmpty()) {
        return true
      }
      // Before validating, remove custom placeholders, which will not
      // be considered valid parts of the URL in some locations:
      // Blank %t and %s:
      valueString = valueString.replace("%[st]".toRegex(), "")
      // Blank %f but not if followed by digit or a-f as it may be a hex sequence
      valueString = valueString.replace("%f(?![0-9a-f])".toRegex(), "")
      // Require a scheme otherwise:
      return try {
        URI(valueString).scheme != null
      } catch (use: URISyntaxException) {
        false
      }
    }
  }

  private companion object {

    private fun findDecodePrefs(preferences: PreferenceScreen, vararg keys: String): Array<CheckBoxPreference> =
        Array(keys.size) { i -> preferences.findPreference(keys[i]) as CheckBoxPreference }
  }
}
