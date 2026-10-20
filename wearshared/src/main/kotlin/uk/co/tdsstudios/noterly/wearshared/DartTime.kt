package uk.co.tdsstudios.noterly.wearshared

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reads and writes times the way the phone's Dart code does. Dart's `DateTime.toIso8601String()` on a local time has
 * no offset ("2026-10-19T09:00:00.000"), so these are local times in the phone's zone, which is also the watch's.
 */
object DartTime {
    private val base = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

    fun parse(value: String, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime {
        val hasOffset = value.endsWith("Z") || Regex("[+-]\\d{2}:?\\d{2}$").containsMatchIn(value.substringAfter('T'))
        if (hasOffset) return OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDateTime()
        return LocalDateTime.parse(value)
    }

    fun parseOrNull(value: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? =
        value?.let { runCatching { parse(it, zone) }.getOrNull() }

    /** Same output as Dart: milliseconds always, microseconds only when not zero. */
    fun format(value: LocalDateTime): String {
        val micros = (value.nano / 1_000) % 1_000
        return base.format(value) + if (micros != 0) "%03d".format(micros) else ""
    }

    fun toInstant(value: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Instant = value.atZone(zone).toInstant()

    fun toEpochMillis(value: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long = toInstant(value, zone).toEpochMilli()

    fun fromInstant(value: Instant, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime = LocalDateTime.ofInstant(value, zone)
}
