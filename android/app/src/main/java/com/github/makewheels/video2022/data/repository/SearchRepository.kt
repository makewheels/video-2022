package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.VideoApi
import com.github.makewheels.video2022.data.model.SearchResultResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val videoApi: VideoApi
) {
    suspend fun search(
        query: String,
        category: String?,
        page: Int,
        pageSize: Int
    ): Result<SearchResultResponse> = runCatching {
        val resp = videoApi.searchVideos(query, category, page, pageSize)
        if (!resp.isSuccess) throw Exception(resp.message ?: "搜索失败")
        resp.data ?: throw Exception("数据为空")
    }
}
