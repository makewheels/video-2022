package com.github.makewheels.video2022.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.model.VideoItem
import com.github.makewheels.video2022.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val selectedCategory: String? = null,
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val hasSearched: Boolean = false,
    val total: Long = 0,
    val currentPage: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private val pageSize = 20

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun selectCategory(category: String?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        search()
    }

    fun search() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null, currentPage = 0)
            searchRepository.search(state.query, state.selectedCategory, 0, pageSize)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        videos = resp.content,
                        isLoading = false,
                        hasMore = resp.currentPage < resp.totalPages - 1,
                        hasSearched = true,
                        total = resp.total,
                        currentPage = 0
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasSearched = true,
                        errorMessage = it.message
                    )
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        val nextPage = state.currentPage + 1
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            searchRepository.search(state.query, state.selectedCategory, nextPage, pageSize)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        videos = _uiState.value.videos + resp.content,
                        isLoading = false,
                        hasMore = resp.currentPage < resp.totalPages - 1,
                        currentPage = nextPage,
                        total = resp.total
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }
}
