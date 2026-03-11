package com.github.makewheels.video2022.ui.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.repository.UploadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YouTubeUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val watchUrl: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class YouTubeViewModel @Inject constructor(
    private val uploadRepository: UploadRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(YouTubeUiState())
    val uiState = _uiState.asStateFlow()

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(url = url, errorMessage = null, isSuccess = false)
    }

    fun download() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请输入 YouTube 链接")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            uploadRepository.createYoutubeVideo(url)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, isSuccess = true, watchUrl = resp.watchUrl
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message)
                }
        }
    }

    fun reset() {
        _uiState.value = YouTubeUiState()
    }
}
