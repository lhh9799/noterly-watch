package uk.co.tdsstudios.noterly.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import uk.co.tdsstudios.noterly.wearshared.Command
import uk.co.tdsstudios.noterly.wearshared.Snapshot
import uk.co.tdsstudios.noterly.wearshared.WatchState
import uk.co.tdsstudios.noterly.wearshared.WearJson

private val Context.watchDataStore: DataStore<Preferences> by preferencesDataStore(name = "watch")

/** What the watch keeps between runs: the last snapshot and the commands the phone has not applied yet. */
data class StoredData(
    val snapshot: Snapshot?,
    val pending: List<Command>,
    /** When the last snapshot arrived, in epoch millis, or 0. */
    val lastSyncMillis: Long,
    /** Tags of the sends the watch has notified, so each is shown once. */
    val firedTags: List<String>,
    /** Items that have an alarm set for the watch's own notification. */
    val scheduledIds: List<String>,
)

class WatchStore(private val context: Context) {
    private val snapshotKey = stringPreferencesKey("snapshot")
    private val pendingKey = stringPreferencesKey("pending")
    private val lastSyncKey = longPreferencesKey("lastSync")
    private val firedTagsKey = stringPreferencesKey("firedTags")
    private val scheduledKey = stringPreferencesKey("scheduled")

    private val commandList = ListSerializer(Command.serializer())
    private val stringList = ListSerializer(String.serializer())

    val data: Flow<StoredData> = context.watchDataStore.data.map { prefs ->
        StoredData(
            snapshot = prefs[snapshotKey]?.let { runCatching { WearJson.decodeFromString(Snapshot.serializer(), it) }.getOrNull() },
            pending = prefs[pendingKey]?.let { runCatching { WearJson.decodeFromString(commandList, it) }.getOrNull() }.orEmpty(),
            lastSyncMillis = prefs[lastSyncKey] ?: 0L,
            firedTags = prefs[firedTagsKey]?.let { runCatching { WearJson.decodeFromString(stringList, it) }.getOrNull() }.orEmpty(),
            scheduledIds = prefs[scheduledKey]?.let { runCatching { WearJson.decodeFromString(stringList, it) }.getOrNull() }.orEmpty(),
        )
    }

    /**
     * Stores a snapshot from the phone and drops the commands it says were applied. A snapshot older than the stored
     * one is ignored (see [WatchState.replacesSnapshot]). Returns false when ignored.
     */
    suspend fun saveSnapshot(snapshot: Snapshot, receivedAtMillis: Long): Boolean {
        var saved = false
        context.watchDataStore.edit { prefs ->
            val current = prefs[snapshotKey]?.let { runCatching { WearJson.decodeFromString(Snapshot.serializer(), it) }.getOrNull() }
            prefs[lastSyncKey] = receivedAtMillis
            if (!WatchState.replacesSnapshot(current, snapshot)) return@edit

            prefs[snapshotKey] = WearJson.encodeToString(Snapshot.serializer(), snapshot)
            val pending = prefs[pendingKey]?.let { runCatching { WearJson.decodeFromString(commandList, it) }.getOrNull() }.orEmpty()
            val acknowledged = WatchState.acknowledged(snapshot, pending)
            prefs[pendingKey] = WearJson.encodeToString(commandList, pending.filterNot { it.commandId in acknowledged })
            saved = true
        }
        return saved
    }

    suspend fun addPending(command: Command) {
        context.watchDataStore.edit { prefs ->
            val pending = prefs[pendingKey]?.let { runCatching { WearJson.decodeFromString(commandList, it) }.getOrNull() }.orEmpty()
            prefs[pendingKey] = WearJson.encodeToString(commandList, pending + command)
        }
    }

    suspend fun addFiredTag(tag: String): List<String> {
        var tags = emptyList<String>()
        context.watchDataStore.edit { prefs ->
            val current = prefs[firedTagsKey]?.let { runCatching { WearJson.decodeFromString(stringList, it) }.getOrNull() }.orEmpty()
            tags = (current + tag).distinct().takeLast(MAX_FIRED_TAGS)
            prefs[firedTagsKey] = WearJson.encodeToString(stringList, tags)
        }
        return tags
    }

    /** Forgets the notified sends of reminders that are no longer active, so a restored one notifies again. */
    suspend fun forgetFiredTags(keep: (String) -> Boolean) {
        context.watchDataStore.edit { prefs ->
            val current = prefs[firedTagsKey]?.let { runCatching { WearJson.decodeFromString(stringList, it) }.getOrNull() } ?: return@edit
            val kept = current.filter(keep)
            if (kept.size != current.size) prefs[firedTagsKey] = WearJson.encodeToString(stringList, kept)
        }
    }

    suspend fun setScheduledIds(ids: List<String>) {
        context.watchDataStore.edit { prefs -> prefs[scheduledKey] = WearJson.encodeToString(stringList, ids) }
    }

    private companion object {
        const val MAX_FIRED_TAGS = 200
    }
}
