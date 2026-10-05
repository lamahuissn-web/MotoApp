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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.AreasBoard
import com.lamahuissn.motoapp.data.MotoArea
import java.time.LocalDate
import java.time.LocalTime

/**
 * The office's day, as the collector starts it — the panel's own dashboard, on a phone.
 *
 * The structure is the collector's web dashboard, element for element: the brand bar with the ⚡ mark,
 * the greeting and the date, the shift's counts as stat cards with a tinted tile each, then the areas
 * as rows — a glyph tile, the name, a meta line, and what the street owes on the far side in the
 * panel's destructive red, with «مستحق» under it.
 *
 * Counts are counted here; money is never added up on the phone, so no card carries a total.
 */
@Composable
fun AreasScreen(
    userName: String,
    load: Load<AreasBoard>,
    problem: Problem?,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onOpen: (MotoArea) -> Unit,
) {
    var confirmingSignOut by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Edge-to-edge is on: without this the brand bar renders under the phone's own status bar,
            // which is where the owner saw the battery and the clock sitting on top of the header.
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AppBar(
            onRefresh = onRefresh,
            onSignOut = { confirmingSignOut = true },
        )

        when (load) {
            Load.Idle, Load.Busy -> BusyBox()

            is Load.Failed -> Column(Modifier.padding(16.dp)) {
                ProblemCard(
                    title = stringResource(R.string.areas_failed_title),
                    message = problem?.message ?: load.message,
                    code = problem?.code,
                    onRetry = onRefresh,
                )
            }

            is Load.Ready -> {
                val board = load.value

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Greeting(userName = userName, period = board.label) }

                    item { ShiftCounts(board) }

                    item {
                        SectionHeading(
                            text = stringResource(R.string.areas_section),
                            trailing = pluralStringResource(R.plurals.areas_count, board.areas.size, board.areas.size),
                        )
                    }

                    if (board.areas.isEmpty()) {
                        item {
                            MotoCard {
                                Text(
                                    text = stringResource(R.string.areas_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    items(board.areas, key = { it.name }) { area ->
                        AreaRow(area = area, onOpen = { onOpen(area) })
                    }

                    if (problem != null) {
                        item {
                            ProblemCard(
                                title = stringResource(R.string.areas_failed_title),
                                message = problem.message,
                                code = problem.code,
                                onRetry = onRefresh,
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
}

/**
 * The panel's app bar: the brand tile, the product's name over the collector's, and the two things he
 * does from anywhere — refresh the day, or hand the phone over.
 */
@Composable
private fun AppBar(onRefresh: () -> Unit, onSignOut: () -> Unit) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tile(tone = Tone.Primary, size = 36.dp) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LocalContentColor.current,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleSmall,
                )
                // Not the collector's name: it is already the greeting under this bar, and on a phone
                // the panel hides its own copy for the same reason.
                MicroLabel(stringResource(R.string.app_tagline))
            }

            IconAction(Icons.Filled.Refresh, stringResource(R.string.refresh), onRefresh)
            Spacer(Modifier.width(8.dp))
            IconAction(Icons.Filled.SwitchAccount, stringResource(R.string.sign_out), onSignOut)
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The panel's heading: a greeting that matches the hour, this collector's name, and the date. */
@Composable
private fun Greeting(userName: String, period: String?) {
    val morning = LocalTime.now().hour < 12

    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            text = stringResource(
                if (morning) R.string.greeting_morning else R.string.greeting_evening,
                userName,
            ),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = LocalDate.now().toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // The cycle's own name, and only when the office sent one: a badge reading «2026-10»
            // beside the date «2026-10-05» was the same fact printed twice.
            if (period != null) {
                Spacer(Modifier.width(8.dp))
                Badge(text = period, tone = Tone.Primary)
            }
        }
    }
}

/** The shift in three counts, each a panel stat card: a label, a figure, and a tinted tile. */
@Composable
private fun ShiftCounts(board: AreasBoard) {
    val houses = board.areas.sumOf { it.active }
    val read = board.areas.sumOf { it.readingDone }
    val toRead = board.areas.sumOf { it.readingTotal }
    val owing = board.areas.count { it.owes }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCard(
            label = stringResource(R.string.strip_areas),
            value = board.areas.size.toString(),
            sub = pluralStringResource(R.plurals.houses_count, houses, houses),
            tone = Tone.Info,
            icon = Icons.Filled.Place,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.strip_readings),
            value = "$read/$toRead",
            sub = stringResource(R.string.strip_readings_sub),
            tone = if (toRead > 0 && read >= toRead) Tone.Success else Tone.Warning,
            icon = Icons.Filled.ElectricMeter,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.strip_owing),
            value = owing.toString(),
            sub = stringResource(R.string.strip_owing_sub),
            tone = if (owing > 0) Tone.Danger else Tone.Success,
            icon = Icons.Filled.Payments,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    sub: String,
    tone: Tone,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    MotoCard(modifier = modifier, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Amount(value = value, tone = Tone.Neutral, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(2.dp))
                MicroLabel(sub)
            }

            Tile(tone = tone, size = 32.dp) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = LocalContentColor.current,
                )
            }
        }
    }
}

/** One area, as the panel's route row: a glyph, the name, a meta line, and what the street owes. */
@Composable
private fun AreaRow(area: MotoArea, onOpen: () -> Unit) {
    MotoCard(onClick = onOpen, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tile(tone = Tone.Info, size = 40.dp) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = LocalContentColor.current,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = area.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.area_row_meta,
                        pluralStringResource(R.plurals.houses_count, area.houses, area.houses),
                        area.active,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (area.owes) {
                    Amount(
                        value = Money.usd(area.owed),
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

            // Forward, in Arabic, is to the left — the same direction the panel's own rows point.
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
