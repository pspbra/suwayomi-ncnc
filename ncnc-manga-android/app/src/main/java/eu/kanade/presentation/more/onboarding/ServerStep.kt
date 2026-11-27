package eu.kanade.presentation.more.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.suwayomi.SuwayomiServerPreferences
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

internal class ServerStep : OnboardingStep {
    private var configured by mutableStateOf(false)

    override val isComplete: Boolean
        get() = configured

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val preferences = remember { SuwayomiServerPreferences(context) }
        val scope = rememberCoroutineScope()
        var url by remember { mutableStateOf(preferences.serverUrl) }
        var username by remember { mutableStateOf(preferences.username) }
        var password by remember { mutableStateOf(preferences.password) }
        var error by remember { mutableStateOf<String?>(null) }
        var saving by remember { mutableStateOf(false) }
        val connectionFailedMessage = stringResource(MR.strings.onboarding_server_connection_failed)

        LaunchedEffect(Unit) {
            configured = preferences.isConfigured
        }

        // The onboarding screen already scrolls its content, so this must not add
        // another vertical scroll container: nesting them crashes on measurement.
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(MR.strings.onboarding_server_description))
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    configured = false
                    error = null
                },
                label = { Text(stringResource(MR.strings.onboarding_server_url)) },
                placeholder = { Text("https://example.com:4567") },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    configured = false
                    error = null
                },
                label = { Text(stringResource(MR.strings.username)) },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    configured = false
                    error = null
                },
                label = { Text(stringResource(MR.strings.password)) },
                singleLine = true,
                enabled = !saving,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !saving && url.isNotBlank(),
                onClick = {
                    val wasConfigured = preferences.isConfigured
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            preferences.login(url, username, password)
                        }.onSuccess {
                            configured = true
                            saving = false
                        }.onFailure {
                            configured = wasConfigured
                            saving = false
                            error = it.message ?: connectionFailedMessage
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (saving) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(MR.strings.onboarding_server_connect))
                }
            }
            if (configured) {
                Text(
                    text = stringResource(MR.strings.onboarding_server_connected),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
