package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.ApiResponse
import com.github.makewheels.video2022.data.model.NotificationListResponse
import retrofit2.http.*

interface NotificationApi {
    @GET("/notification/getMyNotifications")
    suspend fun getMyNotifications(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): ApiResponse<NotificationListResponse>

    @POST("/notification/markAsRead")
    suspend fun markAsRead(@Body body: Map<String, String>): ApiResponse<Any?>

    @POST("/notification/markAllAsRead")
    suspend fun markAllAsRead(): ApiResponse<Any?>

    @GET("/notification/getUnreadCount")
    suspend fun getUnreadCount(): ApiResponse<Int>
}
