package com.github.makewheels.video2022.ui.login

import com.github.makewheels.video2022.data.model.User
import com.github.makewheels.video2022.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private lateinit var userRepository: UserRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty phone and code`() {
        val vm = LoginViewModel(userRepository)
        val state = vm.uiState.value
        assertEquals("", state.phone)
        assertEquals("", state.code)
        assertFalse(state.isCodeSent)
        assertFalse(state.isLoading)
        assertFalse(state.isLoginSuccess)
        assertNull(state.errorMessage)
    }

    @Test
    fun `updatePhone updates phone in state`() {
        val vm = LoginViewModel(userRepository)
        vm.updatePhone("13800138000")
        assertEquals("13800138000", vm.uiState.value.phone)
    }

    @Test
    fun `updateCode updates code in state`() {
        val vm = LoginViewModel(userRepository)
        vm.updateCode("1234")
        assertEquals("1234", vm.uiState.value.code)
    }

    @Test
    fun `updatePhone clears error message`() {
        val vm = LoginViewModel(userRepository)
        // Trigger an error first by requesting code with short phone
        vm.requestVerificationCode()
        assertNotNull(vm.uiState.value.errorMessage)

        vm.updatePhone("138")
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `requestVerificationCode sets error when phone is not 11 digits`() {
        val vm = LoginViewModel(userRepository)
        vm.updatePhone("1234567")
        vm.requestVerificationCode()

        assertEquals("请输入11位手机号", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isCodeSent)
    }

    @Test
    fun `requestVerificationCode sets isCodeSent on success`() = runTest {
        coEvery { userRepository.requestVerificationCode("13800138000") } returns Result.success(Unit)

        val vm = LoginViewModel(userRepository)
        vm.updatePhone("13800138000")
        vm.requestVerificationCode()

        assertTrue(vm.uiState.value.isCodeSent)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `requestVerificationCode sets error on failure`() = runTest {
        coEvery { userRepository.requestVerificationCode("13800138000") } returns
                Result.failure(Exception("rate limited"))

        val vm = LoginViewModel(userRepository)
        vm.updatePhone("13800138000")
        vm.requestVerificationCode()

        assertFalse(vm.uiState.value.isCodeSent)
        assertEquals("rate limited", vm.uiState.value.errorMessage)
    }

    @Test
    fun `submitVerificationCode sets error when code is blank`() {
        val vm = LoginViewModel(userRepository)
        vm.updatePhone("13800138000")
        vm.updateCode("  ")
        vm.submitVerificationCode()

        assertEquals("请输入验证码", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoginSuccess)
    }

    @Test
    fun `submitVerificationCode sets isLoginSuccess on success`() = runTest {
        val user = User(
            id = "u1", phone = "13800138000", registerChannel = "android",
            createTime = null, updateTime = null, token = "tok123"
        )
        coEvery { userRepository.submitVerificationCode("13800138000", "1234") } returns
                Result.success(user)
        coEvery { userRepository.initClientAndSession() } returns Unit

        val vm = LoginViewModel(userRepository)
        vm.updatePhone("13800138000")
        vm.updateCode("1234")
        vm.submitVerificationCode()

        assertTrue(vm.uiState.value.isLoginSuccess)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `submitVerificationCode calls initClientAndSession on success`() = runTest {
        val user = User(
            id = "u1", phone = "13800138000", registerChannel = "android",
            createTime = null, updateTime = null, token = "tok123"
        )
        coEvery { userRepository.submitVerificationCode("13800138000", "1234") } returns
                Result.success(user)
        coEvery { userRepository.initClientAndSession() } returns Unit

        val vm = LoginViewModel(userRepository)
        vm.updatePhone("13800138000")
        vm.updateCode("1234")
        vm.submitVerificationCode()

        coVerify { userRepository.initClientAndSession() }
    }

    @Test
    fun `submitVerificationCode sets error on failure`() = runTest {
        coEvery { userRepository.submitVerificationCode("13800138000", "0000") } returns
                Result.failure(Exception("wrong code"))

        val vm = LoginViewModel(userRepository)
        vm.updatePhone("13800138000")
        vm.updateCode("0000")
        vm.submitVerificationCode()

        assertFalse(vm.uiState.value.isLoginSuccess)
        assertEquals("wrong code", vm.uiState.value.errorMessage)
    }

    @Test
    fun `phone is trimmed before sending verification code`() = runTest {
        coEvery { userRepository.requestVerificationCode("13800138000") } returns Result.success(Unit)

        val vm = LoginViewModel(userRepository)
        vm.updatePhone("13800138000")
        vm.requestVerificationCode()

        coVerify { userRepository.requestVerificationCode("13800138000") }
    }
}
