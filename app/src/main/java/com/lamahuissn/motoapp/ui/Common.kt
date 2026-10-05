package com.lamahuissn.motoapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lamahuissn.motoapp.ui.theme.AmberInk
import com.lamahuissn.motoapp.ui.theme.BlueInk
import com.lamahuissn.motoapp.ui.theme.DangerInk
import com.lamahuissn.motoapp.ui.theme.GreenInk
import com.lamahuissn.motoapp.ui.theme.NightAmberInk
import com.lamahuissn.motoapp.ui.theme.NightBlueInk
import com.lamahuissn.motoapp.ui.theme.NightDanger
import com.lamahuissn.motoapp.ui.theme.NightGreen
import com.lamahuissn.motoapp.ui.theme.NightVioletInk
import com.lamahuissn.motoapp.ui.theme.VioletInk
import com.lamahuissn.motoapp.ui.theme.Yellow

/** What a screen is showing: nothing yet, work in progress, the thing, or the sentence that failed. */
sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Busy : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String?) : Load<Nothing>
}

/**
 * The panel's tinted variants, plus the two its own stylesheet adds.
 *
 * `Primary` is the brand (amber), `Success` a thing done, `Danger` money owed, `Warning` something
 * waiting on a person, `Info` a fact about a household, `Violet` a flat connection, `Neutral` a
 * quantity that is neither good nor bad. The same tint never means two things on two screens.
 */
enum class Tone { Neutral, Primary, Info, Success, Danger, Warning, Violet }

/** True when the app is wearing its dark tokens. */
@Composable
private fun isNight(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/**
 * The panel's tile rule: a 12% tint of a hue carries a deeper shade of the same hue.
 *
 * `color-mix(in oklab, var(--primary) 12%, transparent)` in the panel's stylesheet, done with alpha
 * here because the tint sits on a card rather than on the page.
 */
@Composable
fun toneColours(tone: Tone): Pair<Color, Color> {
    val night = isNight()
    val scheme = MaterialTheme.colorScheme

    val ink = when (tone) {
        Tone.Neutral -> scheme.onSurfaceVariant
        Tone.Primary -> if (night) NightAmberInk else AmberInk
        Tone.Info -> if (night) NightBlueInk else BlueInk
        Tone.Success -> if (night) NightGreen else GreenInk
        Tone.Danger -> if (night) NightDanger else DangerInk
        Tone.Warning -> if (night) NightAmberInk else AmberInk
        Tone.Violet -> if (night) NightVioletInk else VioletInk
    }

    return when (tone) {
        Tone.Neutral -> scheme.surfaceVariant to ink
        Tone.Warning -> Yellow.copy(alpha = 0.20f) to ink
        else -> ink.copy(alpha = 0.12f) to ink
    }
}

/** The figure colour for money owed or clear — for the number itself, never for a tint. */
@Composable
fun toneInk(tone: Tone): Color {
    val night = isNight()

    return when (tone) {
        Tone.Neutral -> MaterialTheme.colorScheme.onSurface
        Tone.Primary -> if (night) NightAmberInk else AmberInk
        Tone.Info -> if (night) NightBlueInk else BlueInk
        Tone.Success -> if (night) NightGreen else GreenInk
        Tone.Danger -> MaterialTheme.colorScheme.error
        Tone.Warning -> if (night) NightAmberInk else AmberInk
        Tone.Violet -> if (night) NightVioletInk else VioletInk
    }
}

/**
 * Money, as the office writes it.
 *
 * The value arrives from the server as a decimal string and is shown exactly as it arrived; the
 * isolate characters (U+2066 … U+2069) keep `$37.50` as one left-to-right run inside an Arabic
 * sentence, which is how the printed invoices read and how a collector says the figure out loud.
 */
object Money {
    fun usd(value: String): String = "\u2066$$value\u2069"

    fun lira(value: Long): String =
        "\u2066" + "LBP " + value.toString().reversed().chunked(3).joinToString(",").reversed() + "\u2069"

    fun liraRate(value: Double): String = "\u2066" + "1$ = " + value.toLong().toString() + " LBP" + "\u2069"

    /** The office's rate as a field shows it: `90000`, not `90000.0`. */
    fun rateText(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}

/**
 * The card every screen is built from — the panel's own: a surface, an 8dp radius, and a hairline
 * border instead of a shadow (on a phone in the sun a shadow is invisible and a hairline is not).
 */
@Composable
fun MotoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

/** A tinted square holding one glyph — the panel's `.tile`, at the size the panel uses. */
@Composable
fun Tile(
    tone: Tone = Tone.Neutral,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val (container, ink) = toneColours(tone)

    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) { content() }
    }
}

/** The first letters of a household's name, on the brand tile the panel gives it. */
@Composable
fun Avatar(name: String, tone: Tone = Tone.Primary, size: Dp = 40.dp) {
    Tile(tone = tone, size = size) {
        Text(
            text = initials(name),
            style = TextStyle(fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.SemiBold),
            color = LocalContentColor.current,
        )
    }
}

/**
 * Initials, with «بيت» and the definite article dropped.
 *
 * Every household in an area is called «بيت فلان», so keeping the whole name made every circle read the
 * same two letters — a mark that marks nothing. «ال» is dropped for the same reason: «النور» and
 * «الشارع» would both begin with «ا».
 */
fun initials(name: String): String {
    val words = name.trim()
        .split(" ")
        .filter { it.isNotBlank() && it !in setOf("بيت", "منزل", "شقة", "محل") }

    return words.take(2)
        .map { word -> word.removePrefix("ال").take(1).ifEmpty { word.take(1) } }
        .joinToString("")
        .ifEmpty { name.trim().removePrefix("ال").take(1) }
}

enum class BadgeSize { Small, Medium, Large }

/** The panel's badge: a tinted pill with the hue's deeper shade for its text. */
@Composable
fun Badge(
    text: String,
    tone: Tone = Tone.Neutral,
    size: BadgeSize = BadgeSize.Small,
    modifier: Modifier = Modifier,
) {
    val (container, ink) = toneColours(tone)

    val vertical = when (size) {
        BadgeSize.Small -> 2.dp
        BadgeSize.Medium -> 4.dp
        BadgeSize.Large -> 6.dp
    }
    val horizontal = when (size) {
        BadgeSize.Small -> 8.dp
        BadgeSize.Medium -> 10.dp
        BadgeSize.Large -> 12.dp
    }
    val style = when (size) {
        BadgeSize.Small -> MaterialTheme.typography.labelSmall
        BadgeSize.Medium -> MaterialTheme.typography.labelMedium
        BadgeSize.Large -> MaterialTheme.typography.labelLarge
    }

    Box(
        modifier = modifier
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = horizontal, vertical = vertical),
    ) {
        Text(text = text, style = style, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The panel's micro-label: `text-2xs font-semibold uppercase tracking-widest text-muted-foreground`.
 *
 * Arabic has no case, so the weight and the letter-spacing carry it alone — which is why the tracking
 * survives here and the uppercase does not.
 */
@Composable
fun MicroLabel(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Neutral) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
        color = if (tone == Tone.Neutral) MaterialTheme.colorScheme.onSurfaceVariant else toneInk(tone),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * A figure.
 *
 * `tabular-nums` (the panel's `.amount-cell`) so a column of amounts lines up digit for digit and a
 * collector scanning a street does not re-read a number that moved a pixel.
 */
@Composable
fun Amount(
    value: String,
    tone: Tone = Tone.Neutral,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    modifier: Modifier = Modifier,
) {
    Text(
        text = value,
        style = style.copy(fontFeatureSettings = "tnum", letterSpacing = (-0.01).sp),
        fontWeight = FontWeight.SemiBold,
        color = toneInk(tone),
        maxLines = 1,
        modifier = modifier,
    )
}

/** A `justify-between` row: what it is on one side, what it is worth on the other. */
@Composable
fun LineRow(
    label: String,
    value: String? = null,
    valueTone: Tone = Tone.Neutral,
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    muted: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )

        if (value != null) {
            Text(
                text = value,
                style = valueStyle.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = toneInk(valueTone),
                textAlign = TextAlign.End,
            )
        }
    }
}

/** A section heading with an optional count on the far side — the panel's `flex items-baseline`. */
@Composable
fun SectionHeading(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One choice in a [Segmented] control, with an optional state dot. */
data class Segment(val label: String, val dotTone: Tone? = null)

/**
 * The panel's view switcher: a muted track holding solid-on-active segments.
 *
 * The active segment is the only filled one, so which panel is on screen is never ambiguous — the same
 * control the collector already uses on the web screen for this household.
 */
@Composable
fun Segmented(
    segments: List<Segment>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            val active = index == selected
            val container = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
            val ink = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(container)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = segment.label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)

                if (segment.dotTone != null) {
                    Spacer(Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(toneInk(segment.dotTone)),
                    )
                }
            }
        }
    }
}

/** A `kt-btn` at the panel's large size: a 48dp bar, 8dp radius, dark text on the brand. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (enabled && !busy) Modifier.clickable { onClick() } else Modifier),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }

            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** A `kt-btn-outline`: a hairline border on the card surface. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: Tone = Tone.Neutral,
    height: Dp = 44.dp,
) {
    val ink = if (tone == Tone.Neutral) MaterialTheme.colorScheme.onSurface else toneInk(tone)

    Surface(
        modifier = modifier
            .height(height)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) ink else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The panel's `kt-alert kt-alert-light`: a tinted block carrying one sentence. */
@Composable
fun Alert(text: String, tone: Tone = Tone.Warning, modifier: Modifier = Modifier) {
    val (container, ink) = toneColours(tone)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = ink)
    }
}

/**
 * The sentence the server said, kept whole.
 *
 * A failure is shown as the office's own words with the machine code beside it for whoever repairs it
 * — never as a stack trace, and never as a generic «حدث خطأ», which tells a collector nothing about
 * whether to try again or telephone the office.
 */
@Composable
fun ProblemCard(
    title: String,
    message: String?,
    code: String? = null,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "أعِد المحاولة",
    modifier: Modifier = Modifier,
) {
    MotoCard(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = toneInk(Tone.Danger))

        if (message != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (code != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (onRetry != null) {
            Spacer(Modifier.height(6.dp))
            OutlineButton(text = retryLabel, onClick = onRetry, height = 40.dp)
        }
    }
}

@Composable
fun BusyBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
