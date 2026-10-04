package com.lamahuissn.motoapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.AreaSheet
import com.lamahuissn.motoapp.data.House

/**
 * One area's houses — the sheet the collector walks with.
 *
 * Every card is built to be read out loud at a door, in this order: the name, what is left to pay,
 * the months that figure is made of, and whether the meter has been read this cycle. A flat house
 * whose month has not been billed says so — «لا فاتورة لهذا الشهر» is a different sentence at a door
 * from «صفر», and only one of them is true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HousesScreen(
    areaName: String,
    load: Load<AreaSheet>,
    problem: Problem?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(areaName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (load) {
                Load.Idle, Load.Busy -> BusyBox()

                is Load.Failed -> Column(Modifier.padding(16.dp)) {
                    ProblemCard(
                        title = stringResource(R.string.houses_failed_title),
                        message = problem?.message ?: load.message,
                        code = problem?.code,
                        onRetry = onRetry,
                    )
                }

                is Load.Ready -> {
                    if (load.value.houses.isEmpty()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.houses_empty), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item {
                                Text(
                                    text = stringResource(R.string.houses_period, load.value.period),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            items(load.value.houses, key = { it.id }) { house -> HouseCard(house) }

                            if (problem != null) {
                                item {
                                    ProblemCard(
                                        title = stringResource(R.string.houses_failed_title),
                                        message = problem.message,
                                        code = problem.code,
                                        onRetry = onRetry,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HouseCard(house: House) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = house.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )

                Chip(
                    text = if (house.isMetered) {
                        stringResource(R.string.house_metered)
                    } else {
                        stringResource(R.string.house_flat)
                    },
                )

                if (house.exempt) {
                    Chip(
                        text = stringResource(R.string.house_exempt),
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Fact(
                    label = stringResource(R.string.house_balance),
                    value = Money.usd(house.balance),
                    emphasize = true,
                    modifier = Modifier.weight(1f),
                )

                if (house.credit != "0.00") {
                    Fact(
                        label = stringResource(R.string.house_credit),
                        value = Money.usd(house.credit),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (house.unpaidMonths.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.house_months),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        house.unpaidMonths.take(4).forEach { month ->
                            Chip(
                                text = month.label + " · " + Money.usd(month.remaining),
                                container = MaterialTheme.colorScheme.secondaryContainer,
                            )
                        }
                        if (house.unpaidMonths.size > 4) {
                            Chip(text = "+" + (house.unpaidMonths.size - 4).toString())
                        }
                    }
                }
            }

            if (house.flatAndNotBilled) {
                Text(
                    text = stringResource(R.string.house_not_billed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (house.isMetered) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (house.readingDone) {
                        Chip(
                            text = stringResource(R.string.house_reading_done, house.readingValue ?: ""),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    } else {
                        Chip(text = stringResource(R.string.house_not_read))
                    }

                    if (house.readingEstimated) {
                        Chip(text = stringResource(R.string.house_estimated))
                    }

                    Chip(text = stringResource(R.string.house_previous, house.previousValue))
                }
            }
        }
    }
}
