package com.github.makewheels.video2022.ui.myvideos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.model.VideoItem
import com.github.makewheels.video2022.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyVideosUiState(
    val videos: List<VideoItem> = emptyList(),
    val keyword: String = "",
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val deleteTargetId: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class MyVideosViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyVideosUiState())
    val uiState = _uiState.asStateFlow()
    private val pageSize = 20

    init {
        loadVideos()
    }

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val kw = _uiState.value.keyword.ifBlank { null }
            videoRepository.getMyVideoList(0, pageSize, kw)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        videos = resp.list,
                        isLoading = false,
                        hasMore = resp.list.size >= pageSize
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, errorMessage = it.message
                    )
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            val kw = state.keyword.ifBlank { null }
            videoRepository.getMyVideoList(state.videos.size, pageSize, kw)
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

    fun updateKeyword(keyword: String) {
        _uiState.value = _uiState.value.copy(keyword = keyword)
    }

    fun search() {
        loadVideos()
    }

    fun showDeleteDialog(videoId: String) {
        _uiState.value = _uiState.value.copy(showDeleteDialog = true, deleteTargetId = videoId)
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = false, deleteTargetId = null)
    }

    fun confirmDelete() {
        val videoId = _uiState.value.deleteTargetId ?: return
        viewModelScope.launch {
            videoRepository.deleteVideo(videoId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        videos = _uiState.value.videos.filter { it.id != videoId },
                        showDeleteDialog = false,
                        deleteTargetId = null
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        showDeleteDialog = false, errorMessage = it.message
                    )
                }
        }
    }
}
