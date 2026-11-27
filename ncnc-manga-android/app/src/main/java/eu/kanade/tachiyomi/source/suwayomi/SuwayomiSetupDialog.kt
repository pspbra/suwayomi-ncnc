package eu.kanade.tachiyomi.source.suwayomi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.app.di.appGraph

@Composable
@Suppress("ktlint:standard:function-naming")
fun SuwayomiSetupDialog(
    dismissible: Boolean,
    onConfigured: () -> Unit,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val preferences = remember { SuwayomiServerPreferences(context) }
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(preferences.serverUrl.ifBlank { "https://mangasub.filexiazaiwr.work:82" }) }
    var username by remember { mutableStateOf(preferences.username) }
    var password by remember { mutableStateOf(preferences.password) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (dismissible && !saving) onDismiss() },
        title = { Text("连接 Suwayomi") },
        text = {
            Column {
                Text("请输入服务器地址和 Suwayomi 多用户账户。登录后将保存会话，并同步此账号的阅读记录。")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://example.com:4567") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("账户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && url.isNotBlank(),
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            preferences.login(url, username, password)
                        }.onSuccess {
                            // The cached server sources belong to the previous server.
                            context.appGraph.suwayomiSourceCache.clear()
                            saving = false
                            onConfigured()
                        }.onFailure {
                            saving = false
                            error = "连接失败：${it.message ?: "请检查地址和账户信息"}"
                        }
                    }
                },
            ) {
                if (saving) {
                    CircularProgressIndicator()
                } else {
                    Text("保存并连接")
                }
            }
        },
        dismissButton =
            if (dismissible) {
                { TextButton(onClick = onDismiss) { Text("取消") } }
            } else {
                null
            },
    )
}
