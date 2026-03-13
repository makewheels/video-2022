package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.LikeApi
import com.github.makewheels.video2022.data.api.VideoApi
import com.github.makewheels.video2022.data.model.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VideoRepositoryTest {

    private lateinit var videoApi: VideoApi
    private lateinit var likeApi: LikeApi
    private lateinit var repository: VideoRepository

    @Before
    fun setup() {
        videoApi = mockk()
        likeApi = mockk()
        repository = VideoRepository(videoApi, likeApi)
    }

    private fun createVideoItem(id: String = "v1", title: String = "Test") = VideoItem(
        id = id, watchId = "w1", title = title,
        description = "desc", status = "READY", visibility = "PUBLIC",
        watchCount = 10, duration = 60L, createTime = null,
        createTimeString = null, watchUrl = null, shortUrl = null,
        type = "UPLOAD", coverUrl = null, youtubePublishTimeString = null
    )

    @Test
    fun `getMyVideoList returns video list on success`() = runTest {
        val videos = listOf(createVideoItem("v1"), createVideoItem("v2"))
        val listResponse = VideoListResponse(list = videos, total = 2)
        coEvery { videoApi.getMyVideoList(0, 20, null) } returns
                ApiResponse(code = 0, message = "ok", data = listResponse)

        val result = repository.getMyVideoList(0, 20)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.list?.size)
        assertEquals(2L, result.getOrNull()?.total)
    }

    @Test
    fun `getMyVideoList returns failure when API returns error`() = runTest {
        coEvery { videoApi.getMyVideoList(0, 20, null) } returns
                ApiResponse(code = 1, message = "unauthorized", data = null)

        val result = repository.getMyVideoList(0, 20)
        assertTrue(result.isFailure)
        assertEquals("unauthorized", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getMyVideoList handles exception from API`() = runTest {
        coEvery { videoApi.getMyVideoList(0, 20, null) } throws RuntimeException("network error")

        val result = repository.getMyVideoList(0, 20)
        assertTrue(result.isFailure)
        assertEquals("network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getMyVideoList returns failure when data is null`() = runTest {
        coEvery { videoApi.getMyVideoList(0, 20, null) } returns
                ApiResponse(code = 0, message = "ok", data = null)

        val result = repository.getMyVideoList(0, 20)
        assertTrue(result.isFailure)
    }

    @Test
    fun `getPublicVideoList returns video list on success`() = runTest {
        val videos = listOf(createVideoItem("v1"))
        val listResponse = VideoListResponse(list = videos, total = 1)
        coEvery { videoApi.getPublicVideoList(0, 10, null) } returns
                ApiResponse(code = 0, message = "ok", data = listResponse)

        val result = repository.getPublicVideoList(0, 10)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.list?.size)
    }

    @Test
    fun `getPublicVideoList with keyword passes keyword to API`() = runTest {
        val listResponse = VideoListResponse(list = emptyList(), total = 0)
        coEvery { videoApi.getPublicVideoList(0, 20, "kotlin") } returns
                ApiResponse(code = 0, message = "ok", data = listResponse)

        val result = repository.getPublicVideoList(0, 20, "kotlin")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.list?.size)
    }

    @Test
    fun `getVideoDetail returns video on success`() = runTest {
        val video = createVideoItem("v1", "My Video")
        coEvery { videoApi.getVideoDetail("v1") } returns
                ApiResponse(code = 0, message = "ok", data = video)

        val result = repository.getVideoDetail("v1")
        assertTrue(result.isSuccess)
        assertEquals("My Video", result.getOrNull()?.title)
    }

    @Test
    fun `getVideoDetail returns failure on error`() = runTest {
        coEvery { videoApi.getVideoDetail("v1") } returns
                ApiResponse(code = 404, message = "not found", data = null)

        val result = repository.getVideoDetail("v1")
        assertTrue(result.isFailure)
        assertEquals("not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `deleteVideo returns success`() = runTest {
        coEvery { videoApi.deleteVideo("v1") } returns
                ApiResponse(code = 0, message = "ok", data = null)

        val result = repository.deleteVideo("v1")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `deleteVideo returns failure on error`() = runTest {
        coEvery { videoApi.deleteVideo("v1") } returns
                ApiResponse(code = 403, message = "forbidden", data = null)

        val result = repository.deleteVideo("v1")
        assertTrue(result.isFailure)
        assertEquals("forbidden", result.exceptionOrNull()?.message)
    }

    @Test
    fun `updateVideoInfo returns updated video on success`() = runTest {
        val updated = createVideoItem("v1", "Updated Title")
        val request = UpdateVideoInfoRequest(id = "v1", title = "Updated Title")
        coEvery { videoApi.updateVideoInfo(request) } returns
                ApiResponse(code = 0, message = "ok", data = updated)

        val result = repository.updateVideoInfo(request)
        assertTrue(result.isSuccess)
        assertEquals("Updated Title", result.getOrNull()?.title)
    }

    @Test
    fun `getVideoStatus returns status on success`() = runTest {
        val status = VideoStatus(videoId = "v1", status = "READY", isReady = true)
        coEvery { videoApi.getVideoStatus("v1") } returns
                ApiResponse(code = 0, message = "ok", data = status)

        val result = repository.getVideoStatus("v1")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isReady == true)
    }

    @Test
    fun `getLikeStatus returns like status on success`() = runTest {
        val likeStatus = LikeStatus(likeCount = 5, dislikeCount = 1, userAction = "LIKE")
        coEvery { likeApi.getStatus("v1") } returns
                ApiResponse(code = 0, message = "ok", data = likeStatus)

        val result = repository.getLikeStatus("v1")
        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrNull()?.likeCount)
        assertEquals("LIKE", result.getOrNull()?.userAction)
    }

    @Test
    fun `like returns success`() = runTest {
        coEvery { likeApi.like("v1") } returns
                ApiResponse(code = 0, message = "ok", data = null)

        val result = repository.like("v1")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `dislike returns success`() = runTest {
        coEvery { likeApi.dislike("v1") } returns
                ApiResponse(code = 0, message = "ok", data = null)

        val result = repository.dislike("v1")
        assertTrue(result.isSuccess)
    }
}
