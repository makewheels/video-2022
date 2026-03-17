package com.github.makewheels.video2022.ui.watchhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.model.WatchHistoryItem
import com.github.makewheels.video2022.data.repository.WatchHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchHistoryUiState(
    val videos: List<WatchHistoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val showClearDialog: Boolean = false,
    val errorMessage: String? = null,
    val currentPage: Int = 0
)

@HiltViewModel
class WatchHistoryViewModel @Inject constructor(
    private val repository: WatchHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchHistoryUiState())
    val uiState = _uiState.asStateFlow()

    private val pageSize = 20

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.getWatchHistory(0, pageSize)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        videos = resp.list,
                        isLoading = false,
                        hasMore = resp.list.size < resp.total,
                        currentPage = 0
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = it.message
                    )
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            val nextPage = state.currentPage + 1
            repository.getWatchHistory(nextPage, pageSize)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        videos = _uiState.value.videos + resp.list,
                        isLoading = false,
                        hasMore = _uiState.value.videos.size + resp.list.size < resp.total,
                        currentPage = nextPage
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }

    fun showClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = true)
    }

    fun dismissClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = false)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearWatchHistory()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        videos = emptyList(),
                        showClearDialog = false,
                        hasMore = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        showClearDialog = false,
                        errorMessage = it.message
                    )
                }
        }
    }
}