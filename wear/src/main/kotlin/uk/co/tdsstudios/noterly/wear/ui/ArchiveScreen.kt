package uk.co.tdsstudios.noterly.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Text
import uk.co.tdsstudios.noterly.wear.R
import uk.co.tdsstudios.noterly.wearshared.DartTime
import uk.co.tdsstudios.noterly.wearshared.NoteItem

/** Recently done reminders. Tapping one opens it, where it can be restored. */
@Composable
fun ArchiveScreen(items: List<NoteItem>, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val shown = items.take(MAX_ARCHIVED)
    ScrollingScreen(title = stringResource(R.string.archive_title)) {
        if (items.isEmpty()) item { CenteredNote(stringResource(R.string.archive_empty)) }
        itemsIndexed(shown, key = { _, item -> item.id }) { index, item ->
            val done = DartTime.parseOrNull(item.archivedDateTime)
            FilledTonalButton(
                onClick = { onOpen(item.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = groupShape(index, shown.size),
                icon = { ColourBadge(item.colour) },
                label = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = done?.let {
                    {
                        IconLine(
                            painterResource(R.drawable.ic_history),
                            stringResource(R.string.archive_archived, Format.pastTime(context, it)),
                        )
                    }
                },
            )
        }
    }
}

private const val MAX_ARCHIVED = 30
