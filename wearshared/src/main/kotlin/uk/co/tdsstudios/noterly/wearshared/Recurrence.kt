package uk.co.tdsstudios.noterly.wearshared

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * Port of the phone's repeat calculation (`NotificationItem.nextRepeatDateTime` and
 * `RepetitionData.nextWeekdayDateTime`). It has to give the same answers as the Dart code, including its quirks:
 *
 * - hourly, daily and plain weekly repeats add a fixed Duration, so across a daylight saving change the time of day
 *   moves by an hour, like Dart's `DateTime.add`;
 * - weekday repeats add calendar days, so the time of day is kept;
 * - monthly and yearly repeats build `DateTime(year, month + n, day)`, which has no time part (midnight) and rolls an
 *   overflowing day into the next month (31 Jan + 1 month = 3 Mar in a non-leap year).
 *
 * Results are local times in [zone]; comparisons are done on instants.
 */
object Recurrence {
    fun next(repetition: RepetitionData, lastSent: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime {
        val n = repetition.number.toLong()
        return when (repetition.repetition) {
            Repetition.HOURLY -> plusDuration(lastSent, Duration.ofHours(n), zone)
            Repetition.DAILY -> plusDuration(lastSent, Duration.ofDays(n), zone)
            Repetition.WEEKLY ->
                if (repetition.hasWeekdays) nextWeekday(repetition, lastSent)
                else plusDuration(lastSent, Duration.ofDays(n * 7), zone)
            Repetition.MONTHLY -> dartDate(lastSent.year, lastSent.monthValue + repetition.number, lastSent.dayOfMonth)
            Repetition.YEARLY -> dartDate(lastSent.year + repetition.number, lastSent.monthValue, lastSent.dayOfMonth)
        }
    }

    /** `nextRepeatDateTime` for an item: based on its dateTime, or now when it has none. */
    fun nextForItem(item: NoteItem, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
        val repetition = item.repetitionData ?: return null
        val lastSent = DartTime.parseOrNull(item.dateTime, zone) ?: now
        return next(repetition, lastSent, zone)
    }

    /**
     * What marking a repeating item as done does on the phone (`updateRepeatingNotification`): keep moving the
     * dateTime to the next repeat until it is in the future.
     */
    fun advancePast(repetition: RepetitionData, start: LocalDateTime, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime {
        var dateTime = start
        var guard = 0
        while (isBefore(dateTime, now, zone) && guard++ < 100_000) {
            dateTime = next(repetition, dateTime, zone)
        }
        return dateTime
    }

    /** The next [count] send times from [start] (included when it is after [now]). */
    fun upcoming(repetition: RepetitionData, start: LocalDateTime, now: LocalDateTime, count: Int, zone: ZoneId = ZoneId.systemDefault()): List<LocalDateTime> {
        val result = mutableListOf<LocalDateTime>()
        var dateTime = advancePast(repetition, start, now, zone)
        while (result.size < count) {
            result += dateTime
            dateTime = next(repetition, dateTime, zone)
        }
        return result
    }

    /** `RepetitionData.alignToWeekdays`: the same time on the first selected weekday on or after [dateTime]. */
    fun alignToWeekdays(repetition: RepetitionData, dateTime: LocalDateTime): LocalDateTime {
        if (!repetition.hasWeekdays) return dateTime
        for (i in 0 until 7) {
            val candidate = addDays(dateTime, i.toLong())
            if (candidate.dayOfWeek.value in repetition.weekdays) return candidate
        }
        return dateTime
    }

    private fun nextWeekday(repetition: RepetitionData, lastSent: LocalDateTime): LocalDateTime {
        val number = repetition.number
        val lastSentWeek = startOfWeek(lastSent)
        for (i in 1..7 * number) {
            val candidate = addDays(lastSent, i.toLong())
            if (candidate.dayOfWeek.value !in repetition.weekdays) continue
            val weeks = (ChronoUnit.DAYS.between(lastSentWeek, startOfWeek(candidate)) / 7.0).roundToLong()
            if (weeks % number == 0L) return candidate
        }
        return addDays(lastSent, 7L * number) // Not reached while at least one weekday is selected
    }

    // Dart's DateTime(y, m, d + days, h, min, s): calendar days, and the fraction of a second is dropped
    private fun addDays(dateTime: LocalDateTime, days: Long): LocalDateTime =
        dateTime.toLocalDate().plusDays(days).atTime(dateTime.hour, dateTime.minute, dateTime.second)

    private fun startOfWeek(dateTime: LocalDateTime): LocalDate =
        dateTime.toLocalDate().minusDays((dateTime.dayOfWeek.value - 1).toLong())

    // Dart's DateTime(year, month, day) normalises an overflowing month or day instead of failing
    private fun dartDate(year: Int, month: Int, day: Int): LocalDateTime =
        LocalDate.of(year, 1, 1).plusMonths((month - 1).toLong()).plusDays((day - 1).toLong()).atStartOfDay()

    private fun plusDuration(dateTime: LocalDateTime, duration: Duration, zone: ZoneId): LocalDateTime =
        DartTime.fromInstant(DartTime.toInstant(dateTime, zone).plus(duration), zone)

    fun isBefore(a: LocalDateTime, b: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        DartTime.toInstant(a, zone).isBefore(DartTime.toInstant(b, zone))
}
