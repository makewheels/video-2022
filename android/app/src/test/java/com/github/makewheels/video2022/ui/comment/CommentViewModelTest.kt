package com.github.makewheels.video2022.ui.comment

import com.github.makewheels.video2022.data.model.Comment
import com.github.makewheels.video2022.data.model.CommentPageResponse
import com.github.makewheels.video2022.data.repository.CommentRepository
import com.github.makewheels.video2022.ui.watch.CommentViewModel
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
class CommentViewModelTest {

    private lateinit var commentRepository: CommentRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun createComment(id: String, content: String = "Comment $id") = Comment(
        id = id, videoId = "v1", userId = "u1", userPhone = null,
        content = content, parentId = null, replyToUserId = null,
        replyToUserPhone = null, likeCount = 0, replyCount = 0,
        createTime = null, updateTime = null
    )

    private fun pageResponse(comments: List<Comment>, page: Int = 0, pageSize: Int = 20) =
        CommentPageResponse(
            list = comments,
            total = comments.size.toLong(),
            totalPages = if (comments.isEmpty()) 0 else 1,
            currentPage = page,
            pageSize = pageSize
        )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        commentRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CommentViewModel {
        return CommentViewModel(commentRepository)
    }

    @Test
    fun `loadComments — success — updates state with comment list`() = runTest {
        val comments = listOf(createComment("1"), createComment("2"))
        coEvery { commentRepository.getComments("v1", 0, 20) } returns
                Result.success(pageResponse(comments))

        val vm = createViewModel()
        vm.loadComments("v1")

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.comments.size)
        assertEquals("Comment 1", state.comments[0].content)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadComments — failure — sets error message`() = runTest {
        coEvery { commentRepository.getComments("v1", 0, 20) } returns
                Result.failure(Exception("network error"))

        val vm = createViewModel()
        vm.loadComments("v1")

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("network error", state.errorMessage)
    }

    @Test
    fun `loadMore — appends comments to existing list`() = runTest {
        val page1 = (1..20).map { createComment("$it") }
        val page2 = listOf(createComment("21"), createComment("22"))
        coEvery { commentRepository.getComments("v1", 0, 20) } returns
                Result.success(CommentPageResponse(list = page1, total = 22, totalPages = 2, currentPage = 0, pageSize = 20))
        coEvery { commentRepository.getComments("v1", 1, 20) } returns
                Result.success(CommentPageResponse(list = page2, total = 22, totalPages = 2, currentPage = 1, pageSize = 20))

        val vm = createViewModel()
        vm.loadComments("v1")
        assertEquals(20, vm.uiState.value.comments.size)

        vm.loadMore()

        assertEquals(22, vm.uiState.value.comments.size)
        assertFalse(vm.uiState.value.hasMore)
    }

    @Test
    fun `sendComment — success — prepends new comment and clears input`() = runTest {
        coEvery { commentRepository.getComments("v1", 0, 20) } returns
                Result.success(pageResponse(emptyList()))
        val newComment = createComment("new", "Hello world")
        coEvery { commentRepository.addComment("v1", "Hello world", null) } returns
                Result.success(newComment)

        val vm = createViewModel()
        vm.loadComments("v1")
        vm.updateInput("Hello world")
        vm.sendComment()

        val state = vm.uiState.value
        assertEquals(1, state.comments.size)
        assertEquals("Hello world", state.comments[0].content)
        assertEquals("", state.inputText)
        assertNull(state.replyTarget)
        assertFalse(state.isSending)
    }

    @Test
    fun `sendComment — empty input — does nothing`() = runTest {
        coEvery { commentRepository.getComments("v1", 0, 20) } returns
                Result.success(pageResponse(emptyList()))

        val vm = createViewModel()
        vm.loadComments("v1")
        vm.updateInput("   ")
        vm.sendComment()

        coVerify(exactly = 0) { commentRepository.addComment(any(), any(), any()) }
    }
}
