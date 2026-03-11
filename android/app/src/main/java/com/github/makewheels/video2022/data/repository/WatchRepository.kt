package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.WatchApi
import com.github.makewheels.video2022.data.model.*
import com.github.makewheels.video2022.util.TokenManager
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchRepository @Inject constructor(
    private val watchApi: WatchApi,
    private val tokenManager: TokenManager
) {
    suspend fun getWatchInfo(watchId: String): Result<WatchInfo> = runCatching {
        val resp = watchApi.getWatchInfo(watchId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取播放信息失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun sendHeartbeat(
        videoId: String,
        playerTimeMs: Long,
        playerStatus: String,
        volume: Float
    ): Result<Unit> = runCatching {
        val request = HeartbeatRequest(
            videoId = videoId,
            clientId = tokenManager.getClientIdSync() ?: "",
            sessionId = tokenManager.getSessionIdSync() ?: "",
            clientTime = Instant.now().toString(),
            playerTime = playerTimeMs / 1000,
            playerStatus = playerStatus,
            playerVolume = volume
        )
        watchApi.addHeartbeat(request)
    }

    suspend fun getProgress(videoId: String): Result<Long?> = runCatching {
        val clientId = tokenManager.getClientIdSync() ?: return@runCatching null
        val resp = watchApi.getProgress(videoId, clientId)
        resp.data?.progressInMillis
    }
}
