package com.github.makewheels.video2022.ui.settings

import com.github.makewheels.video2022.util.TokenManager
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
class SettingsViewModelTest {

    private lateinit var tokenManager: TokenManager
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        tokenManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(tokenManager)
    }

    @Test
    fun `init — loads user phone from token manager`() = runTest {
        coEvery { tokenManager.getUserPhone() } returns "13800138000"

        val vm = createViewModel()

        assertEquals("13800138000", vm.uiState.value.userPhone)
    }

    @Test
    fun `init — no phone stored — userPhone is null`() = runTest {
        coEvery { tokenManager.getUserPhone() } returns null

        val vm = createViewModel()

        assertNull(vm.uiState.value.userPhone)
    }

    @Test
    fun `logout — clears token and calls onDone callback`() = runTest {
        coEvery { tokenManager.getUserPhone() } returns "13800138000"
        coEvery { tokenManager.clearToken() } returns Unit

        val vm = createViewModel()
        var callbackInvoked = false
        vm.logout { callbackInvoked = true }

        assertTrue(callbackInvoked)
        coVerify { tokenManager.clearToken() }
    }

    @Test
    fun `logout — sets isLoggingOut to true during logout`() = runTest {
        coEvery { tokenManager.getUserPhone() } returns "13800138000"
        coEvery { tokenManager.clearToken() } returns Unit

        val vm = createViewModel()
        vm.logout { }

        assertTrue(vm.uiState.value.isLoggingOut)
    }
}
