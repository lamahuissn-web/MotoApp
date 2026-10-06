package com.lamahuissn.motoapp.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.QueueState
import com.lamahuissn.motoapp.data.QueuedWrite
import java.time.LocalDate

/**
 * The queue — what the collector has recorded and the office has not answered about yet (spec 010).
 *
 * This screen exists because the alternative is a collector who cannot tell the difference between «the
 * office has my money» and «my phone has my money». Every row says which of the two it is, in the office's
 * own words, and nothing here can make a queued collection vanish by accident.
 */
@Composable
fun QueueScreen(
    records: List<QueuedWrite>,
    busy: Boolean,
    onSend: () -> Unit,
    onDiscard: (QueuedWrite) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    refreshing: Boolean = false,
    onPullRefresh: () -> Unit = {},
) {
    val context = LocalContext.current
    var discarding by remember { mutableStateOf<QueuedWrite?>(null) }

    val owed = records.count { it.isOpen }
    val settled = records.size - owed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            // The bottom bar carries the navigation inset on this screen.
    ) {
        MotoAppBar(
            subtitle = stringResource(R.string.queue_subtitle),
            onRefresh = onSend,
            onSignOut = onSignOut,
            onBack = onBack,
        )

        PullToRefresh(
            refreshing = refreshing,
            onRefresh = onPullRefresh,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.queue_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (owed == 0) {
                                    stringResource(R.string.queue_all_settled)
                                } else {
                                    pluralStringResource(R.plurals.queue_owed, owed, owed)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (owed > 0) {
                            Badge(text = owed.toString(), tone = Tone.Warning, size = BadgeSize.Large)
                        }
                    }
                }

                if (owed > 0) {
                    item {
                        OutlineButton(
                            text = stringResource(R.string.queue_send_now),
                            onClick = onSend,
                            modifier = Modifier.fillMaxWidth(),
                            tone = Tone.Primary,
                        )
                    }
                }

                if (busy) {
                    item { BusyBox(Modifier.padding(vertical = 24.dp)) }
                }

                if (records.isEmpty()) {
                    item {
                        MotoCard {
                            Text(
                                text = stringResource(R.string.queue_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // What is still owed comes first: it is the part that needs the collector's attention.
                items(records.sortedByDescending { it.isOpen }, key = { it.key }) { record ->
                    QueueRow(
                        record = record,
                        onDiscard = { discarding = record },
                        onShare = { shareReceipt(context, it) },
                    )
                }

                if (settled > 0 && owed > 0) {
                    item {
                        MicroLabel(stringResource(R.string.queue_settled_hint, settled))
                    }
                }
            }
        }
    }

    discarding?.let { record ->
        AlertDialog(
            onDismissRequest = { discarding = null },
            title = { Text(stringResource(R.string.queue_discard_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.queue_discard_text,
                        record.houseName,
                        if (record.isMoney) " " + Money.usd(record.amount.orEmpty()) else " " + (record.currentReading ?: ""),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { onDiscard(record); discarding = null }) {
                    Text(stringResource(R.string.queue_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { discarding = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** One recorded write, with the state that decides whether the office already has it. */
@Composable
private fun QueueRow(
    record: QueuedWrite,
    onDiscard: () -> Unit,
    onShare: (String) -> Unit,
) {
    val app = LocalContext.current
    val (tone, label) = when (record.state) {
        QueueState.Recorded -> Tone.Warning to app.getString(R.string.queue_state_recorded)
        QueueState.Waiting -> Tone.Warning to app.getString(R.string.queue_state_waiting)
        QueueState.Done -> Tone.Success to app.getString(R.string.queue_state_done)
        QueueState.NeedsReconciliation -> Tone.Danger to app.getString(R.string.queue_state_reconciliation)
        QueueState.Discarded -> Tone.Neutral to app.getString(R.string.queue_state_discarded)
    }

    MotoCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tile(tone = if (record.isMoney) Tone.Primary else Tone.Info, size = 36.dp) {
                Icon(
                    imageVector = if (record.isMoney) Icons.Filled.AccountBalanceWallet else Icons.Filled.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LocalContentColor.current,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.houseName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = app.getString(
                        if (record.isMoney) R.string.queue_line_collection else R.string.queue_line_reading,
                        if (record.isMoney) Money.usd(record.amount.orEmpty()) else record.currentReading.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(3.dp))
                MicroLabel(when {
                    record.isMoney -> app.getString(R.string.queue_price_hint, record.collectedOn.orEmpty())
                    else -> app.getString(R.string.queue_period_hint, record.period.orEmpty())
                })
            }

            Column(horizontalAlignment = Alignment.End) {
                Badge(text = label, tone = tone)
                Spacer(Modifier.height(6.dp))
                MicroLabel(clock(record.createdAt))
            }
        }

        if (!record.lastError.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = record.lastError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Actions, and only the ones that make sense in this state: a settled row is read-only, a
        // discarded one is a note, and an owed row can be sent again or cancelled by hand.
        if (record.isOpen || (record.state == QueueState.Done && record.receiptText != null)) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (record.isOpen) {
                    TextButton(onClick = onDiscard) {
                        Text(stringResource(R.string.queue_discard), color = MaterialTheme.colorScheme.error)
                    }
                }

                record.receiptText?.takeIf { record.state == QueueState.Done }?.let { receipt ->
                    TextButton(onClick = { onShare(receipt) }) {
                        Text(stringResource(R.string.share_receipt))
                    }
                }
            }
        }
    }
}

/** `2026-10-05T18:25:11` as «اليوم 18:25» or «2026-10-04 18:25» — enough to place it in a day. */
private fun clock(createdAt: String): String = clockText(createdAt)

/** The receipt as words, handed to the household's own phone — the same way the house screen does it. */
private fun shareReceipt(context: Context, receipt: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.receipt_heading))
        putExtra(Intent.EXTRA_TEXT, receipt)
    }

    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_receipt)))
}
