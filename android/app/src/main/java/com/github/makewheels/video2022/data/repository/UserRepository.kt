package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.UserApi
import com.github.makewheels.video2022.data.model.ChannelInfo
import com.github.makewheels.video2022.data.model.UpdateProfileRequest
import com.github.makewheels.video2022.data.model.User
import com.github.makewheels.video2022.util.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userApi: UserApi,
    private val tokenManager: TokenManager
) {
    suspend fun requestVerificationCode(phone: String): Result<Unit> = runCatching {
        val response = userApi.requestVerificationCode(phone)
        if (!response.isSuccess) throw Exception(response.message ?: "请求验证码失败")
    }

    suspend fun submitVerificationCode(phone: String, code: String): Result<User> = runCatching {
        val response = userApi.submitVerificationCode(phone, code)
        if (!response.isSuccess) throw Exception(response.message ?: "验证码错误")
        val user = response.data ?: throw Exception("返回用户数据为空")
        user.token?.let { tokenManager.setToken(it) }
        tokenManager.setUserPhone(phone)
        user
    }

    suspend fun initClientAndSession() {
        if (tokenManager.getClientIdSync() == null) {
            val clientResp = userApi.requestClientId()
            clientResp.data?.get("clientId")?.let { tokenManager.setClientId(it) }
        }
        val sessionResp = userApi.requestSessionId()
        sessionResp.data?.get("sessionId")?.let { tokenManager.setSessionId(it) }
    }

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    suspend fun logout() {
        tokenManager.clearToken()
    }

    suspend fun updateProfile(request: UpdateProfileRequest): Result<Unit> = runCatching {
        val response = userApi.updateProfile(request)
        if (!response.isSuccess) throw Exception(response.message ?: "更新个人资料失败")
    }

    suspend fun getMyProfile(): Result<User> = runCatching {
        val response = userApi.getMyProfile()
        if (!response.isSuccess) throw Exception(response.message ?: "获取个人资料失败")
        response.data ?: throw Exception("返回数据为空")
    }

    suspend fun getChannel(userId: String): Result<ChannelInfo> = runCatching {
        val response = userApi.getChannel(userId)
        if (!response.isSuccess) throw Exception(response.message ?: "获取频道信息失败")
        response.data ?: throw Exception("返回数据为空")
    }

    suspend fun subscribe(channelUserId: String): Result<Unit> = runCatching {
        val response = userApi.subscribe(channelUserId)
        if (!response.isSuccess) throw Exception(response.message ?: "订阅失败")
    }

    suspend fun unsubscribe(channelUserId: String): Result<Unit> = runCatching {
        val response = userApi.unsubscribe(channelUserId)
        if (!response.isSuccess) throw Exception(response.message ?: "取消订阅失败")
    }

    suspend fun getSubscriptionStatus(channelUserId: String): Result<Boolean> = runCatching {
        val response = userApi.getSubscriptionStatus(channelUserId)
        if (!response.isSuccess) throw Exception(response.message ?: "获取订阅状态失败")
        response.data ?: false
    }
}
