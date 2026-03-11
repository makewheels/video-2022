package com.github.makewheels.video2022.ui.watch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.model.LikeStatus
import com.github.makewheels.video2022.data.model.WatchInfo
import com.github.makewheels.video2022.data.repository.CommentRepository
import com.github.makewheels.video2022.data.repository.VideoRepository
import com.github.makewheels.video2022.data.repository.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchUiState(
    val watchInfo: WatchInfo? = null,
    val likeStatus: LikeStatus? = null,
    val commentCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class WatchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val watchRepository: WatchRepository,
    private val videoRepository: VideoRepository,
    private val commentRepository: CommentRepository
) : ViewModel() {
    val watchId: String = savedStateHandle["watchId"] ?: ""

    private val _uiState = MutableStateFlow(WatchUiState())
    val uiState = _uiState.asStateFlow()

    private var heartbeatJob: Job? = null

    init {
        loadWatchInfo()
    }

    private fun loadWatchInfo() {
        viewModelScope.launch {
            watchRepository.getWatchInfo(watchId)
                .onSuccess { info ->
                    _uiState.value = _uiState.value.copy(watchInfo = info, isLoading = false)
                    loadLikeStatus(info.videoId)
                    loadCommentCount(info.videoId)
                }
                .onFailure {
                    _uiState.value =
                        _uiState.value.copy(isLoading = false, errorMessage = it.message)
                }
        }
    }

    private fun loadLikeStatus(videoId: String) {
        viewModelScope.launch {
            videoRepository.getLikeStatus(videoId).onSuccess { status ->
                _uiState.value = _uiState.value.copy(likeStatus = status)
            }
        }
    }

    private fun loadCommentCount(videoId: String) {
        viewModelScope.launch {
            commentRepository.getCount(videoId).onSuccess { count ->
                _uiState.value = _uiState.value.copy(commentCount = count)
            }
        }
    }

    fun startHeartbeat(
        getPlayerTimeMs: () -> Long,
        getPlayerStatus: () -> String,
        getVolume: () -> Float
    ) {
        heartbeatJob?.cancel()
        val videoId = _uiState.value.watchInfo?.videoId ?: return
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                watchRepository.sendHeartbeat(
                    videoId,
                    getPlayerTimeMs(),
                    getPlayerStatus(),
                    getVolume()
                )
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
    }

    fun like() {
        val videoId = _uiState.value.watchInfo?.videoId ?: return
        val current = _uiState.value.likeStatus ?: return
        _uiState.value = _uiState.value.copy(
            likeStatus = current.copy(
                likeCount = if (current.userAction == "LIKE") current.likeCount - 1 else current.likeCount + 1,
                dislikeCount = if (current.userAction == "DISLIKE") current.dislikeCount - 1 else current.dislikeCount,
                userAction = if (current.userAction == "LIKE") null else "LIKE"
            )
        )
        viewModelScope.launch {
            videoRepository.like(videoId).onFailure { loadLikeStatus(videoId) }
        }
    }

    fun dislike() {
        val videoId = _uiState.value.watchInfo?.videoId ?: return
        val current = _uiState.value.likeStatus ?: return
        _uiState.value = _uiState.value.copy(
            likeStatus = current.copy(
                dislikeCount = if (current.userAction == "DISLIKE") current.dislikeCount - 1 else current.dislikeCount + 1,
                likeCount = if (current.userAction == "LIKE") current.likeCount - 1 else current.likeCount,
                userAction = if (current.userAction == "DISLIKE") null else "DISLIKE"
            )
        )
        viewModelScope.launch {
            videoRepository.dislike(videoId).onFailure { loadLikeStatus(videoId) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        heartbeatJob?.cancel()
    }
}
