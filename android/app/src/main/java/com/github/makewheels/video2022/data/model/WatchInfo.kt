package com.github.makewheels.video2022.data.model

data class WatchInfo(
    val videoId: String,
    val coverUrl: String?,
    val videoStatus: String?,
    val multivariantPlaylistUrl: String?,
    val progressInMillis: Long?
)

data class HeartbeatRequest(
    val videoId: String,
    val clientId: String,
    val sessionId: String,
    val videoStatus: String = "READY",
    val playerProvider: String = "ANDROID_EXOPLAYER",
    val clientTime: String,
    val type: String = "TIMER",
    val event: String? = null,
    val playerTime: Long,
    val playerStatus: String,
    val playerVolume: Float
)

data class ProgressResponse(
    val progressInMillis: Long?
)
