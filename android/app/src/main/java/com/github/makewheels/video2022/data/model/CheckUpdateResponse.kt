package com.github.makewheels.video2022.data.model

data class CheckUpdateResponse(
    val hasUpdate: Boolean,
    val versionCode: Int?,
    val versionName: String?,
    val versionInfo: String?,
    val downloadUrl: String?,
    val isForceUpdate: Boolean?
)
