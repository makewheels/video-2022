package com.github.makewheels.video2022.ui.upload

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.*
import com.github.makewheels.video2022.service.UploadWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    viewModel: UploadViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        var name = "video.mp4"
        var size = 0L
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = it.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = it.getLong(sizeIdx)
            }
        }
        viewModel.setSelectedVideo(uri, name, size)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("上传视频", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        if (uiState.selectedUri == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { videoPickerLauncher.launch("video/*") }) {
                    Icon(Icons.Filled.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text("从相册选择")
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("文件: ${uiState.fileName}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "大小: ${uiState.fileSize / 1024 / 1024} MB",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("标题") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("描述") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(Modifier.height(16.dp))

            if (uiState.isUploading) {
                LinearProgressIndicator(
                    progress = { uiState.uploadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("上传中 ${(uiState.uploadProgress * 100).toInt()}%")
            } else if (uiState.isUploadComplete) {
                Text("上传完成！", color = MaterialTheme.colorScheme.primary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.clearSelection() }) {
                        Text("取消")
                    }
                    Button(onClick = {
                        viewModel.startUpload { fileUri, fileId, videoId, bucket, endpoint,
                                                key, accessKeyId, secretKey, sessionToken ->
                            val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                                .setInputData(
                                    workDataOf(
                                        UploadWorker.KEY_FILE_URI to fileUri,
                                        UploadWorker.KEY_FILE_ID to fileId,
                                        UploadWorker.KEY_VIDEO_ID to videoId,
                                        UploadWorker.KEY_BUCKET to bucket,
                                        UploadWorker.KEY_ENDPOINT to endpoint,
                                        UploadWorker.KEY_OBJECT_KEY to key,
                                        UploadWorker.KEY_ACCESS_KEY_ID to accessKeyId,
                                        UploadWorker.KEY_SECRET_KEY to secretKey,
                                        UploadWorker.KEY_SESSION_TOKEN to sessionToken
                                    )
                                )
                                .build()
                            WorkManager.getInstance(context).enqueue(workRequest)
                        }
                    }) {
                        Text("开始上传")
                    }
                }
            }

            uiState.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
