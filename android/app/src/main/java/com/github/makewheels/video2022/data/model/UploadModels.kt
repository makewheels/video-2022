package com.github.makewheels.video2022.data.model

data class UploadCredentials(
    val bucket: String,
    val accessKeyId: String,
    val endpoint: String,
    val secretKey: String,
    val provider: String,
    val sessionToken: String,
    val expiration: String,
    val key: String
)
