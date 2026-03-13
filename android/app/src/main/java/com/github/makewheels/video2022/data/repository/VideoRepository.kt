package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.LikeApi
import com.github.makewheels.video2022.data.api.VideoApi
import com.github.makewheels.video2022.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val videoApi: VideoApi,
    private val likeApi: LikeApi
) {
    suspend fun getMyVideoList(skip: Int, limit: Int, keyword: String? = null): Result<VideoListResponse> = runCatching {
        val resp = videoApi.getMyVideoList(skip, limit, keyword)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取视频列表失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun getPublicVideoList(skip: Int, limit: Int, keyword: String? = null): Result<VideoListResponse> = runCatching {
        val resp = videoApi.getPublicVideoList(skip, limit, keyword)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取公开视频列表失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun getVideoDetail(videoId: String): Result<VideoItem> = runCatching {
        val resp = videoApi.getVideoDetail(videoId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取视频详情失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun updateVideoInfo(request: UpdateVideoInfoRequest): Result<VideoItem> = runCatching {
        val resp = videoApi.updateVideoInfo(request)
        if (!resp.isSuccess) throw Exception(resp.message ?: "更新失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun deleteVideo(videoId: String): Result<Unit> = runCatching {
        val resp = videoApi.deleteVideo(videoId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "删除失败")
    }

    suspend fun getVideoStatus(videoId: String): Result<VideoStatus> = runCatching {
        val resp = videoApi.getVideoStatus(videoId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取状态失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun getLikeStatus(videoId: String): Result<LikeStatus> = runCatching {
        val resp = likeApi.getStatus(videoId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取点赞状态失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun like(videoId: String): Result<Unit> = runCatching {
        val resp = likeApi.like(videoId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "点赞失败")
    }

    suspend fun dislike(videoId: String): Result<Unit> = runCatching {
        val resp = likeApi.dislike(videoId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "踩失败")
    }
}
