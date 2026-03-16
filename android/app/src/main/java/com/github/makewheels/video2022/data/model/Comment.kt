package com.github.makewheels.video2022.data.model

data class Comment(
    val id: String,
    val videoId: String?,
    val userId: String?,
    val userPhone: String?,
    val content: String?,
    val parentId: String?,
    val replyToUserId: String?,
    val replyToUserPhone: String?,
    val likeCount: Int?,
    val replyCount: Int?,
    val createTime: String?,
    val updateTime: String?
)

data class CommentPageResponse(
    val list: List<Comment>,
    val total: Long,
    val totalPages: Int,
    val currentPage: Int,
    val pageSize: Int
)

data class AddCommentRequest(
    val videoId: String,
    val content: String,
    val parentId: String? = null
)

data class CommentCount(
    val count: Int
)
