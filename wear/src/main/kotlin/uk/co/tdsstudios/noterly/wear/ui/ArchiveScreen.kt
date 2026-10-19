package uk.co.tdsstudios.noterly.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Text
import uk.co.tdsstudios.noterly.wear.R
import uk.co.tdsstudios.noterly.wearshared.DartTime
import uk.co.tdsstudios.noterly.wearshared.NoteItem

/** Recently done reminders. Tapping one opens it, where it can be restored. */
@Composable
fun ArchiveScreen(items: List<NoteItem>, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    ScrollingScreen(title = stringResource(R.string.archive_title)) {
        if (items.isEmpty()) item { CenteredNote(stringResource(R.string.archive_empty)) }
        items(items.take(MAX_ARCHIVED), key = { it.id }) { item ->
            val done = DartTime.parseOrNull(item.archivedDateTime)
            FilledTonalButton(
                onClick = { onOpen(item.id) },
                modifier = Modifier.fillMaxWidth(),
                icon = { ColourDot(item.colour) },
                label = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = { if (done != null) Text(Format.time(context, done)) },
            )
        }
    }
}

private const val MAX_ARCHIVED = 30
