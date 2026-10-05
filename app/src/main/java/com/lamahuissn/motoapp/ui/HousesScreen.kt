package com.lamahuissn.motoapp.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.AreaSheet
import com.lamahuissn.motoapp.data.House

/**
 * One area's houses — the sheet the collector walks with, as the panel's own route screen.
 *
 * Heading, then the two counts that matter on a walk (who owes, and how much of the reading is done),
 * then the rows: a `MTR`/`FLT` tile, the household, what is left to do about its meter, and the balance
 * on the far side in the panel's red or green. The same row the web app gives him for this street.
 */
@Composable
fun HousesScreen(
    areaName: String,
    load: Load<AreaSheet>,
    problem: Problem?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenHouse: (House) -> Unit,
) {
    val sheet = (load as? Load.Ready)?.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        text = areaName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sheet?.let {
                            pluralStringResource(R.plurals.houses_count, it.houses.size, it.houses.size)
                        } ?: stringResource(R.string.houses_period, ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (sheet != null) {
                Spacer(Modifier.height(10.dp))

                val metered = sheet.houses.count { it.isMetered }
                val read = sheet.houses.count { it.isMetered && it.readingDone }
                val owing = sheet.houses.count { it.owes }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge(
                        text = pluralStringResource(R.plurals.houses_due_count, owing, owing),
                        tone = if (owing > 0) Tone.Danger else Tone.Success,
                        size = BadgeSize.Medium,
                    )

                    if (metered > 0) {
                        Badge(
                            text = if (read >= metered) {
                                stringResource(R.string.area_reading_done)
                            } else {
                                stringResource(R.string.area_reading_left, read, metered)
                            },
                            tone = if (read >= metered) Tone.Success else Tone.Warning,
                            size = BadgeSize.Medium,
                        )
                    }

                    Badge(text = sheet.periodText, tone = Tone.Primary, size = BadgeSize.Medium)
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

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

            is Load.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SectionHeading(
                        text = stringResource(R.string.houses_section),
                        trailing = pluralStringResource(
                            R.plurals.houses_count,
                            load.value.houses.size,
                            load.value.houses.size,
                        ),
                    )
                }

                if (load.value.houses.isEmpty()) {
                    item {
                        MotoCard {
                            Text(
                                text = stringResource(R.string.houses_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(load.value.houses, key = { it.id }) { house ->
                    HouseRow(house = house, onOpen = { onOpenHouse(house) })
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

/**
 * One household, as the panel's own row for it.
 *
 * The tile carries `MTR`/`FLT` exactly as the web row does — the letter pair is what the office reads
 * at a glance, and the reading chip beside the name is the one question a metered street asks.
 */
@Composable
private fun HouseRow(house: House, onOpen: () -> Unit) {
    MotoCard(onClick = onOpen, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tile(tone = if (house.isMetered) Tone.Info else Tone.Violet, size = 40.dp) {
                Text(
                    text = if (house.isMetered) "MTR" else "FLT",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = house.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    Spacer(Modifier.width(6.dp))

                    when {
                        house.exempt -> Badge(stringResource(R.string.house_exempt), Tone.Info)
                        house.isMetered && house.readingDone -> Badge(
                            text = stringResource(R.string.chip_read),
                            tone = if (house.readingEstimated) Tone.Warning else Tone.Success,
                        )
                        house.isMetered -> Badge(stringResource(R.string.chip_awaiting))
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = houseRowMeta(house),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (house.owes) {
                    Amount(
                        value = Money.usd(house.balance),
                        tone = Tone.Danger,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    MicroLabel(stringResource(R.string.due_label))
                } else {
                    Amount(value = "0", tone = Tone.Success, style = MaterialTheme.typography.titleSmall)
                    MicroLabel(stringResource(R.string.settled_label), tone = Tone.Success)
                }
            }

            Spacer(Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The meta line under a household's name: the meter and its last value, or nothing at all. */
@Composable
private fun houseRowMeta(house: House): String {
    val parts = mutableListOf<String>()

    if (house.meterNo != null) {
        parts += stringResource(R.string.house_meter_short, house.meterNo)
    }

    if (house.isMetered && house.previousValue != "0.00") {
        parts += stringResource(R.string.house_previous, house.previousValue)
    }

    if (house.unpaidMonths.isNotEmpty()) {
        parts += pluralStringResource(R.plurals.months_count, house.unpaidMonths.size, house.unpaidMonths.size)
    }

    if (house.flatAndNotBilled) {
        parts += stringResource(R.string.house_not_billed)
    }

    return parts.joinToString(" · ")
}
