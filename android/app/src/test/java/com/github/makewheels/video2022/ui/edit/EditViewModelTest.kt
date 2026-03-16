package com.github.makewheels.video2022.ui.edit

import androidx.lifecycle.SavedStateHandle
import com.github.makewheels.video2022.data.model.UpdateVideoInfoRequest
import com.github.makewheels.video2022.data.model.VideoItem
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
class EditViewModelTest {

    private lateinit var videoRepository: VideoRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun createSavedStateHandle(videoId: String = "v1") = SavedStateHandle(mapOf("videoId" to videoId))

    private fun createVideoItem(
        id: String = "v1",
        title: String = "Test Video",
        description: String = "desc",
        visibility: String = "PUBLIC"
    ) = VideoItem(
        id = id, watchId = "w1", title = title,
        description = description, status = "READY", visibility = visibility,
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

    @Test
    fun `loadVideo populates state on success`() = runTest {
        val video = createVideoItem(title = "My Video", description = "My desc", visibility = "PRIVATE")
        coEvery { videoRepository.getVideoDetail("v1") } returns Result.success(video)

        val vm = EditViewModel(createSavedStateHandle(), videoRepository)

        val state = vm.uiState.value
        assertEquals("v1", state.videoId)
        assertEquals("My Video", state.title)
        assertEquals("My desc", state.description)
        assertEquals("PRIVATE", state.visibility)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadVideo sets error on failure`() = runTest {
        coEvery { videoRepository.getVideoDetail("v1") } returns
                Result.failure(Exception("not found"))

        val vm = EditViewModel(createSavedStateHandle(), videoRepository)

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("not found", state.errorMessage)
    }

    @Test
    fun `updateTitle updates state and clears isSaved`() = runTest {
        val video = createVideoItem()
        coEvery { videoRepository.getVideoDetail("v1") } returns Result.success(video)

        val vm = EditViewModel(createSavedStateHandle(), videoRepository)
        vm.updateTitle("New Title")

        assertEquals("New Title", vm.uiState.value.title)
        assertFalse(vm.uiState.value.isSaved)
    }

    @Test
    fun `updateDescription updates state and clears isSaved`() = runTest {
        val video = createVideoItem()
        coEvery { videoRepository.getVideoDetail("v1") } returns Result.success(video)

        val vm = EditViewModel(createSavedStateHandle(), videoRepository)
        vm.updateDescription("New desc")

        assertEquals("New desc", vm.uiState.value.description)
        assertFalse(vm.uiState.value.isSaved)
    }

    @Test
    fun `updateVisibility updates state`() = runTest {
        val video = createVideoItem()
        coEvery { videoRepository.getVideoDetail("v1") } returns Result.success(video)

        val vm = EditViewModel(createSavedStateHandle(), videoRepository)
        vm.updateVisibility("PRIVATE")

        assertEquals("PRIVATE", vm.uiState.value.visibility)
    }

    @Test
    fun `save calls repository and sets isSaved on success`() = runTest {
        val video = createVideoItem()
        val updatedVideo = createVideoItem(title = "Test Video")
        coEvery { videoRepository.getVideoDetail("v1") } returns Result.success(video)
        coEvery { videoRepository.updateVideoInfo(any()) } returns Result.success(updatedVideo)

        val vm = EditViewModel(createSavedStateHandle(), videoRepository)
        vm.save()

        val state = vm.uiState.value
        assertTrue(state.isSaved)
        assertFalse(state.isSaving)
        assertNull(state.errorMessage)
    }

    @Test
    fun `save sets error on failure`() = runTest {
        val video = createVideoItem()
        coEvery { videoRepository.getVideoDetail("v1") } returns Result.success(video)
        coEvery { videoRepository.updateVideoInfo(any()) } returns
                Result.failure(Exception("save failed"))

        val vm = EditViewModel(createSavedStateHandle(), videoRepository)
        vm.save()

        val state = vm.uiState.value
        assertFalse(state.isSaved)
        assertFalse(state.isSaving)
        assertEquals("save failed", state.errorMessage)
    }

    @Test
    fun `save sends correct request to repository`() = runTest {
        val video = createVideoItem()
        val expectedRequest = UpdateVideoInfoRequest(
            id = "v1", title = "Test Video", description = "desc", visibility = "PUBLIC",
            tags = emptyList(), category = ""
        )
        coEvery { videoRepository.getVideoDetail("v1") } returns Result.success(video)
        coEvery { videoRepository.updateVideoInfo(expectedRequest) } returns
                Result.success(video)

        val vm = EditViewModel(createSavedStateHandle(), videoRepository)
        vm.save()

        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun `delete calls onDeleted callback on success`() = runTest {
        val video = createVideoItem()
        coEvery { videoRepository.getVideoDetail("v1") } returns Result.success(video)
        coEvery { videoRepository.deleteVideo("v1") } returns Result.success(Unit)

        val vm = EditViewModel(createSavedStateHandle(), videoRepository)
        var deletedCalled = false
        vm.delete { deletedCalled = true }

        assertTrue(deletedCalled)
    }

    @Test
    fun `delete sets error on failure`() = runTest {
        val video = createVideoItem()
        coEvery { videoRepository.getVideoDetail("v1") } returns Result.success(video)
        coEvery { videoRepository.deleteVideo("v1") } returns
                Result.failure(Exception("delete failed"))

        val vm = EditViewModel(createSavedStateHandle(), videoRepository)
        var deletedCalled = false
        vm.delete { deletedCalled = true }

        assertFalse(deletedCalled)
        assertEquals("delete failed", vm.uiState.value.errorMessage)
    }

    @Test
    fun `empty videoId from SavedStateHandle defaults to empty string`() = runTest {
        coEvery { videoRepository.getVideoDetail("") } returns
                Result.failure(Exception("invalid id"))

        val vm = EditViewModel(SavedStateHandle(), videoRepository)

        assertEquals("", vm.uiState.value.videoId)
    }
}
