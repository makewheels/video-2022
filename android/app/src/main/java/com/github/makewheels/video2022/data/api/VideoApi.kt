package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import retrofit2.http.*

interface VideoApi {
    @POST("/video/create")
    suspend fun createVideo(@Body request: CreateVideoRequest): ApiResponse<CreateVideoResponse>

    @GET("/file/getUploadCredentials")
    suspend fun getUploadCredentials(@Query("fileId") fileId: String): ApiResponse<UploadCredentials>

    @GET("/file/uploadFinish")
    suspend fun uploadFinish(@Query("fileId") fileId: String): ApiResponse<Any?>

    @GET("/video/rawFileUploadFinish")
    suspend fun rawFileUploadFinish(@Query("videoId") videoId: String): ApiResponse<Any?>

    @POST("/video/updateInfo")
    suspend fun updateVideoInfo(@Body request: UpdateVideoInfoRequest): ApiResponse<VideoItem>

    @POST("/video/updateWatchSettings")
    suspend fun updateWatchSettings(@Body request: UpdateWatchSettingsRequest): ApiResponse<Any?>

    @GET("/video/getVideoDetail")
    suspend fun getVideoDetail(@Query("videoId") videoId: String): ApiResponse<VideoItem>

    @GET("/video/getMyVideoList")
    suspend fun getMyVideoList(
        @Query("skip") skip: Int,
        @Query("limit") limit: Int,
        @Query("keyword") keyword: String? = null
    ): ApiResponse<VideoListResponse>

    @GET("/video/getPublicVideoList")
    suspend fun getPublicVideoList(
        @Query("skip") skip: Int,
        @Query("limit") limit: Int,
        @Query("keyword") keyword: String? = null
    ): ApiResponse<VideoListResponse>

    @GET("/video/getRawFileDownloadUrl")
    suspend fun getRawFileDownloadUrl(@Query("videoId") videoId: String): ApiResponse<Map<String, String>>

    @GET("/video/delete")
    suspend fun deleteVideo(@Query("videoId") videoId: String): ApiResponse<Any?>

    @GET("/video/getVideoStatus")
    suspend fun getVideoStatus(@Query("videoId") videoId: String): ApiResponse<VideoStatus>

    @GET("/search")
    suspend fun searchVideos(
        @Query("q") query: String,
        @Query("category") category: String?,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): ApiResponse<SearchResultResponse>
}
