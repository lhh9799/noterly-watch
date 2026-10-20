package uk.co.tdsstudios.noterly.wear.sync

import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import uk.co.tdsstudios.noterly.wear.repository
import uk.co.tdsstudios.noterly.wearshared.WearPaths

/** Receives snapshots from the phone, and hears when the phone connects or goes away. */
class WatchListenerService : WearableListenerService() {
    // Callbacks run on a background thread of the service, so blocking here is fine
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val snapshots = dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WearPaths.SNAPSHOT }
            .mapNotNull { DataMapItem.fromDataItem(it.dataItem).dataMap.getString(WearPaths.KEY_JSON) }
        runBlocking { snapshots.forEach { repository.onSnapshotJson(it) } }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name != WearPaths.CAPABILITY_PHONE) return
        val connected = capabilityInfo.nodes.isNotEmpty()
        repository.onPhoneReachabilityChanged(connected)
        // Back in reach: ask for the latest list. Commands queued meanwhile are delivered by the Data Layer, and queued
        // again in case it dropped them.
        if (connected) runBlocking { repository.refreshConnection(requestSnapshot = true) }
    }
}
