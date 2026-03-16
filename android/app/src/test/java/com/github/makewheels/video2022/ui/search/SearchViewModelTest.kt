package com.github.makewheels.video2022.ui.search

import com.github.makewheels.video2022.data.model.SearchResultResponse
import com.github.makewheels.video2022.data.model.VideoItem
import com.github.makewheels.video2022.data.repository.SearchRepository
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
class SearchViewModelTest {

    private lateinit var searchRepository: SearchRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun createVideoItem(id: String, title: String = "Video $id") = VideoItem(
        id = id, watchId = "w$id", title = title,
        description = null, status = "READY", visibility = "PUBLIC",
        watchCount = 0, duration = 60L, createTime = null,
        createTimeString = null, watchUrl = null, shortUrl = null,
        type = "UPLOAD", coverUrl = null, youtubePublishTimeString = null
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        searchRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search updates state with results on success`() = runTest {
        val videos = listOf(createVideoItem("1", "测试视频"), createVideoItem("2", "另一个视频"))
        coEvery { searchRepository.search("测试", null, 0, 20) } returns
                Result.success(SearchResultResponse(content = videos, total = 2, totalPages = 1, currentPage = 0, pageSize = 20))

        val vm = SearchViewModel(searchRepository)
        vm.updateQuery("测试")
        vm.search()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasSearched)
        assertEquals(2, state.videos.size)
        assertEquals(2L, state.total)
    }

    @Test
    fun `search sets error message on failure`() = runTest {
        coEvery { searchRepository.search("fail", null, 0, 20) } returns
                Result.failure(Exception("network error"))

        val vm = SearchViewModel(searchRepository)
        vm.updateQuery("fail")
        vm.search()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasSearched)
        assertEquals("network error", state.errorMessage)
    }

    @Test
    fun `selectCategory triggers search`() = runTest {
        coEvery { searchRepository.search("", "音乐", 0, 20) } returns
                Result.success(SearchResultResponse(content = emptyList(), total = 0, totalPages = 0, currentPage = 0, pageSize = 20))

        val vm = SearchViewModel(searchRepository)
        vm.selectCategory("音乐")

        assertEquals("音乐", vm.uiState.value.selectedCategory)
        assertTrue(vm.uiState.value.hasSearched)
    }

    @Test
    fun `loadMore appends videos to existing list`() = runTest {
        val page1 = (1..20).map { createVideoItem("$it") }
        val page2 = listOf(createVideoItem("21"), createVideoItem("22"))

        coEvery { searchRepository.search("test", null, 0, 20) } returns
                Result.success(SearchResultResponse(content = page1, total = 22, totalPages = 2, currentPage = 0, pageSize = 20))
        coEvery { searchRepository.search("test", null, 1, 20) } returns
                Result.success(SearchResultResponse(content = page2, total = 22, totalPages = 2, currentPage = 1, pageSize = 20))

        val vm = SearchViewModel(searchRepository)
        vm.updateQuery("test")
        vm.search()
        assertEquals(20, vm.uiState.value.videos.size)

        vm.loadMore()
        assertEquals(22, vm.uiState.value.videos.size)
        assertFalse(vm.uiState.value.hasMore)
    }

    @Test
    fun `loadMore does nothing when hasMore is false`() = runTest {
        val videos = listOf(createVideoItem("1"))
        coEvery { searchRepository.search("q", null, 0, 20) } returns
                Result.success(SearchResultResponse(content = videos, total = 1, totalPages = 1, currentPage = 0, pageSize = 20))

        val vm = SearchViewModel(searchRepository)
        vm.updateQuery("q")
        vm.search()

        assertFalse(vm.uiState.value.hasMore)
        vm.loadMore()
        assertEquals(1, vm.uiState.value.videos.size)
    }

    @Test
    fun `updateQuery updates query in state`() = runTest {
        val vm = SearchViewModel(searchRepository)
        vm.updateQuery("hello")
        assertEquals("hello", vm.uiState.value.query)
    }
}
