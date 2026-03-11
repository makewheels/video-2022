package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import retrofit2.http.*

interface CommentApi {
    @POST("/comment/add")
    suspend fun addComment(@Body request: AddCommentRequest): ApiResponse<Comment>

    @GET("/comment/getByVideoId")
    suspend fun getByVideoId(
        @Query("videoId") videoId: String,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "time"
    ): ApiResponse<List<Comment>>

    @GET("/comment/getReplies")
    suspend fun getReplies(
        @Query("parentId") parentId: String,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 20
    ): ApiResponse<List<Comment>>

    @GET("/comment/delete")
    suspend fun deleteComment(@Query("commentId") commentId: String): ApiResponse<Any?>

    @GET("/comment/like")
    suspend fun likeComment(@Query("commentId") commentId: String): ApiResponse<Any?>

    @GET("/comment/getCount")
    suspend fun getCount(@Query("videoId") videoId: String): ApiResponse<CommentCount>
}
