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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.AreasBoard
import com.lamahuissn.motoapp.data.CollectorStats
import com.lamahuissn.motoapp.data.MotoArea
import com.lamahuissn.motoapp.data.SearchHit
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
    search: Load<List<SearchHit>>,
    onSearch: (String) -> Unit,
    onOpenHit: (SearchHit) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onOpen: (MotoArea) -> Unit,
) {
    var confirmingSignOut by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val searching = query.trim().length >= 2

    // Search mode follows the QUERY only, never the field's focus.
    //
    // Following focus was the first attempt, and it shook: touching the field flipped the layout, the
    // layout change rebuilt the field inside the list, the rebuilt field lost focus, the layout flipped
    // back — a loop with the keyboard never getting a chance to open. The query is state the typing
    // owns, so nothing the layout does can undo it.
    val searchMode = query.isNotEmpty()

    val listState = rememberLazyListState()

    LaunchedEffect(searchMode) {
        if (searchMode) {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Edge-to-edge is on: without this the brand bar renders under the phone's own status bar,
            // which is where the owner saw the battery and the clock sitting on top of the header.
            .statusBarsPadding()
            // …and the same for the keyboard: the screen gives up exactly the room the keyboard takes,
            // so the field and its answers are never under it.
            .imePadding()
            .navigationBarsPadding(),
    ) {
        MotoAppBar(
            subtitle = stringResource(R.string.app_tagline),
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

                Column(modifier = Modifier.fillMaxSize()) {
                    // The field is pinned under the brand bar, OUTSIDE the scrolling list, so typing
                    // never moves it and nothing the list does can take its focus away. This is the
                    // whole fix for the shaking screen.
                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                        SearchField(
                            query = query,
                            onChange = { query = it; onSearch(it) },
                            onClear = {
                                query = ""
                                onSearch("")
                            },
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!searchMode) {
                            item { Greeting(userName = userName, period = board.label) }

                            // The web dashboard's first row: what he took today and what he has taken
                            // this month — the collector's own money, summed by the office, never by the
                            // phone.
                            board.stats?.let { stats ->
                                item { MoneyRow(stats) }
                            }

                            item { ShiftCounts(board) }

                            board.stats?.let { stats ->
                                item { CashInHand(stats) }
                            }
                        }

                        if (searching) {
                        item {
                            SectionHeading(
                                text = stringResource(R.string.search_results),
                                trailing = if (search is Load.Ready) {
                                    (search as Load.Ready).value.size.toString()
                                } else {
                                    null
                                },
                            )
                        }

                        when (search) {
                            Load.Idle, Load.Busy -> item { BusyBox() }

                            is Load.Failed -> item {
                                Alert(text = stringResource(R.string.problem_unreachable), tone = Tone.Danger)
                            }

                            is Load.Ready -> {
                                if (search.value.isEmpty()) {
                                    item { Alert(text = stringResource(R.string.search_empty), tone = Tone.Neutral) }
                                } else {
                                    items(search.value, key = { it.id }) { hit ->
                                        SearchRow(hit = hit, onOpen = { onOpenHit(hit) })
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            SectionHeading(
                                text = stringResource(R.string.areas_section),
                                trailing = pluralStringResource(
                                    R.plurals.areas_count,
                                    board.areas.size,
                                    board.areas.size,
                                ),
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
                    }

                        if (problem != null && !searchMode) {
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
                    text = stringResource(R.string.area_row_meta, area.active, area.houses),
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

/**
 * The collector's own takings, as the panel's dashboard shows them: today beside this month, each with
 * the count of payments under it and a tinted tile of its own.
 */
@Composable
private fun MoneyRow(stats: CollectorStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MoneyCard(
            label = stringResource(R.string.stats_today),
            total = stats.todayTotal,
            count = stats.todayCount,
            tone = Tone.Success,
            icon = Icons.Filled.Payments,
            modifier = Modifier.weight(1f),
        )
        MoneyCard(
            label = stringResource(R.string.stats_month),
            total = stats.monthTotal,
            count = stats.monthCount,
            tone = Tone.Primary,
            icon = Icons.Filled.Payments,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MoneyCard(
    label: String,
    total: String,
    count: Int,
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
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Amount(value = Money.usd(total), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(3.dp))
                MicroLabel(pluralStringResource(R.plurals.payments_count, count, count))
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

/**
 * Cash still in the collector's pocket — the one figure that is his, and the only one he must be able
 * to check before he hands anything over.
 *
 * Under it, the last few handovers: the dates behind the «سلّمت» figure, because a total with no dates
 * under it is a number he has to take the office's word for.
 */
@Composable
private fun CashInHand(stats: CollectorStats) {
    MotoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                MicroLabel(stringResource(R.string.cash_in_hand))
                Spacer(Modifier.height(4.dp))
                Amount(
                    value = Money.usd(stats.inHand),
                    tone = Tone.Primary,
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.cash_handed_line, Money.usd(stats.handedTotal)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Badge(
                text = stringResource(R.string.required_badge),
                tone = if (stats.inHand != "0.00") Tone.Warning else Tone.Success,
                size = BadgeSize.Large,
            )
        }

        if (stats.handovers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.handovers.forEach { handover ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = listOfNotNull(handover.date, handover.note).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Amount(
                            value = Money.usd(handover.amount),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/** The panel's search field: a bordered box with a magnifier and the collector's own question in it. */
@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )

        Spacer(Modifier.width(10.dp))

        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )

        // One tap back to the day: with the keyboard open this is the only way out that does not need
        // a second hand, and a collector in a street has one hand.
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onClear() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.search_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** One household as a search answer: enough to recognise the door before walking to it. */
@Composable
private fun SearchRow(hit: SearchHit, onOpen: () -> Unit) {
    MotoCard(onClick = onOpen, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tile(tone = if (hit.isMetered) Tone.Info else Tone.Violet, size = 40.dp) {
                Text(
                    text = if (hit.isMetered) "MTR" else "FLT",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = hit.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (hit.status == "inactive") {
                        Spacer(Modifier.width(6.dp))
                        Badge(stringResource(R.string.house_inactive))
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = listOfNotNull(hit.route, hit.meterNo).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (hit.owes) {
                    Amount(
                        value = Money.usd(hit.balance),
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
