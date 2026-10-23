package uk.co.tdsstudios.noterly.wear.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.co.tdsstudios.noterly.wear.repository
import uk.co.tdsstudios.noterly.wearshared.DartTime
import uk.co.tdsstudios.noterly.wearshared.WatchState
import kotlin.math.abs

/** A reminder is due. The watch notifies it whether or not the phone is connected. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(ReminderAlarms.EXTRA_ITEM_ID) ?: return
        val dueMillis = intent.getLongExtra(ReminderAlarms.EXTRA_DUE_MILLIS, 0)
        val result = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repository = context.repository
                val view = repository.current()
                val item = view.item(itemId)
                val due = item?.let { WatchState.nextDue(it) }?.let { DartTime.toEpochMillis(it) }

                // Skip if it was done, removed or moved since the alarm was set
                val stillDue = item != null && !item.archived && due != null && abs(due - dueMillis) < 60_000
                if (stillDue) {
                    WatchNotifications.show(context, item!!, view.settings, dueMillis)
                    repository.recordOwnNotification(itemId, dueMillis)
                }
                repository.refreshOutputs()
            } finally {
                result.finish()
            }
        }
    }
}
