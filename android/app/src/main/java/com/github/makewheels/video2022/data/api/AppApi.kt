package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.ApiResponse
import com.github.makewheels.video2022.data.model.CheckUpdateResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface AppApi {
    @GET("/app/checkUpdate")
    suspend fun checkUpdate(
        @Query("platform") platform: String,
        @Query("versionCode") versionCode: Int
    ): ApiResponse<CheckUpdateResponse>
}
