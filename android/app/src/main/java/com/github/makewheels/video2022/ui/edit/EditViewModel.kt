package com.github.makewheels.video2022.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.model.UpdateVideoInfoRequest
import com.github.makewheels.video2022.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditUiState(
    val videoId: String = "",
    val title: String = "",
    val description: String = "",
    val visibility: String = "PUBLIC",
    val tags: List<String> = emptyList(),
    val category: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class EditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository
) : ViewModel() {
    private val videoId: String = savedStateHandle["videoId"] ?: ""
    private val _uiState = MutableStateFlow(EditUiState(videoId = videoId))
    val uiState = _uiState.asStateFlow()

    init {
        loadVideo()
    }

    private fun loadVideo() {
        viewModelScope.launch {
            videoRepository.getVideoDetail(videoId)
                .onSuccess { video ->
                    _uiState.value = _uiState.value.copy(
                        title = video.title ?: "",
                        description = video.description ?: "",
                        visibility = video.visibility ?: "PUBLIC",
                        tags = video.tags ?: emptyList(),
                        category = video.category ?: "",
                        isLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, errorMessage = it.message
                    )
                }
        }
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title, isSaved = false)
    }

    fun updateDescription(desc: String) {
        _uiState.value = _uiState.value.copy(description = desc, isSaved = false)
    }

    fun updateVisibility(v: String) {
        _uiState.value = _uiState.value.copy(visibility = v, isSaved = false)
    }

    fun updateTags(tags: List<String>) {
        _uiState.value = _uiState.value.copy(tags = tags, isSaved = false)
    }

    fun addTag(tag: String) {
        val current = _uiState.value.tags
        if (tag.isNotBlank() && !current.contains(tag.trim())) {
            _uiState.value = _uiState.value.copy(tags = current + tag.trim(), isSaved = false)
        }
    }

    fun removeTag(tag: String) {
        _uiState.value = _uiState.value.copy(tags = _uiState.value.tags - tag, isSaved = false)
    }

    fun updateCategory(category: String) {
        _uiState.value = _uiState.value.copy(category = category, isSaved = false)
    }

    fun save() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            videoRepository.updateVideoInfo(
                UpdateVideoInfoRequest(
                    id = videoId,
                    title = _uiState.value.title,
                    description = _uiState.value.description,
                    visibility = _uiState.value.visibility,
                    tags = _uiState.value.tags,
                    category = _uiState.value.category
                )
            ).onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isSaving = false, errorMessage = it.message
                )
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            videoRepository.deleteVideo(videoId)
                .onSuccess { onDeleted() }
                .onFailure {
                    _uiState.value = _uiState.value.copy(errorMessage = it.message)
                }
        }
    }
}
