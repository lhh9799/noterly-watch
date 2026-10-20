package uk.co.tdsstudios.noterly.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import uk.co.tdsstudios.noterly.wear.R
import uk.co.tdsstudios.noterly.wearshared.DartTime
import uk.co.tdsstudios.noterly.wearshared.NoteItem

@Composable
fun DetailScreen(
    note: NoteItem?,
    waitingForPhone: Boolean,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onEdit: (NoteItem) -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
) {
    val context = LocalContext.current
    if (note == null) {
        ScrollingScreen { item { CenteredNote(stringResource(R.string.detail_not_found)) } }
        return
    }

    ScrollingScreen {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ColourDot(note.colour, 12.dp)
                Text(note.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
        if (note.body.isNotBlank()) {
            item { Text(note.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        }
        item {
            val dateTime = DartTime.parseOrNull(note.dateTime)
            CenteredNote(if (dateTime == null) stringResource(R.string.detail_immediate) else Format.time(context, dateTime))
        }
        DartTime.parseOrNull(note.snoozeDateTime)?.let { snoozed ->
            item { CenteredNote(stringResource(R.string.list_snoozed_until, Format.time(context, snoozed))) }
        }
        if (note.isRepeating) {
            item { CenteredNote(Format.repeatLabel(note)) }
        }
        if (waitingForPhone) {
            item { CenteredNote(stringResource(R.string.list_waiting_for_phone)) }
        }

        if (note.archived) {
            item {
                Button(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    label = { Text(stringResource(R.string.detail_restore)) },
                )
            }
        } else {
            item {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    label = { Text(stringResource(R.string.detail_done)) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    label = { Text(stringResource(R.string.detail_snooze)) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { onEdit(note) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    label = { Text(stringResource(R.string.detail_edit)) },
                )
            }
        }
        item {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                label = { Text(stringResource(R.string.detail_delete)) },
            )
        }
    }
}

@Composable
fun DeleteScreen(onConfirm: () -> Unit, onCancel: () -> Unit) {
    ScrollingScreen {
        item { Text(stringResource(R.string.delete_confirm), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        item {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                label = { Text(stringResource(R.string.detail_delete)) },
            )
        }
        item {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.delete_cancel)) })
        }
    }
}
