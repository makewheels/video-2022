package com.github.makewheels.video2022.ui.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.repository.UploadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UploadUiState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val fileSize: Long = 0,
    val title: String = "",
    val description: String = "",
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val isUploadComplete: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val uploadRepository: UploadRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState = _uiState.asStateFlow()

    fun setSelectedVideo(uri: Uri, fileName: String, fileSize: Long) {
        _uiState.value = _uiState.value.copy(
            selectedUri = uri,
            fileName = fileName,
            fileSize = fileSize,
            title = fileName.substringBeforeLast(".")
        )
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun updateDescription(desc: String) {
        _uiState.value = _uiState.value.copy(description = desc)
    }

    fun clearSelection() {
        _uiState.value = UploadUiState()
    }

    fun startUpload(
        onEnqueueWorker: (
            fileUri: String, fileId: String, videoId: String,
            bucket: String, endpoint: String, key: String,
            accessKeyId: String, secretKey: String, sessionToken: String
        ) -> Unit
    ) {
        val state = _uiState.value
        val uri = state.selectedUri ?: return

        viewModelScope.launch {
            _uiState.value = state.copy(isUploading = true, errorMessage = null)

            uploadRepository.createVideo(state.fileName, state.fileSize)
                .onSuccess { createResp ->
                    uploadRepository.getUploadCredentials(createResp.fileId)
                        .onSuccess { creds ->
                            onEnqueueWorker(
                                uri.toString(),
                                createResp.fileId,
                                createResp.videoId,
                                creds.bucket,
                                creds.endpoint,
                                creds.key,
                                creds.accessKeyId,
                                creds.secretKey,
                                creds.sessionToken
                            )
                        }
                        .onFailure {
                            _uiState.value = _uiState.value.copy(
                                isUploading = false, errorMessage = it.message
                            )
                        }
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false, errorMessage = it.message
                    )
                }
        }
    }

    fun setUploadProgress(progress: Float) {
        _uiState.value = _uiState.value.copy(uploadProgress = progress)
    }

    fun setUploadComplete() {
        _uiState.value = _uiState.value.copy(isUploading = false, isUploadComplete = true)
    }
}
