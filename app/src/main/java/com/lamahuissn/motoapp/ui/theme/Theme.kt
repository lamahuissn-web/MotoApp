package com.lamahuissn.motoapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The panel's colour scheme, mapped onto Material's slots.
 *
 * Material is the mechanism; the values are the panel's. Where the panel has a token Material has no
 * slot for (a hairline border, muted body text), the slot Material DOES have is pointed at the
 * panel's value — `outlineVariant` is the border, `onSurfaceVariant` is the muted grey — so a screen
 * written with Material still comes out looking like the office's own software.
 */
private val Light = lightColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    primaryContainer = Muted,
    onPrimaryContainer = Ink,
    secondary = Ink,
    onSecondary = Paper,
    tertiary = Green,
    onTertiary = Paper,
    error = Danger,
    onError = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Muted,
    onSurfaceVariant = InkMuted,
    surfaceContainerHighest = Muted,
    outline = Line,
    outlineVariant = Line,
)

private val Dark = darkColorScheme(
    primary = NightAmber,
    onPrimary = OnAmber,
    primaryContainer = NightCard,
    onPrimaryContainer = NightInk,
    secondary = NightInk,
    onSecondary = NightPaper,
    tertiary = NightGreen,
    onTertiary = NightPaper,
    error = NightDanger,
    onError = NightPaper,
    background = NightPaper,
    onBackground = NightInk,
    surface = NightCard,
    onSurface = NightInk,
    surfaceVariant = NightCard,
    onSurfaceVariant = NightMuted,
    surfaceContainerHighest = NightCard,
    outline = NightLine,
    outlineVariant = NightLine,
)

/**
 * The panel's radius scale, unchanged: `--radius` is 0.5rem, so `--radius-lg` (a card, a button) is
 * 8dp, `--radius-md` 6dp, `--radius-sm` 4dp and `--radius-xl` 12dp.
 *
 * This replaced a much rounder scale (16–26dp). Rounded corners are the one place where "looks
 * friendly" and "looks like the office's software" pull in opposite directions, and the owner asked
 * for the second.
 */
private val MotoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/**
 * The one theme the app wears.
 *
 * Two decisions are made here rather than per screen:
 *
 *  - **The direction is right-to-left, always.** The office reads Arabic and the collector's sheet is a
 *    document he reads to a household at a door; the phone's own language must not turn that document
 *    around.
 *  - **Dark mode is not a preference here.** A collector reads the screen at a door, sometimes at
 *    night, sometimes in the sun, so both schemes are designed rather than inverted — and the dark
 *    values are the panel's dark tokens, lifted exactly where the panel lifts them.
 */
@Composable
fun MotoAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (darkTheme) Dark else Light,
            typography = MotoTypography,
            shapes = MotoShapes,
            content = content,
        )
    }
}
