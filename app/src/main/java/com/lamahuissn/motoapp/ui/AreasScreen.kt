package com.lamahuissn.motoapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
 * The office's day, as the collector starts it: the areas, what each owes, and how much of the walk
 * is already read.
 *
 * The figures are the server's, shown as they arrived — including the areas card's own honesty about
 * the reading: «قُرئ ٣ من ٤» is a fact about the day's work, not decoration.
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.areas_title))
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
                    problem?.let {
                        ProblemCard(
                            title = stringResource(R.string.areas_failed_title),
                            message = it.message,
                            code = it.code,
                            onRetry = onRefresh,
                        )
                    } ?: ProblemCard(
                        title = stringResource(R.string.areas_failed_title),
                        message = load.message,
                        onRetry = onRefresh,
                    )
                }

                is Load.Ready -> {
                    if (load.value.areas.isEmpty()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.areas_empty), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item {
                                Text(
                                    text = stringResource(R.string.areas_period, load.value.period),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            items(load.value.areas, key = { it.name }) { area ->
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

@Composable
private fun AreaCard(area: MotoArea, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(area.name, style = MaterialTheme.typography.titleLarge)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.area_houses, area.active))

                    if (area.readingTotal > 0) {
                        Chip(
                            text = if (area.readingLeft == 0) {
                                stringResource(R.string.area_reading_done)
                            } else {
                                stringResource(R.string.area_reading_left, area.readingDone, area.readingTotal)
                            },
                            container = if (area.readingLeft == 0) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                        )
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
                    color = if (area.owes) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
