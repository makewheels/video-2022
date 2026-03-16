package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.NotificationApi
import com.github.makewheels.video2022.data.model.NotificationListResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val notificationApi: NotificationApi
) {
    suspend fun getMyNotifications(page: Int, pageSize: Int): Result<NotificationListResponse> = runCatching {
        val resp = notificationApi.getMyNotifications(page, pageSize)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取通知失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun markAsRead(notificationId: String): Result<Unit> = runCatching {
        val resp = notificationApi.markAsRead(mapOf("notificationId" to notificationId))
        if (!resp.isSuccess) throw Exception(resp.message ?: "标记已读失败")
    }

    suspend fun markAllAsRead(): Result<Unit> = runCatching {
        val resp = notificationApi.markAllAsRead()
        if (!resp.isSuccess) throw Exception(resp.message ?: "标记全部已读失败")
    }

    suspend fun getUnreadCount(): Result<Int> = runCatching {
        val resp = notificationApi.getUnreadCount()
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取未读数失败")
        resp.data ?: 0
    }
}
