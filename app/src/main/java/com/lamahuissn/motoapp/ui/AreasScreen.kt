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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.AreasBoard
import com.lamahuissn.motoapp.data.MotoArea

/**
 * The office's day, as the collector starts it.
 *
 * The strip at the top is the day in three counts — how many areas, how many houses, how much of the
 * walk is read — and then the areas themselves, each with what it owes in the office's amber. Counts
 * are counted here; money is never added up on the phone, so the strip carries no total.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // A medium bar, not the small one: the title carries a second line (whose session this
            // is), and a 64dp bar clips it.
            MediumTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.areas_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = { confirmingSignOut = true }) {
                        Icon(Icons.Filled.SwitchAccount, contentDescription = stringResource(R.string.sign_out))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (load) {
                Load.Idle, Load.Busy -> BusyBox()

                is Load.Failed -> Column(Modifier.padding(20.dp)) {
                    ProblemCard(
                        title = stringResource(R.string.areas_failed_title),
                        message = problem?.message ?: load.message,
                        code = problem?.code,
                        onRetry = onRefresh,
                    )
                }

                is Load.Ready -> {
                    val board = load.value

                    if (board.areas.isEmpty()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.areas_empty), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item {
                                BoardStrip(board)
                                Spacer(Modifier.height(6.dp))
                                SectionTitle(stringResource(R.string.areas_section) + " · " + board.period)
                            }

                            items(board.areas, key = { it.name }) { area ->
                                AreaCard(area = area, onOpen = { onOpen(area) })
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

/** The day in three counts: areas, houses, and how much of the reading is done. */
@Composable
private fun BoardStrip(board: AreasBoard) {
    val houses = board.areas.sumOf { it.active }
    val read = board.areas.sumOf { it.readingDone }
    val toRead = board.areas.sumOf { it.readingTotal }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryTile(
            label = stringResource(R.string.strip_areas),
            value = board.areas.size.toString(),
            tone = Tone.Info,
            modifier = Modifier.weight(1f),
        )
        SummaryTile(
            label = stringResource(R.string.strip_houses),
            value = houses.toString(),
            modifier = Modifier.weight(1f),
        )
        SummaryTile(
            label = stringResource(R.string.strip_readings),
            value = "$read/$toRead",
            tone = if (toRead > 0 && read >= toRead) Tone.Good else Tone.Neutral,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AreaCard(area: MotoArea, onOpen: () -> Unit) {
    MotoCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LeadingMark(Icons.Filled.Place, Tone.Info)

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(area.name, style = MaterialTheme.typography.titleLarge)

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(stringResource(R.string.area_houses, area.active))

                    if (area.readingTotal > 0) {
                        if (area.readingLeft == 0) {
                            Chip(stringResource(R.string.area_reading_done), Tone.Good)
                        } else {
                            Chip(
                                stringResource(R.string.area_reading_left, area.readingDone, area.readingTotal),
                                Tone.Money,
                            )
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.area_owed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Money.usd(area.owed),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (area.owes) toneInk(Tone.Money) else toneInk(Tone.Neutral),
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
