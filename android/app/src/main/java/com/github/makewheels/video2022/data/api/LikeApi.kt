package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query

interface LikeApi {
    @GET("/videoLike/like")
    suspend fun like(@Query("videoId") videoId: String): ApiResponse<Any?>

    @GET("/videoLike/dislike")
    suspend fun dislike(@Query("videoId") videoId: String): ApiResponse<Any?>

    @GET("/videoLike/getStatus")
    suspend fun getStatus(@Query("videoId") videoId: String): ApiResponse<LikeStatus>
}
