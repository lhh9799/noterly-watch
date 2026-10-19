package uk.co.tdsstudios.noterly.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import uk.co.tdsstudios.noterly.wear.R
import uk.co.tdsstudios.noterly.wear.repository
import uk.co.tdsstudios.noterly.wear.ui.Format
import uk.co.tdsstudios.noterly.wear.ui.MainActivity
import uk.co.tdsstudios.noterly.wearshared.NoteItem
import uk.co.tdsstudios.noterly.wearshared.WatchState

/** The next three reminders and a 'New' button. */
class NoterlyTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> = scope.future {
        val next = repository.current().active.take(3)
        val layout = materialScope(this@NoterlyTileService, requestParams.deviceConfiguration) {
            primaryLayout(
                titleSlot = { text(getString(R.string.tile_label).layoutString) },
                mainSlot = {
                    if (next.isEmpty()) {
                        text(getString(R.string.tile_empty).layoutString, maxLines = 2)
                    } else {
                        LayoutElementBuilders.Column.Builder()
                            .setWidth(expand())
                            .apply {
                                next.forEach { addContent(text(line(it).layoutString, typography = Typography.BODY_MEDIUM, maxLines = 1)) }
                            }
                            .build()
                    }
                },
                bottomSlot = {
                    textEdgeButton(onClick = launch(newNote = true), labelContent = { text(getString(R.string.tile_new).layoutString) })
                },
                onClick = launch(newNote = false),
            )
        }

        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    @Deprecated("Kept for tile hosts that still ask for resources separately")
    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        scope.future { ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build() }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun line(item: NoteItem): String {
        val due = WatchState.nextDue(item)?.let { Format.time(this, it) } ?: getString(R.string.list_now)
        return "$due  ${item.title}"
    }

    private fun launch(newNote: Boolean): ModifiersBuilders.Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(MainActivity::class.java.name)
            .apply { if (newNote) addKeyToExtraMapping("new", ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build()) }
            .build()
        return ModifiersBuilders.Clickable.Builder()
            .setId(if (newNote) "new" else "open")
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build())
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val FRESHNESS_MS = 15 * 60 * 1000L

        fun requestUpdate(context: Context) {
            runCatching { getUpdater(context).requestUpdate(NoterlyTileService::class.java) }
        }
    }
}
