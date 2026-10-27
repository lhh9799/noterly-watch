package uk.co.tdsstudios.noterly.wear.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import uk.co.tdsstudios.noterly.wearshared.DartTime
import uk.co.tdsstudios.noterly.wearshared.NoteItem
import uk.co.tdsstudios.noterly.wearshared.SnapshotSettings
import uk.co.tdsstudios.noterly.wearshared.WatchState
import java.time.LocalDateTime

/**
 * Sets an exact alarm for the next send of every active reminder. Whether the watch actually notifies is decided
 * when the alarm goes off: only if the phone is not connected (otherwise the phone's own notification is bridged
 * over), so the same reminder never rings twice. Reminders that are not shown on the watch get no alarm.
 */
object ReminderAlarms {
    const val EXTRA_ITEM_ID = "itemId"
    const val EXTRA_DUE_MILLIS = "dueMillis"

    /** Returns the IDs that now have an alarm. */
    fun reschedule(context: Context, items: List<NoteItem>, settings: SnapshotSettings, previouslyScheduled: List<String>): List<String> {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        val scheduled = mutableListOf<String>()

        for (item in items) {
            val fireAt = if (WatchState.notifiesOnWatch(item, settings)) WatchState.fireTime(item, now) else null
            if (fireAt == null) {
                cancel(context, item.id)
                continue
            }
            val millis = DartTime.toEpochMillis(fireAt)
            val operation = pendingIntent(context, item.id, millis, PendingIntent.FLAG_UPDATE_CURRENT)!!
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
            }
            scheduled += item.id
        }

        val ids = items.map { it.id }.toSet()
        previouslyScheduled.filter { it !in ids }.forEach { cancel(context, it) }
        return scheduled
    }

    private fun cancel(context: Context, itemId: String) {
        val operation = pendingIntent(context, itemId, 0, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(operation)
        operation.cancel()
    }

    private fun pendingIntent(context: Context, itemId: String, dueMillis: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setData(Uri.parse("noterly://alarm/$itemId")) // One alarm per item
            .putExtra(EXTRA_ITEM_ID, itemId)
            .putExtra(EXTRA_DUE_MILLIS, dueMillis)
        return PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }
}
