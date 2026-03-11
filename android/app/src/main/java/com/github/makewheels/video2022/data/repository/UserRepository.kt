package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.UserApi
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
}
