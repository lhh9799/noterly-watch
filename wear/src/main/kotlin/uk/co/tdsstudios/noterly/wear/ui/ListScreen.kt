package uk.co.tdsstudios.noterly.wear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import uk.co.tdsstudios.noterly.wear.R
import uk.co.tdsstudios.noterly.wear.data.WatchView
import uk.co.tdsstudios.noterly.wearshared.DartTime
import uk.co.tdsstudios.noterly.wearshared.ListSection
import uk.co.tdsstudios.noterly.wearshared.NoteItem
import java.time.LocalDateTime

@Composable
fun ListScreen(
    view: WatchView?,
    phoneConnected: Boolean?,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onArchive: () -> Unit,
) {
    val context = LocalContext.current
    val active = view?.active.orEmpty()

    ScrollingScreen(
        title = stringResource(R.string.list_title),
        edgeButton = {
            EdgeButton(onClick = onNew, buttonSize = EdgeButtonSize.Medium, colors = ButtonDefaults.filledVariantButtonColors()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.list_new))
            }
        },
    ) {
        // Offline: say how old the list is
        if (phoneConnected == false || view?.hasSnapshot == false) {
            item {
                CenteredNote(
                    if (view == null || view.lastSyncMillis == 0L) stringResource(R.string.list_never_synced)
                    else stringResource(R.string.list_last_synced, Format.clock(context, view.lastSyncMillis)),
                )
            }
        }

        if (view != null && active.isEmpty()) {
            item { CenteredNote(stringResource(R.string.list_empty)) }
        }

        // Grouped like the phone's list, so a reminder is found under the same heading on both
        view?.activeSections.orEmpty().forEach { (section, sectionItems) ->
            item(key = "section-${section.name}") { SectionHeader(stringResource(sectionTitle(section))) }
            itemsIndexed(sectionItems, key = { _, item -> item.id }) { index, item ->
                FilledTonalButton(
                    onClick = { onOpen(item.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = groupShape(index, sectionItems.size),
                    icon = { ColourBadge(item.colour) },
                    label = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = if (hasDetails(item)) ({ ItemDetails(item) }) else null,
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onArchive,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                icon = { Icon(painterResource(R.drawable.ic_history), contentDescription = null) },
                label = { Text(stringResource(R.string.list_archive)) },
            )
        }
    }
}

/** The phone app's list shows the body, when it is due, how it repeats and until when it is snoozed. */
private fun hasDetails(item: NoteItem): Boolean =
    item.body.isNotBlank() || item.dateTime != null || item.repetitionData != null || snoozedUntil(item) != null

private fun snoozedUntil(item: NoteItem): LocalDateTime? =
    DartTime.parseOrNull(item.snoozeDateTime)?.takeIf { it.isAfter(LocalDateTime.now()) }

@Composable
private fun ItemDetails(item: NoteItem) {
    val context = LocalContext.current
    Column {
        if (item.body.isNotBlank()) Text(item.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        DartTime.parseOrNull(item.dateTime)?.let { IconLine(painterResource(R.drawable.ic_schedule), Format.time(context, it)) }
        if (item.repetitionData != null) {
            IconLine(painterResource(R.drawable.ic_repeat), stringResource(R.string.repeats, Format.repeatLabel(context, item)))
        }
        snoozedUntil(item)?.let {
            IconLine(painterResource(R.drawable.ic_snooze), stringResource(R.string.list_snoozed_until, Format.time(context, it)))
        }
    }
}

private fun sectionTitle(section: ListSection): Int = when (section) {
    ListSection.IMMEDIATE -> R.string.list_section_immediate
    ListSection.SCHEDULED -> R.string.list_section_scheduled
    ListSection.REPEATING -> R.string.list_section_repeating
}
