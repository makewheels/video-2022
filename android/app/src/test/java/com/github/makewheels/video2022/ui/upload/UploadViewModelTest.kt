package com.github.makewheels.video2022.ui.upload

import android.net.Uri
import com.github.makewheels.video2022.data.model.CreateVideoResponse
import com.github.makewheels.video2022.data.model.UploadCredentials
import com.github.makewheels.video2022.data.repository.UploadRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadViewModelTest {

    private lateinit var uploadRepository: UploadRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        uploadRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): UploadViewModel {
        return UploadViewModel(uploadRepository)
    }

    @Test
    fun `setSelectedVideo — sets file info and derives title from filename`() = runTest {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://media/video.mp4"

        vm.setSelectedVideo(uri, "my_video.mp4", 1024L)

        val state = vm.uiState.value
        assertEquals(uri, state.selectedUri)
        assertEquals("my_video.mp4", state.fileName)
        assertEquals(1024L, state.fileSize)
        assertEquals("my_video", state.title)
    }

    @Test
    fun `updateTitle and updateDescription — updates state fields`() = runTest {
        val vm = createViewModel()

        vm.updateTitle("New Title")
        assertEquals("New Title", vm.uiState.value.title)

        vm.updateDescription("A description")
        assertEquals("A description", vm.uiState.value.description)
    }

    @Test
    fun `clearSelection — resets state to defaults`() = runTest {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://media/video.mp4"

        vm.setSelectedVideo(uri, "video.mp4", 2048L)
        vm.updateTitle("Custom Title")
        vm.clearSelection()

        val state = vm.uiState.value
        assertNull(state.selectedUri)
        assertEquals("", state.fileName)
        assertEquals("", state.title)
        assertEquals(0L, state.fileSize)
    }

    @Test
    fun `startUpload — success — calls onEnqueueWorker with credentials`() = runTest {
        val createResp = CreateVideoResponse(
            watchId = "w1", shortUrl = "https://short.url",
            videoId = "vid1", watchUrl = "https://watch.url", fileId = "f1"
        )
        val creds = UploadCredentials(
            bucket = "bucket", accessKeyId = "ak", endpoint = "ep",
            secretKey = "sk", provider = "OSS", sessionToken = "st",
            expiration = "2099-01-01", key = "key"
        )
        coEvery { uploadRepository.createVideo("video.mp4", 2048L) } returns Result.success(createResp)
        coEvery { uploadRepository.getUploadCredentials("f1") } returns Result.success(creds)

        val vm = createViewModel()
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://media/video.mp4"
        vm.setSelectedVideo(uri, "video.mp4", 2048L)

        var workerEnqueued = false
        vm.startUpload { fileUri, fileId, videoId, bucket, endpoint, key, akId, sk, token ->
            workerEnqueued = true
            assertEquals("content://media/video.mp4", fileUri)
            assertEquals("f1", fileId)
            assertEquals("vid1", videoId)
            assertEquals("bucket", bucket)
        }

        assertTrue(workerEnqueued)
    }

    @Test
    fun `startUpload — createVideo fails — sets error message`() = runTest {
        coEvery { uploadRepository.createVideo(any(), any()) } returns
                Result.failure(Exception("create failed"))

        val vm = createViewModel()
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://media/video.mp4"
        vm.setSelectedVideo(uri, "video.mp4", 2048L)

        vm.startUpload { _, _, _, _, _, _, _, _, _ -> }

        val state = vm.uiState.value
        assertFalse(state.isUploading)
        assertEquals("create failed", state.errorMessage)
    }
}
