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

import android.content.Context
import android.preference.PreferenceManager
import java.util.Locale

/**
 * Handles any locale-specific logic for the client.
 *
 * @author Sean Owen
 */
@Suppress("DEPRECATION") // android.preference.PreferenceManager
object LocaleManager {

  private const val DEFAULT_TLD = "com"
  private const val DEFAULT_COUNTRY = "US"
  private const val DEFAULT_LANGUAGE = "en"

  /**
   * Locales (well, countries) where Google web search is available.
   * These should be kept in sync with our translations.
   */
  private val GOOGLE_COUNTRY_TLD: Map<String, String> = mapOf(
      "AR" to "com.ar", // ARGENTINA
      "AU" to "com.au", // AUSTRALIA
      "BR" to "com.br", // BRAZIL
      "BG" to "bg", // BULGARIA
      Locale.CANADA.country to "ca",
      Locale.CHINA.country to "cn",
      "CZ" to "cz", // CZECH REPUBLIC
      "DK" to "dk", // DENMARK
      "FI" to "fi", // FINLAND
      Locale.FRANCE.country to "fr",
      Locale.GERMANY.country to "de",
      "GR" to "gr", // GREECE
      "HU" to "hu", // HUNGARY
      "ID" to "co.id", // INDONESIA
      "IL" to "co.il", // ISRAEL
      Locale.ITALY.country to "it",
      Locale.JAPAN.country to "co.jp",
      Locale.KOREA.country to "co.kr",
      "NL" to "nl", // NETHERLANDS
      "PL" to "pl", // POLAND
      "PT" to "pt", // PORTUGAL
      "RO" to "ro", // ROMANIA
      "RU" to "ru", // RUSSIA
      "SK" to "sk", // SLOVAK REPUBLIC
      "SI" to "si", // SLOVENIA
      "ES" to "es", // SPAIN
      "SE" to "se", // SWEDEN
      "CH" to "ch", // SWITZERLAND
      Locale.TAIWAN.country to "tw",
      "TR" to "com.tr", // TURKEY
      "UA" to "com.ua", // UKRAINE
      Locale.UK.country to "co.uk",
      Locale.US.country to "com")

  /**
   * Google Product Search for mobile is available in fewer countries than web search. See here:
   * http://support.google.com/merchants/bin/answer.py?hl=en-GB&answer=160619
   */
  private val GOOGLE_PRODUCT_SEARCH_COUNTRY_TLD: Map<String, String> = mapOf(
      "AU" to "com.au", // AUSTRALIA
      // Locale.CHINA.country to "cn",
      Locale.FRANCE.country to "fr",
      Locale.GERMANY.country to "de",
      Locale.ITALY.country to "it",
      Locale.JAPAN.country to "co.jp",
      "NL" to "nl", // NETHERLANDS
      "ES" to "es", // SPAIN
      "CH" to "ch", // SWITZERLAND
      Locale.UK.country to "co.uk",
      Locale.US.country to "com")

  /**
   * Book search is offered everywhere that web search is available.
   */
  private val GOOGLE_BOOK_SEARCH_COUNTRY_TLD: Map<String, String> = GOOGLE_COUNTRY_TLD

  private val TRANSLATED_HELP_ASSET_LANGUAGES: Collection<String> = listOf(
      "de", "en", "es", "fa", "fr", "it", "ja", "ko", "nl", "pt", "ru", "uk", "zh-rCN", "zh")

  /**
   * @param context application's [Context]
   * @return country-specific TLD suffix appropriate for the current default locale
   *  (e.g. "co.uk" for the United Kingdom)
   */
  @JvmStatic
  fun getCountryTLD(context: Context): String = doGetTLD(GOOGLE_COUNTRY_TLD, context)

  /**
   * The same as above, but specifically for Google Product Search.
   *
   * @param context application's [Context]
   * @return The top-level domain to use.
   */
  @JvmStatic
  fun getProductSearchCountryTLD(context: Context): String =
      doGetTLD(GOOGLE_PRODUCT_SEARCH_COUNTRY_TLD, context)

  /**
   * The same as above, but specifically for Google Book Search.
   *
   * @param context application's [Context]
   * @return The top-level domain to use.
   */
  @JvmStatic
  fun getBookSearchCountryTLD(context: Context): String =
      doGetTLD(GOOGLE_BOOK_SEARCH_COUNTRY_TLD, context)

  /**
   * Does a given URL point to Google Book Search, regardless of domain.
   *
   * @param url The address to check.
   * @return True if this is a Book Search URL.
   */
  @JvmStatic
  fun isBookSearchUrl(url: String): Boolean =
      BOOK_SEARCH_URL_PREFIXES.any { url.startsWith(it) }

  /**
   * Both schemes, because this matches a *scanned* URL rather than one this app builds.
   * Google Book Search has served https for years, so an http-only test matched almost
   * nothing in the field and quietly withheld the "search book contents" option.
   */
  private val BOOK_SEARCH_URL_PREFIXES = arrayOf(
      "https://google.com/books", "https://books.google.",
      "http://google.com/books", "http://books.google.")

  private fun getSystemCountry(): String {
    val locale = Locale.getDefault()
    return locale?.country ?: DEFAULT_COUNTRY
  }

  private fun getSystemLanguage(): String {
    val locale = Locale.getDefault() ?: return DEFAULT_LANGUAGE
    // Special case Chinese
    if (Locale.SIMPLIFIED_CHINESE == locale) {
      return "zh-rCN"
    }
    return locale.language
  }

  @JvmStatic
  fun getTranslatedAssetLanguage(): String {
    val language = getSystemLanguage()
    return if (TRANSLATED_HELP_ASSET_LANGUAGES.contains(language)) language else DEFAULT_LANGUAGE
  }

  private fun doGetTLD(map: Map<String, String>, context: Context): String =
      map[getCountry(context)] ?: DEFAULT_TLD

  private fun getCountry(context: Context): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val countryOverride = prefs.getString(PreferencesActivity.KEY_SEARCH_COUNTRY, "-")
    if (countryOverride != null && countryOverride.isNotEmpty() && "-" != countryOverride) {
      return countryOverride
    }
    return getSystemCountry()
  }
}
