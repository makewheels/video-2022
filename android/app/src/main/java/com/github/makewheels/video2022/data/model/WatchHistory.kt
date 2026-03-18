package com.github.makewheels.video2022.data.model

data class WatchHistoryItem(
    val videoId: String,
    val title: String?,
    val coverUrl: String?,
    val watchTime: String?
)

data class WatchHistoryResponse(
    val list: List<WatchHistoryItem>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)