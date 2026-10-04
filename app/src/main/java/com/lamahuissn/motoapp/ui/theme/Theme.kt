package com.lamahuissn.motoapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val Light = lightColorScheme(
    primary = BrandBlueDark,
    onPrimary = CardSurface,
    primaryContainer = BrandBlueLight,
    onPrimaryContainer = Ink,
    secondary = Amber,
    onSecondary = CardSurface,
    secondaryContainer = AmberLight,
    onSecondaryContainer = Ink,
    tertiary = Good,
    onTertiary = CardSurface,
    background = Paper,
    onBackground = Ink,
    surface = CardSurface,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = InkMuted,
    outline = Line,
    error = Amber,
)

private val Dark = darkColorScheme(
    primary = BrandBlue,
    onPrimary = NightInk,
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = NightInk,
    secondary = AmberLight,
    onSecondary = NightInk,
    secondaryContainer = Amber,
    onSecondaryContainer = NightInk,
    tertiary = NightGood,
    onTertiary = NightPaper,
    background = NightPaper,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurface,
    onSurfaceVariant = NightMuted,
    outline = NightLine,
    error = AmberLight,
)

/**
 * The one theme the app wears.
 *
 * Two decisions are made here rather than per screen:
 *
 *  - **The direction is right-to-left, always.** The office reads Arabic and the collector's sheet is
 *    a document he reads to a household at a door; the phone's own language must not turn that
 *    document around. The first run on an English phone (2026-10-04) put an Arabic sentence inside a
 *    left-to-right screen and broke it across two lines with the full stop in front — the words were
 *    right and every line was facing the wrong way.
 *  - **Dark mode is not a preference here.** A collector reads the screen at a door, sometimes at
 *    night, sometimes in the sun, and the office's phones are split between the two.
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
            content = content,
        )
    }
}
