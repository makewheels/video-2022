package com.github.makewheels.video2022.ui.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.BuildConfig
import com.github.makewheels.video2022.data.api.AppApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class UpdateUiState(
    val showDialog: Boolean = false,
    val isForceUpdate: Boolean = false,
    val versionName: String? = null,
    val versionInfo: String? = null,
    val downloadUrl: String? = null,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false,
    val downloadedFile: File? = null
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val appApi: AppApi,
    private val apkDownloader: ApkDownloader
) : ViewModel() {
    private val _updateUiState = MutableStateFlow(UpdateUiState())
    val updateUiState = _updateUiState.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            try {
                val response = appApi.checkUpdate("android", BuildConfig.VERSION_CODE)
                if (response.isSuccess && response.data?.hasUpdate == true) {
                    _updateUiState.value = _updateUiState.value.copy(
                        showDialog = true,
                        isForceUpdate = response.data.isForceUpdate == true,
                        versionName = response.data.versionName,
                        versionInfo = response.data.versionInfo,
                        downloadUrl = response.data.downloadUrl
                    )
                }
            } catch (_: Exception) {
                // Silently ignore update check failures
            }
        }
    }

    fun startDownload(context: Context) {
        val url = _updateUiState.value.downloadUrl ?: return
        if (_updateUiState.value.isDownloading) return

        _updateUiState.value = _updateUiState.value.copy(
            isDownloading = true,
            downloadProgress = 0f
        )

        viewModelScope.launch {
            try {
                val file = apkDownloader.download(url) { progress ->
                    _updateUiState.value = _updateUiState.value.copy(
                        downloadProgress = progress
                    )
                }
                _updateUiState.value = _updateUiState.value.copy(
                    isDownloading = false,
                    downloadedFile = file,
                    downloadProgress = 1f
                )
                apkDownloader.installApk(context, file)
            } catch (_: Exception) {
                _updateUiState.value = _updateUiState.value.copy(
                    isDownloading = false,
                    downloadProgress = 0f
                )
            }
        }
    }

    fun dismissDialog() {
        _updateUiState.value = _updateUiState.value.copy(showDialog = false)
    }
}
