package com.github.makewheels.video2022.ui.watchhistory

import com.github.makewheels.video2022.data.model.WatchHistoryItem
import com.github.makewheels.video2022.data.model.WatchHistoryResponse
import com.github.makewheels.video2022.data.repository.WatchHistoryRepository
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
class WatchHistoryViewModelTest {

    private lateinit var repository: WatchHistoryRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun createHistoryItem(videoId: String) = WatchHistoryItem(
        videoId = videoId,
        title = "Video $videoId",
        coverUrl = "https://example.com/cover.jpg",
        watchTime = "2026-03-17 10:00:00"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WatchHistoryViewModel {
        return WatchHistoryViewModel(repository)
    }

    @Test
    fun `init - loads history on creation`() = runTest {
        val items = listOf(createHistoryItem("v1"), createHistoryItem("v2"))
        coEvery { repository.getWatchHistory(0, 20) } returns
                Result.success(WatchHistoryResponse(list = items, total = 2, page = 0, pageSize = 20))

        val vm = createViewModel()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.videos.size)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadMore - appends items to list`() = runTest {
        val page1 = listOf(createHistoryItem("v1"))
        val page2 = listOf(createHistoryItem("v2"))
        coEvery { repository.getWatchHistory(0, 20) } returns
                Result.success(WatchHistoryResponse(list = page1, total = 2, page = 0, pageSize = 20))
        coEvery { repository.getWatchHistory(1, 20) } returns
                Result.success(WatchHistoryResponse(list = page2, total = 2, page = 1, pageSize = 20))

        val vm = createViewModel()
        assertEquals(1, vm.uiState.value.videos.size)

        vm.loadMore()

        assertEquals(2, vm.uiState.value.videos.size)
    }

    @Test
    fun `clearHistory - clears list on success`() = runTest {
        val items = listOf(createHistoryItem("v1"))
        coEvery { repository.getWatchHistory(0, 20) } returns
                Result.success(WatchHistoryResponse(list = items, total = 1, page = 0, pageSize = 20))
        coEvery { repository.clearWatchHistory() } returns Result.success(Unit)

        val vm = createViewModel()
        assertEquals(1, vm.uiState.value.videos.size)

        vm.clearHistory()

        assertEquals(0, vm.uiState.value.videos.size)
        assertFalse(vm.uiState.value.showClearDialog)
    }

    @Test
    fun `showClearDialog and dismissClearDialog - toggles dialog state`() = runTest {
        coEvery { repository.getWatchHistory(0, 20) } returns
                Result.success(WatchHistoryResponse(list = emptyList(), total = 0, page = 0, pageSize = 20))

        val vm = createViewModel()
        assertFalse(vm.uiState.value.showClearDialog)

        vm.showClearDialog()
        assertTrue(vm.uiState.value.showClearDialog)

        vm.dismissClearDialog()
        assertFalse(vm.uiState.value.showClearDialog)
    }
}