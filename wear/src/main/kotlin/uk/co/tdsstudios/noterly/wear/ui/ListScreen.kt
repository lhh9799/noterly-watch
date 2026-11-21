package uk.co.tdsstudios.noterly.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import uk.co.tdsstudios.noterly.wear.R
import uk.co.tdsstudios.noterly.wear.data.WatchView
import uk.co.tdsstudios.noterly.wearshared.ListSection

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
            EdgeButton(onClick = onNew, buttonSize = EdgeButtonSize.Medium) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.list_new))
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
            item(key = "section-${section.name}") {
                ListSubHeader { Text(stringResource(sectionTitle(section))) }
            }
            items(sectionItems, key = { it.id }) { item ->
                FilledTonalButton(
                    onClick = { onOpen(item.id) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { ColourDot(item.colour) },
                    label = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(Format.itemSummary(context, item), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onArchive,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.list_archive)) },
            )
        }
    }
}

private fun sectionTitle(section: ListSection): Int = when (section) {
    ListSection.IMMEDIATE -> R.string.list_section_immediate
    ListSection.SCHEDULED -> R.string.list_section_scheduled
    ListSection.REPEATING -> R.string.list_section_repeating
}
