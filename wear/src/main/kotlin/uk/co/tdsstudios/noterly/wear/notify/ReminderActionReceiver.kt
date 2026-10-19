package uk.co.tdsstudios.noterly.wear.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.co.tdsstudios.noterly.wear.repository

/** 'Mark as done' and 'Snooze' on a notification the watch showed by itself. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(ReminderAlarms.EXTRA_ITEM_ID) ?: return
        val result = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                WatchNotifications.cancel(context, itemId)
                when (intent.action) {
                    ACTION_DONE -> context.repository.markDone(itemId)
                    ACTION_SNOOZE -> context.repository.snooze(itemId)
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "uk.co.tdsstudios.noterly.wear.DONE"
        const val ACTION_SNOOZE = "uk.co.tdsstudios.noterly.wear.SNOOZE"
    }
}
