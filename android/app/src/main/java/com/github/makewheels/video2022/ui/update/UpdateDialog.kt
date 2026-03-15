package com.github.makewheels.video2022.ui.update

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onUpdate: () -> Unit,
    onDismiss: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text("发现新版本 ${state.versionName ?: ""}") },
        text = {
            Column {
                if (!state.versionInfo.isNullOrBlank()) {
                    Text(
                        text = state.versionInfo,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (state.isDownloading) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { state.downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${(state.downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !state.isDownloading
            ) {
                Text(if (state.isDownloading) "下载中..." else "立即更新")
            }
        },
        dismissButton = if (onDismiss != null && !state.isDownloading) {
            { TextButton(onClick = onDismiss) { Text("稍后再说") } }
        } else {
            null
        }
    )
}
