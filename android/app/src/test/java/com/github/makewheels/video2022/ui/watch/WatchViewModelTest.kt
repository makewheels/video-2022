package com.github.makewheels.video2022.ui.watch

import androidx.lifecycle.SavedStateHandle
import com.github.makewheels.video2022.data.model.LikeStatus
import com.github.makewheels.video2022.data.model.WatchInfo
import com.github.makewheels.video2022.data.repository.CommentRepository
import com.github.makewheels.video2022.data.repository.VideoRepository
import com.github.makewheels.video2022.data.repository.WatchRepository
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
class WatchViewModelTest {

    private lateinit var watchRepository: WatchRepository
    private lateinit var videoRepository: VideoRepository
    private lateinit var commentRepository: CommentRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testWatchInfo = WatchInfo(
        videoId = "vid1",
        coverUrl = "https://example.com/cover.jpg",
        videoStatus = "READY",
        multivariantPlaylistUrl = "https://example.com/playlist.m3u8",
        progressInMillis = 5000L
    )

    private val testLikeStatus = LikeStatus(
        likeCount = 10, dislikeCount = 2, userAction = null
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        watchRepository = mockk()
        videoRepository = mockk()
        commentRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        watchId: String = "w1",
        likeStatus: LikeStatus = testLikeStatus
    ): WatchViewModel {
        coEvery { videoRepository.getLikeStatus("vid1") } returns Result.success(likeStatus)
        coEvery { commentRepository.getCount("vid1") } returns Result.success(5)
        return WatchViewModel(
            SavedStateHandle(mapOf("watchId" to watchId)),
            watchRepository, videoRepository, commentRepository
        )
    }

    @Test
    fun `init — loads watch info on success — updates state`() = runTest {
        coEvery { watchRepository.getWatchInfo("w1") } returns Result.success(testWatchInfo)

        val vm = createViewModel()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("vid1", state.watchInfo?.videoId)
        assertNull(state.errorMessage)
    }

    @Test
    fun `init — watch info failure — sets error message`() = runTest {
        coEvery { watchRepository.getWatchInfo("w1") } returns
                Result.failure(Exception("not found"))

        val vm = createViewModel()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("not found", state.errorMessage)
    }

    @Test
    fun `init — success — loads like status and comment count`() = runTest {
        coEvery { watchRepository.getWatchInfo("w1") } returns Result.success(testWatchInfo)

        val vm = createViewModel()

        val state = vm.uiState.value
        assertEquals(10, state.likeStatus?.likeCount)
        assertEquals(5, state.commentCount)
    }

    @Test
    fun `like — no previous action — increments like count`() = runTest {
        coEvery { watchRepository.getWatchInfo("w1") } returns Result.success(testWatchInfo)
        coEvery { videoRepository.like("vid1") } returns Result.success(Unit)

        val vm = createViewModel()
        vm.like()

        val status = vm.uiState.value.likeStatus!!
        assertEquals(11, status.likeCount)
        assertEquals("LIKE", status.userAction)
    }

    @Test
    fun `like — already liked — toggles off`() = runTest {
        val likedStatus = LikeStatus(likeCount = 10, dislikeCount = 2, userAction = "LIKE")
        coEvery { watchRepository.getWatchInfo("w1") } returns Result.success(testWatchInfo)
        coEvery { videoRepository.like("vid1") } returns Result.success(Unit)

        val vm = createViewModel(likeStatus = likedStatus)
        vm.like()

        val status = vm.uiState.value.likeStatus!!
        assertEquals(9, status.likeCount)
        assertNull(status.userAction)
    }
}
