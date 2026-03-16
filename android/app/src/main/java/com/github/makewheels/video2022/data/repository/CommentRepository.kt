package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.CommentApi
import com.github.makewheels.video2022.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepository @Inject constructor(
    private val commentApi: CommentApi
) {
    suspend fun getComments(videoId: String, page: Int = 0, pageSize: Int = 20): Result<CommentPageResponse> = runCatching {
        val resp = commentApi.getByVideoId(videoId, page, pageSize)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取评论失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun getReplies(parentId: String, skip: Int = 0, limit: Int = 20): Result<List<Comment>> = runCatching {
        val resp = commentApi.getReplies(parentId, skip, limit)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取回复失败")
        resp.data ?: emptyList()
    }

    suspend fun addComment(videoId: String, content: String, parentId: String? = null): Result<Comment> = runCatching {
        val resp = commentApi.addComment(AddCommentRequest(videoId, content, parentId))
        if (!resp.isSuccess) throw Exception(resp.message ?: "发表评论失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun deleteComment(commentId: String): Result<Unit> = runCatching {
        val resp = commentApi.deleteComment(commentId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "删除评论失败")
    }

    suspend fun likeComment(commentId: String): Result<Unit> = runCatching {
        commentApi.likeComment(commentId)
    }

    suspend fun getCount(videoId: String): Result<Int> = runCatching {
        val resp = commentApi.getCount(videoId)
        resp.data?.count ?: 0
    }
}
