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
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.AreaSheet
import com.lamahuissn.motoapp.data.House

/**
 * One area's houses — the sheet the collector walks with.
 *
 * Every card is built to be read out loud at a door, in this order: the household's name, what is left
 * to pay, the months that figure is made of, and whether the meter has been read this cycle. A flat
 * house whose month has not been billed says so — «لا فاتورة لهذا الشهر بعد» is a different sentence
 * at a door from «صفر», and only one of them is true.
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
            // Medium, like the areas screen: the area's name and the cycle it is being walked for.
            MediumTopAppBar(
                title = {
                    Column {
                        Text(areaName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = stringResource(R.string.houses_period, (load as? Load.Ready)?.value?.period ?: ""),
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
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item {
                                SheetStrip(sheet)
                                Spacer(Modifier.height(6.dp))
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
    MotoCard(onClick = onOpen) {
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

                Spacer(Modifier.height(7.dp))

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
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
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
                    Chip(
                        text = month.label + " · " + Money.usd(month.remaining),
                        tone = Tone.Money,
                    )
                }
                if (house.unpaidMonths.size > 3) {
                    Chip(text = "+" + (house.unpaidMonths.size - 3).toString())
                }
            }
        }

        if (house.flatAndNotBilled) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.house_not_billed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (house.isMetered) {
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ElectricMeter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )

                Spacer(Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (house.readingDone) {
                        Chip(
                            text = stringResource(R.string.house_reading_done, house.readingValue ?: ""),
                            tone = Tone.Good,
                        )
                    } else {
                        Chip(text = stringResource(R.string.house_not_read), tone = Tone.Money)
                    }

                    if (house.readingEstimated) {
                        Chip(stringResource(R.string.house_estimated))
                    }

                    Chip(stringResource(R.string.house_previous, house.previousValue))
                }
            }
        }
    }
}
