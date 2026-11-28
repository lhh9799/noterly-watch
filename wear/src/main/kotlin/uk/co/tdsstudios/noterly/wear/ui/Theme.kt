package uk.co.tdsstudios.noterly.wear.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.wear.compose.material3.ColorScheme

/**
 * The phone app's dark theme colours (Material 3 Expressive, in the blue of its README screenshots), so the watch app
 * and its tile look like the phone app. ARGB, shared by the app and the tile.
 */
object PhoneColours {
    const val PRIMARY = 0xFFB8C4FF
    const val PRIMARY_DIM = 0xFFA0ACE6
    const val ON_PRIMARY = 0xFF1F2C61
    const val PRIMARY_CONTAINER = 0xFF354571
    const val ON_PRIMARY_CONTAINER = 0xFFDCE1FF
    const val SECONDARY = 0xFFC1C5DD
    const val SECONDARY_DIM = 0xFFA6AAC1
    const val ON_SECONDARY = 0xFF2B3042
    const val SECONDARY_CONTAINER = 0xFF414659
    const val ON_SECONDARY_CONTAINER = 0xFFDEE1F9

    /** The phone's list items (its surfaceContainerLow) */
    const val SURFACE_CONTAINER = 0xFF1A1B21
    const val SURFACE_CONTAINER_LOW = 0xFF121318
    const val SURFACE_CONTAINER_HIGH = 0xFF292A2F

    /** The phone's surfaceContainerHighest, under the colour badges */
    const val SURFACE_CONTAINER_HIGHEST = 0xFF33353A
    const val ON_SURFACE = 0xFFE4E2EA
    const val ON_SURFACE_VARIANT = 0xFFC5C6D0
    const val OUTLINE = 0xFF8F909A
    const val OUTLINE_VARIANT = 0xFF45464F
    const val BACKGROUND = 0xFF000000
}

val NoterlyColorScheme = ColorScheme(
    primary = Color(PhoneColours.PRIMARY),
    primaryDim = Color(PhoneColours.PRIMARY_DIM),
    primaryContainer = Color(PhoneColours.PRIMARY_CONTAINER),
    onPrimary = Color(PhoneColours.ON_PRIMARY),
    onPrimaryContainer = Color(PhoneColours.ON_PRIMARY_CONTAINER),
    secondary = Color(PhoneColours.SECONDARY),
    secondaryDim = Color(PhoneColours.SECONDARY_DIM),
    secondaryContainer = Color(PhoneColours.SECONDARY_CONTAINER),
    onSecondary = Color(PhoneColours.ON_SECONDARY),
    onSecondaryContainer = Color(PhoneColours.ON_SECONDARY_CONTAINER),
    surfaceContainerLow = Color(PhoneColours.SURFACE_CONTAINER_LOW),
    surfaceContainer = Color(PhoneColours.SURFACE_CONTAINER),
    surfaceContainerHigh = Color(PhoneColours.SURFACE_CONTAINER_HIGH),
    onSurface = Color(PhoneColours.ON_SURFACE),
    onSurfaceVariant = Color(PhoneColours.ON_SURFACE_VARIANT),
    outline = Color(PhoneColours.OUTLINE),
    outlineVariant = Color(PhoneColours.OUTLINE_VARIANT),
    background = Color(PhoneColours.BACKGROUND),
    onBackground = Color(PhoneColours.ON_SURFACE),
)

/** Behind a reminder's colour dot: the colour at 22 % over the phone's surfaceContainerHighest, like the phone's badges. */
fun badgeBackground(colour: Long): Color =
    Color(colour).copy(alpha = 0.22f).compositeOver(Color(PhoneColours.SURFACE_CONTAINER_HIGHEST))
