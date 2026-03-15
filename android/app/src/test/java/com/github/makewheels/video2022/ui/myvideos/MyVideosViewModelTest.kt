package com.github.makewheels.video2022.ui.myvideos

import com.github.makewheels.video2022.data.model.VideoItem
import com.github.makewheels.video2022.data.model.VideoListResponse
import com.github.makewheels.video2022.data.repository.VideoRepository
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
class MyVideosViewModelTest {

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

    private fun createViewModel(): MyVideosViewModel {
        return MyVideosViewModel(videoRepository)
    }

    @Test
    fun `init — loads videos on creation`() = runTest {
        val videos = listOf(createVideoItem("1"), createVideoItem("2"))
        coEvery { videoRepository.getMyVideoList(0, 20, null) } returns
                Result.success(VideoListResponse(list = videos, total = 2))

        val vm = createViewModel()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.videos.size)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadVideos — failure — sets error message`() = runTest {
        coEvery { videoRepository.getMyVideoList(0, 20, null) } returns
                Result.failure(Exception("auth required"))

        val vm = createViewModel()

        assertEquals("auth required", vm.uiState.value.errorMessage)
    }

    @Test
    fun `search — filters videos by keyword`() = runTest {
        val allVideos = listOf(createVideoItem("1"), createVideoItem("2"))
        val filtered = listOf(createVideoItem("1"))
        coEvery { videoRepository.getMyVideoList(0, 20, null) } returns
                Result.success(VideoListResponse(list = allVideos, total = 2))
        coEvery { videoRepository.getMyVideoList(0, 20, "Video 1") } returns
                Result.success(VideoListResponse(list = filtered, total = 1))

        val vm = createViewModel()
        assertEquals(2, vm.uiState.value.videos.size)

        vm.updateKeyword("Video 1")
        vm.search()

        assertEquals(1, vm.uiState.value.videos.size)
        assertEquals("Video 1", vm.uiState.value.keyword)
    }

    @Test
    fun `confirmDelete — success — removes video from list`() = runTest {
        val videos = listOf(createVideoItem("1"), createVideoItem("2"))
        coEvery { videoRepository.getMyVideoList(0, 20, null) } returns
                Result.success(VideoListResponse(list = videos, total = 2))
        coEvery { videoRepository.deleteVideo("1") } returns Result.success(Unit)

        val vm = createViewModel()
        vm.showDeleteDialog("1")
        assertTrue(vm.uiState.value.showDeleteDialog)

        vm.confirmDelete()

        val state = vm.uiState.value
        assertEquals(1, state.videos.size)
        assertEquals("2", state.videos[0].id)
        assertFalse(state.showDeleteDialog)
        assertNull(state.deleteTargetId)
    }

    @Test
    fun `dismissDeleteDialog — clears delete dialog state`() = runTest {
        coEvery { videoRepository.getMyVideoList(0, 20, null) } returns
                Result.success(VideoListResponse(list = emptyList(), total = 0))

        val vm = createViewModel()
        vm.showDeleteDialog("1")
        assertTrue(vm.uiState.value.showDeleteDialog)

        vm.dismissDeleteDialog()

        assertFalse(vm.uiState.value.showDeleteDialog)
        assertNull(vm.uiState.value.deleteTargetId)
    }
}
