package com.github.makewheels.video2022.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.model.Comment
import com.github.makewheels.video2022.data.repository.CommentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentUiState(
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val inputText: String = "",
    val replyTarget: Comment? = null,
    val isSending: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CommentViewModel @Inject constructor(
    private val commentRepository: CommentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CommentUiState())
    val uiState = _uiState.asStateFlow()

    private var currentVideoId: String = ""
    private val pageSize = 20

    fun loadComments(videoId: String) {
        currentVideoId = videoId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, comments = emptyList())
            commentRepository.getComments(videoId, 0, pageSize)
                .onSuccess { comments ->
                    _uiState.value = _uiState.value.copy(
                        comments = comments,
                        isLoading = false,
                        hasMore = comments.size >= pageSize
                    )
                }
                .onFailure {
                    _uiState.value =
                        _uiState.value.copy(isLoading = false, errorMessage = it.message)
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            commentRepository.getComments(currentVideoId, state.comments.size, pageSize)
                .onSuccess { comments ->
                    _uiState.value = _uiState.value.copy(
                        comments = _uiState.value.comments + comments,
                        isLoading = false,
                        hasMore = comments.size >= pageSize
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun setReplyTarget(comment: Comment?) {
        _uiState.value = _uiState.value.copy(replyTarget = comment)
    }

    fun sendComment() {
        val state = _uiState.value
        val content = state.inputText.trim()
        if (content.isEmpty() || state.isSending) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSending = true)
            commentRepository.addComment(
                videoId = currentVideoId,
                content = content,
                parentId = state.replyTarget?.id
            ).onSuccess { newComment ->
                _uiState.value = _uiState.value.copy(
                    comments = listOf(newComment) + _uiState.value.comments,
                    inputText = "",
                    replyTarget = null,
                    isSending = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = it.message
                )
            }
        }
    }

    fun clearReplyTarget() {
        _uiState.value = _uiState.value.copy(replyTarget = null)
    }
}
