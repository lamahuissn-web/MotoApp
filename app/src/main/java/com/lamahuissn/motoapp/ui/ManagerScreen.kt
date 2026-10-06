package com.lamahuissn.motoapp.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.HandoverAnswer
import com.lamahuissn.motoapp.data.ManagerBoard
import com.lamahuissn.motoapp.data.ManagerCollector
import com.lamahuissn.motoapp.data.RecentPayment
import com.lamahuissn.motoapp.data.ReversalAnswer
import java.time.LocalDate

/**
 * The manager's board (spec 009, phase A) — the office in his pocket.
 *
 * **Read-only, by the owner's decision.** A manager away from his desk answers two questions: how did the
 * month go, and how much is on each collector. Nothing here writes: the writes a manager might want from
 * a pocket — reversing a receipt, changing a tariff — are exactly the ones that must not be one tap away
 * in a street.
 *
 * Every figure comes from the office's own services (`OfficeBoard`, `CollectionService`,
 * `CollectorStats`), so the desk and the pocket cannot disagree about the month.
 */
@Composable
fun ManagerScreen(
    userName: String,
    load: Load<ManagerBoard>,
    problem: Problem?,
    onRefresh: () -> Unit,
    onOpenAreas: () -> Unit,
    onSignOut: () -> Unit,
    onOpenHouse: (id: Int) -> Unit,
    refreshing: Boolean = false,
    onPullRefresh: () -> Unit = {},
    handover: Load<HandoverAnswer> = Load.Idle,
    onHandover: (collectorId: Int, amount: String, date: String, note: String?) -> Unit = { _, _, _, _ -> },
    onCloseHandover: () -> Unit = {},
    reversal: Load<ReversalAnswer> = Load.Idle,
    onReverse: (paymentId: Int, reason: String) -> Unit = { _, _ -> },
    onCloseReversal: () -> Unit = {},
) {
    var confirmingSignOut by remember { mutableStateOf(false) }
    var handoverFor by remember { mutableStateOf<ManagerCollector?>(null) }
    var reversalFor by remember { mutableStateOf<RecentPayment?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            // The bottom bar carries the navigation inset on this screen.
    ) {
        MotoAppBar(
            subtitle = stringResource(R.string.mgr_subtitle),
            onRefresh = onRefresh,
            onSignOut = { confirmingSignOut = true },
        )

        when (load) {
            Load.Idle, Load.Busy -> BusyBox()

            is Load.Failed -> Column(Modifier.padding(16.dp)) {
                ProblemCard(
                    title = stringResource(R.string.mgr_failed_title),
                    message = problem?.message ?: load.message,
                    code = problem?.code,
                    onRetry = onRefresh,
                )
            }

            is Load.Ready -> {
                val board = load.value

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
                            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                                Text(
                                    text = stringResource(R.string.mgr_greeting, userName),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Badge(text = board.label, tone = Tone.Primary)
                                    Spacer(Modifier.width(8.dp))
                                    Badge(
                                        text = pluralStringResource(
                                            R.plurals.houses_count,
                                            board.active,
                                            board.active,
                                        ),
                                        tone = Tone.Info,
                                    )
                                }
                            }
                        }

                        if (problem != null) {
                            item {
                                ProblemCard(
                                    title = stringResource(R.string.mgr_stale_title),
                                    message = problem.message,
                                    code = problem.code,
                                    onRetry = onRefresh,
                                )
                            }
                        }

                        // The month in two cards: how much of the billing came in, and what came in.
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ManagerStat(
                                    label = stringResource(R.string.mgr_rate),
                                    value = Money.percent(board.rate),
                                    sub = stringResource(
                                        R.string.mgr_rate_sub,
                                        Money.percent(board.ratePrevious),
                                        Money.delta(board.rate - board.ratePrevious),
                                    ),
                                    tone = when {
                                        board.rate >= 80 -> Tone.Success
                                        board.rate >= 50 -> Tone.Warning
                                        else -> Tone.Danger
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                ManagerStat(
                                    label = stringResource(R.string.mgr_collected),
                                    value = Money.usd(board.collected),
                                    sub = stringResource(R.string.mgr_invoiced_sub, Money.usd(board.invoiced)),
                                    tone = Tone.Success,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        // What is still out — the figure a manager is asked about most often.
                        item {
                            MotoCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        MicroLabel(stringResource(R.string.mgr_outstanding))
                                        Spacer(Modifier.height(4.dp))
                                        Amount(
                                            value = Money.usd(board.outstanding),
                                            tone = Tone.Danger,
                                            style = MaterialTheme.typography.displaySmall,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = if (board.outstandingInactive != "0.00") {
                                                stringResource(
                                                    R.string.mgr_inactive_sub,
                                                    Money.usd(board.outstandingInactive),
                                                )
                                            } else {
                                                stringResource(R.string.mgr_outstanding_sub)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Badge(
                                        text = stringResource(R.string.required_badge),
                                        tone = if (board.outstanding != "0.00") Tone.Danger else Tone.Success,
                                        size = BadgeSize.Large,
                                    )
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ManagerStat(
                                    label = stringResource(R.string.mgr_houses),
                                    value = board.active.toString(),
                                    sub = stringResource(R.string.mgr_houses_sub, board.metered, board.flat),
                                    tone = Tone.Info,
                                    modifier = Modifier.weight(1f),
                                )
                                ManagerStat(
                                    label = stringResource(R.string.mgr_kwh),
                                    value = board.kwh,
                                    sub = stringResource(R.string.mgr_kwh_sub, Money.usd(board.kwhPrice)),
                                    tone = Tone.Warning,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        // The question a manager is asked in a phone call: how much is on each collector?
                        item {
                            SectionHeading(
                                text = stringResource(R.string.mgr_collectors),
                                trailing = board.collectors.size.toString(),
                            )
                        }

                        if (board.collectors.isEmpty()) {
                            item {
                                MotoCard {
                                    Text(
                                        text = stringResource(R.string.mgr_no_collectors),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        items(board.collectors, key = { it.id }) { collector ->
                            CollectorRow(
                                collector = collector,
                                onOpen = {
                                    // Opening a sheet clears whatever the last one answered: a manager who
                                    // settled with Salim must not see that receipt over Karim's name.
                                    onCloseHandover()
                                    handoverFor = collector
                                },
                            )
                        }

                        if (board.recentPayments.isNotEmpty()) {
                            item {
                                SectionHeading(stringResource(R.string.mgr_recent))
                            }

                            items(board.recentPayments, key = { it.id }) { payment ->
                                PaymentRow(
                                    payment = payment,
                                    onOpen = onOpenHouse,
                                    onReverse = {
                                        // A new sheet starts from the office's last word, never from the
                                        // previous receipt's answer.
                                        onCloseReversal()
                                        reversalFor = payment
                                    },
                                )
                            }
                        }

                        item {
                            Spacer(Modifier.height(4.dp))
                            OutlineButton(
                                text = stringResource(R.string.mgr_open_areas),
                                onClick = onOpenAreas,
                                modifier = Modifier.fillMaxWidth(),
                            )
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

    handoverFor?.let { collector ->
        HandoverDialog(
            collector = collector,
            load = handover,
            onConfirm = { amount, date, note -> onHandover(collector.id, amount, date, note) },
            onClose = {
                handoverFor = null
                onCloseHandover()
            },
        )
    }

    reversalFor?.let { receipt ->
        ReversalDialog(
            receipt = receipt,
            load = reversal,
            onConfirm = { reason -> onReverse(receipt.id, reason) },
            onClose = {
                reversalFor = null
                onCloseReversal()
            },
        )
    }
}

/**
 * A manager taking one receipt back (spec 009, phase C) — the most dangerous screen in this app.
 *
 * It draws twice on purpose. The first sheet names the receipt and asks WHY in writing; the second says, in
 * a sentence nobody can mistake, that the household will owe the money again and that there is no undo, and
 * only then does the request leave the phone. What the office answers is what stands: the amount that came
 * off the books and the household's new balance, both the office's figures — the phone never subtracts.
 */
@Composable
private fun ReversalDialog(
    receipt: RecentPayment,
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
                Text(receipt.subscriber.orEmpty(), style = MaterialTheme.typography.titleMedium)
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
                        receipt.subscriber.orEmpty(),
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
 * The manager signs for the cash he just received (spec 009, phase B).
 *
 * The amount starts at everything the collector holds — that is what the manager came to clear, and the
 * figure he is least likely to have to correct. Everything the office says about it comes back here: a
 * recorded handover with the new balance, or the engine's own refusal naming the figure the collector does
 * hold. The phone never subtracts to check either.
 */
@Composable
private fun HandoverDialog(
    collector: ManagerCollector,
    load: Load<HandoverAnswer>,
    onConfirm: (amount: String, date: String, note: String?) -> Unit,
    onClose: () -> Unit,
) {
    var amount by rememberSaveable(collector.id) { mutableStateOf(collector.inHand) }
    var date by rememberSaveable(collector.id) { mutableStateOf(LocalDate.now().toString()) }
    var note by rememberSaveable(collector.id) { mutableStateOf("") }

    val busy = load is Load.Busy
    val recorded = (load as? Load.Ready)?.value

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(stringResource(R.string.handover_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(collector.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                if (recorded != null) {
                    Alert(
                        text = stringResource(R.string.handover_recorded, Money.usd(recorded.amount)),
                        tone = Tone.Success,
                    )
                    Spacer(Modifier.height(8.dp))
                    MicroLabel(stringResource(R.string.handover_in_hand_after, Money.usd(recorded.collectorInHand)))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.handover_in_hand_now),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Amount(
                            value = Money.usd(collector.inHand),
                            tone = if (collector.holds) Tone.Danger else Tone.Success,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    FieldBox(
                        label = stringResource(R.string.handover_amount),
                        value = amount,
                        onChange = { amount = it },
                        keyboard = KeyboardType.Decimal,
                        prefix = "$",
                    )

                    Spacer(Modifier.height(10.dp))
                    FieldBox(
                        label = stringResource(R.string.handover_date),
                        value = date,
                        onChange = { date = it },
                        keyboard = KeyboardType.Number,
                        placeholder = LocalDate.now().toString(),
                    )

                    Spacer(Modifier.height(10.dp))
                    FieldBox(
                        label = stringResource(R.string.handover_note),
                        value = note,
                        onChange = { note = it },
                        keyboard = KeyboardType.Text,
                        placeholder = stringResource(R.string.optional),
                    )

                    if (load is Load.Failed) {
                        Spacer(Modifier.height(12.dp))
                        Alert(text = load.message.orEmpty(), tone = Tone.Danger)
                    }
                }
            }
        },
        confirmButton = {
            if (recorded != null) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.done)) }
            } else {
                TextButton(
                    enabled = !busy && amount.isNotBlank() && date.isNotBlank(),
                    onClick = { onConfirm(amount, date, note.takeIf { it.isNotBlank() }) },
                ) {
                    Text(stringResource(R.string.handover_confirm))
                }
            }
        },
        dismissButton = {
            if (recorded == null) {
                TextButton(enabled = !busy, onClick = onClose) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** A figure with its label and its sub-line — the panel's stat card, on a manager's phone. */
@Composable
private fun ManagerStat(
    label: String,
    value: String,
    sub: String,
    tone: Tone,
    modifier: Modifier = Modifier,
) {
    MotoCard(modifier = modifier, padding = 12.dp) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Amount(value = value, tone = tone, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(3.dp))
        MicroLabel(sub)
    }
}

/**
 * One collector: his day, his cycle, and the cash the office is waiting for.
 *
 * «النقد بذمّته» is the row's point, so it is the loudest thing on it — red while he holds money, green
 * when he has handed everything over.
 */
@Composable
private fun CollectorRow(collector: ManagerCollector, onOpen: () -> Unit) {
    MotoCard(onClick = onOpen, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = collector.name, tone = if (collector.holds) Tone.Warning else Tone.Success)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = collector.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.mgr_collector_day,
                        Money.usd(collector.todayTotal),
                        pluralStringResource(R.plurals.payments_count, collector.todayCount, collector.todayCount),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.mgr_collector_cycle, Money.usd(collector.collected))
                        + " · " + stringResource(R.string.mgr_collector_handed, Money.usd(collector.handed)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Amount(
                    value = Money.usd(collector.inHand),
                    tone = if (collector.holds) Tone.Danger else Tone.Success,
                    style = MaterialTheme.typography.titleMedium,
                )
                MicroLabel(
                    text = stringResource(R.string.mgr_in_hand),
                    tone = if (collector.holds) Tone.Danger else Tone.Success,
                )
            }
        }
    }
}

/** One receipt, as the office recorded it — enough to recognise a line in the books. */
@Composable
private fun PaymentRow(payment: RecentPayment, onOpen: (id: Int) -> Unit, onReverse: () -> Unit) {
    MotoCard(onClick = { onOpen(payment.subscriberId) }, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tile(tone = Tone.Neutral, size = 36.dp) {
                Icon(
                    imageVector = Icons.Filled.Payments,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LocalContentColor.current,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = payment.subscriber.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(payment.collector, payment.number, payment.collectedOn).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Amount(value = Money.usd(payment.amount), style = MaterialTheme.typography.titleSmall)

            // The one write reachable from this screen, and a small door on purpose: taking a receipt back
            // must never be a stray thumb on the way to a house.
            TextButton(
                onClick = onReverse,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(stringResource(R.string.reversal_action), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
