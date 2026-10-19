package uk.co.tdsstudios.noterly.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/**
 * A scrolling screen. ScalingLazyColumn scrolls with the rotating bezel by default, and ScreenScaffold shows the time
 * and scroll indicator and keeps [edgeButton] at the bottom edge.
 */
@Composable
fun ScrollingScreen(
    title: String? = null,
    edgeButton: (@Composable () -> Unit)? = null,
    content: ScalingLazyListScope.() -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val column: @Composable (PaddingValues) -> Unit = { contentPadding ->
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {
            if (title != null) item { ListHeader { Text(title) } }
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
fun ColourDot(colour: Long, size: Dp = 14.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(colour)),
    )
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
