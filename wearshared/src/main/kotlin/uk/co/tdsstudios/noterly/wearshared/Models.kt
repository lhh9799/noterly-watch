package uk.co.tdsstudios.noterly.wearshared

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Mirrors the phone's `Repetition` enum. Stored by index, like the phone's data.json. */
enum class Repetition { HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY }

/** Mirrors the phone's `RepetitionData` (lib/models/repetition_data.dart). */
@Serializable
data class RepetitionData(
    val type: Int,
    val number: Int,
    /** 1 = Monday … 7 = Sunday. Only used by weekly repeats; empty means every [number] weeks on the same weekday. */
    val weekdays: List<Int> = emptyList(),
) {
    val repetition: Repetition get() = Repetition.entries[type]

    val hasWeekdays: Boolean get() = repetition == Repetition.WEEKLY && weekdays.isNotEmpty()

    companion object {
        fun of(repetition: Repetition, number: Int, weekdays: List<Int> = emptyList()) =
            RepetitionData(repetition.ordinal, number, if (repetition == Repetition.WEEKLY) weekdays.sorted() else emptyList())
    }
}

/**
 * One reminder. The fields up to [dismissible] are `NotificationItem.toJson()` from the phone as is; the rest are
 * added by the phone when it builds a snapshot.
 *
 * Times are Dart `toIso8601String()` strings in the phone's local time zone without an offset, see [DartTime].
 */
@Serializable
data class NoteItem(
    val id: String,
    val title: String,
    val body: String = "",
    /** ARGB. A Long because Dart writes it as an unsigned 32-bit value. */
    val colour: Long = 0xFF2196F3,
    val dateTime: String? = null,
    val repetitionData: RepetitionData? = null,
    val archived: Boolean = false,
    val archivedDateTime: String? = null,
    val snoozeDateTime: String? = null,
    val dismissible: Boolean = false,
    val updatedAt: String? = null,
    /** For repeating items, the next send times worked out by the phone. */
    val upcoming: List<String> = emptyList(),
    /** The repeat description in the phone's language, e.g. "weekly (Mon, Wed, Fri)". */
    val repeatLabel: String? = null,
) {
    val isImmediate: Boolean get() = dateTime == null
    val isRepeating: Boolean get() = repetitionData != null
    val isSnoozed: Boolean get() = snoozeDateTime != null
}

@Serializable
data class SnapshotSettings(
    val snoozeSeconds: Long = 3600,
    val dismissibleEnabled: Boolean = true,
    val dismissibleByDefault: Boolean = false,
)

@Serializable
data class Snapshot(
    val schema: Int = SCHEMA,
    val rev: Long = 0,
    val generatedAt: String? = null,
    val settings: SnapshotSettings = SnapshotSettings(),
    val items: List<NoteItem> = emptyList(),
    /** IDs of the most recent commands the phone has applied (or ignored), so the watch can drop them from its queue. */
    val appliedCommands: List<String> = emptyList(),
) {
    companion object {
        const val SCHEMA = 1
    }
}

object CommandType {
    const val DONE = "done"
    const val SNOOZE = "snooze"
    const val CREATE = "create"
    const val UPDATE = "update"
    const val DELETE = "delete"
    const val RESTORE = "restore"
}

/**
 * Values a command carries. Which fields are read depends on the type:
 * - done: [seenDateTime], the item's dateTime as the watch saw it, so a repeat is not advanced twice.
 * - create, update: [title], [body], [dateTime] (null = immediately), [repetitionData] (null = no repeat), [colour].
 */
@Serializable
data class CommandPayload(
    val seenDateTime: String? = null,
    val title: String? = null,
    val body: String? = null,
    val dateTime: String? = null,
    val repetitionData: RepetitionData? = null,
    val colour: Long? = null,
)

@Serializable
data class Command(
    val commandId: String,
    val type: String,
    val itemId: String,
    val baseRev: Long,
    val issuedAt: String,
    val payload: CommandPayload = CommandPayload(),
)

@OptIn(ExperimentalSerializationApi::class)
val WearJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
    coerceInputValues = true
}
