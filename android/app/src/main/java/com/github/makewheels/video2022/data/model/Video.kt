package com.github.makewheels.video2022.data.model

data class VideoItem(
    val id: String,
    val watchId: String?,
    val title: String?,
    val description: String?,
    val status: String?,
    val visibility: String?,
    val watchCount: Int?,
    val duration: Long?,
    val createTime: String?,
    val createTimeString: String?,
    val watchUrl: String?,
    val shortUrl: String?,
    val type: String?,
    val coverUrl: String?,
    val youtubePublishTimeString: String?,
    val uploaderName: String? = null,
    val uploaderAvatarUrl: String? = null,
    val uploaderId: String? = null
)

data class VideoListResponse(
    val list: List<VideoItem>,
    val total: Long
)

data class CreateVideoRequest(
    val videoType: String,
    val rawFilename: String? = null,
    val youtubeUrl: String? = null,
    val size: Long? = null,
    val ttl: String = "PERMANENT"
)

data class CreateVideoResponse(
    val watchId: String,
    val shortUrl: String,
    val videoId: String,
    val watchUrl: String,
    val fileId: String
)

data class UpdateVideoInfoRequest(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val visibility: String? = null
)

data class VideoStatus(
    val videoId: String,
    val status: String,
    val isReady: Boolean
)

data class UpdateWatchSettingsRequest(
    val videoId: String,
    val showUploadTime: Boolean? = null,
    val showWatchCount: Boolean? = null
)
