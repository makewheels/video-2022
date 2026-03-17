package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.WatchApi
import com.github.makewheels.video2022.data.model.WatchHistoryResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchHistoryRepository @Inject constructor(
    private val watchApi: WatchApi
) {
    suspend fun getWatchHistory(page: Int, pageSize: Int): Result<WatchHistoryResponse> = runCatching {
        val resp = watchApi.getWatchHistory(page, pageSize)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取观看历史失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun clearWatchHistory(): Result<Unit> = runCatching {
        val resp = watchApi.clearWatchHistory()
        if (!resp.isSuccess) throw Exception(resp.message ?: "清除观看历史失败")
    }
}