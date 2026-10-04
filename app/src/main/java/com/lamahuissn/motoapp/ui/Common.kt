package com.lamahuissn.motoapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** What a screen is showing: nothing yet, work in progress, the thing, or the sentence that failed. */
sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Busy : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String?) : Load<Nothing>
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
}

/** A small square label: a badge, a state, a month. */
@Composable
fun Chip(
    text: String,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(container, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/**
 * The sentence the server said, kept whole.
 *
 * A failure is shown as the office's own words with the machine code beside it for whoever repairs
 * it — never as a stack trace, and never as a generic «حدث خطأ», which tells a collector nothing
 * about whether to try again or telephone the office.
 */
@Composable
fun ProblemCard(
    title: String,
    message: String?,
    code: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WifiOff, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }

            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }

            if (code != null) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                )
            }

            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("أعِد المحاولة") }
            }
        }
    }
}

/** A card's own label/value pair: the shape every figure on these screens takes. */
@Composable
fun Fact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (emphasize) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun BusyBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
