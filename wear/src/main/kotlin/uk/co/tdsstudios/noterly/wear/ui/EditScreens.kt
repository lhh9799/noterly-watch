package uk.co.tdsstudios.noterly.wear.ui

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.DatePicker
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextToggleButton
import androidx.wear.compose.material3.TextToggleButtonDefaults
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

/** Create or edit, laid out like the phone app's page: title and colour, then time and repeat. */
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
            EdgeButton(
                onClick = { if (canSave) onSave() else askForTitle() },
                buttonSize = EdgeButtonSize.Medium,
                colors = ButtonDefaults.filledVariantButtonColors(),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(if (draft.itemId == null) R.string.edit_create else R.string.edit_save))
            }
        },
    ) {
        item { SectionHeader(stringResource(R.string.edit_section_details)) }
        item {
            // The label above the title, like the phone's text field
            FilledTonalButton(onClick = { askForTitle() }, modifier = Modifier.fillMaxWidth(), shape = groupShape(0, 2)) {
                Column {
                    Text(titleLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(draft.title.ifBlank { stringResource(R.string.edit_title_empty) }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            // Starts as a random colour of the phone's 12; each tap picks another
            FilledTonalButton(
                onClick = { onChange { it.copy(colour = NoterlyColours.nextRandom(it.colour)) } },
                modifier = Modifier.fillMaxWidth(),
                shape = groupShape(1, 2),
                icon = { ColourBadge(draft.colour, 24.dp) },
                label = { Text(stringResource(R.string.edit_colour)) },
                secondaryLabel = { Text(stringResource(R.string.edit_colour_hint)) },
            )
        }
        item { SectionHeader(stringResource(R.string.edit_section_timing)) }
        item {
            FilledTonalButton(
                onClick = onTime,
                modifier = Modifier.fillMaxWidth(),
                shape = groupShape(0, 2),
                label = { Text(stringResource(R.string.edit_time)) },
                secondaryLabel = { Text(draft.dateTime?.let { Format.time(context, it) } ?: stringResource(R.string.time_now)) },
            )
        }
        item {
            FilledTonalButton(
                onClick = onRepeat,
                modifier = Modifier.fillMaxWidth(),
                shape = groupShape(1, 2),
                label = { Text(stringResource(R.string.edit_repeat)) },
                secondaryLabel = { Text(draft.repetition?.let { Format.repeatLabel(context, it) } ?: stringResource(R.string.repeat_none)) },
            )
        }
    }
}

private class TimeChoice(val label: String, val current: Boolean = false, val dateTime: () -> LocalDateTime?)

@Composable
fun TimeChoiceScreen(draft: Draft, onChosen: (LocalDateTime?) -> Unit, onPick: () -> Unit) {
    val context = LocalContext.current
    val choices = buildList {
        // Editing: keep the current time
        if (draft.itemId != null && draft.dateTime != null) {
            add(TimeChoice(stringResource(R.string.time_keep, Format.time(context, draft.dateTime)), current = true) { draft.dateTime })
        }
        add(TimeChoice(stringResource(R.string.time_now)) { null })
        add(TimeChoice(stringResource(R.string.time_30_minutes)) { LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0) })
        add(TimeChoice(stringResource(R.string.time_1_hour)) { LocalDateTime.now().plusHours(1).withSecond(0).withNano(0) })
    }
    val count = choices.size + 1 // and "Choose a time"

    ScrollingScreen(title = stringResource(R.string.time_title), icon = painterResource(R.drawable.ic_schedule)) {
        choices.forEachIndexed { index, choice ->
            item {
                FilledTonalButton(
                    onClick = { onChosen(choice.dateTime()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = groupShape(index, count),
                    icon = if (choice.current) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                    label = { Text(choice.label) },
                )
            }
        }
        item {
            FilledTonalButton(
                onClick = onPick,
                modifier = Modifier.fillMaxWidth(),
                shape = groupShape(count - 1, count),
                label = { Text(stringResource(R.string.time_pick)) },
            )
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

/**
 * Repeat type, interval and, for weekly repeats, the days of the week (same rules as the phone). Like the phone app's
 * dialog, nothing changes until OK; Cancel or swiping back keeps the repeat as it was.
 */
@Composable
fun RepeatScreen(repetition: RepetitionData?, onConfirm: (RepetitionData?) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(repetition) }
    val current = chosen
    val options = listOf(
        null to R.string.repeat_none,
        Repetition.HOURLY to R.string.repeat_hourly,
        Repetition.DAILY to R.string.repeat_daily,
        Repetition.WEEKLY to R.string.repeat_weekly,
        Repetition.MONTHLY to R.string.repeat_monthly,
        Repetition.YEARLY to R.string.repeat_yearly,
    )

    ScrollingScreen(
        title = stringResource(R.string.repeat_title),
        icon = painterResource(R.drawable.ic_repeat),
        subtitle = current?.let { stringResource(R.string.repeats, Format.repeatLabel(context, it)) } ?: stringResource(R.string.repeat_none),
    ) {
        options.forEachIndexed { index, (type, label) ->
            item {
                val selected = current?.repetition == type
                Button(
                    onClick = { chosen = type?.let { RepetitionData.of(it, current?.number ?: 1, current?.weekdays.orEmpty()) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = groupShape(index, options.size),
                    colors = if (selected) selectedColors() else ButtonDefaults.filledTonalButtonColors(),
                    icon = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                    label = { Text(stringResource(label)) },
                )
            }
        }

        if (current != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepButton(painterResource(R.drawable.ic_remove), stringResource(R.string.repeat_decrease)) {
                        chosen = current.copy(number = (current.number - 1).coerceAtLeast(1))
                    }
                    IntervalField(
                        stringResource(R.string.repeat_interval),
                        pluralStringResource(intervalPlural(current.repetition), current.number, current.number),
                        // What the buttons leave
                        Modifier.weight(1f, fill = false),
                    )
                    StepButton(rememberVectorPainter(Icons.Filled.Add), stringResource(R.string.repeat_increase)) {
                        chosen = current.copy(number = (current.number + 1).coerceAtMost(99))
                    }
                }
            }
        }

        if (current?.repetition == Repetition.WEEKLY) {
            item { CenteredNote(stringResource(R.string.repeat_days_of_week)) }
            // Two rows: seven buttons in one would be too small to tap
            Format.weekdaysInLocaleOrder().chunked(4).forEach { row ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                        for (day in row) {
                            val selected = day.value in current.weekdays
                            TextToggleButton(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    val days = if (checked) current.weekdays + day.value else current.weekdays - day.value
                                    chosen = RepetitionData.of(Repetition.WEEKLY, current.number, days.distinct())
                                },
                                modifier = Modifier.size(40.dp),
                                colors = TextToggleButtonDefaults.colors(
                                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                    uncheckedContainerColor = Color.Transparent,
                                    uncheckedContentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                // Smaller for longer short names, such as Polish "niedz."
                                val style = MaterialTheme.typography.labelMedium
                                BasicText(
                                    Format.shortDay(day),
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                    style = style.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = LocalContentColor.current,
                                    ),
                                    maxLines = 1,
                                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            DialogActions(
                onCancel = onCancel,
                confirmLabel = stringResource(R.string.ok),
                onConfirm = { onConfirm(current) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** The chosen option, like the phone app's selected items */
@Composable
private fun selectedColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

@Composable
private fun StepButton(icon: Painter, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(IconButtonDefaults.SmallButtonSize),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Icon(icon, contentDescription = description)
    }
}

/**
 * The interval in an outlined box with its label on the border, like the phone app's text fields. A long value (such
 * as Italian "12 settimane") gets smaller rather than pushing the buttons off the screen.
 */
@Composable
private fun IntervalField(label: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(top = 7.dp)) {
        Box(
            Modifier
                .widthIn(min = 72.dp)
                .height(40.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            val style = MaterialTheme.typography.labelLarge
            BasicText(
                value,
                style = style.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize),
            )
        }
        Text(
            label,
            modifier = Modifier
                .offset(x = 9.dp, y = (-7).dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun intervalPlural(repetition: Repetition): Int = when (repetition) {
    Repetition.HOURLY -> R.plurals.repeat_every_hours
    Repetition.DAILY -> R.plurals.repeat_every_days
    Repetition.WEEKLY -> R.plurals.repeat_every_weeks
    Repetition.MONTHLY -> R.plurals.repeat_every_months
    Repetition.YEARLY -> R.plurals.repeat_every_years
}
