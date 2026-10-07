package com.lamahuissn.motoapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.sp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.CorrectionAnswer
import com.lamahuissn.motoapp.data.ReceiptMessage
import com.lamahuissn.motoapp.data.ReversalAnswer
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

    /**
     * A meter's pair as the office says it out loud: «15 → 66».
     *
     * Isolated and left-to-right for the same reason every other figure is: inside an Arabic sentence the
     * arrow would otherwise be re-ordered around the numbers and read as «66 ← 15».
     */
    fun readingPair(previous: String, current: String): String =
        "\u2066" + previous + " → " + current + "\u2069"

    /** The office's rate as a field shows it: `90000`, not `90000.0`. */
    fun rateText(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /**
     * A percentage.
     *
     * The sign and the `%` are bidi-neutral: inside an Arabic sentence «36.2%» renders as «%36.2» and
     * «-28.9%» as «%28.9-». Isolating keeps each figure with its sign — the same trick the panel uses.
     */
    fun percent(value: Double): String = "\u2066" + trim(value) + "%\u2069"

    /** A change against last month: `+3.4`, with its sign, isolated for the same reason. */
    fun delta(value: Double): String =
        "\u2066" + (if (value >= 0) "+" else "") + trim(value) + "\u2069"

    private fun trim(value: Double): String =
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

/** A small square action in the brand bar: refresh, sign out, and the like. */
@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The brand bar every home screen wears: the ⚡ tile, the product's name, and the two things a user does
 * from anywhere — refresh, and hand the phone over. The same bar on the collector's areas and on the
 * manager's board, so the app does not become two apps.
 *
 * `onBack` is for a screen that is a step rather than a home: a manager who walked down to the network
 * needs the way back up, and an app whose only exit from a secondary screen is the system's back button
 * is an app you close by accident.
 */
@Composable
fun MotoAppBar(
    subtitle: String,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconAction(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    description = stringResource(R.string.back),
                    onClick = onBack,
                )
                Spacer(Modifier.width(10.dp))
            }

            Tile(tone = Tone.Primary, size = 36.dp) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LocalContentColor.current,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleSmall,
                )
                MicroLabel(subtitle)
            }

            IconAction(Icons.Filled.Refresh, stringResource(R.string.refresh), onRefresh)
            Spacer(Modifier.width(8.dp))
            IconAction(Icons.Filled.SwitchAccount, stringResource(R.string.sign_out), onSignOut)
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The pull-down gesture, wired once for every list in the app.
 *
 * A collector standing in a street has just spoken to the office's data — he wants to pull the screen
 * down and ask again, without hunting for a refresh icon. The indicator stays up until the answer is in,
 * because a refresh that shows nothing while it runs reads as a refresh that did nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        content = content,
    )
}

/**
 * The queue as one line, wherever a collector is standing (spec 010).
 *
 * It wears the fact a man holding cash needs above everything else: the office has not answered about
 * something yet, and it is still here. Tapping it opens the queue.
 */
@Composable
fun QueueCard(owed: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    MotoCard(modifier = modifier, onClick = onOpen, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tile(tone = Tone.Warning, size = 36.dp) {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LocalContentColor.current,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.queue_card_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = pluralStringResource(R.plurals.queue_card_hint, owed, owed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * «نسخة محفوظة» — worn wherever a screen is showing the phone's own copy instead of the office's answer.
 *
 * The phone keeps the last answer so the app OPENS without a network at all (spec 010). That trade is
 * only honest if the screen says so: an old balance must never pass for a fresh one, because the office's
 * own figure is what the desk will charge from.
 */
@Composable
fun StaleNote(asOf: String, modifier: Modifier = Modifier) {
    Alert(
        text = stringResource(R.string.stale_note, clockText(asOf)),
        tone = Tone.Warning,
        modifier = modifier,
    )
}

/** `2026-10-05T18:25:11` as «اليوم 18:25» or «2026-10-04 18:25» — enough to place a copy in a day. */
fun clockText(stamp: String): String {
    val day = stamp.take(10)
    val time = stamp.drop(11).take(5)

    return if (day == java.time.LocalDate.now().toString()) "اليوم $time" else "$day $time"
}

@Composable
fun BusyBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * A labelled field, the panel's `kt-input`: the label above, a bordered box below, the value in
 * tabular figures.
 *
 * Written by hand rather than with a Material text field because the panel's fields have no floating
 * label and no underline — and a form that looks like a different product is the whole thing the owner
 * noticed.
 */
@Composable
fun FieldBox(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType,
    prefix: String? = null,
    placeholder: String = "0",
    align: TextAlign = TextAlign.Start,
    textSize: TextUnit = 16.sp,
    enabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MicroLabel(label)
        Spacer(Modifier.height(5.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prefix != null) {
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
            }

            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = textSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                    textAlign = align,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/**
 * A receipt as the reversal dialog needs it.
 *
 * Two screens take receipts back and read from different lists — the manager's board carries the last few of
 * the day, a household's file carries his own log — so the dialog takes the fields it draws rather than
 * either list's type.
 */
data class ReceiptFace(
    val id: Int,
    val amount: String,
    val number: String?,
    val collector: String?,
    val collectedOn: String?,
    val house: String,
)

/**
 * Taking one receipt back (spec 009, phase C) — the most dangerous screen in this app.
 *
 * It draws twice on purpose. The first sheet names the receipt and asks WHY in writing; the second says, in a
 * sentence nobody can mistake, that the household will owe the money again and that there is no undo, and
 * only then does the request leave the phone. What the office answers is what stands: the amount that came
 * off the books and the household's new balance, both the office's figures — the phone never subtracts.
 */
@Composable
fun ReversalDialog(
    receipt: ReceiptFace,
    load: Load<ReversalAnswer>,
    onConfirm: (reason: String) -> Unit,
    onClose: () -> Unit,
) {
    var reason by rememberSaveable(receipt.id) { mutableStateOf("") }
    var confirming by rememberSaveable(receipt.id) { mutableStateOf(false) }

    val busy = load is Load.Busy
    val done = (load as? Load.Ready)?.value
    val enough = reason.trim().length >= 10

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(stringResource(R.string.reversal_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(receipt.house, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                MicroLabel(
                    listOfNotNull(receipt.collector, receipt.number, receipt.collectedOn).joinToString(" · "),
                )
                Spacer(Modifier.height(6.dp))
                Amount(value = Money.usd(receipt.amount))

                Spacer(Modifier.height(12.dp))

                when {
                    done != null -> {
                        Alert(
                            text = stringResource(R.string.reversal_recorded, Money.usd(done.amount)),
                            tone = Tone.Success,
                        )
                        Spacer(Modifier.height(8.dp))
                        MicroLabel(
                            stringResource(R.string.reversal_balance_after, Money.usd(done.subscriberBalance)),
                        )
                    }

                    else -> {
                        FieldBox(
                            label = stringResource(R.string.reversal_reason_label),
                            value = reason,
                            onChange = { reason = it },
                            keyboard = KeyboardType.Text,
                            enabled = !busy,
                        )

                        if (!enough && reason.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            MicroLabel(stringResource(R.string.reversal_reason_needed), tone = Tone.Danger)
                        }

                        if (load is Load.Failed) {
                            Spacer(Modifier.height(8.dp))
                            Alert(text = load.message.orEmpty(), tone = Tone.Danger)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (done == null) {
                TextButton(onClick = { confirming = true }, enabled = enough && !busy) {
                    Text(stringResource(R.string.reversal_confirm))
                }
            } else {
                TextButton(onClick = onClose) { Text(stringResource(R.string.done)) }
            }
        },
        dismissButton = {
            if (done == null) {
                TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.reversal_second_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.reversal_second_text,
                        Money.usd(receipt.amount),
                        receipt.house,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = false; onConfirm(reason.trim()) }) {
                    Text(stringResource(R.string.reversal_second_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * Hand the receipt to whatever the phone uses for messages.
 *
 * The office writes it, the phone only carries it: WhatsApp (or whatever the household actually uses)
 * receives the office's own words, so a receipt held in a street and one held at the counter cannot drift
 * apart. No permission, no account, no service of ours in the middle.
 *
 * It lives here rather than on one screen because two screens now send a receipt: the one that just took the
 * money, and the purse's review where an old one is sent again to a household that lost it.
 */
fun sendReceipt(context: Context, message: ReceiptMessage) {
    // The office's own link first: it lands IN the household's chat with the receipt already written, which
    // is the tap a collector actually wants. The link exists only when the office could turn that household's
    // number into an international one — so a number the office will not vouch for is never guessed at here.
    val link = message.waLink

    if (link != null && openLink(context, link)) {
        return
    }

    // No vouchable number, or no app to open it: the words still leave the phone, through whatever the
    // household actually uses.
    shareReceipt(context, message.text)
}

/** True when something on this phone took the link. A missing app is a fallback, not a crash. */
private fun openLink(context: Context, link: String): Boolean = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
}.isSuccess

fun shareReceipt(context: Context, receipt: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.receipt_heading))
        putExtra(Intent.EXTRA_TEXT, receipt)
    }

    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_receipt)))
}

/**
 * Correcting a reading (spec 012) — the office's own act, in the collector's hand.
 *
 * It draws twice, like the reversal, and for the same reason: the first sheet asks WHY in writing (the
 * sentence the trail will carry), and the second says plainly that the reading and its invoice go and that
 * the original stays only in the audit. The engine keeps its own refusals — a paid invoice, a month already
 * closed — and their sentences are what this sheet shows.
 */
@Composable
fun CorrectionDialog(
    label: String,
    pair: String,
    consumption: String?,
    load: Load<CorrectionAnswer>,
    onConfirm: (reason: String) -> Unit,
    onClose: () -> Unit,
) {
    var reason by rememberSaveable(label) { mutableStateOf("") }
    var confirming by rememberSaveable(label) { mutableStateOf(false) }

    val busy = load is Load.Busy
    val done = (load as? Load.Ready)?.value
    val enough = reason.trim().length >= 10

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(stringResource(R.string.correction_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Amount(value = pair, style = MaterialTheme.typography.bodyMedium)
                consumption?.let {
                    Spacer(Modifier.height(2.dp))
                    MicroLabel(stringResource(R.string.reading_kwh, it))
                }

                Spacer(Modifier.height(12.dp))

                when {
                    done != null -> {
                        Alert(
                            text = if (done.billNumber != null) {
                                stringResource(
                                    R.string.correction_done,
                                    done.label ?: label,
                                    Money.usd(done.billTotal ?: "0.00"),
                                )
                            } else {
                                stringResource(R.string.correction_done_no_bill, done.label ?: label)
                            },
                            tone = Tone.Success,
                        )
                        Spacer(Modifier.height(8.dp))
                        MicroLabel(
                            stringResource(R.string.correction_balance_after, Money.usd(done.houseBalance)),
                        )
                    }

                    else -> {
                        FieldBox(
                            label = stringResource(R.string.correction_reason_label),
                            value = reason,
                            onChange = { reason = it },
                            keyboard = KeyboardType.Text,
                            enabled = !busy,
                        )

                        if (!enough && reason.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            MicroLabel(stringResource(R.string.correction_reason_needed), tone = Tone.Danger)
                        }

                        if (load is Load.Failed) {
                            Spacer(Modifier.height(8.dp))
                            Alert(text = load.message.orEmpty(), tone = Tone.Danger)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (done == null) {
                TextButton(onClick = { confirming = true }, enabled = enough && !busy) {
                    Text(stringResource(R.string.correction_confirm))
                }
            } else {
                TextButton(onClick = onClose) { Text(stringResource(R.string.done)) }
            }
        },
        dismissButton = {
            if (done == null) {
                TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.correction_second_title)) },
            text = { Text(stringResource(R.string.correction_second_text, label)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onConfirm(reason.trim()) }) {
                    Text(stringResource(R.string.correction_second_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
