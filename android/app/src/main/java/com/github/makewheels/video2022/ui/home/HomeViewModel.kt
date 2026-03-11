package com.github.makewheels.video2022.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.model.VideoItem
import com.github.makewheels.video2022.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val pageSize = 20

    init {
        loadVideos()
    }

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            videoRepository.getMyVideoList(0, pageSize)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        videos = resp.list,
                        isLoading = false,
                        hasMore = resp.list.size >= pageSize
                    )
                }
                .onFailure {
                    _uiState.value =
                        _uiState.value.copy(isLoading = false, errorMessage = it.message)
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            videoRepository.getMyVideoList(0, pageSize)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        videos = resp.list,
                        isRefreshing = false,
                        hasMore = resp.list.size >= pageSize
                    )
                }
                .onFailure {
                    _uiState.value =
                        _uiState.value.copy(isRefreshing = false, errorMessage = it.message)
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            videoRepository.getMyVideoList(state.videos.size, pageSize)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        videos = _uiState.value.videos + resp.list,
                        isLoading = false,
                        hasMore = resp.list.size >= pageSize
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }
}
