package com.github.makewheels.video2022.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.model.Playlist
import com.github.makewheels.video2022.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val showCreateDialog: Boolean = false,
    val newTitle: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState = _uiState.asStateFlow()
    private val pageSize = 20

    init { loadPlaylists() }

    fun loadPlaylists() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            playlistRepository.getMyPlaylists(0, pageSize)
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(
                        playlists = list, isLoading = false, hasMore = list.size >= pageSize
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message)
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            playlistRepository.getMyPlaylists(state.playlists.size, pageSize)
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(
                        playlists = _uiState.value.playlists + list, isLoading = false, hasMore = list.size >= pageSize
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
    }

    fun showCreateDialog() { _uiState.value = _uiState.value.copy(showCreateDialog = true, newTitle = "") }
    fun dismissCreateDialog() { _uiState.value = _uiState.value.copy(showCreateDialog = false) }
    fun updateNewTitle(title: String) { _uiState.value = _uiState.value.copy(newTitle = title) }

    fun createPlaylist() {
        val title = _uiState.value.newTitle.trim()
        if (title.isBlank()) return
        viewModelScope.launch {
            playlistRepository.createPlaylist(title)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(showCreateDialog = false)
                    loadPlaylists()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(errorMessage = it.message)
                }
        }
    }
}
