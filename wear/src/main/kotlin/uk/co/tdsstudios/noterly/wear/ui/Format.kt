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
import java.time.temporal.ChronoUnit
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

    /**
     * When something happened, like the phone app's archive: "today, 14:30", "yesterday, 14:30", the day of the week
     * in the last week, otherwise the date.
     */
    fun pastTime(context: Context, dateTime: LocalDateTime): String {
        val locale = Locale.getDefault()
        val clock = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        fun format(skeleton: String) =
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(dateTime)
        return when (ChronoUnit.DAYS.between(dateTime.toLocalDate(), LocalDate.now())) {
            0L -> context.getString(R.string.archive_today, format(clock))
            1L -> context.getString(R.string.archive_yesterday, format(clock))
            // The phone's wording: the best pattern for both puts the Korean day in brackets
            in 2L..6L -> context.getString(R.string.archive_day_and_time, format("EEEE"), format(clock))
            else -> time(context, dateTime)
        }
    }

    /** The phone's own wording when it sent one (in the phone app's language), otherwise the same wording worked out here. */
    fun repeatLabel(context: Context, item: NoteItem): String =
        item.repeatLabel ?: item.repetitionData?.let { repeatLabel(context, it) }.orEmpty()

    fun repeatLabel(context: Context, repetition: RepetitionData): String {
        val n = repetition.number
        val interval = if (n == 1) {
            context.getString(
                when (repetition.repetition) {
                    Repetition.HOURLY -> R.string.repeat_summary_hourly
                    Repetition.DAILY -> R.string.repeat_summary_daily
                    Repetition.WEEKLY -> R.string.repeat_summary_weekly
                    Repetition.MONTHLY -> R.string.repeat_summary_monthly
                    Repetition.YEARLY -> R.string.repeat_summary_yearly
                },
            )
        } else {
            context.resources.getQuantityString(
                when (repetition.repetition) {
                    Repetition.HOURLY -> R.plurals.repeat_summary_every_hours
                    Repetition.DAILY -> R.plurals.repeat_summary_every_days
                    Repetition.WEEKLY -> R.plurals.repeat_summary_every_weeks
                    Repetition.MONTHLY -> R.plurals.repeat_summary_every_months
                    Repetition.YEARLY -> R.plurals.repeat_summary_every_years
                },
                n,
                n,
            )
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
