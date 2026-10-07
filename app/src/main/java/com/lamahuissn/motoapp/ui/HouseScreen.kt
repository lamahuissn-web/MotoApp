package com.lamahuissn.motoapp.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.House
import com.lamahuissn.motoapp.data.HouseReceipt
import com.lamahuissn.motoapp.data.ReversalAnswer
import com.lamahuissn.motoapp.ui.theme.Green

/** What the collector is looking at inside one household. */
private const val VIEW_OVERVIEW = 0
private const val VIEW_READING = 1
private const val VIEW_PAYMENT = 2

/**
 * One household — the panel's own subscriber screen, on a phone.
 *
 * The structure is the web screen the collector already knows, element for element: an identity card,
 * the balance in the panel's red or green with its big state badge, then the three-way switcher
 * (نظرة عامة / قراءة / قبض) and the panel behind the chosen view. The reading and the collection happen
 * INSIDE the screen here rather than in dialogs, because that is what the web does and because a
 * collector at a door should not lose sight of the balance while he types.
 *
 * Everything shown is the server's number. The phone fills in a reading or an amount and sends it, and
 * when the answer is unknown it offers the SAME attempt again rather than a new one — a new attempt is a
 * second invoice or a second receipt for one act at one door.
 */
@Composable
fun HouseScreen(
    load: Load<House>,
    problem: Problem?,
    busy: Boolean,
    outcome: Outcome?,
    sheetRate: Double?,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onRead: (current: String, note: String?, estimated: Boolean, kwh: String, reason: String?) -> Unit,
    onCollect: (amount: String, currency: String, rate: String?, note: String?) -> Unit,
    onRetryWrite: () -> Unit,
    onDismissOutcome: () -> Unit,
    staleAsOf: String? = null,
    queueOwed: Int = 0,
    onOpenQueue: () -> Unit = {},
    refreshing: Boolean = false,
    onPullRefresh: () -> Unit = {},
    canReverse: Boolean = false,
    reversal: Load<ReversalAnswer> = Load.Idle,
    onReverse: (paymentId: Int, reason: String) -> Unit = { _, _ -> },
    onCloseReversal: () -> Unit = {},
    myId: Int? = null,
    receiptToShare: String? = null,
    onSendReceipt: (paymentId: Int) -> Unit = {},
    onShareDone: () -> Unit = {},
) {
    val house = (load as? Load.Ready)?.value
    // The receipt a manager is taking back (spec 009, phase C). Only a manager ever sets this: the button
    // that opens it is not drawn for a collector, and the route refuses him even if it were.
    var reversalFor by remember { mutableStateOf<ReceiptFace?>(null) }
    val context = LocalContext.current
    val scroll = rememberScrollState()

    // A write landing is the moment the collector MUST see: he may be at the bottom of the payment form
    // with the keyboard up, and «did it go through?» has to be answered without him hunting for it.
    LaunchedEffect(outcome) {
        if (outcome != null) scroll.animateScrollTo(0)
    }

    // A receipt sent again for a household that lost it (spec 011 §8): the office's words, out through
    // whatever the phone uses for messages, and the state cleared so it is not sent twice.
    val shareContext = LocalContext.current

    LaunchedEffect(receiptToShare) {
        val text = receiptToShare ?: return@LaunchedEffect
        shareReceipt(shareContext, text)
        onShareDone()
    }

    // The view follows what just happened: a receipt belongs on the payment panel, a recorded reading
    // on the reading panel — the same place the web app leaves the collector standing.
    var view by rememberSaveable {
        mutableStateOf(
            when (outcome?.kind) {
                OutcomeKind.Collection -> VIEW_PAYMENT
                OutcomeKind.Reading -> VIEW_READING
                else -> VIEW_OVERVIEW
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            // The keyboard: a collector typing an amount must see the field, the balance and the button
            // without the keyboard sitting on two of the three.
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = house?.name ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MicroLabel(house?.areaName.orEmpty())
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // The house as the phone last read it: the collector can still take the reading and the money
        // with no signal, and the screen says the figures are the office's last answer, not a fresh one.
        if (staleAsOf != null) {
            StaleNote(
                asOf = staleAsOf,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }

        // …and whatever is still owed to the office is never more than a tap away, on the screen where
        // the collector is most likely to be holding cash (spec 010).
        // A tap that failed says so, in the office's own sentence.
        if (problem != null) {
            Alert(
                text = problem.message,
                tone = Tone.Danger,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }

        if (queueOwed > 0) {
            QueueCard(
                owed = queueOwed,
                onOpen = onOpenQueue,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            )
        }

        when (load) {
            Load.Idle, Load.Busy -> BusyBox()

            is Load.Failed -> Column(Modifier.padding(16.dp)) {
                ProblemCard(
                    title = stringResource(R.string.house_failed_title),
                    message = problem?.message ?: load.message,
                    code = problem?.code,
                    onRetry = onRetryLoad,
                )
            }

            is Load.Ready -> {
                val entry = load.value

                PullToRefresh(
                    refreshing = refreshing,
                    onRefresh = onPullRefresh,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IdentityCard(entry)
                        BalanceCard(entry)

                        // A refresh that failed while he is looking at figures he already had: say so
                        // rather than let him read a balance that may be a minute old as if it were now.
                        if (problem != null) {
                            ProblemCard(
                                title = stringResource(R.string.house_stale_title),
                                message = problem.message,
                                code = problem.code,
                                onRetry = onRetryLoad,
                            )
                        }

                        if (outcome != null && outcome.kind == OutcomeKind.Unknown) {
                            // A write the office has not answered about yet is RECORDED, not failed — and the
                            // collector may be holding cash against it. So it reads as «سُجّل في هاتفك» with
                            // what it was, and it never invites him to take the money a second time.
                            MotoCard(padding = 14.dp) {
                                Alert(text = outcome.title, tone = Tone.Warning)
                                Spacer(Modifier.height(10.dp))

                                outcome.facts.forEach { (label, value) ->
                                    Text(
                                        text = if (value.isBlank()) label else "$label — $value",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }

                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlineButton(
                                        text = stringResource(R.string.queue_send_now),
                                        onClick = onRetryWrite,
                                        tone = Tone.Warning,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlineButton(
                                        text = stringResource(R.string.queue_open),
                                        onClick = onOpenQueue,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                OutlineButton(
                                    text = stringResource(R.string.done),
                                    onClick = onDismissOutcome,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        if (outcome != null && outcome.kind == OutcomeKind.Refused) {
                            Alert(
                                text = outcome.problem?.message ?: stringResource(R.string.outcome_refused_hint),
                                tone = Tone.Danger,
                            )
                            OutlineButton(
                                text = stringResource(R.string.done),
                                onClick = onDismissOutcome,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (outcome != null && outcome.kind == OutcomeKind.Reading) {
                            Alert(
                                text = stringResource(R.string.reading_saved_alert),
                                tone = Tone.Success,
                            )
                        }

                        if (outcome != null && outcome.kind == OutcomeKind.Collection && outcome.receipt != null) {
                            ReceiptCard(
                                outcome = outcome,
                                onShare = { receipt -> shareReceipt(context, receipt) },
                                onDone = onDismissOutcome,
                            )
                        } else {
                            Segmented(
                                segments = listOf(
                                    Segment(stringResource(R.string.view_overview)),
                                    Segment(
                                        label = stringResource(R.string.view_reading),
                                        dotTone = if (!entry.isMetered) {
                                            null
                                        } else if (entry.readingDone) {
                                            Tone.Success
                                        } else {
                                            Tone.Warning
                                        },
                                    ),
                                    Segment(
                                        label = stringResource(R.string.view_collect),
                                        dotTone = if (entry.owes) Tone.Danger else null,
                                    ),
                                ),
                                selected = view,
                                onSelect = { view = it },
                            )

                            when (view) {
                                VIEW_READING -> ReadingPanel(
                                    house = entry,
                                    busy = busy,
                                    onRead = onRead,
                                )

                                VIEW_PAYMENT -> PaymentPanel(
                                    house = entry,
                                    rate = sheetRate,
                                    busy = busy,
                                    onCollect = onCollect,
                                )

                                else -> OverviewPanel(
                                    house = entry,
                                    canReverse = canReverse,
                                    onGoCollect = { view = VIEW_PAYMENT },
                                    onGoReading = { view = VIEW_READING },
                                    myId = myId,
                                    onSendReceipt = onSendReceipt,
                                    onReverse = { receipt ->
                                        onCloseReversal()
                                        reversalFor = ReceiptFace(
                                            id = receipt.id,
                                            amount = receipt.amount,
                                            number = receipt.number,
                                            collector = receipt.collector,
                                            collectedOn = receipt.collectedOn,
                                            house = entry.name,
                                        )
                                    },
                                )
                            }
                        }

                        OutlineButton(
                            text = stringResource(R.string.back),
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
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

/** Who this is: the panel's tile, the name, the type and state badges, and the meta grid. */
@Composable
private fun IdentityCard(house: House) {
    MotoCard {
        Row(verticalAlignment = Alignment.Top) {
            Avatar(name = house.name, size = 48.dp)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = house.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(
                        text = if (house.isMetered) {
                            stringResource(R.string.house_metered)
                        } else {
                            stringResource(R.string.house_flat)
                        },
                        tone = if (house.isMetered) Tone.Info else Tone.Violet,
                    )

                    Badge(
                        text = if (house.status == "inactive") {
                            stringResource(R.string.house_inactive)
                        } else {
                            stringResource(R.string.house_active)
                        },
                        tone = if (house.status == "inactive") Tone.Neutral else Tone.Success,
                    )

                    if (house.exempt) {
                        Badge(stringResource(R.string.house_exempt), Tone.Info)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (house.meterNo != null) {
                        MetaLine(stringResource(R.string.house_meter_short, house.meterNo))
                    }
                    if (house.phone != null) {
                        MetaLine(house.phone)
                    }
                    if (house.discountPercent != "0.00") {
                        MetaLine(stringResource(R.string.house_discount_line, house.discountPercent))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The balance, the way the panel shows it: the label above, the figure large in red or green, and the
 * household's state as a big badge on the far side.
 */
@Composable
private fun BalanceCard(house: House) {
    MotoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                MicroLabel(stringResource(R.string.house_balance))

                Spacer(Modifier.height(4.dp))

                Amount(
                    value = Money.usd(house.balance),
                    tone = if (house.owes) Tone.Danger else Tone.Success,
                    style = MaterialTheme.typography.displaySmall,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = if (house.owes) {
                        stringResource(R.string.balance_due_line)
                    } else if (house.flatAndNotBilled) {
                        stringResource(R.string.house_not_billed)
                    } else {
                        stringResource(R.string.balance_clear_line)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Badge(
                    text = if (house.owes) {
                        stringResource(R.string.due_label)
                    } else {
                        stringResource(R.string.settled_label)
                    },
                    tone = if (house.owes) Tone.Danger else Tone.Success,
                    size = BadgeSize.Large,
                )

                if (house.credit != "0.00") {
                    Spacer(Modifier.height(6.dp))
                    MicroLabel(stringResource(R.string.house_credit_row, Money.usd(house.credit)), tone = Tone.Success)
                }

                if (house.unpaidMonthsCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    MicroLabel(
                        pluralStringResource(R.plurals.months_count, house.unpaidMonthsCount, house.unpaidMonthsCount),
                    )
                }
            }
        }
    }
}

/** The panel's overview: the this-cycle invoice, its lines, its total, and the way to collect. */
@Composable
private fun OverviewPanel(
    house: House,
    canReverse: Boolean,
    onGoCollect: () -> Unit,
    onGoReading: () -> Unit,
    onReverse: (HouseReceipt) -> Unit,
    myId: Int?,
    onSendReceipt: (paymentId: Int) -> Unit,
) {
    val bill = house.bills.lastOrNull()

    if (bill == null) {
        MotoCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tile(tone = Tone.Neutral, size = 48.dp) {
                    Text(text = "—", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = if (house.flatAndNotBilled) {
                        stringResource(R.string.no_bill_flat)
                    } else {
                        stringResource(R.string.no_bill_metered)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.no_bill_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (house.isMetered && !house.readingDone) {
                    PrimaryButton(
                        text = stringResource(R.string.take_reading),
                        onClick = onGoReading,
                    )
                }
            }
        }

        return
    }

    MotoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bill_of_period, bill.label),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (bill.number != null) {
                    MicroLabel(bill.number)
                }
            }

            Badge(text = billStatus(bill.remaining, bill.paid), tone = billStatusTone(bill.remaining, bill.paid))
        }

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (bill.consumption != null) {
                LineRow(stringResource(R.string.row_consumption), bill.consumption + " kWh")
            }

            if (bill.discount != "0.00") {
                LineRow(stringResource(R.string.row_discount), Money.usd(bill.discount), valueTone = Tone.Success)
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LineRow(
                label = stringResource(R.string.row_total_bill),
                value = Money.usd(bill.total),
                valueStyle = MaterialTheme.typography.titleSmall,
                muted = false,
            )

            if (bill.paid != "0.00") {
                LineRow(stringResource(R.string.row_paid), "− " + Money.usd(bill.paid), valueTone = Tone.Success)
            }

            if (bill.remaining != "0.00") {
                LineRow(
                    label = stringResource(R.string.row_remaining_bill),
                    value = Money.usd(bill.remaining),
                    valueTone = Tone.Danger,
                )
            }
        }

        if (house.unpaidMonths.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionHeading(stringResource(R.string.house_months))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                house.unpaidMonths.forEach { month ->
                    LineRow(label = month.label, value = Money.usd(month.remaining), valueTone = Tone.Danger)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (house.owes) {
            PrimaryButton(text = stringResource(R.string.house_action_collect), onClick = onGoCollect)
        } else {
            Alert(text = stringResource(R.string.balance_clear_line), tone = Tone.Success)
        }
    }

    // The rest of the household's invoices, the way the panel lists recent bills.
    if (house.bills.size > 1) {
        SectionHeading(stringResource(R.string.house_invoices))
        MotoCard(padding = 14.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                house.bills.reversed().forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // An invoice log that shows only what was billed says nothing about what was
                            // PAID on it — and «الفواتير ودفعاتها» is one question, not two.
                            if (line.remaining != "0.00") {
                                MicroLabel(
                                    stringResource(R.string.receipt_remaining_line, Money.usd(line.remaining)),
                                    tone = Tone.Danger,
                                )
                            }
                        }
                        Amount(
                            value = Money.usd(line.total),
                            tone = Tone.Neutral,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(toneInk(billStatusTone(line.remaining, line.paid))),
                        )
                    }
                }
            }
        }
    }

    // The household's receipts (spec 009, phase D): what a collector needs at the door to answer «أنت دفعت
    // الشهر الماضي», and what a manager reads before taking one back. Newest first, as the panel lists them.
    SectionHeading(stringResource(R.string.house_receipts))

    if (house.payments.isEmpty()) {
        MotoCard(padding = 14.dp) {
            Text(
                text = stringResource(R.string.house_no_receipts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        return
    }

    MotoCard(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            house.payments.forEachIndexed { index, receipt ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = listOfNotNull(receipt.collectedOn, receipt.collector)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (receipt.number != null) {
                                MicroLabel(receipt.number)
                            }
                        }

                        Amount(value = Money.usd(receipt.amount), style = MaterialTheme.typography.titleSmall)

                        if (canReverse || (myId != null && receipt.collectorId == myId)) {
                            // Sending a receipt again is the collector's own receipt or the office's — the
                            // same rule the receipt endpoint enforces, answered before the tap.
                            TextButton(
                                onClick = { onSendReceipt(receipt.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.share_receipt),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }

                        if (canReverse) {
                            // The receipt's own log is where a manager takes one back: here he sees the whole
                            // story around it, which a row in the day's list cannot show him.
                            TextButton(
                                onClick = { onReverse(receipt) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.reversal_action),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }

                    // What this receipt cleared — the line a household disputes.
                    receipt.settled.forEach { cleared ->
                        Text(
                            text = stringResource(
                                R.string.receipt_settled_line,
                                cleared.label ?: cleared.period.orEmpty(),
                                Money.usd(cleared.amount),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (receipt.lbpAmount != null && receipt.lbpAmount != 0) {
                        MicroLabel(Money.lira(receipt.lbpAmount.toLong()))
                    }

                    receipt.note?.let { MicroLabel(it) }
                }

                if (index != house.payments.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** The reading: the input while it is still to be taken, the record once it is in. */
@Composable
private fun ReadingPanel(
    house: House,
    busy: Boolean,
    onRead: (String, String?, Boolean, String, String?) -> Unit,
) {
    if (!house.isMetered) {
        MotoCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tile(tone = Tone.Neutral, size = 48.dp) {
                    Text(text = "$", style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.no_meter_needed), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.no_meter_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        return
    }

    // The three fields the form needs are declared out here, above both branches, so that the meter log
    // below renders in EITHER state. It used to be that a recorded month returned early and took the
    // history with it — and that is the one state where the history is what a person came to read.
    var current by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var estimatedOpen by rememberSaveable { mutableStateOf(false) }

    if (house.readingDone) {
        MotoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.reading_recorded_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Badge(stringResource(R.string.done), Tone.Success)
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LineRow(stringResource(R.string.read_prev_row), house.previousValue)
                LineRow(stringResource(R.string.read_current_row), house.readingValue.orEmpty())

                house.bills.lastOrNull()?.consumption?.let { consumption ->
                    LineRow(stringResource(R.string.row_consumption), consumption + " kWh")
                }
            }

            if (house.readingEstimated) {
                Spacer(Modifier.height(10.dp))
                Alert(text = stringResource(R.string.read_estimated), tone = Tone.Warning)
            }
        }

    } else {
        MotoCard {
            Text(stringResource(R.string.take_reading), style = MaterialTheme.typography.titleSmall)

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.read_previous_hint, house.previousValue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))

            FieldBox(
                label = stringResource(R.string.read_current),
                value = current,
                onChange = { current = it },
                keyboard = KeyboardType.Decimal,
                align = TextAlign.Center,
                textSize = 24.sp,
                placeholder = "0",
            )

            Spacer(Modifier.height(10.dp))

            FieldBox(
                label = stringResource(R.string.field_note),
                value = note,
                onChange = { note = it },
                keyboard = KeyboardType.Text,
                placeholder = "",
            )

            Spacer(Modifier.height(14.dp))

            PrimaryButton(
                text = stringResource(R.string.read_save),
                onClick = { onRead(current.trim(), note.takeIf { it.isNotBlank() }, false, "", null) },
                enabled = current.isNotBlank(),
                busy = busy,
            )

            Spacer(Modifier.height(8.dp))

            OutlineButton(
                text = stringResource(R.string.read_estimated_open),
                onClick = { estimatedOpen = true },
                modifier = Modifier.fillMaxWidth(),
                tone = Tone.Warning,
            )
        }
    }

    // Every month this meter has been read — the log the panel keeps in the file, and the one a household
    // argues about. It is here, under the input, because the collector needs last month's figure while he
    // types this month's, and because «القراءة السابقة» on its own answers only the last one.
    if (house.readings.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        SectionHeading(stringResource(R.string.house_readings))

        MotoCard(padding = 14.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                house.readings.forEachIndexed { index, row ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.label, style = MaterialTheme.typography.bodyMedium)

                                    // The cycle the file is read as of, marked: the card above already shows
                                    // this month's pair, and a log that repeats it unlabelled reads as a
                                    // duplicate rather than as history.
                                    if (row.period == house.period) {
                                        Spacer(Modifier.width(6.dp))
                                        Badge(
                                            text = stringResource(R.string.reading_this_month),
                                            tone = Tone.Neutral,
                                        )
                                    }
                                }
                                MicroLabel(listOfNotNull(row.reader, row.readOn).joinToString(" · "))
                            }

                            Amount(
                                value = Money.readingPair(row.previous, row.current),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            row.consumption?.let {
                                MicroLabel(stringResource(R.string.reading_kwh, it))
                            }

                            if (row.estimated) {
                                Spacer(Modifier.width(8.dp))
                                Badge(
                                    text = stringResource(R.string.reading_estimated_badge),
                                    tone = Tone.Warning,
                                )
                            }
                        }
                    }

                    if (index != house.readings.lastIndex) {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    } else {
        Spacer(Modifier.height(18.dp))
        SectionHeading(stringResource(R.string.house_readings))

        MotoCard(padding = 14.dp) {
            Text(
                text = stringResource(R.string.house_no_readings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (estimatedOpen) {
        EstimatedReadingDialog(
            house = house,
            busy = busy,
            onDismiss = { estimatedOpen = false },
            onConfirm = { kwh, reason ->
                estimatedOpen = false
                onRead("", null, true, kwh, reason)
            },
        )
    }
}

/** The money: which currency is in his hand, how much, and the way to record it. */
@Composable
private fun PaymentPanel(
    house: House,
    rate: Double?,
    busy: Boolean,
    onCollect: (String, String, String?, String?) -> Unit,
) {
    if (!house.owes) {
        MotoCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tile(tone = Tone.Success, size = 48.dp) {
                    Text(text = "✓", style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.collect_nothing_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.collect_nothing_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        return
    }

    var currency by rememberSaveable { mutableStateOf("usd") }
    var amount by rememberSaveable { mutableStateOf(house.balance) }
    var typedRate by rememberSaveable { mutableStateOf(rate?.let { Money.rateText(it) } ?: "") }
    var note by rememberSaveable { mutableStateOf("") }

    val lira = currency == "lbp"
    val valid = amount.isNotBlank() && amount != "0" && (!lira || typedRate.isNotBlank())

    MotoCard {
        Alert(
            text = stringResource(R.string.collect_balance, Money.usd(house.balance)),
            tone = Tone.Neutral,
        )

        Spacer(Modifier.height(14.dp))

        MicroLabel(stringResource(R.string.collect_unit))

        Spacer(Modifier.height(6.dp))

        // Which currency is in his hand — two buttons, the active one filled, exactly as the panel.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CurrencyButton(
                text = stringResource(R.string.collect_usd),
                active = ! lira,
                onClick = { currency = "usd"; amount = house.balance },
                modifier = Modifier.weight(1f),
            )
            CurrencyButton(
                text = stringResource(R.string.collect_lbp),
                active = lira,
                onClick = { currency = "lbp"; amount = "" },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))

        FieldBox(
            label = if (lira) stringResource(R.string.collect_amount_lbp) else stringResource(R.string.collect_amount),
            value = amount,
            onChange = { amount = it },
            keyboard = KeyboardType.Decimal,
            prefix = if (lira) "LBP" else "$",
            textSize = 20.sp,
            placeholder = "0",
        )

        Spacer(Modifier.height(8.dp))

        OutlineButton(
            text = stringResource(R.string.collect_full_balance, Money.usd(house.balance)),
            onClick = { if (! lira) amount = house.balance },
            enabled = ! lira,
            height = 40.dp,
        )

        if (lira) {
            Spacer(Modifier.height(14.dp))

            FieldBox(
                label = stringResource(R.string.collect_rate),
                value = typedRate,
                onChange = { typedRate = it },
                keyboard = KeyboardType.Number,
                placeholder = "0",
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(if (rate == null) R.string.collect_no_rate else R.string.collect_rate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))

        FieldBox(
            label = stringResource(R.string.field_note),
            value = note,
            onChange = { note = it },
            keyboard = KeyboardType.Text,
            placeholder = "",
        )

        Spacer(Modifier.height(16.dp))

        PrimaryButton(
            text = stringResource(R.string.collect_confirm),
            onClick = {
                onCollect(
                    amount.trim(),
                    currency,
                    if (lira) typedRate.trim() else null,
                    note.takeIf { it.isNotBlank() },
                )
            },
            enabled = valid,
            busy = busy,
        )
    }
}

@Composable
private fun CurrencyButton(text: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (active) {
        Surface2(modifier = modifier, onClick = onClick) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    } else {
        OutlineButton(text = text, onClick = onClick, modifier = modifier)
    }
}

/** A filled bar, the size of an outline button, for the active half of a pair. */
@Composable
private fun Surface2(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primary)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The panel's receipt: a green head with the amount, then what it settled and the way to send it. */
@Composable
private fun ReceiptCard(outcome: Outcome, onShare: (String) -> Unit, onDone: () -> Unit) {
    MotoCard(padding = 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Green)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.payment_received),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = Color.White,
            )

            Spacer(Modifier.height(6.dp))

            outcome.facts.firstOrNull { it.first == stringResource(R.string.outcome_collected) }
                ?.second
                ?.let { collected ->
                    Text(
                        text = collected,
                        style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                        color = Color.White,
                    )
                }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            outcome.facts.forEach { (label, value) ->
                if (value.isNotBlank() && label != stringResource(R.string.outcome_collected)) {
                    LineRow(label = label, value = value)
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            MicroLabel(stringResource(R.string.receipt_heading))

            SelectionContainer {
                Text(
                    text = outcome.receipt.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(6.dp))

            PrimaryButton(
                text = stringResource(R.string.share_receipt),
                onClick = { onShare(outcome.receipt.orEmpty()) },
            )

            OutlineButton(
                text = stringResource(R.string.done),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


/** The estimate path: a dead meter is the office's decision, and it needs its reason in writing. */
@Composable
private fun EstimatedReadingDialog(
    house: House,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var kwh by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.read_estimated_open)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(house.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.read_estimated_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FieldBox(
                    label = stringResource(R.string.read_kwh),
                    value = kwh,
                    onChange = { kwh = it },
                    keyboard = KeyboardType.Decimal,
                )

                FieldBox(
                    label = stringResource(R.string.read_reason),
                    value = reason,
                    onChange = { reason = it },
                    keyboard = KeyboardType.Text,
                    placeholder = "",
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = kwh.isNotBlank() && reason.isNotBlank() && ! busy,
                onClick = { onConfirm(kwh.trim(), reason.trim()) },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun billStatus(remaining: String, paid: String): String = when {
    remaining == "0.00" -> stringResource(R.string.bill_status_paid)
    paid != "0.00" -> stringResource(R.string.bill_status_partial)
    else -> stringResource(R.string.bill_status_unpaid)
}

@Composable
private fun billStatusTone(remaining: String, paid: String): Tone = when {
    remaining == "0.00" -> Tone.Success
    paid != "0.00" -> Tone.Warning
    else -> Tone.Danger
}
