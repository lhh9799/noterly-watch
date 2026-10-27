package uk.co.tdsstudios.noterly.wear.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import uk.co.tdsstudios.noterly.wear.R
import uk.co.tdsstudios.noterly.wear.ui.MainActivity
import uk.co.tdsstudios.noterly.wearshared.NoteItem
import uk.co.tdsstudios.noterly.wearshared.SnapshotSettings
import uk.co.tdsstudios.noterly.wearshared.WatchState

/** The watch's own notifications. The phone's are not bridged over, so these are the only ones on the watch. */
object WatchNotifications {
    private const val CHANNEL_ID = "reminders"
    private const val EXTRA_DUE_MILLIS = "noterly.dueMillis"
    private const val EXTRA_ITEM_ID = "noterly.itemId"
    private const val EXTRA_IMMEDIATE = "noterly.immediate"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Shows [item] for its send at [dueMillis], or, if null, as an immediate reminder shown now. */
    fun show(context: Context, item: NoteItem, settings: SnapshotSettings, dueMillis: Long?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return

        val id = WatchState.notificationId(item.id)
        val open = PendingIntent.getActivity(
            context, id, MainActivity.detailIntent(context, item.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon_48)
            .setContentTitle(item.title)
            .setContentText(item.body.ifBlank { null })
            .setColor(item.colour.toInt())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // Same rule as the phone: only dismissible reminders can be swiped away, and only if allowed in Settings
            .setOngoing(!(settings.dismissibleEnabled && item.dismissible))
            .setAutoCancel(false)
            .setWhen(dueMillis ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setLocalOnly(true)
            .setContentIntent(open)
            .addExtras(android.os.Bundle().apply {
                putString(EXTRA_ITEM_ID, item.id)
                if (dueMillis == null) putBoolean(EXTRA_IMMEDIATE, true) else putLong(EXTRA_DUE_MILLIS, dueMillis)
            })
            .addAction(0, context.getString(R.string.detail_done), action(context, item.id, ReminderActionReceiver.ACTION_DONE))
            .addAction(0, context.getString(R.string.detail_snooze), action(context, item.id, ReminderActionReceiver.ACTION_SNOOZE))
            .build()

        manager.notify(id, notification)
    }

    fun cancel(context: Context, itemId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(WatchState.notificationId(itemId))
    }

    /**
     * Removes the watch's own notifications for reminders that were done, deleted or moved to a later time, for
     * example on the phone after it reconnected. An immediate one stays until then, although it has no send time.
     */
    fun cancelStale(context: Context, items: List<NoteItem>, settings: SnapshotSettings) {
        val manager = context.getSystemService(NotificationManager::class.java)
        for (shown in manager.activeNotifications) {
            val extras = shown.notification.extras
            val itemId = extras.getString(EXTRA_ITEM_ID) ?: continue
            val dueMillis = if (extras.getBoolean(EXTRA_IMMEDIATE)) null else extras.getLong(EXTRA_DUE_MILLIS)
            val item = items.firstOrNull { it.id == itemId }
            val turnedOff = item != null && !WatchState.notifiesOnWatch(item, settings)
            if (turnedOff || WatchState.ownNotificationStale(item, dueMillis, System.currentTimeMillis())) manager.cancel(shown.id)
        }
    }

    private fun action(context: Context, itemId: String, action: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .setData(android.net.Uri.parse("noterly://item/$itemId"))
            .putExtra(ReminderAlarms.EXTRA_ITEM_ID, itemId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
