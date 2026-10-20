package uk.co.tdsstudios.noterly.wear.ui

import android.content.Context
import android.text.format.DateFormat
import uk.co.tdsstudios.noterly.wear.R
import uk.co.tdsstudios.noterly.wearshared.DartTime
import uk.co.tdsstudios.noterly.wearshared.NoteItem
import uk.co.tdsstudios.noterly.wearshared.Repetition
import uk.co.tdsstudios.noterly.wearshared.RepetitionData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

object Format {
    /** "14:30" today, otherwise "Tue 20 Oct 14:30", following the watch's 12/24-hour setting. */
    fun time(context: Context, dateTime: LocalDateTime): String {
        val locale = Locale.getDefault()
        val clock = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        val skeleton = if (dateTime.toLocalDate() == LocalDate.now()) clock else "EEEdMMM$clock"
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(dateTime)
    }

    /** For short complications, which fit about 7 characters: "14:30" today, "Wed" this week, otherwise "21 Oct". */
    fun shortTime(context: Context, dateTime: LocalDateTime): String {
        val date = dateTime.toLocalDate()
        val today = LocalDate.now()
        if (date == today) return time(context, dateTime)
        val skeleton = if (date.isAfter(today) && date.isBefore(today.plusDays(7))) "EEE" else "dMMM"
        val locale = Locale.getDefault()
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(dateTime)
    }

    fun clock(context: Context, millis: Long): String =
        time(context, DartTime.fromInstant(java.time.Instant.ofEpochMilli(millis)))

    /** The line under a reminder's title: when it notifies next, and whether it is snoozed or repeats. */
    fun itemSummary(context: Context, item: NoteItem): String {
        val parts = mutableListOf<String>()
        val snoozed = DartTime.parseOrNull(item.snoozeDateTime)
        val dateTime = DartTime.parseOrNull(item.dateTime)
        when {
            snoozed != null -> parts += context.getString(R.string.list_snoozed_until, time(context, snoozed))
            dateTime != null -> parts += time(context, dateTime)
            else -> parts += context.getString(R.string.list_now)
        }
        item.repetitionData?.let { parts += repeatLabel(item) }
        return parts.joinToString(" · ")
    }

    /** The phone's own wording when it sent one, otherwise the same wording worked out here (English, like the phone). */
    fun repeatLabel(item: NoteItem): String = item.repeatLabel ?: item.repetitionData?.let(::repeatLabel).orEmpty()

    fun repeatLabel(repetition: RepetitionData): String {
        val n = repetition.number
        val interval = if (n == 1) {
            when (repetition.repetition) {
                Repetition.HOURLY -> "hourly"
                Repetition.DAILY -> "daily"
                Repetition.WEEKLY -> "weekly"
                Repetition.MONTHLY -> "monthly"
                Repetition.YEARLY -> "yearly"
            }
        } else {
            val unit = when (repetition.repetition) {
                Repetition.HOURLY -> "hours"
                Repetition.DAILY -> "days"
                Repetition.WEEKLY -> "weeks"
                Repetition.MONTHLY -> "months"
                Repetition.YEARLY -> "years"
            }
            "every $n $unit"
        }
        if (!repetition.hasWeekdays) return interval
        return "$interval (${weekdaysInLocaleOrder().filter { it.value in repetition.weekdays }.joinToString(", ") { shortDay(it) }})"
    }

    fun weekdaysInLocaleOrder(): List<DayOfWeek> {
        val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        return (0L until 7L).map { first.plus(it) }
    }

    fun shortDay(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}
