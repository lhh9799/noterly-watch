package uk.co.tdsstudios.noterly.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ChildButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import uk.co.tdsstudios.noterly.wear.R

/**
 * A scrolling screen. ScalingLazyColumn scrolls with the rotating bezel by default, and ScreenScaffold shows the time
 * and scroll indicator and keeps [edgeButton] at the bottom edge. Items are 2 dp apart, like the phone app's list
 * groups (see [groupShape]); [icon] and [subtitle] go with the title, like the phone app's dialogs. The list starts
 * with its second item in the middle of the screen, or at the top with [startAtTop], like Wear's own dialogs.
 */
@Composable
fun ScrollingScreen(
    title: String? = null,
    icon: Painter? = null,
    subtitle: String? = null,
    startAtTop: Boolean = false,
    edgeButton: (@Composable () -> Unit)? = null,
    content: ScalingLazyListScope.() -> Unit,
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = if (startAtTop) 0 else 1)
    val column: @Composable (PaddingValues) -> Unit = { contentPadding ->
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            autoCentering = if (startAtTop) null else AutoCenteringParams(),
        ) {
            if (title != null) item { ScreenTitle(title, icon, subtitle) }
            content()
        }
    }

    if (edgeButton != null) {
        ScreenScaffold(scrollState = listState, edgeButton = { edgeButton() }) { contentPadding -> column(contentPadding) }
    } else {
        ScreenScaffold(scrollState = listState) { contentPadding -> column(contentPadding) }
    }
}

@Composable
private fun ScreenTitle(title: String, icon: Painter?, subtitle: String?) {
    if (icon == null && subtitle == null) {
        ListHeader { Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        return
    }
    // Without ListHeader's padding, so the taller title stays clear of the time above it
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The heading of a group, in the phone app's style. */
@Composable
fun SectionHeader(text: String) {
    ListSubHeader(contentColor = MaterialTheme.colorScheme.primary) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The shape of item [index] of [count] in a group, like the phone app's list groups: rounded at the ends of the group
 * and only slightly where its items meet.
 */
fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GROUP_OUTER_RADIUS else GROUP_INNER_RADIUS
    val bottom = if (index == count - 1) GROUP_OUTER_RADIUS else GROUP_INNER_RADIUS
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

private val GROUP_OUTER_RADIUS = 24.dp
private val GROUP_INNER_RADIUS = 4.dp

/** A reminder's colour as the phone app shows it: a dot on a rounded square of the same colour, faded. */
@Composable
fun ColourBadge(colour: Long, size: Dp = 28.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 36))
            .background(badgeBackground(colour)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size * 0.4f)
                .clip(CircleShape)
                .background(Color(colour)),
        )
    }
}

/** A line under a button's label with an icon in front, like the details under the phone app's list items. */
@Composable
fun IconLine(icon: Painter, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Cancel and a confirming action side by side, like the buttons of the phone app's dialogs. */
@Composable
fun DialogActions(
    onCancel: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmIcon: ImageVector? = null,
) {
    // Labels too long for one line (in some languages) put the confirming action below, like Wear's own dialogs
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChildButton(
            onClick = onCancel,
            colors = ButtonDefaults.childButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(stringResource(R.string.cancel), maxLines = 1)
        }
        Button(onClick = onConfirm) {
            if (confirmIcon != null) {
                Icon(confirmIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(confirmLabel, maxLines = 1)
        }
    }
}

@Composable
fun CenteredNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
