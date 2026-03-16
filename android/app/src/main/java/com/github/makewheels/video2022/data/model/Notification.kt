package com.github.makewheels.video2022.data.model

import java.util.Date

data class NotificationItem(
    val id: String,
    val type: String?,
    val content: String?,
    val fromUserId: String?,
    val toUserId: String?,
    val relatedVideoId: String?,
    val relatedCommentId: String?,
    val read: Boolean = false,
    val createTime: String?
)

data class NotificationListResponse(
    val list: List<NotificationItem>,
    val total: Long,
    val totalPages: Int,
    val currentPage: Int,
    val pageSize: Int
)
