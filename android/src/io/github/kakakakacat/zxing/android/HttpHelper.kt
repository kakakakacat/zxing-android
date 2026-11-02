/*
 * Copyright (C) 2010 ZXing authors
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

import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.net.URLConnection

/**
 * Utility methods for retrieving content over HTTP using the more-supported `java.net` classes
 * in Android.
 */
object HttpHelper {

  private val REDIRECTOR_DOMAINS: Collection<String> = hashSetOf(
      "amzn.to", "bit.ly", "bitly.com", "fb.me", "goo.gl", "is.gd", "j.mp", "lnkd.in", "ow.ly",
      "R.BEETAGG.COM", "r.beetagg.com", "SCN.BY", "su.pr", "t.co", "tinyurl.com", "tr.im")

  /**
   * Enumeration of supported HTTP content types
   */
  enum class ContentType {
    /** HTML-like content type, including HTML, XHTML, etc.  */
    HTML,
    /** JSON content  */
    JSON,
    /** XML  */
    XML,
    /** Plain text content  */
    TEXT,
  }

  /**
   * Downloads the entire resource instead of part.
   *
   * @param uri URI to retrieve
   * @param type expected text-like MIME type of that content
   * @return content as a `String`
   * @throws IOException if the content can't be retrieved because of a bad URI, network problem, etc.
   */
  @JvmStatic
  @JvmOverloads
  @Throws(IOException::class)
  fun downloadViaHttp(uri: String, type: ContentType, maxChars: Int = Int.MAX_VALUE): CharSequence {
    val contentTypes = when (type) {
      ContentType.HTML -> "application/xhtml+xml,text/html,text/*,*/*"
      ContentType.JSON -> "application/json,text/*,*/*"
      ContentType.XML -> "application/xml,text/*,*/*"
      ContentType.TEXT -> "text/*,*/*"
    }
    return downloadViaHttp(uri, contentTypes, maxChars)
  }

  @Throws(IOException::class)
  private fun downloadViaHttp(uri: String, contentTypes: String, maxChars: Int): CharSequence {
    var currentUri = uri
    var redirects = 0
    while (redirects < 5) {
      val url = URL(currentUri)
      val connection = safelyOpenConnection(url)
      connection.instanceFollowRedirects = true // Won't work HTTP -> HTTPS or vice versa
      connection.setRequestProperty("Accept", contentTypes)
      connection.setRequestProperty("Accept-Charset", "utf-8,*")
      connection.setRequestProperty("User-Agent", "ZXing (Android)")
      try {
        when (val responseCode = safelyConnect(connection)) {
          HttpURLConnection.HTTP_OK -> return consume(connection, maxChars)
          // 301 as well as 302: the live Google Books endpoint answers the book-search
          // request with a permanent redirect to books.google.<tld>. HttpURLConnection
          // follows that itself as long as the scheme does not change, so this loop is
          // what covers a redirect that crosses http/https -- which is exactly the case
          // instanceFollowRedirects refuses, and exactly the case a 301 off a cleartext
          // URL would have been.
          HttpURLConnection.HTTP_MOVED_PERM,
          HttpURLConnection.HTTP_MOVED_TEMP -> {
            val location = connection.getHeaderField("Location")
                ?: throw IOException("No Location")
            currentUri = location
            redirects++
          }
          else -> throw IOException("Bad HTTP response: $responseCode")
        }
      } finally {
        connection.disconnect()
      }
    }
    throw IOException("Too many redirects")
  }

  private fun getEncoding(connection: URLConnection): String {
    val contentTypeHeader = connection.getHeaderField("Content-Type")
    if (contentTypeHeader != null) {
      val charsetStart = contentTypeHeader.indexOf("charset=")
      if (charsetStart >= 0) {
        return contentTypeHeader.substring(charsetStart + "charset=".length)
      }
    }
    return "UTF-8"
  }

  @Throws(IOException::class)
  private fun consume(connection: URLConnection, maxChars: Int): CharSequence {
    val encoding = getEncoding(connection)
    val out = StringBuilder()
    InputStreamReader(connection.getInputStream(), encoding).use { input ->
      val buffer = CharArray(1024)
      var charsRead = 0
      while (out.length < maxChars && input.read(buffer).also { charsRead = it } > 0) {
        out.append(buffer, 0, charsRead)
      }
    }
    return out
  }

  @JvmStatic
  @Throws(IOException::class)
  fun unredirect(uri: URI): URI {
    if (!REDIRECTOR_DOMAINS.contains(uri.host)) {
      return uri
    }
    val connection = safelyOpenConnection(uri.toURL())
    connection.instanceFollowRedirects = false
    connection.doInput = false
    connection.requestMethod = "HEAD"
    connection.setRequestProperty("User-Agent", "ZXing (Android)")
    try {
      when (safelyConnect(connection)) {
        HttpURLConnection.HTTP_MULT_CHOICE,
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_SEE_OTHER,
        307 -> { // No constant for 307 Temporary Redirect ?
          val location = connection.getHeaderField("Location")
          if (location != null) {
            try {
              return URI(location)
            } catch (e: URISyntaxException) {
              // nevermind
            }
          }
        }
      }
      return uri
    } finally {
      connection.disconnect()
    }
  }

  @Throws(IOException::class)
  private fun safelyOpenConnection(url: URL): HttpURLConnection {
    val conn = try {
      url.openConnection()
    } catch (npe: NullPointerException) {
      // Another strange bug in Android?
      throw IOException(npe)
    }
    if (conn !is HttpURLConnection) {
      throw IOException("Expected HttpURLConnection but got " + conn.javaClass)
    }
    return conn
  }

  @Throws(IOException::class)
  private fun safelyConnect(connection: HttpURLConnection): Int {
    try {
      connection.connect()
    } catch (e: RuntimeException) {
      // These are, generally, Android bugs
      throw IOException(e)
    }
    return try {
      connection.responseCode
    } catch (e: NullPointerException) {
      // this is maybe this Android bug: http://code.google.com/p/android/issues/detail?id=15554
      throw IOException(e)
    } catch (e: StringIndexOutOfBoundsException) {
      throw IOException(e)
    } catch (e: IllegalArgumentException) {
      throw IOException(e)
    }
  }
}
