package eu.kanade.presentation.more.settings.screen

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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.source.suwayomi.SuwayomiServerPreferences
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Server connection editor exposed from the regular application settings. */
object SettingsServerScreen : SearchableSettings {
    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = MR.strings.pref_category_server

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val preferences = remember { SuwayomiServerPreferences(context) }
        val syncStatus by context.appGraph.suwayomiReadingSync.status
            .collectAsState()
        val scope = rememberCoroutineScope()
        var url by remember { mutableStateOf(preferences.serverUrl) }
        var username by remember { mutableStateOf(preferences.username) }
        var password by remember { mutableStateOf(preferences.password) }
        var error by remember { mutableStateOf<String?>(null) }
        var connected by remember { mutableStateOf(preferences.isConfigured) }
        var saving by remember { mutableStateOf(false) }
        val connectionFailedMessage = stringResource(MR.strings.onboarding_server_connection_failed)

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.server_connection),
                preferenceItems =
                    listOf(
                        Preference.PreferenceItem.CustomPreference(
                            title = stringResource(MR.strings.server_connection),
                            content = {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = stringResource(MR.strings.onboarding_server_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    OutlinedTextField(
                                        value = url,
                                        onValueChange = {
                                            url = it
                                            connected = false
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
                                            connected = false
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
                                            connected = false
                                            error = null
                                        },
                                        label = { Text(stringResource(MR.strings.password)) },
                                        singleLine = true,
                                        enabled = !saving,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    error?.let {
                                        Text(
                                            text = it,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    Button(
                                        enabled = !saving && url.isNotBlank(),
                                        onClick = {
                                            saving = true
                                            connected = false
                                            error = null
                                            scope.launch {
                                                runCatching {
                                                    preferences.login(url, username, password)
                                                }.onSuccess {
                                                    context.appGraph.suwayomiSourceCache.clear()
                                                    url = preferences.serverUrl
                                                    username = preferences.username
                                                    password = ""
                                                    connected = true
                                                    saving = false
                                                }.onFailure {
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
                                    if (preferences.isConfigured) {
                                        Button(onClick = {
                                            preferences.clear()
                                            context.appGraph.suwayomiSourceCache.clear()
                                            password = ""
                                            connected = false
                                        }, enabled = !saving) { Text("退出服务器账号") }
                                        Button(onClick = {
                                            scope.launch { context.appGraph.suwayomiReadingSync.synchronize() }
                                        }, enabled = !saving) { Text("立即同步阅读记录") }
                                        if (syncStatus.isNotBlank()) Text(syncStatus)
                                    }
                                    if (connected) {
                                        Text(
                                            text = stringResource(MR.strings.onboarding_server_connected),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            },
                        ),
                    ),
            ),
        )
    }
}
