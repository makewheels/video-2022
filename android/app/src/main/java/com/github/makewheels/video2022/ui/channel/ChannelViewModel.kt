package com.github.makewheels.video2022.ui.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.model.ChannelInfo
import com.github.makewheels.video2022.data.model.VideoItem
import com.github.makewheels.video2022.data.repository.UserRepository
import com.github.makewheels.video2022.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelUiState(
    val channel: ChannelInfo? = null,
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val videoRepository: VideoRepository
) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadChannel()
    }

    fun loadChannel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userRepository.getChannel(userId)
                .onSuccess { channel ->
                    _uiState.value = _uiState.value.copy(channel = channel, isLoading = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message)
                }
            // Load videos
            videoRepository.getPublicVideoList(0, 50)
                .onSuccess { resp ->
                    val filtered = resp.list.filter { it.uploaderId == userId }
                    _uiState.value = _uiState.value.copy(videos = filtered)
                }
        }
    }

    fun toggleSubscribe() {
        val channel = _uiState.value.channel ?: return
        viewModelScope.launch {
            if (channel.isSubscribed) {
                userRepository.unsubscribe(userId)
            } else {
                userRepository.subscribe(userId)
            }
            loadChannel()
        }
    }
}
