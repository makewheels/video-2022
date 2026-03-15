package com.github.makewheels.video2022.ui.update

import com.github.makewheels.video2022.data.api.AppApi
import com.github.makewheels.video2022.data.model.ApiResponse
import com.github.makewheels.video2022.data.model.CheckUpdateResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    private lateinit var appApi: AppApi
    private lateinit var apkDownloader: ApkDownloader
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        appApi = mockk()
        apkDownloader = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): UpdateViewModel {
        return UpdateViewModel(appApi, apkDownloader)
    }

    private fun noUpdateResponse() = ApiResponse(
        code = 0,
        message = "ok",
        data = CheckUpdateResponse(
            hasUpdate = false,
            versionCode = null,
            versionName = null,
            versionInfo = null,
            downloadUrl = null,
            isForceUpdate = null
        )
    )

    private fun updateResponse(force: Boolean = false) = ApiResponse(
        code = 0,
        message = "ok",
        data = CheckUpdateResponse(
            hasUpdate = true,
            versionCode = 2,
            versionName = "2.0.0",
            versionInfo = "Bug fixes and improvements",
            downloadUrl = "https://example.com/app.apk",
            isForceUpdate = force
        )
    )

    @Test
    fun `checkForUpdate — no update available — dialog not shown`() = runTest {
        coEvery { appApi.checkUpdate(any(), any()) } returns noUpdateResponse()

        val vm = createViewModel()
        vm.checkForUpdate()

        val state = vm.updateUiState.value
        assertFalse(state.showDialog)
        assertNull(state.versionName)
        assertNull(state.downloadUrl)
    }

    @Test
    fun `checkForUpdate — update available, not force — shows dialog with dismiss option`() = runTest {
        coEvery { appApi.checkUpdate(any(), any()) } returns updateResponse(force = false)

        val vm = createViewModel()
        vm.checkForUpdate()

        val state = vm.updateUiState.value
        assertTrue(state.showDialog)
        assertFalse(state.isForceUpdate)
        assertEquals("2.0.0", state.versionName)
        assertEquals("Bug fixes and improvements", state.versionInfo)
        assertEquals("https://example.com/app.apk", state.downloadUrl)
    }

    @Test
    fun `checkForUpdate — force update — shows dialog without dismiss`() = runTest {
        coEvery { appApi.checkUpdate(any(), any()) } returns updateResponse(force = true)

        val vm = createViewModel()
        vm.checkForUpdate()

        val state = vm.updateUiState.value
        assertTrue(state.showDialog)
        assertTrue(state.isForceUpdate)
    }

    @Test
    fun `checkForUpdate — API error — silently ignored`() = runTest {
        coEvery { appApi.checkUpdate(any(), any()) } throws RuntimeException("network error")

        val vm = createViewModel()
        vm.checkForUpdate()

        val state = vm.updateUiState.value
        assertFalse(state.showDialog)
        assertEquals(UpdateUiState(), state)
    }

    @Test
    fun `checkForUpdate — API returns error code — dialog not shown`() = runTest {
        coEvery { appApi.checkUpdate(any(), any()) } returns ApiResponse(
            code = 500,
            message = "server error",
            data = null
        )

        val vm = createViewModel()
        vm.checkForUpdate()

        val state = vm.updateUiState.value
        assertFalse(state.showDialog)
    }

    @Test
    fun `dismissDialog — sets showDialog false`() = runTest {
        coEvery { appApi.checkUpdate(any(), any()) } returns updateResponse(force = false)

        val vm = createViewModel()
        vm.checkForUpdate()
        assertTrue(vm.updateUiState.value.showDialog)

        vm.dismissDialog()

        assertFalse(vm.updateUiState.value.showDialog)
    }

    @Test
    fun `checkForUpdate — version info fields set correctly from response`() = runTest {
        val response = ApiResponse(
            code = 0,
            message = "ok",
            data = CheckUpdateResponse(
                hasUpdate = true,
                versionCode = 10,
                versionName = "3.1.0",
                versionInfo = "New feature: dark mode\nPerformance improvements",
                downloadUrl = "https://cdn.example.com/v3.1.0/app.apk",
                isForceUpdate = false
            )
        )
        coEvery { appApi.checkUpdate(any(), any()) } returns response

        val vm = createViewModel()
        vm.checkForUpdate()

        val state = vm.updateUiState.value
        assertEquals("3.1.0", state.versionName)
        assertEquals("New feature: dark mode\nPerformance improvements", state.versionInfo)
        assertEquals("https://cdn.example.com/v3.1.0/app.apk", state.downloadUrl)
        assertFalse(state.isForceUpdate)
        assertFalse(state.isDownloading)
        assertEquals(0f, state.downloadProgress)
        assertNull(state.downloadedFile)
    }
}
