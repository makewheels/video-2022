package com.github.makewheels.video2022.data.model

data class ChannelInfo(
    val userId: String,
    val nickname: String?,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val bio: String?,
    val subscriberCount: Long = 0,
    val videoCount: Long = 0,
    val isSubscribed: Boolean = false
)

data class UpdateProfileRequest(
    val nickname: String?,
    val bio: String?
)
