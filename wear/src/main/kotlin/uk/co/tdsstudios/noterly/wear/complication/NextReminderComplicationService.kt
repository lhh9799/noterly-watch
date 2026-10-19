package uk.co.tdsstudios.noterly.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import uk.co.tdsstudios.noterly.wear.R
import uk.co.tdsstudios.noterly.wear.repository
import uk.co.tdsstudios.noterly.wear.ui.Format
import uk.co.tdsstudios.noterly.wear.ui.MainActivity
import uk.co.tdsstudios.noterly.wearshared.WatchState

/** The time and title of the next reminder, for the watch face. */
class NextReminderComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val active = repository.current().active
        // The next scheduled one; immediate reminders have no time to show
        val next = active.firstOrNull { WatchState.nextDue(it) != null } ?: active.firstOrNull()
        val due = next?.let { WatchState.nextDue(it) }
        val time = due?.let { Format.time(this, it) } ?: if (next != null) getString(R.string.list_now) else "—"
        val shortTime = due?.let { Format.shortTime(this, it) } ?: time
        val title = next?.title ?: getString(R.string.tile_empty)
        val tap = PendingIntent.getActivity(
            this, 0,
            next?.let { MainActivity.detailIntent(this, it.id) } ?: MainActivity.newNoteIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return build(request.complicationType, time, shortTime, title, tap)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, "09:00", "09:00", "Gym", null)

    private fun build(type: ComplicationType, time: String, shortTime: String, title: String, tap: PendingIntent?): ComplicationData? {
        val description = PlainComplicationText.Builder("$time $title").build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(PlainComplicationText.Builder(shortTime).build(), description)
                .setTitle(PlainComplicationText.Builder(title).build())
                .setTapAction(tap)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(PlainComplicationText.Builder(title).build(), description)
                .setTitle(PlainComplicationText.Builder(time).build())
                .setTapAction(tap)
                .build()

            else -> null
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, NextReminderComplicationService::class.java)).requestUpdateAll()
            }
        }
    }
}
