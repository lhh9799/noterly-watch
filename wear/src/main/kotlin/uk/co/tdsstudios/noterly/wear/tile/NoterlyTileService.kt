package uk.co.tdsstudios.noterly.wear.tile

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.ButtonDefaults.filledVariantButtonColors
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.background
import androidx.wear.protolayout.modifiers.clip
import androidx.wear.protolayout.modifiers.clipBottomLeft
import androidx.wear.protolayout.modifiers.clipBottomRight
import androidx.wear.protolayout.modifiers.clipTopLeft
import androidx.wear.protolayout.modifiers.clipTopRight
import androidx.wear.protolayout.modifiers.padding
import androidx.wear.protolayout.modifiers.toProtoLayoutModifiers
import androidx.wear.protolayout.types.LayoutColor
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
import uk.co.tdsstudios.noterly.wear.ui.PhoneColours
import uk.co.tdsstudios.noterly.wear.ui.badgeBackground
import uk.co.tdsstudios.noterly.wearshared.NoteItem
import uk.co.tdsstudios.noterly.wearshared.WatchState

/** The next three reminders and a 'New' button. */
class NoterlyTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> = scope.future {
        val next = repository.current().active.take(3)
        // The app's colours rather than the watch face's, so the tile looks like the app and the phone app
        val layout = materialScope(this@NoterlyTileService, requestParams.deviceConfiguration, allowDynamicTheme = false, defaultColorScheme = COLOURS) {
            primaryLayout(
                titleSlot = { text(getString(R.string.tile_label).layoutString) },
                mainSlot = {
                    if (next.isEmpty()) {
                        text(getString(R.string.tile_empty).layoutString, maxLines = 2)
                    } else {
                        LayoutElementBuilders.Column.Builder()
                            .setWidth(expand())
                            .apply {
                                next.forEachIndexed { index, item ->
                                    if (index > 0) addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).build())
                                    addContent(row(item, index, next.size))
                                }
                            }
                            .build()
                    }
                },
                bottomSlot = {
                    textEdgeButton(
                        onClick = launch(newNote = true),
                        colors = filledVariantButtonColors(),
                        labelContent = { text(getString(R.string.tile_new).layoutString) },
                    )
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

    /** A reminder as an item of the phone app's list groups: colour badge, title, and when it is due. */
    private fun MaterialScope.row(item: NoteItem, index: Int, count: Int): LayoutElementBuilders.LayoutElement {
        val top = if (index == 0) ROW_OUTER_RADIUS else ROW_INNER_RADIUS
        val bottom = if (index == count - 1) ROW_OUTER_RADIUS else ROW_INNER_RADIUS
        val due = WatchState.nextDue(item)?.let { Format.time(this@NoterlyTileService, it) } ?: getString(R.string.list_now)
        val shape = LayoutModifier.background(colorScheme.surfaceContainer)
            .clipTopLeft(top, top).clipTopRight(top, top).clipBottomLeft(bottom, bottom).clipBottomRight(bottom, bottom)
            .padding(start = 10f, top = 0f, end = 12f, bottom = 0f, rtlAware = true)
        return LayoutElementBuilders.Row.Builder()
            .setWidth(expand())
            .setHeight(dp(32f))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(shape.toProtoLayoutModifiers())
            .addContent(badge(item.colour))
            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(7f)).build())
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                    .addContent(
                        text(
                            item.title.layoutString,
                            // Small enough for titles such as "Team meeting" beside the time
                            typography = Typography.LABEL_SMALL,
                            maxLines = 1,
                            alignment = LayoutElementBuilders.TEXT_ALIGN_START,
                            overflow = LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE,
                        ),
                    )
                    .build(),
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).build())
            .addContent(text(due.layoutString, typography = Typography.BODY_SMALL, color = colorScheme.onSurfaceVariant, maxLines = 1))
            .build()
    }

    /** The reminder's colour as a dot on a faded square of it, like the app and the phone app. */
    private fun badge(colour: Long): LayoutElementBuilders.LayoutElement = LayoutElementBuilders.Box.Builder()
        .setWidth(dp(18f))
        .setHeight(dp(18f))
        .setModifiers(LayoutModifier.background(LayoutColor(badgeBackground(colour).toArgb())).clip(6.5f).toProtoLayoutModifiers())
        .addContent(
            LayoutElementBuilders.Box.Builder()
                .setWidth(dp(7f))
                .setHeight(dp(7f))
                .setModifiers(LayoutModifier.background(LayoutColor(colour.toInt())).clip(3.5f).toProtoLayoutModifiers())
                .build(),
        )
        .build()

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
        private const val ROW_OUTER_RADIUS = 16f
        private const val ROW_INNER_RADIUS = 4f

        private val COLOURS = ColorScheme(
            primary = LayoutColor(PhoneColours.PRIMARY.toInt()),
            onPrimary = LayoutColor(PhoneColours.ON_PRIMARY.toInt()),
            primaryContainer = LayoutColor(PhoneColours.PRIMARY_CONTAINER.toInt()),
            onPrimaryContainer = LayoutColor(PhoneColours.ON_PRIMARY_CONTAINER.toInt()),
            secondary = LayoutColor(PhoneColours.SECONDARY.toInt()),
            secondaryContainer = LayoutColor(PhoneColours.SECONDARY_CONTAINER.toInt()),
            onSecondaryContainer = LayoutColor(PhoneColours.ON_SECONDARY_CONTAINER.toInt()),
            surfaceContainer = LayoutColor(PhoneColours.SURFACE_CONTAINER.toInt()),
            onSurface = LayoutColor(PhoneColours.ON_SURFACE.toInt()),
            onSurfaceVariant = LayoutColor(PhoneColours.ON_SURFACE_VARIANT.toInt()),
            outline = LayoutColor(PhoneColours.OUTLINE.toInt()),
            background = LayoutColor(PhoneColours.BACKGROUND.toInt()),
            onBackground = LayoutColor(PhoneColours.ON_SURFACE.toInt()),
        )

        fun requestUpdate(context: Context) {
            runCatching { getUpdater(context).requestUpdate(NoterlyTileService::class.java) }
        }
    }
}
