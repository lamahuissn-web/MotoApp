package com.lamahuissn.motoapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.MotoApi
import com.lamahuissn.motoapp.data.ServerAnswer
import kotlinx.coroutines.launch

/** What the screen is doing right now, as one value rather than three booleans. */
private sealed interface Probe {
    data object Idle : Probe
    data object Running : Probe
    data class Answered(val answer: ServerAnswer) : Probe
}

/**
 * The first screen, and the only one so far: it proves the phone reaches the office's server.
 *
 * It is deliberately a real call rather than a placeholder — the address is the compiled-in one, the
 * answer is the server's own sentence in the office's language, and a failure says which failure it
 * was. Everything the collector will use is built on this same client and this same contract.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen() {
    val scope = rememberCoroutineScope()
    var probe: Probe by remember { mutableStateOf(Probe.Idle) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BrandHeader()
            ServerCard()

            Button(
                onClick = {
                    probe = Probe.Running
                    scope.launch { probe = Probe.Answered(MotoApi.ping()) }
                },
                enabled = probe !is Probe.Running,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = stringResource(
                        if (probe is Probe.Running) R.string.probe_running else R.string.probe_button,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            when (val state = probe) {
                Probe.Idle -> Unit
                Probe.Running -> Unit
                is Probe.Answered -> AnswerCard(state.answer)
            }

            Text(
                text = stringResource(R.string.next_slice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.server_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // An address is read left to right even on an Arabic screen: the isolate keeps the
            // hostname in one piece instead of letting the bidi algorithm scatter its dots.
            Text(
                text = MotoApi.baseUrl,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun AnswerCard(answer: ServerAnswer) {
    val (tint, container) = when {
        !answer.reachable -> Icons.Filled.CloudOff to MaterialTheme.colorScheme.errorContainer
        answer.ok -> Icons.Filled.CloudDone to MaterialTheme.colorScheme.tertiaryContainer
        else -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.secondaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(imageVector = tint, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!answer.reachable) {
                    Text(
                        text = stringResource(R.string.result_unreachable_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.result_unreachable_body, answer.trouble.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.result_answer_title, answer.httpStatus),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    answer.code?.let { code ->
                        Text(
                            text = stringResource(R.string.result_code_label, code),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    answer.message?.let { message ->
                        Text(text = message, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (answer.message == null && answer.code == null) {
                        Text(
                            text = stringResource(R.string.result_unexpected_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
