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

private val Light = lightColorScheme(
    primary = Blue,
    onPrimary = Card,
    primaryContainer = BlueContainer,
    onPrimaryContainer = BlueDeep,
    secondary = Money,
    onSecondary = Card,
    secondaryContainer = MoneyContainer,
    onSecondaryContainer = Money,
    tertiary = Good,
    onTertiary = Card,
    tertiaryContainer = GoodContainer,
    onTertiaryContainer = Good,
    error = Danger,
    onError = Card,
    errorContainer = DangerContainer,
    onErrorContainer = Danger,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = CardQuiet,
    onSurfaceVariant = InkMuted,
    surfaceContainerHighest = CardQuiet,
    outline = Line,
    outlineVariant = Line,
)

private val Dark = darkColorScheme(
    primary = NightBlue,
    onPrimary = NightPaper,
    primaryContainer = NightBlueContainer,
    onPrimaryContainer = NightBlueDeep,
    secondary = NightMoney,
    onSecondary = NightPaper,
    secondaryContainer = NightMoneyContainer,
    onSecondaryContainer = NightMoney,
    tertiary = NightGood,
    onTertiary = NightPaper,
    tertiaryContainer = NightGoodContainer,
    onTertiaryContainer = NightGood,
    error = NightDanger,
    onError = NightPaper,
    errorContainer = NightDangerContainer,
    onErrorContainer = NightDanger,
    background = NightPaper,
    onBackground = NightInk,
    surface = NightCard,
    onSurface = NightInk,
    surfaceVariant = NightCardQuiet,
    onSurfaceVariant = NightMuted,
    surfaceContainerHighest = NightCardQuiet,
    outline = NightLine,
    outlineVariant = NightLine,
)

/** Soft, generous corners: a card on a phone held in one hand should not look like a spreadsheet. */
private val MotoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/**
 * The one theme the app wears.
 *
 * Two decisions are made here rather than per screen:
 *
 *  - **The direction is right-to-left, always.** The office reads Arabic and the collector's sheet is a
 *    document he reads to a household at a door; the phone's own language must not turn that document
 *    around. The first run on an English phone (2026-10-04) put an Arabic sentence inside a
 *    left-to-right screen and broke it across two lines with the full stop in front.
 *  - **Dark mode is not a preference here.** A collector reads the screen at a door, sometimes at
 *    night, sometimes in the sun, and the office's phones are split between the two — so both schemes
 *    are designed, not just inverted.
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
