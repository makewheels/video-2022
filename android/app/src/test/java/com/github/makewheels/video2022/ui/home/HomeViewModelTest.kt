package com.github.makewheels.video2022.ui.home

import com.github.makewheels.video2022.data.model.VideoItem
import com.github.makewheels.video2022.data.model.VideoListResponse
import com.github.makewheels.video2022.data.repository.VideoRepository
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
class HomeViewModelTest {

    private lateinit var videoRepository: VideoRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun createVideoItem(id: String) = VideoItem(
        id = id, watchId = "w$id", title = "Video $id",
        description = null, status = "READY", visibility = "PUBLIC",
        watchCount = 0, duration = 60L, createTime = null,
        createTimeString = null, watchUrl = null, shortUrl = null,
        type = "UPLOAD", coverUrl = null, youtubePublishTimeString = null
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        videoRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(videoRepository)
    }

    @Test
    fun `loadVideos updates state with video list on success`() = runTest {
        val videos = listOf(createVideoItem("1"), createVideoItem("2"))
        coEvery { videoRepository.getPublicVideoList(0, 20, null) } returns
                Result.success(VideoListResponse(list = videos, total = 2))

        val vm = createViewModel()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.videos.size)
        assertEquals("Video 1", state.videos[0].title)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadVideos sets error message on failure`() = runTest {
        coEvery { videoRepository.getPublicVideoList(0, 20, null) } returns
                Result.failure(Exception("network error"))

        val vm = createViewModel()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("network error", state.errorMessage)
    }

    @Test
    fun `loadVideos sets hasMore false when less than page size`() = runTest {
        val videos = listOf(createVideoItem("1"))
        coEvery { videoRepository.getPublicVideoList(0, 20, null) } returns
                Result.success(VideoListResponse(list = videos, total = 1))

        val vm = createViewModel()

        assertFalse(vm.uiState.value.hasMore)
    }

    @Test
    fun `loadVideos sets hasMore true when exactly page size`() = runTest {
        val videos = (1..20).map { createVideoItem("$it") }
        coEvery { videoRepository.getPublicVideoList(0, 20, null) } returns
                Result.success(VideoListResponse(list = videos, total = 100))

        val vm = createViewModel()

        assertTrue(vm.uiState.value.hasMore)
    }

    @Test
    fun `refresh reloads videos from beginning`() = runTest {
        val initialVideos = listOf(createVideoItem("1"))
        val refreshedVideos = listOf(createVideoItem("1"), createVideoItem("2"))

        coEvery { videoRepository.getPublicVideoList(0, 20, null) } returns
                Result.success(VideoListResponse(list = initialVideos, total = 1)) andThen
                Result.success(VideoListResponse(list = refreshedVideos, total = 2))

        val vm = createViewModel()
        assertEquals(1, vm.uiState.value.videos.size)

        vm.refresh()

        val state = vm.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals(2, state.videos.size)
    }

    @Test
    fun `refresh sets error on failure`() = runTest {
        coEvery { videoRepository.getPublicVideoList(0, 20, null) } returns
                Result.success(VideoListResponse(list = emptyList(), total = 0)) andThen
                Result.failure(Exception("refresh failed"))

        val vm = createViewModel()
        vm.refresh()

        val state = vm.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals("refresh failed", state.errorMessage)
    }

    @Test
    fun `loadMore appends videos to existing list`() = runTest {
        val page1 = (1..20).map { createVideoItem("$it") }
        val page2 = listOf(createVideoItem("21"), createVideoItem("22"))

        coEvery { videoRepository.getPublicVideoList(0, 20, null) } returns
                Result.success(VideoListResponse(list = page1, total = 22))
        coEvery { videoRepository.getPublicVideoList(20, 20, null) } returns
                Result.success(VideoListResponse(list = page2, total = 22))

        val vm = createViewModel()
        assertEquals(20, vm.uiState.value.videos.size)

        vm.loadMore()

        assertEquals(22, vm.uiState.value.videos.size)
        assertFalse(vm.uiState.value.hasMore) // page2 has less than 20
    }

    @Test
    fun `loadMore does nothing when hasMore is false`() = runTest {
        val videos = listOf(createVideoItem("1"))
        coEvery { videoRepository.getPublicVideoList(0, 20, null) } returns
                Result.success(VideoListResponse(list = videos, total = 1))

        val vm = createViewModel()
        assertFalse(vm.uiState.value.hasMore)

        vm.loadMore()
        // Should not call API again; list stays same
        assertEquals(1, vm.uiState.value.videos.size)
    }
}
