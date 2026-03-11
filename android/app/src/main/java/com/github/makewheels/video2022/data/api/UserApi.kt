package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import retrofit2.http.GET
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
}
