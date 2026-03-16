package com.github.makewheels.video2022.data.model

data class SearchResultResponse(
    val content: List<VideoItem>,
    val total: Long,
    val totalPages: Int,
    val currentPage: Int,
    val pageSize: Int
)
