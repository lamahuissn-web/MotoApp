package com.lamahuissn.motoapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
 * Dark mode is not a preference here: a collector reads the screen at a door, sometimes at night,
 * sometimes in the sun, and the office's own phones are split between the two.
 */
@Composable
fun MotoAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = MotoTypography,
        content = content,
    )
}
