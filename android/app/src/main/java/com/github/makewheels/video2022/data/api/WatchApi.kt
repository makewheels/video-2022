package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import retrofit2.http.*

interface WatchApi {
    @GET("/watchController/getWatchInfo")
    suspend fun getWatchInfo(@Query("watchId") watchId: String): ApiResponse<WatchInfo>

    @POST("/heartbeat/add")
    suspend fun addHeartbeat(@Body request: HeartbeatRequest): ApiResponse<Any?>

    @GET("/progress/getProgress")
    suspend fun getProgress(
        @Query("videoId") videoId: String,
        @Query("clientId") clientId: String
    ): ApiResponse<ProgressResponse?>
}
