package uk.co.tdsstudios.noterly.wear.ui

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.DatePicker
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePicker
import androidx.wear.input.RemoteInputIntentHelper
import uk.co.tdsstudios.noterly.wear.R
import uk.co.tdsstudios.noterly.wearshared.NoterlyColours
import uk.co.tdsstudios.noterly.wearshared.Repetition
import uk.co.tdsstudios.noterly.wearshared.RepetitionData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val TITLE_INPUT_KEY = "title"

/** Create or edit: title (voice or keyboard), time, repeat and colour. */
@Composable
fun EditScreen(
    draft: Draft,
    onChange: ((Draft) -> Draft) -> Unit,
    onTime: () -> Unit,
    onRepeat: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val titleLabel = stringResource(R.string.edit_title_hint)

    // The system input screen offers voice, keyboard and handwriting
    val titleInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.let { RemoteInput.getResultsFromIntent(it) }?.getCharSequence(TITLE_INPUT_KEY)?.toString()?.trim()
        if (!text.isNullOrEmpty()) onChange { it.copy(title = text) }
    }
    fun askForTitle() {
        val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(TITLE_INPUT_KEY).setLabel(titleLabel).build()))
        titleInput.launch(intent)
    }

    val canSave = draft.title.isNotBlank()
    ScrollingScreen(
        title = stringResource(if (draft.itemId == null) R.string.edit_new_title else R.string.edit_title),
        edgeButton = {
            EdgeButton(onClick = { if (canSave) onSave() else askForTitle() }, buttonSize = EdgeButtonSize.Medium) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(if (draft.itemId == null) R.string.edit_create else R.string.edit_save))
            }
        },
    ) {
        item {
            Button(
                onClick = { askForTitle() },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        draft.title.ifBlank { stringResource(R.string.edit_title_empty) },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                secondaryLabel = { Text(titleLabel) },
            )
        }
        item {
            FilledTonalButton(
                onClick = onTime,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.edit_time)) },
                secondaryLabel = { Text(draft.dateTime?.let { Format.time(context, it) } ?: stringResource(R.string.time_now)) },
            )
        }
        item {
            FilledTonalButton(
                onClick = onRepeat,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.edit_repeat)) },
                secondaryLabel = { Text(draft.repetition?.let { Format.repeatLabel(context, it) } ?: stringResource(R.string.repeat_none)) },
            )
        }
        item {
            // Starts as a random colour of the phone's 12; each tap picks another
            FilledTonalButton(
                onClick = { onChange { it.copy(colour = NoterlyColours.nextRandom(it.colour)) } },
                modifier = Modifier.fillMaxWidth(),
                icon = { ColourDot(draft.colour, 20.dp) },
                label = { Text(stringResource(R.string.edit_colour)) },
                secondaryLabel = { Text(stringResource(R.string.edit_colour_hint)) },
            )
        }
    }
}

@Composable
fun TimeChoiceScreen(draft: Draft, onChosen: (LocalDateTime?) -> Unit, onPick: () -> Unit) {
    val context = LocalContext.current
    ScrollingScreen(title = stringResource(R.string.edit_time)) {
        // Editing: keep the current time
        if (draft.itemId != null && draft.dateTime != null) {
            item {
                Button(
                    onClick = { onChosen(draft.dateTime) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.time_keep, Format.time(context, draft.dateTime))) },
                )
            }
        }
        item {
            FilledTonalButton(onClick = { onChosen(null) }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.time_now)) })
        }
        item {
            FilledTonalButton(
                onClick = { onChosen(LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.time_30_minutes)) },
            )
        }
        item {
            FilledTonalButton(
                onClick = { onChosen(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.time_1_hour)) },
            )
        }
        item {
            FilledTonalButton(onClick = onPick, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.time_pick)) })
        }
    }
}

@Composable
fun DatePickerScreen(draft: Draft, onPicked: (LocalDate) -> Unit) {
    DatePicker(
        initialDate = draft.dateTime?.toLocalDate() ?: LocalDate.now(),
        onDatePicked = onPicked,
    )
}

@Composable
fun TimePickerScreen(draft: Draft, onPicked: (LocalTime) -> Unit) {
    TimePicker(
        initialTime = draft.dateTime?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0),
        onTimePicked = onPicked,
    )
}

/** Repeat type, interval and, for weekly repeats, the days of the week (same rules as the phone). */
@Composable
fun RepeatScreen(repetition: RepetitionData?, onChange: (RepetitionData?) -> Unit, onDone: () -> Unit) {
    val options = listOf(
        null to R.string.repeat_none,
        Repetition.HOURLY to R.string.repeat_hourly,
        Repetition.DAILY to R.string.repeat_daily,
        Repetition.WEEKLY to R.string.repeat_weekly,
        Repetition.MONTHLY to R.string.repeat_monthly,
        Repetition.YEARLY to R.string.repeat_yearly,
    )

    ScrollingScreen(
        title = stringResource(R.string.edit_repeat),
        edgeButton = {
            EdgeButton(onClick = onDone, buttonSize = EdgeButtonSize.Small) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.repeat_done))
            }
        },
    ) {
        for ((type, label) in options) {
            item {
                val selected = repetition?.repetition == type
                val onClick = {
                    onChange(type?.let { RepetitionData.of(it, repetition?.number ?: 1, repetition?.weekdays.orEmpty()) })
                }
                if (selected) {
                    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(label)) })
                } else {
                    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(label)) })
                }
            }
        }

        if (repetition != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = { onChange(repetition.copy(number = (repetition.number - 1).coerceAtLeast(1))) },
                        label = { Text("−") },
                    )
                    Text(
                        stringResource(
                            when (repetition.repetition) {
                                Repetition.HOURLY -> R.string.repeat_every_hours
                                Repetition.DAILY -> R.string.repeat_every_days
                                Repetition.WEEKLY -> R.string.repeat_every_weeks
                                Repetition.MONTHLY -> R.string.repeat_every_months
                                Repetition.YEARLY -> R.string.repeat_every_years
                            },
                            repetition.number,
                        ),
                        modifier = Modifier.width(72.dp),
                        textAlign = TextAlign.Center,
                    )
                    FilledTonalButton(
                        onClick = { onChange(repetition.copy(number = (repetition.number + 1).coerceAtMost(99))) },
                        label = { Text("+") },
                    )
                }
            }
        }

        if (repetition?.repetition == Repetition.WEEKLY) {
            item { CenteredNote(stringResource(R.string.repeat_days_of_week)) }
            for (day in Format.weekdaysInLocaleOrder()) {
                item {
                    val selected = day.value in repetition.weekdays
                    val onClick = {
                        val days = if (selected) repetition.weekdays - day.value else repetition.weekdays + day.value
                        onChange(RepetitionData.of(Repetition.WEEKLY, repetition.number, days.distinct()))
                    }
                    if (selected) {
                        Button(
                            onClick = onClick,
                            modifier = Modifier.fillMaxWidth(),
                            icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                            label = { Text(Format.shortDay(day)) },
                        )
                    } else {
                        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), label = { Text(Format.shortDay(day)) })
                    }
                }
            }
        }
    }
}
