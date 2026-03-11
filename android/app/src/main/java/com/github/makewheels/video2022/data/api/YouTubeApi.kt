package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApi {
    @GET("/youtube/getFileExtension")
    suspend fun getFileExtension(@Query("youtubeVideoId") videoId: String): ApiResponse<Map<String, String>>

    @GET("/youtube/getVideoInfo")
    suspend fun getVideoInfo(@Query("youtubeVideoId") videoId: String): ApiResponse<Map<String, Any>>
}
