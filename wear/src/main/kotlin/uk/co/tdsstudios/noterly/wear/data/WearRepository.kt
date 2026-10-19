package uk.co.tdsstudios.noterly.wear.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.wear.phone.interactions.notifications.BridgingConfig
import androidx.wear.phone.interactions.notifications.BridgingManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.tdsstudios.noterly.wear.complication.NextReminderComplicationService
import uk.co.tdsstudios.noterly.wear.notify.ReminderAlarms
import uk.co.tdsstudios.noterly.wear.notify.WatchNotifications
import uk.co.tdsstudios.noterly.wear.tile.NoterlyTileService
import uk.co.tdsstudios.noterly.wearshared.Command
import uk.co.tdsstudios.noterly.wearshared.CommandPayload
import uk.co.tdsstudios.noterly.wearshared.CommandType
import uk.co.tdsstudios.noterly.wearshared.Commands
import uk.co.tdsstudios.noterly.wearshared.DartTime
import uk.co.tdsstudios.noterly.wearshared.NoteItem
import uk.co.tdsstudios.noterly.wearshared.RepetitionData
import uk.co.tdsstudios.noterly.wearshared.Snapshot
import uk.co.tdsstudios.noterly.wearshared.SnapshotSettings
import uk.co.tdsstudios.noterly.wearshared.WatchState
import uk.co.tdsstudios.noterly.wearshared.WearJson
import uk.co.tdsstudios.noterly.wearshared.WearPaths
import java.time.LocalDateTime
import java.util.UUID

/** What the screens, tile and complication show. */
data class WatchView(
    /** All items, archived included, with pending commands applied. */
    val items: List<NoteItem>,
    val settings: SnapshotSettings,
    val rev: Long,
    val hasSnapshot: Boolean,
    val lastSyncMillis: Long,
    val pendingIds: Set<String>,
) {
    val active: List<NoteItem> get() = WatchState.activeSorted(items)
    val archived: List<NoteItem> get() = WatchState.archivedSorted(items)

    fun item(id: String): NoteItem? = items.firstOrNull { it.id == id }
}

/**
 * The phone is the only source of truth. The watch keeps the phone's last snapshot, sends a command for every change
 * the user makes, and shows the commands the phone has not applied yet on top of the snapshot.
 */
class WearRepository private constructor(private val context: Context) {
    private val store = WatchStore(context)
    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val refreshMutex = Mutex()

    private val _phoneConnected = MutableStateFlow<Boolean?>(null)

    /** Whether a phone with Noterly is reachable. Null until first checked. */
    val phoneConnected: StateFlow<Boolean?> = _phoneConnected

    val view: Flow<WatchView> = store.data.map(::toView)

    suspend fun current(): WatchView = toView(store.data.first())

    private fun toView(data: StoredData): WatchView {
        val snapshot = data.snapshot ?: Snapshot()
        val acknowledged = WatchState.acknowledged(snapshot, data.pending)
        val pending = data.pending.filterNot { it.commandId in acknowledged }
        return WatchView(
            items = WatchState.overlay(snapshot, pending),
            settings = snapshot.settings,
            rev = snapshot.rev,
            hasSnapshot = data.snapshot != null,
            lastSyncMillis = data.lastSyncMillis,
            pendingIds = pending.map { it.itemId }.toSet(),
        )
    }

    // region Sync

    suspend fun onSnapshotJson(json: String) {
        val snapshot = runCatching { WearJson.decodeFromString(Snapshot.serializer(), json) }
            .onFailure { Log.w(TAG, "Unreadable snapshot", it) }
            .getOrNull() ?: return
        if (snapshot.schema > Snapshot.SCHEMA) Log.w(TAG, "Snapshot schema ${snapshot.schema} is newer than this app, reading what it can")

        if (store.saveSnapshot(snapshot, System.currentTimeMillis())) {
            _phoneConnected.value = true
            refreshOutputs()
        }
    }

    /** Reads the snapshot the Data Layer already holds, for when the app starts before any change event. */
    suspend fun loadStoredSnapshot() {
        runCatching {
            val items = dataClient.getDataItems(Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(WearPaths.SNAPSHOT).build()).await()
            try {
                items.mapNotNull { DataMapItem.fromDataItem(it).dataMap.getString(WearPaths.KEY_JSON) }
                    .forEach { onSnapshotJson(it) }
            } finally {
                items.release()
            }
        }.onFailure { Log.w(TAG, "Failed to read the stored snapshot", it) }
    }

    /** Checks whether the phone is reachable, and asks it for a fresh snapshot when it is. */
    suspend fun refreshConnection(requestSnapshot: Boolean = false): Boolean {
        val nodes = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
            runCatching {
                capabilityClient.getCapability(WearPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE).await().nodes
            }.getOrNull()
        }.orEmpty()
        val connected = nodes.isNotEmpty()
        _phoneConnected.value = connected

        if (connected && requestSnapshot) {
            for (node in nodes) {
                runCatching { messageClient.sendMessage(node.id, WearPaths.SYNC_REQUEST, ByteArray(0)).await() }
                    .onFailure { Log.w(TAG, "Failed to send a sync request to ${node.displayName}", it) }
            }
        }
        return connected
    }

    fun onPhoneReachabilityChanged(connected: Boolean) {
        _phoneConnected.value = connected
    }

    // endregion

    // region Commands

    suspend fun markDone(itemId: String) {
        val item = current().item(itemId) ?: return
        issue(CommandType.DONE, itemId, CommandPayload(seenDateTime = item.dateTime))
    }

    suspend fun snooze(itemId: String) = issue(CommandType.SNOOZE, itemId)

    suspend fun create(title: String, dateTime: LocalDateTime?, repetition: RepetitionData?, colour: Long): String {
        val id = UUID.randomUUID().toString()
        issue(CommandType.CREATE, id, ItemFields(title, "", dateTime, repetition, colour).toPayload())
        return id
    }

    suspend fun update(itemId: String, fields: ItemFields) = issue(CommandType.UPDATE, itemId, fields.toPayload())

    suspend fun delete(itemId: String) = issue(CommandType.DELETE, itemId)

    suspend fun restore(itemId: String) = issue(CommandType.RESTORE, itemId)

    private suspend fun issue(type: String, itemId: String, payload: CommandPayload = CommandPayload()) {
        val command = Commands.new(type, itemId, current().rev, LocalDateTime.now(), payload)
        store.addPending(command)
        send(command)
        refreshOutputs()
        if (type == CommandType.CREATE || type == CommandType.UPDATE || type == CommandType.RESTORE) notifyIfImmediate(itemId)
    }

    /**
     * The phone shows a reminder with no time as soon as it is saved. While the phone is away the watch does that
     * itself, and tags it so the phone's copy is not bridged over again once it reconnects.
     */
    private suspend fun notifyIfImmediate(itemId: String) {
        val view = current()
        val item = view.item(itemId)
        if (!WatchState.notifiesImmediately(item) || refreshConnection()) return
        WatchNotifications.show(context, item!!, view.settings, null)
        recordOwnNotification(itemId, null)
    }

    /**
     * Each command is its own DataItem, so it waits on the watch while the phone is away and is delivered when it
     * reconnects. The phone deletes it once applied.
     */
    private suspend fun send(command: Command) {
        val request = PutDataMapRequest.create(WearPaths.commandPath(command.commandId)).apply {
            dataMap.putString(WearPaths.KEY_JSON, WearJson.encodeToString(Command.serializer(), command))
        }.asPutDataRequest().setUrgent()
        runCatching { dataClient.putDataItem(request).await() }.onFailure { Log.w(TAG, "Failed to queue command ${command.type}", it) }
    }

    // endregion

    // region Outputs

    /** Re-sets alarms, removes stale watch notifications and refreshes the tile and complication. */
    suspend fun refreshOutputs() = refreshMutex.withLock {
        val view = current()
        val scheduled = ReminderAlarms.reschedule(context, view.items, store.data.first().scheduledIds)
        store.setScheduledIds(scheduled)
        WatchNotifications.cancelStale(context, view.items)
        NoterlyTileService.requestUpdate(context)
        NextReminderComplicationService.requestUpdate(context)
    }

    /**
     * Records a send the watch notified by itself, so the phone's copy of it is not bridged over as well. [dueMillis]
     * is null for an immediate reminder.
     */
    suspend fun recordOwnNotification(itemId: String, dueMillis: Long?) {
        val tags = store.addFiredTag(WatchState.bridgeTag(itemId, dueMillis))
        runCatching {
            BridgingManager.fromContext(context).setConfig(
                BridgingConfig.Builder(context, true).addExcludedTags(tags.toSet()).build(),
            )
        }.onFailure { Log.w(TAG, "Failed to update bridging config", it) }
    }

    // endregion

    companion object {
        private const val TAG = "WearRepository"
        private const val CONNECTION_TIMEOUT_MS = 5_000L

        @Volatile
        private var instance: WearRepository? = null

        fun get(context: Context): WearRepository =
            instance ?: synchronized(this) { instance ?: WearRepository(context.applicationContext).also { instance = it } }
    }
}

/** The editable fields of a reminder. Body is kept from the phone; the watch does not edit it. */
data class ItemFields(
    val title: String,
    val body: String,
    val dateTime: LocalDateTime?,
    val repetition: RepetitionData?,
    val colour: Long,
) {
    fun toPayload() = CommandPayload(
        title = title,
        body = body,
        dateTime = dateTime?.let(DartTime::format),
        repetitionData = repetition,
        colour = colour,
    )
}
