package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import com.github.makewheels.video2022.data.model.ChannelInfo
import com.github.makewheels.video2022.data.model.UpdateProfileRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface UserApi {
    @GET("/user/requestVerificationCode")
    suspend fun requestVerificationCode(@Query("phone") phone: String): ApiResponse<Any?>

    @GET("/user/submitVerificationCode")
    suspend fun submitVerificationCode(
        @Query("phone") phone: String,
        @Query("code") code: String
    ): ApiResponse<User>

    @GET("/user/getUserByToken")
    suspend fun getUserByToken(@Query("token") token: String): ApiResponse<User>

    @GET("/client/requestClientId")
    suspend fun requestClientId(): ApiResponse<Map<String, String>>

    @GET("/session/requestSessionId")
    suspend fun requestSessionId(): ApiResponse<Map<String, String>>

    @POST("/user/updateProfile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): ApiResponse<Any?>

    @GET("/user/getMyProfile")
    suspend fun getMyProfile(): ApiResponse<User>

    @GET("/user/getChannel")
    suspend fun getChannel(@Query("userId") userId: String): ApiResponse<ChannelInfo>

    @GET("/subscription/subscribe")
    suspend fun subscribe(@Query("channelUserId") channelUserId: String): ApiResponse<Any?>

    @GET("/subscription/unsubscribe")
    suspend fun unsubscribe(@Query("channelUserId") channelUserId: String): ApiResponse<Any?>

    @GET("/subscription/getStatus")
    suspend fun getSubscriptionStatus(@Query("channelUserId") channelUserId: String): ApiResponse<Boolean>
}
