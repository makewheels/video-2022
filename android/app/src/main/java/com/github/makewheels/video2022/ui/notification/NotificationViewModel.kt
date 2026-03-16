package com.github.makewheels.video2022.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.model.NotificationItem
import com.github.makewheels.video2022.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = true,
    val unreadCount: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState = _uiState.asStateFlow()

    private val pageSize = 20

    init {
        loadNotifications()
        loadUnreadCount()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            notificationRepository.getMyNotifications(0, pageSize)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        notifications = resp.list,
                        isLoading = false,
                        hasMore = resp.list.size >= pageSize
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

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            notificationRepository.getMyNotifications(0, pageSize)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        notifications = resp.list,
                        isRefreshing = false,
                        hasMore = resp.list.size >= pageSize
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        errorMessage = it.message
                    )
                }
            loadUnreadCount()
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val page = _uiState.value.notifications.size / pageSize
            notificationRepository.getMyNotifications(page, pageSize)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        notifications = _uiState.value.notifications + resp.list,
                        isLoading = false,
                        hasMore = resp.list.size >= pageSize
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

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        notifications = _uiState.value.notifications.map {
                            if (it.id == notificationId) it.copy(read = true) else it
                        },
                        unreadCount = maxOf(0, _uiState.value.unreadCount - 1)
                    )
                }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        notifications = _uiState.value.notifications.map { it.copy(read = true) },
                        unreadCount = 0
                    )
                }
        }
    }

    private fun loadUnreadCount() {
        viewModelScope.launch {
            notificationRepository.getUnreadCount()
                .onSuccess { count ->
                    _uiState.value = _uiState.value.copy(unreadCount = count)
                }
        }
    }
}
