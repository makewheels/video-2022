package com.github.makewheels.video2022.data.model

import org.junit.Assert.*
import org.junit.Test

class ApiResponseTest {

    @Test
    fun `success response has isSuccess true`() {
        val response = ApiResponse(code = 0, message = "success", data = "hello")
        assertTrue(response.isSuccess)
    }

    @Test
    fun `error response has isSuccess false`() {
        val response = ApiResponse(code = 1, message = "error", data = null)
        assertFalse(response.isSuccess)
    }

    @Test
    fun `negative error code is not success`() {
        val response = ApiResponse(code = -1, message = "server error", data = null)
        assertFalse(response.isSuccess)
    }

    @Test
    fun `response data is accessible`() {
        val videoList = VideoListResponse(
            list = listOf(
                VideoItem(
                    id = "v1", watchId = "w1", title = "Test Video",
                    description = "desc", status = "READY", visibility = "PUBLIC",
                    watchCount = 100, duration = 120L, createTime = null,
                    createTimeString = null, watchUrl = null, shortUrl = null,
                    type = "UPLOAD", coverUrl = null, youtubePublishTimeString = null
                )
            ),
            total = 1
        )
        val response = ApiResponse(code = 0, message = "ok", data = videoList)
        assertTrue(response.isSuccess)
        assertEquals(1, response.data?.list?.size)
        assertEquals("v1", response.data?.list?.first()?.id)
        assertEquals(1L, response.data?.total)
    }

    @Test
    fun `null data on error response`() {
        val response = ApiResponse<String>(code = 500, message = "internal error", data = null)
        assertFalse(response.isSuccess)
        assertNull(response.data)
        assertEquals("internal error", response.message)
    }

    @Test
    fun `null message is allowed`() {
        val response = ApiResponse(code = 0, message = null, data = 42)
        assertTrue(response.isSuccess)
        assertNull(response.message)
        assertEquals(42, response.data)
    }

    @Test
    fun `generic type works with User`() {
        val user = User(
            id = "u1", phone = "13800138000",
            registerChannel = "android", createTime = null,
            updateTime = null, token = "tok123"
        )
        val response = ApiResponse(code = 0, message = "ok", data = user)
        assertTrue(response.isSuccess)
        assertEquals("u1", response.data?.id)
        assertEquals("tok123", response.data?.token)
    }
}
