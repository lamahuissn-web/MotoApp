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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.MotoApi

/**
 * The door.
 *
 * A collector signs in once per phone: the identity is a username or an address, read exactly the way
 * the panel reads it (the server strips the invisible characters a phone keyboard leaves behind), so
 * what he types on a phone keyboard is what he types on a laptop.
 */
@Composable
fun LoginScreen(
    busy: Boolean,
    problem: Problem?,
    onSignIn: (String, String) -> Unit,
) {
    var identity by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val canSubmit = identity.isNotBlank() && password.isNotBlank() && !busy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = stringResource(R.string.brand_mark_description),
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

        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp),
        )

        OutlinedTextField(
            value = identity,
            onValueChange = { identity = it },
            label = { Text(stringResource(R.string.login_identity)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            // Phone keyboards insert invisible characters and capitalise addresses; the server cleans
            // them, but the keyboard should not be typing them in the first place.
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSignIn(identity, password) }),
        )

        Button(
            onClick = { onSignIn(identity, password) },
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.login_button), style = MaterialTheme.typography.labelLarge)
            }
        }

        if (problem != null) {
            ProblemCard(
                title = stringResource(R.string.login_failed_title),
                message = problem.message,
                code = problem.code,
            )
        }

        Text(
            text = stringResource(R.string.server_label) + ": " + MotoApi.baseUrl,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
