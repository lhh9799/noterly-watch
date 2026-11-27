package uk.co.tdsstudios.noterly.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
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

    // When it is due, until when it is snoozed and how it repeats, one row each
    val dateTime = DartTime.parseOrNull(note.dateTime)
    val rows = buildList {
        add(painterResource(R.drawable.ic_schedule) to (dateTime?.let { Format.time(context, it) } ?: stringResource(R.string.detail_immediate)))
        DartTime.parseOrNull(note.snoozeDateTime)?.let { snoozed ->
            add(painterResource(R.drawable.ic_snooze) to stringResource(R.string.list_snoozed_until, Format.time(context, snoozed)))
        }
        if (note.isRepeating) add(painterResource(R.drawable.ic_repeat) to stringResource(R.string.repeats, Format.repeatLabel(context, note)))
    }

    ScrollingScreen {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ColourBadge(note.colour, 22.dp)
                Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
        if (note.body.isNotBlank()) {
            item {
                Text(
                    note.body,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        rows.forEachIndexed { index, (icon, text) ->
            item { DetailRow(icon, text, groupShape(index, rows.size)) }
        }
        if (waitingForPhone) {
            item { CenteredNote(stringResource(R.string.list_waiting_for_phone)) }
        }

        if (note.archived) {
            item {
                Button(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    label = { Text(stringResource(R.string.detail_restore)) },
                )
            }
        } else {
            item {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    label = { Text(stringResource(R.string.detail_done)) },
                )
            }
            item {
                Button(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                    label = { Text(stringResource(R.string.detail_snooze)) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { onEdit(note) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    label = { Text(stringResource(R.string.detail_edit)) },
                )
            }
        }
        item {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                label = { Text(stringResource(R.string.detail_delete)) },
            )
        }
    }
}

@Composable
private fun DetailRow(icon: Painter, text: String, shape: Shape) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Asks before deleting, like the phone app's dialogs. The question is the second item, so the list centres it. */
@Composable
fun DeleteScreen(onConfirm: () -> Unit, onCancel: () -> Unit) {
    ScrollingScreen {
        item {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
        }
        item {
            Text(
                stringResource(R.string.delete_confirm),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        item {
            DialogActions(
                onCancel = onCancel,
                confirmLabel = stringResource(R.string.detail_delete),
                onConfirm = onConfirm,
                modifier = Modifier.padding(top = 8.dp),
                confirmIcon = Icons.Outlined.Delete,
            )
        }
    }
}
