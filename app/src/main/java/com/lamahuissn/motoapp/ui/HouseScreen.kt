package com.lamahuissn.motoapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.House

/** What the collector opened a dialog for. */
private sealed interface Sheet {
    data object Reading : Sheet
    data object Collection : Sheet
}

/**
 * One house, and the two things a collector does at its door.
 *
 * Everything here is the server's number: the balance, the months it is made of, the meter's last
 * value and the invoices. The phone fills in a reading or an amount and sends it — and when the answer
 * is unknown (a dead spot, a timeout) it offers the SAME attempt again rather than a new one, because a
 * new attempt is a second invoice or a second receipt for one act at one door.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
) {
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val house = (load as? Load.Ready)?.value

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(house?.name ?: stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = house?.areaName.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (load) {
                Load.Idle, Load.Busy -> BusyBox()

                is Load.Failed -> Column(Modifier.padding(20.dp)) {
                    ProblemCard(
                        title = stringResource(R.string.house_failed_title),
                        message = problem?.message ?: load.message,
                        code = problem?.code,
                        onRetry = onRetryLoad,
                    )
                }

                is Load.Ready -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HouseFacts(load.value)

                    if (load.value.isMetered) {
                        Button(
                            onClick = { sheet = Sheet.Reading },
                            enabled = !busy,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Filled.ElectricMeter, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.house_action_read), style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    if (load.value.owes) {
                        Button(
                            onClick = { sheet = Sheet.Collection },
                            enabled = !busy,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.house_action_collect), style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.house_action_nothing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Invoices(load.value)

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    when (sheet) {
        Sheet.Reading -> house?.let {
            ReadingDialog(
                house = it,
                busy = busy,
                onDismiss = { sheet = null },
                onConfirm = { current, note, estimated, kwh, reason ->
                    sheet = null
                    onRead(current, note, estimated, kwh, reason)
                },
            )
        }

        Sheet.Collection -> house?.let {
            CollectionDialog(
                house = it,
                rate = sheetRate,
                busy = busy,
                onDismiss = { sheet = null },
                onConfirm = { amount, currency, rate, note ->
                    sheet = null
                    onCollect(amount, currency, rate, note)
                },
            )
        }

        null -> Unit
    }

    outcome?.let { result ->
        OutcomeDialog(outcome = result, onRetry = onRetryWrite, onDismiss = onDismissOutcome)
    }
}

@Composable
private fun HouseFacts(house: House) {
    MotoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                name = house.name,
                tone = when {
                    house.exempt -> Tone.Info
                    house.owes -> Tone.Money
                    else -> Tone.Good
                },
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(house.name, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(if (house.isMetered) stringResource(R.string.house_metered) else stringResource(R.string.house_flat))
                    if (house.meterNo != null) Chip(house.meterNo)
                    if (house.exempt) Chip(stringResource(R.string.house_exempt), Tone.Info)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Fact(
                label = stringResource(R.string.house_balance),
                value = Money.usd(house.balance),
                tone = if (house.owes) Tone.Money else Tone.Neutral,
                emphasize = true,
                modifier = Modifier.weight(1f),
            )
            if (house.credit != "0.00") {
                Fact(
                    label = stringResource(R.string.house_credit),
                    value = Money.usd(house.credit),
                    tone = Tone.Good,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (house.unpaidMonths.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.house_months),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                house.unpaidMonths.take(3).forEach { month ->
                    Chip(month.label + " · " + Money.usd(month.remaining), Tone.Money)
                }
                if (house.unpaidMonths.size > 3) {
                    Chip("+" + (house.unpaidMonths.size - 3).toString())
                }
            }
        }

        if (house.isMetered) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (house.readingDone) {
                    Chip(stringResource(R.string.house_reading_done, house.readingValue ?: ""), Tone.Good)
                } else {
                    Chip(stringResource(R.string.house_not_read), Tone.Money)
                }
                if (house.readingEstimated) Chip(stringResource(R.string.house_estimated))
                Chip(stringResource(R.string.house_previous, house.previousValue))
            }
        }
    }
}

@Composable
private fun Invoices(house: House) {
    if (house.bills.isEmpty()) {
        return
    }

    SectionTitle(stringResource(R.string.house_invoices))
    house.bills.reversed().forEach { bill ->
        MotoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(bill.label, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = bill.number.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.usd(bill.total), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    if (bill.remaining == "0.00") {
                        Chip(stringResource(R.string.invoice_paid), Tone.Good)
                    } else {
                        Chip(stringResource(R.string.invoice_left, Money.usd(bill.remaining)), Tone.Money)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingDialog(
    house: House,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Boolean, String, String?) -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var estimated by rememberSaveable { mutableStateOf(false) }
    var kwh by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }

    val valid = if (estimated) {
        kwh.isNotBlank() && reason.isNotBlank()
    } else {
        current.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.read_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = house.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (house.previousValue != "0.00") {
                    Text(
                        text = stringResource(R.string.house_previous, house.previousValue),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (! estimated) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text(stringResource(R.string.read_current)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = kwh,
                        onValueChange = { kwh = it },
                        label = { Text(stringResource(R.string.read_kwh)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(stringResource(R.string.read_reason)) },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = estimated, onCheckedChange = { estimated = it })
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.read_estimated))
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.field_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !busy,
                onClick = {
                    onConfirm(
                        current.trim(),
                        note.takeIf { it.isNotBlank() },
                        estimated,
                        kwh.trim(),
                        reason.takeIf { it.isNotBlank() },
                    )
                },
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CollectionDialog(
    house: House,
    rate: Double?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, String?) -> Unit,
) {
    var currency by rememberSaveable { mutableStateOf("usd") }
    var amount by rememberSaveable { mutableStateOf(house.balance) }
    var typedRate by rememberSaveable { mutableStateOf(rate?.let { Money.rateText(it) } ?: "") }
    var note by rememberSaveable { mutableStateOf("") }

    val needsRate = currency == "lbp"
    val rateGiven = ! needsRate || typedRate.isNotBlank()
    val valid = amount.isNotBlank() && amount != "0" && rateGiven

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collect_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(house.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.collect_balance, Money.usd(house.balance)),
                    style = MaterialTheme.typography.bodyLarge,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currency == "usd",
                        onClick = { currency = "usd"; amount = house.balance },
                        label = { Text(stringResource(R.string.collect_usd)) },
                    )
                    FilterChip(
                        selected = currency == "lbp",
                        onClick = { currency = "lbp"; amount = "" },
                        label = { Text(stringResource(R.string.collect_lbp)) },
                    )
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = {
                        Text(stringResource(if (currency == "lbp") R.string.collect_amount_lbp else R.string.collect_amount))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (needsRate) {
                    OutlinedTextField(
                        value = typedRate,
                        onValueChange = { typedRate = it },
                        label = { Text(stringResource(R.string.collect_rate)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            if (rate == null) R.string.collect_no_rate else R.string.collect_rate_hint,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.field_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !busy,
                onClick = {
                    onConfirm(
                        amount.trim(),
                        currency,
                        if (needsRate) typedRate.trim() else null,
                        note.takeIf { it.isNotBlank() },
                    )
                },
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.collect_confirm))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun OutcomeDialog(outcome: Outcome, onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(outcome.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                outcome.facts.forEach { (label, value) ->
                    if (value.isBlank()) {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(text = value, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                outcome.problem?.let { problem ->
                    ProblemCard(
                        title = stringResource(R.string.outcome_problem_title),
                        message = problem.message,
                        code = problem.code,
                    )
                }
            }
        },
        confirmButton = {
            if (outcome.retryable) {
                Button(onClick = onRetry) { Text(stringResource(R.string.retry_send)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
        },
        dismissButton = if (outcome.retryable) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
        } else {
            null
        },
    )
}
