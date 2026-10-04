package com.lamahuissn.motoapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** What a screen is showing: nothing yet, work in progress, the thing, or the sentence that failed. */
sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Busy : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String?) : Load<Nothing>
}

/**
 * The colours a piece of information wears.
 *
 * Four tones, one meaning each: neutral is a fact, info is the system speaking, money is something
 * owed, good is something finished. The same colour never means two things on two screens — a
 * collector learns it once at a door.
 */
enum class Tone { Neutral, Info, Money, Good, Danger }

@Composable
fun toneColours(tone: Tone): Pair<Color, Color> = when (tone) {
    Tone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    Tone.Info -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    Tone.Money -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    Tone.Good -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    Tone.Danger -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}

/** The figure colour for money that is owed or clear — used for the number itself, not its chip. */
@Composable
fun toneInk(tone: Tone): Color = when (tone) {
    Tone.Money -> MaterialTheme.colorScheme.secondary
    Tone.Good -> MaterialTheme.colorScheme.tertiary
    Tone.Danger -> MaterialTheme.colorScheme.error
    Tone.Info -> MaterialTheme.colorScheme.primary
    Tone.Neutral -> MaterialTheme.colorScheme.onSurface
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

    fun lira(value: Long): String = "\u2066" + "LBP " + value.toString().reversed().chunked(3).joinToString(",").reversed() + "\u2069"

    fun liraRate(value: Double): String = "\u2066" + "1$ = " + value.toLong().toString() + " LBP" + "\u2069"

    /** The office's rate as a field shows it: `90000`, not `90000.0`. */
    fun rateText(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}

/**
 * The card every screen is built from: the app's own surface, a hairline edge instead of a shadow (a
 * shadow on a phone in the sun is invisible anyway), and generous padding.
 */
@Composable
fun MotoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val colours = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    if (onClick == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colours,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = border,
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colours,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = border,
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

/** A small rounded label: a state, a month, a count. */
@Composable
fun Chip(
    text: String,
    tone: Tone = Tone.Neutral,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val (container, content) = toneColours(tone)

    Row(
        modifier = modifier
            .background(container, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A label above its figure: the shape every number on these screens takes. */
@Composable
fun Fact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    emphasize: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = if (emphasize) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = toneInk(tone),
            maxLines = 1,
        )
    }
}

/** A heading over a group of cards — the panel has these, and a list without one looks like a dump. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** A rounded square holding an icon: the leading mark of every card. */
@Composable
fun LeadingMark(icon: ImageVector, tone: Tone = Tone.Info, modifier: Modifier = Modifier) {
    val (container, content) = toneColours(tone)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}

/** The first letters of a household's name, so a card is recognisable at a glance in a list. */
@Composable
fun Avatar(name: String, tone: Tone = Tone.Info, modifier: Modifier = Modifier) {
    val (container, content) = toneColours(tone)

    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}

fun initials(name: String): String = name.trim()
    .split(" ")
    .filter { it.isNotBlank() }
    .take(2)
    .map { it.take(1) }
    .joinToString("")

/** One tile of a summary strip: a label and a figure, quieter than a card but not a heading. */
@Composable
fun SummaryTile(label: String, value: String, tone: Tone = Tone.Neutral, modifier: Modifier = Modifier) {
    val (container, content) = toneColours(tone)

    Column(
        modifier = modifier
            .background(container, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = content, maxLines = 1)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = content,
            maxLines = 1,
        )
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            if (code != null) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(retryLabel, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
fun BusyBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
