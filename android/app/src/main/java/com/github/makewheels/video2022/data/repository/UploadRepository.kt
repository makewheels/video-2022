package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.VideoApi
import com.github.makewheels.video2022.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadRepository @Inject constructor(
    private val videoApi: VideoApi
) {
    suspend fun createVideo(filename: String, size: Long): Result<CreateVideoResponse> = runCatching {
        val request = CreateVideoRequest(
            videoType = "USER_UPLOAD",
            rawFilename = filename,
            size = size
        )
        val resp = videoApi.createVideo(request)
        if (!resp.isSuccess) throw Exception(resp.message ?: "创建视频失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun createYoutubeVideo(youtubeUrl: String): Result<CreateVideoResponse> = runCatching {
        val request = CreateVideoRequest(
            videoType = "YOUTUBE",
            youtubeUrl = youtubeUrl
        )
        val resp = videoApi.createVideo(request)
        if (!resp.isSuccess) throw Exception(resp.message ?: "创建 YouTube 视频失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun getUploadCredentials(fileId: String): Result<UploadCredentials> = runCatching {
        val resp = videoApi.getUploadCredentials(fileId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取上传凭证失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun notifyUploadFinish(fileId: String, videoId: String): Result<Unit> = runCatching {
        videoApi.uploadFinish(fileId)
        videoApi.rawFileUploadFinish(videoId)
    }
}
