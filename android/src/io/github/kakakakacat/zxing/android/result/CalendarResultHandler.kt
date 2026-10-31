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

package io.github.kakakakacat.zxing.android.result

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import io.github.kakakakacat.zxing.android.R
import io.github.kakakakacat.zxing.client.result.CalendarParsedResult
import io.github.kakakakacat.zxing.client.result.ParsedResult
import java.text.DateFormat

/**
 * Handles calendar entries encoded in QR Codes.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
class CalendarResultHandler(activity: Activity, result: ParsedResult) :
    ResultHandler(activity, result) {

  override fun getButtonCount(): Int = buttons.size

  override fun getButtonText(index: Int): Int = buttons[index]

  override fun handleButtonPress(index: Int) {
    if (index == 0) {
      val calendarResult = getResult() as CalendarParsedResult

      var description = calendarResult.description
      val organizer = calendarResult.organizer
      if (organizer != null) { // No separate Intent key, put in description
        description = if (description == null) organizer else description + '\n' + organizer
      }

      addCalendarEvent(calendarResult.summary,
                       calendarResult.startTimestamp,
                       calendarResult.isStartAllDay,
                       calendarResult.endTimestamp,
                       calendarResult.location,
                       description,
                       calendarResult.attendees)
    }
  }

  /**
   * Sends an intent to create a new calendar event by prepopulating the Add Event UI. Older
   * versions of the system have a bug where the event title will not be filled out.
   *
   * @param summary A description of the event
   * @param start   The start time
   * @param allDay  if true, event is considered to be all day starting from start time
   * @param end     The end time (optional; can be < 0 if not specified)
   * @param location a text description of the event location
   * @param description a text description of the event itself
   * @param attendees attendees to invite
   */
  private fun addCalendarEvent(summary: String?,
                               start: Long,
                               allDay: Boolean,
                               end: Long,
                               location: String?,
                               description: String?,
                               attendees: Array<String>?) {
    val intent = Intent(Intent.ACTION_INSERT)
    intent.type = "vnd.android.cursor.item/event"
    intent.putExtra("beginTime", start)
    if (allDay) {
      intent.putExtra("allDay", true)
    }
    var endTime = end
    if (endTime < 0L) {
      // + 1 day
      endTime = if (allDay) start + 24 * 60 * 60 * 1000 else start
    }
    intent.putExtra("endTime", endTime)
    intent.putExtra("title", summary)
    intent.putExtra("eventLocation", location)
    intent.putExtra("description", description)
    if (attendees != null) {
      intent.putExtra(Intent.EXTRA_EMAIL, attendees)
      // Documentation says this is either a String[] or comma-separated String, which is right?
    }

    try {
      // Do this manually at first
      rawLaunchIntent(intent)
    } catch (anfe: ActivityNotFoundException) {
      Log.w(TAG, "No calendar app available that responds to " + Intent.ACTION_INSERT)
      // For calendar apps that don't like "INSERT":
      intent.action = Intent.ACTION_EDIT
      launchIntent(intent) // Fail here for real if nothing can handle it
    }
  }

  override fun getDisplayContents(): CharSequence {

    val calResult = getResult() as CalendarParsedResult
    val result = StringBuilder(100)

    ParsedResult.maybeAppend(calResult.summary, result)

    val start = calResult.startTimestamp
    ParsedResult.maybeAppend(format(calResult.isStartAllDay, start), result)

    var end = calResult.endTimestamp
    if (end >= 0L) {
      if (calResult.isEndAllDay && start != end) {
        // Show only year/month/day
        // if it's all-day and this is the end date, it's exclusive, so show the user
        // that it ends on the day before to make more intuitive sense.
        // But don't do it if the event already (incorrectly?) specifies the same start/end
        end -= (24 * 60 * 60 * 1000).toLong()
      }
      ParsedResult.maybeAppend(format(calResult.isEndAllDay, end), result)
    }

    ParsedResult.maybeAppend(calResult.location, result)
    ParsedResult.maybeAppend(calResult.organizer, result)
    ParsedResult.maybeAppend(calResult.attendees, result)
    ParsedResult.maybeAppend(calResult.description, result)
    return result.toString()
  }

  override fun getDisplayTitle(): Int = R.string.result_calendar

  private companion object {

    private val TAG = CalendarResultHandler::class.java.simpleName

    private val buttons = intArrayOf(R.string.button_add_calendar)

    private fun format(allDay: Boolean, date: Long): String? {
      if (date < 0L) {
        return null
      }
      val format = if (allDay) {
        DateFormat.getDateInstance(DateFormat.MEDIUM)
      } else {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
      }
      return format.format(date)
    }
  }
}
