package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.WatchApi
import com.github.makewheels.video2022.data.model.ApiResponse
import com.github.makewheels.video2022.data.model.WatchHistoryItem
import com.github.makewheels.video2022.data.model.WatchHistoryResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WatchHistoryRepositoryTest {

    private lateinit var watchApi: WatchApi
    private lateinit var repository: WatchHistoryRepository

    @Before
    fun setup() {
        watchApi = mockk()
        repository = WatchHistoryRepository(watchApi)
    }

    private fun createHistoryItem(videoId: String) = WatchHistoryItem(
        videoId = videoId,
        title = "Video $videoId",
        coverUrl = "https://example.com/cover.jpg",
        watchTime = "2026-03-17 10:00:00"
    )

    @Test
    fun `getWatchHistory returns history list on success`() = runTest {
        val items = listOf(createHistoryItem("v1"), createHistoryItem("v2"))
        val response = WatchHistoryResponse(list = items, total = 2, page = 0, pageSize = 20)
        coEvery { watchApi.getWatchHistory(0, 20) } returns
                ApiResponse(code = 0, message = "ok", data = response)

        val result = repository.getWatchHistory(0, 20)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.list?.size)
        assertEquals(2L, result.getOrNull()?.total)
    }

    @Test
    fun `getWatchHistory returns failure when API returns error`() = runTest {
        coEvery { watchApi.getWatchHistory(0, 20) } returns
                ApiResponse(code = 1, message = "unauthorized", data = null)

        val result = repository.getWatchHistory(0, 20)

        assertTrue(result.isFailure)
        assertEquals("unauthorized", result.exceptionOrNull()?.message)
    }

    @Test
    fun `clearWatchHistory returns success`() = runTest {
        coEvery { watchApi.clearWatchHistory() } returns
                ApiResponse(code = 0, message = "ok", data = Unit)

        val result = repository.clearWatchHistory()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `clearWatchHistory returns failure on error`() = runTest {
        coEvery { watchApi.clearWatchHistory() } returns
                ApiResponse(code = 500, message = "server error", data = null)

        val result = repository.clearWatchHistory()

        assertTrue(result.isFailure)
        assertEquals("server error", result.exceptionOrNull()?.message)
    }
}