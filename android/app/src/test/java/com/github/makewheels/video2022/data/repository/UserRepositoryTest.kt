package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.UserApi
import com.github.makewheels.video2022.data.model.*
import com.github.makewheels.video2022.util.TokenManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UserRepositoryTest {

    private lateinit var userApi: UserApi
    private lateinit var tokenManager: TokenManager
    private lateinit var repository: UserRepository

    @Before
    fun setup() {
        userApi = mockk()
        tokenManager = mockk(relaxed = true)
        repository = UserRepository(userApi, tokenManager)
    }

    @Test
    fun `requestVerificationCode returns success`() = runTest {
        coEvery { userApi.requestVerificationCode("13800138000") } returns
                ApiResponse(code = 0, message = "ok", data = null)

        val result = repository.requestVerificationCode("13800138000")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `requestVerificationCode returns failure on error`() = runTest {
        coEvery { userApi.requestVerificationCode("invalid") } returns
                ApiResponse(code = 1, message = "invalid phone", data = null)

        val result = repository.requestVerificationCode("invalid")
        assertTrue(result.isFailure)
        assertEquals("invalid phone", result.exceptionOrNull()?.message)
    }

    @Test
    fun `submitVerificationCode returns user on success`() = runTest {
        val user = User(
            id = "u1", phone = "13800138000", registerChannel = "android",
            createTime = null, updateTime = null, token = "tok123"
        )
        coEvery { userApi.submitVerificationCode("13800138000", "1234") } returns
                ApiResponse(code = 0, message = "ok", data = user)

        val result = repository.submitVerificationCode("13800138000", "1234")
        assertTrue(result.isSuccess)
        assertEquals("u1", result.getOrNull()?.id)
        assertEquals("tok123", result.getOrNull()?.token)
    }

    @Test
    fun `submitVerificationCode saves token via TokenManager`() = runTest {
        val user = User(
            id = "u1", phone = "13800138000", registerChannel = "android",
            createTime = null, updateTime = null, token = "tok123"
        )
        coEvery { userApi.submitVerificationCode("13800138000", "1234") } returns
                ApiResponse(code = 0, message = "ok", data = user)

        repository.submitVerificationCode("13800138000", "1234")

        coVerify { tokenManager.setToken("tok123") }
        coVerify { tokenManager.setUserPhone("13800138000") }
    }

    @Test
    fun `submitVerificationCode returns failure on invalid code`() = runTest {
        coEvery { userApi.submitVerificationCode("13800138000", "0000") } returns
                ApiResponse(code = 1, message = "验证码错误", data = null)

        val result = repository.submitVerificationCode("13800138000", "0000")
        assertTrue(result.isFailure)
    }

    @Test
    fun `submitVerificationCode returns failure when user data is null`() = runTest {
        coEvery { userApi.submitVerificationCode("13800138000", "1234") } returns
                ApiResponse(code = 0, message = "ok", data = null)

        val result = repository.submitVerificationCode("13800138000", "1234")
        assertTrue(result.isFailure)
    }

    @Test
    fun `getMyProfile returns user on success`() = runTest {
        val user = User(
            id = "u1", phone = "13800138000", registerChannel = "android",
            createTime = null, updateTime = null, token = null
        )
        coEvery { userApi.getMyProfile() } returns
                ApiResponse(code = 0, message = "ok", data = user)

        val result = repository.getMyProfile()
        assertTrue(result.isSuccess)
        assertEquals("u1", result.getOrNull()?.id)
    }

    @Test
    fun `getMyProfile returns failure on error`() = runTest {
        coEvery { userApi.getMyProfile() } returns
                ApiResponse(code = 401, message = "unauthorized", data = null)

        val result = repository.getMyProfile()
        assertTrue(result.isFailure)
        assertEquals("unauthorized", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getChannel returns channel info on success`() = runTest {
        val channel = ChannelInfo(
            userId = "u1", nickname = "TestUser", avatarUrl = null,
            bannerUrl = null, bio = "Hello", subscriberCount = 100,
            videoCount = 10, isSubscribed = false
        )
        coEvery { userApi.getChannel("u1") } returns
                ApiResponse(code = 0, message = "ok", data = channel)

        val result = repository.getChannel("u1")
        assertTrue(result.isSuccess)
        assertEquals("TestUser", result.getOrNull()?.nickname)
        assertEquals(100L, result.getOrNull()?.subscriberCount)
    }

    @Test
    fun `subscribe returns success`() = runTest {
        coEvery { userApi.subscribe("u2") } returns
                ApiResponse(code = 0, message = "ok", data = null)

        val result = repository.subscribe("u2")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `unsubscribe returns success`() = runTest {
        coEvery { userApi.unsubscribe("u2") } returns
                ApiResponse(code = 0, message = "ok", data = null)

        val result = repository.unsubscribe("u2")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `getSubscriptionStatus returns true when subscribed`() = runTest {
        coEvery { userApi.getSubscriptionStatus("u2") } returns
                ApiResponse(code = 0, message = "ok", data = true)

        val result = repository.getSubscriptionStatus("u2")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `isLoggedIn delegates to TokenManager`() {
        every { tokenManager.isLoggedIn() } returns true
        assertTrue(repository.isLoggedIn())

        every { tokenManager.isLoggedIn() } returns false
        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun `logout clears token`() = runTest {
        repository.logout()
        coVerify { tokenManager.clearToken() }
    }

    @Test
    fun `updateProfile returns success`() = runTest {
        val request = UpdateProfileRequest(nickname = "New Name", bio = "New bio")
        coEvery { userApi.updateProfile(request) } returns
                ApiResponse(code = 0, message = "ok", data = null)

        val result = repository.updateProfile(request)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `initClientAndSession requests clientId when not set`() = runTest {
        every { tokenManager.getClientIdSync() } returns null
        coEvery { userApi.requestClientId() } returns
                ApiResponse(code = 0, message = "ok", data = mapOf("clientId" to "c1"))
        coEvery { userApi.requestSessionId() } returns
                ApiResponse(code = 0, message = "ok", data = mapOf("sessionId" to "s1"))

        repository.initClientAndSession()

        coVerify { tokenManager.setClientId("c1") }
        coVerify { tokenManager.setSessionId("s1") }
    }

    @Test
    fun `initClientAndSession skips clientId when already set`() = runTest {
        every { tokenManager.getClientIdSync() } returns "existing"
        coEvery { userApi.requestSessionId() } returns
                ApiResponse(code = 0, message = "ok", data = mapOf("sessionId" to "s1"))

        repository.initClientAndSession()

        coVerify(exactly = 0) { userApi.requestClientId() }
        coVerify { tokenManager.setSessionId("s1") }
    }
}
