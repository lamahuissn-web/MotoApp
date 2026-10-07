package com.lamahuissn.motoapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Handshake
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.WalletSummary

/**
 * The collector's own purse (spec 011) — the one screen about HIM rather than about a house.
 *
 * It exists because the app knew every house's money and none of his: a man who walks out with cash had no
 * way, from his own pocket, to answer «كم بذمّتي الآن؟» or «متى آخر تسليم؟» without asking the office.
 *
 * Every figure is the office's own — `CollectorStats` and `CollectionService::inHand` on the server — and
 * the phone adds none of them up. Two "today" numbers for one man would be a dispute, not a report.
 *
 * It is a tab, not a step: a collector reaches it from anywhere with his thumb.
 */
@Composable
fun WalletScreen(
    load: Load<WalletSummary>,
    problem: Problem?,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    staleAsOf: String? = null,
    refreshing: Boolean = false,
    onPullRefresh: () -> Unit = {},
    onOpenHouse: (houseId: Int, name: String) -> Unit = { _, _ -> },
) {
    var confirmingSignOut by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            // The bottom bar carries the navigation inset on this screen.
    ) {
        MotoAppBar(
            subtitle = stringResource(R.string.wallet_subtitle),
            onRefresh = onRefresh,
            onSignOut = { confirmingSignOut = true },
        )

        when (load) {
            Load.Idle, Load.Busy -> BusyBox()

            is Load.Failed -> Column(Modifier.padding(16.dp)) {
                ProblemCard(
                    title = stringResource(R.string.wallet_failed_title),
                    message = problem?.message ?: load.message,
                    code = problem?.code,
                    onRetry = onRefresh,
                )
            }

            is Load.Ready -> {
                val purse = load.value

                PullToRefresh(refreshing = refreshing, onRefresh = onPullRefresh) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (staleAsOf != null) {
                            item { StaleNote(asOf = staleAsOf) }
                        }

                        item {
                            Column(modifier = Modifier.padding(bottom = 2.dp)) {
                                Text(
                                    text = purse.name,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (purse.label != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Badge(text = purse.label, tone = Tone.Primary)
                                }
                            }
                        }

                        // The number that runs his day, in the largest type on the screen.
                        item {
                            MotoCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Tile(tone = Tone.Primary, size = 40.dp) {
                                        Icon(
                                            imageVector = Icons.Filled.AccountBalanceWallet,
                                            contentDescription = null,
                                            modifier = Modifier.width(20.dp),
                                            tint = LocalContentColor.current,
                                        )
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        MicroLabel(stringResource(R.string.cash_in_hand))
                                        Spacer(Modifier.height(4.dp))
                                        Amount(
                                            value = Money.usd(purse.inHand),
                                            style = MaterialTheme.typography.headlineSmall,
                                        )
                                    }
                                }

                                if (purse.handedTotal != "0.00") {
                                    Spacer(Modifier.height(10.dp))
                                    MicroLabel(
                                        stringResource(R.string.cash_handed_line, Money.usd(purse.handedTotal)),
                                    )
                                }
                            }
                        }

                        item {
                            MotoCard {
                                StatLine(
                                    label = stringResource(R.string.wallet_today),
                                    value = Money.usd(purse.todayTotal),
                                    sub = pluralStringResource(
                                        R.plurals.wallet_operations,
                                        purse.todayCount,
                                        purse.todayCount,
                                    ),
                                )

                                Spacer(Modifier.height(12.dp))

                                StatLine(
                                    label = stringResource(R.string.wallet_month),
                                    value = Money.usd(purse.monthTotal),
                                    sub = pluralStringResource(
                                        R.plurals.wallet_operations,
                                        purse.monthCount,
                                        purse.monthCount,
                                    ),
                                )
                            }
                        }

                        item {
                            MotoCard {
                                Text(
                                    text = stringResource(R.string.wallet_readings),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(10.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ReadingCount(
                                        label = stringResource(R.string.wallet_today),
                                        count = purse.readingsToday,
                                    )
                                    Spacer(Modifier.width(20.dp))
                                    ReadingCount(
                                        label = stringResource(R.string.wallet_cycle),
                                        count = purse.readingsCycle,
                                    )
                                }
                            }
                        }

                        if (purse.handovers.isNotEmpty()) {
                            item { SectionHeading(stringResource(R.string.wallet_handovers)) }

                            items(purse.handovers.size) { index ->
                                val handover = purse.handovers[index]

                                MotoCard(padding = 14.dp) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Tile(tone = Tone.Success, size = 34.dp) {
                                            Icon(
                                                imageVector = Icons.Filled.Handshake,
                                                contentDescription = null,
                                                modifier = Modifier.width(17.dp),
                                                tint = LocalContentColor.current,
                                            )
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = handover.date.orEmpty(),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            // A handover is an act between two men: the collector sees
                                            // that the office signed for it.
                                            if (handover.receivedBy != null) {
                                                MicroLabel(
                                                    stringResource(R.string.wallet_received_by, handover.receivedBy),
                                                )
                                            }
                                            handover.note?.let { MicroLabel(it) }
                                        }

                                        Amount(
                                            value = Money.usd(handover.amount),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                }
                            }
                        }

                        // His review (spec 011 §7): the lines behind the cash, and what his readings
                        // produced. The wallet answers «how much»; this answers «which ones» — which is the
                        // question a man asks himself before he signs his money over.
                        item { SectionHeading(stringResource(R.string.wallet_review)) }

                        item {
                            MotoCard(padding = 14.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.wallet_my_receipts),
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    MicroLabel(purse.receipts.size.toString())
                                }

                                Spacer(Modifier.height(10.dp))

                                if (purse.receipts.isEmpty()) {
                                    MicroLabel(stringResource(R.string.wallet_no_receipts))
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        purse.receipts.forEach { receipt ->
                                            ReviewRow(
                                                title = receipt.house.orEmpty(),
                                                sub = listOfNotNull(receipt.collectedOn, receipt.number)
                                                    .joinToString(" · "),
                                                value = Money.usd(receipt.amount),
                                                onClick = {
                                                    onOpenHouse(receipt.houseId, receipt.house.orEmpty())
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            MotoCard(padding = 14.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.wallet_my_invoices),
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    MicroLabel(purse.invoices.size.toString())
                                }

                                Spacer(Modifier.height(10.dp))

                                if (purse.invoices.isEmpty()) {
                                    MicroLabel(stringResource(R.string.wallet_no_invoices))
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        purse.invoices.forEach { invoice ->
                                            ReviewRow(
                                                title = invoice.house.orEmpty(),
                                                sub = listOfNotNull(
                                                    invoice.label,
                                                    invoice.consumption?.let {
                                                        stringResource(R.string.reading_kwh, it)
                                                    },
                                                ).joinToString(" · "),
                                                value = Money.usd(invoice.total),
                                                note = if (invoice.remaining != "0.00") {
                                                    stringResource(
                                                        R.string.receipt_remaining_line,
                                                        Money.usd(invoice.remaining),
                                                    )
                                                } else {
                                                    null
                                                },
                                                onClick = {
                                                    onOpenHouse(invoice.houseId, invoice.house.orEmpty())
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (purse.untouched) {
                            item {
                                Alert(
                                    text = stringResource(R.string.wallet_none),
                                    tone = Tone.Neutral,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text(stringResource(R.string.sign_out)) },
            text = { Text(stringResource(R.string.sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmingSignOut = false; onSignOut() }) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignOut = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * One line of his own review: the house, what it was, and the figure — tappable, because the next thing he
 * wants after «which one?» is «let me see that house».
 */
@Composable
private fun ReviewRow(
    title: String,
    sub: String,
    value: String,
    note: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotEmpty()) {
                MicroLabel(sub)
            }
            note?.let { MicroLabel(it, tone = Tone.Danger) }
        }

        Amount(value = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One of the purse's two money lines: what was taken, and over how many receipts. */
@Composable
private fun StatLine(label: String, value: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            MicroLabel(label)
            Spacer(Modifier.height(2.dp))
            MicroLabel(sub)
        }

        Amount(value = value, style = MaterialTheme.typography.titleMedium)
    }
}

/** How many readings he wrote — a figure, not money, so it is drawn plainly. */
@Composable
private fun ReadingCount(label: String, count: Int) {
    Column {
        MicroLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
