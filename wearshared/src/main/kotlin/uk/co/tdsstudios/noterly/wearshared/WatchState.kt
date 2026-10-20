package uk.co.tdsstudios.noterly.wearshared

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * What the watch shows: the last snapshot from the phone with the commands the phone has not applied yet laid on
 * top, so a reminder marked as done on the watch leaves the list straight away.
 */
object WatchState {
    /** Commands the phone has acknowledged in [snapshot], which can be dropped from the queue. */
    fun acknowledged(snapshot: Snapshot, pending: List<Command>): Set<String> {
        val applied = snapshot.appliedCommands.toSet()
        return pending.map { it.commandId }.filter { it in applied }.toSet()
    }

    fun overlay(snapshot: Snapshot, pending: List<Command>, zone: ZoneId = ZoneId.systemDefault()): List<NoteItem> {
        val applied = snapshot.appliedCommands.toSet()
        var items = snapshot.items
        for (command in pending) {
            if (command.commandId in applied) continue
            items = apply(items, command, snapshot.settings, zone)
        }
        return items
    }

    /** Applies [command] the same way the phone will, see WearCommandHandler in the phone app. */
    fun apply(items: List<NoteItem>, command: Command, settings: SnapshotSettings, zone: ZoneId = ZoneId.systemDefault()): List<NoteItem> {
        val issuedAt = DartTime.parse(command.issuedAt, zone)
        val payload = command.payload
        val existing = items.firstOrNull { it.id == command.itemId }

        fun replace(item: NoteItem) = items.map { if (it.id == item.id) item else it }

        return when (command.type) {
            CommandType.DONE -> {
                val item = existing ?: return items
                if (item.archived) return items
                if (item.isRepeating) {
                    val seen = DartTime.parseOrNull(payload.seenDateTime, zone)
                    val current = DartTime.parseOrNull(item.dateTime, zone)
                    // Already moved on (the phone marked it as done too): do not skip a second repeat
                    if (seen != null && current != null && current.isAfter(seen)) return items
                    replace(markRepeatDone(item, issuedAt, zone))
                } else {
                    // Like the phone's archive, which keeps the snooze, so a restore notifies the same way on both
                    replace(item.copy(archived = true, archivedDateTime = command.issuedAt))
                }
            }

            CommandType.SNOOZE -> {
                val item = existing ?: return items
                if (item.archived) return items
                val until = DartTime.fromInstant(DartTime.toInstant(issuedAt, zone).plus(Duration.ofSeconds(settings.snoozeSeconds)), zone)
                replace(item.copy(snoozeDateTime = DartTime.format(until)))
            }

            CommandType.CREATE -> {
                if (existing != null) return items
                items + NoteItem(
                    id = command.itemId,
                    title = payload.title.orEmpty(),
                    body = payload.body.orEmpty(),
                    colour = payload.colour ?: NoterlyColours.random(),
                    dateTime = payload.dateTime,
                    repetitionData = payload.repetitionData,
                    dismissible = false,
                    updatedAt = command.issuedAt,
                )
            }

            CommandType.UPDATE -> {
                val item = existing ?: return items
                val updatedAt = DartTime.parseOrNull(item.updatedAt, zone)
                if (updatedAt != null && updatedAt.isAfter(issuedAt)) return items
                val repeatChanged = item.repetitionData != payload.repetitionData || item.dateTime != payload.dateTime
                replace(
                    item.copy(
                        title = payload.title ?: item.title,
                        body = payload.body ?: item.body,
                        colour = payload.colour ?: item.colour,
                        dateTime = payload.dateTime,
                        repetitionData = payload.repetitionData,
                        updatedAt = command.issuedAt,
                        upcoming = if (repeatChanged) emptyList() else item.upcoming,
                        repeatLabel = if (item.repetitionData != payload.repetitionData) null else item.repeatLabel,
                    ),
                )
            }

            CommandType.DELETE -> items.filterNot { it.id == command.itemId }

            CommandType.RESTORE -> {
                val item = existing ?: return items
                replace(item.copy(archived = false, archivedDateTime = null))
            }

            else -> items
        }
    }

    /** Clears the snooze and moves the dateTime to the next repeat in the future, like the phone's 'Mark as done'. */
    fun markRepeatDone(item: NoteItem, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): NoteItem {
        val repetition = item.repetitionData ?: return item
        val start = DartTime.parseOrNull(item.dateTime, zone) ?: now
        // Done before the repeat is due counts for that repeat, so always move on at least once
        val next = Recurrence.advancePast(repetition, Recurrence.next(repetition, start, zone), now, zone)
        return item.copy(
            dateTime = DartTime.format(next),
            snoozeDateTime = null,
            upcoming = Recurrence.upcoming(repetition, next, now, UPCOMING_COUNT, zone).map(DartTime::format),
        )
    }

    /** Active items in the order of their next notification: immediate ones first, then by time. */
    fun activeSorted(items: List<NoteItem>, zone: ZoneId = ZoneId.systemDefault()): List<NoteItem> =
        items.filterNot { it.archived }.sortedWith(compareBy<NoteItem>({ nextDue(it, zone) != null }, { nextDue(it, zone) }))

    /** Archived items, most recently done first. */
    fun archivedSorted(items: List<NoteItem>, zone: ZoneId = ZoneId.systemDefault()): List<NoteItem> =
        items.filter { it.archived }.sortedByDescending { DartTime.parseOrNull(it.archivedDateTime, zone) }

    /** When the item next notifies: the end of its snooze, or its dateTime. Null for an immediate item. */
    fun nextDue(item: NoteItem, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? =
        DartTime.parseOrNull(item.snoozeDateTime, zone) ?: DartTime.parseOrNull(item.dateTime, zone)

    /**
     * When the watch should notify by itself while the phone is away, or null if not in the future. Like the phone, a
     * reminder notifies once, at the end of its snooze or else at its dateTime, and its next repeat is only scheduled
     * once it is marked as done.
     */
    fun fireTime(item: NoteItem, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
        if (item.archived) return null
        val due = nextDue(item, zone) ?: return null
        return due.takeIf { Recurrence.isBefore(now, it, zone) }
    }

    /** Notification ID for the watch's own notifications: from the UUID, not Dart's String.hashCode. */
    fun notificationId(itemId: String): Int {
        val bits = runCatching { UUID.fromString(itemId) }.map { it.mostSignificantBits xor it.leastSignificantBits }.getOrElse { itemId.hashCode().toLong() }
        return (bits xor (bits ushr 32)).toInt()
    }

    /**
     * Whether a reminder the watch just created, changed or restored should be shown by the watch straight away while
     * the phone is away: an active one with no time, or whose time or snooze has already passed, which the phone would
     * also show at once.
     */
    fun notifiesImmediately(item: NoteItem?, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (item == null || item.archived) return false
        val due = nextDue(item, zone) ?: return true
        return !Recurrence.isBefore(now, due, zone)
    }

    /**
     * Whether one of the watch's own notifications is out of date: the reminder was done or deleted, or now notifies
     * later. [shownDueMillis] is the send it was shown for, null for an immediate reminder.
     */
    fun ownNotificationStale(item: NoteItem?, shownDueMillis: Long?, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (item == null || item.archived) return true
        val due = nextDue(item, zone)?.let { DartTime.toEpochMillis(it, zone) }
        return if (shownDueMillis == null) due != null && due > nowMillis else due == null || due > shownDueMillis
    }

    /**
     * Bridge tag of the phone notification for one send of an item, see BridgingManager on the watch. Immediate
     * reminders have no send time and use [IMMEDIATE_TAG] instead.
     */
    fun bridgeTag(itemId: String, fireEpochMillis: Long?) = "noterly:$itemId:${fireEpochMillis ?: IMMEDIATE_TAG}"

    const val IMMEDIATE_TAG = "immediate"

    const val UPCOMING_COUNT = 5
}

/** The 12 colours of the phone's colour picker (lib/widgets/colour_picker.dart), Material primary 500 values. */
object NoterlyColours {
    val all: List<Long> = listOf(
        0xFFF44336, // red
        0xFF4CAF50, // green
        0xFF2196F3, // blue
        0xFF9C27B0, // purple
        0xFFE91E63, // pink
        0xFFFF9800, // orange
        0xFFFFEB3B, // yellow
        0xFF009688, // teal
        0xFF00BCD4, // cyan
        0xFF3F51B5, // indigo
        0xFF795548, // brown
        0xFF9E9E9E, // grey
    )

    fun random(random: kotlin.random.Random = kotlin.random.Random.Default): Long = all.random(random)

    /** A different colour from [current], for tapping the colour dot. */
    fun nextRandom(current: Long, random: kotlin.random.Random = kotlin.random.Random.Default): Long =
        all.filter { it != current }.random(random)
}

/** Builds commands with a fresh ID. */
object Commands {
    fun new(type: String, itemId: String, baseRev: Long, now: LocalDateTime, payload: CommandPayload = CommandPayload()) =
        Command(
            commandId = UUID.randomUUID().toString(),
            type = type,
            itemId = itemId,
            baseRev = baseRev,
            issuedAt = DartTime.format(now),
            payload = payload,
        )
}
