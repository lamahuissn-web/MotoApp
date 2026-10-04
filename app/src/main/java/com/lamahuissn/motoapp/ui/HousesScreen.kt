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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.AreaSheet
import com.lamahuissn.motoapp.data.House

/**
 * One area's houses — the sheet the collector walks with.
 *
 * The card is built on ONE idea, the same one the invoice card is built on: a head line that uses the
 * whole width — the household on one side, the figure he is being asked for on the other — and then at
 * most two more lines, each of which must carry something a collector says at a door. That is what
 * keeps a house with months owed and a house that is clear the same height, and what stops the list
 * looking like a pile of stacked labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HousesScreen(
    areaName: String,
    load: Load<AreaSheet>,
    problem: Problem?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenHouse: (House) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(areaName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = stringResource(R.string.houses_period, (load as? Load.Ready)?.value?.periodText ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
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
                        title = stringResource(R.string.houses_failed_title),
                        message = problem?.message ?: load.message,
                        code = problem?.code,
                        onRetry = onRetry,
                    )
                }

                is Load.Ready -> {
                    val sheet = load.value

                    if (sheet.houses.isEmpty()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.houses_empty), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item {
                                SheetStrip(sheet)
                                SectionTitle(stringResource(R.string.houses_section))
                            }

                            items(sheet.houses, key = { it.id }) { house ->
                                HouseCard(house = house, onOpen = { onOpenHouse(house) })
                            }

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
private fun SheetStrip(sheet: AreaSheet) {
    val metered = sheet.houses.count { it.isMetered }
    val read = sheet.houses.count { it.isMetered && it.readingDone }
    val owing = sheet.houses.count { it.owes }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryTile(
            label = stringResource(R.string.strip_houses),
            value = sheet.houses.size.toString(),
            modifier = Modifier.weight(1f),
        )
        SummaryTile(
            label = stringResource(R.string.strip_house_read),
            value = "$read/$metered",
            tone = if (metered > 0 && read >= metered) Tone.Good else Tone.Neutral,
            modifier = Modifier.weight(1f),
        )
        SummaryTile(
            label = stringResource(R.string.strip_owing),
            value = owing.toString(),
            tone = if (owing > 0) Tone.Money else Tone.Good,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HouseCard(house: House, onOpen: () -> Unit) {
    val avatarTone = when {
        house.exempt -> Tone.Info
        house.owes -> Tone.Money
        else -> Tone.Good
    }

    MotoCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = house.name, tone = avatarTone)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = house.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(
                        text = if (house.isMetered) {
                            stringResource(R.string.house_metered)
                        } else {
                            stringResource(R.string.house_flat)
                        },
                    )

                    if (house.exempt) {
                        Chip(stringResource(R.string.house_exempt), Tone.Info)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.house_balance),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Money.usd(house.balance),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (house.owes) toneInk(Tone.Money) else toneInk(Tone.Neutral),
                )
            }
        }

        if (house.unpaidMonths.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                house.unpaidMonths.take(2).forEach { month ->
                    Chip(text = month.label + " · " + Money.usd(month.remaining), tone = Tone.Money)
                }
                if (house.unpaidMonths.size > 2) {
                    Chip(text = "+" + (house.unpaidMonths.size - 2).toString())
                }
            }
        } else if (house.flatAndNotBilled) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.house_not_billed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (house.credit != "0.00") {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.house_credit_row, Money.usd(house.credit)),
                style = MaterialTheme.typography.bodyMedium,
                color = toneInk(Tone.Good),
            )
        }

        if (house.isMetered) {
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ElectricMeter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )

                Spacer(Modifier.width(6.dp))

                Text(
                    text = if (house.readingDone) {
                        stringResource(R.string.house_reading_done, house.readingValue ?: "")
                    } else {
                        stringResource(R.string.house_not_read)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = toneInk(if (house.readingDone) Tone.Good else Tone.Money),
                )

                if (house.readingEstimated) {
                    Spacer(Modifier.width(6.dp))
                    Chip(stringResource(R.string.house_estimated))
                }

                Spacer(Modifier.weight(1f))

                Text(
                    text = stringResource(R.string.house_previous, house.previousValue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
