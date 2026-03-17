# Android 观看历史功能实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 Android 客户端补全观看历史功能，包括 Repository、ViewModel、API 调用和底部导航入口。

**Architecture:** 采用 MVVM 架构，使用 Hilt 依赖注入。数据流：Screen → ViewModel → Repository → API。

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Retrofit, Coroutines, StateFlow

---

## Task 1: 添加 WatchHistory API 接口

**Files:**
- Modify: `android/app/src/main/java/com/github/makewheels/video2022/data/api/WatchApi.kt`

**Step 1: 添加 API 方法**

在 `WatchApi.kt` 末尾添加两个方法：

```kotlin
@GET("/watchHistory/getMyHistory")
suspend fun getWatchHistory(
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int
): ApiResponse<WatchHistoryResponse>

@DELETE("/watchHistory/clear")
suspend fun clearWatchHistory(): ApiResponse<Unit>
```

**Step 2: 添加导入**

在文件顶部添加导入：
```kotlin
import com.github.makewheels.video2022.data.model.WatchHistoryResponse
```

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/github/makewheels/video2022/data/api/WatchApi.kt
git commit -m "feat(android): 添加观看历史 API 接口"
```

---

## Task 2: 创建 WatchHistoryRepository

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/repository/WatchHistoryRepository.kt`
- Create: `android/app/src/test/java/com/github/makewheels/video2022/data/repository/WatchHistoryRepositoryTest.kt`

**Step 1: 写测试（失败）**

创建测试文件：

```kotlin
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
```

**Step 2: 运行测试验证失败**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "WatchHistoryRepositoryTest" 2>&1 | head -30
```

预期：失败，找不到 `WatchHistoryRepository` 类

**Step 3: 创建 Repository 实现**

```kotlin
package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.WatchApi
import com.github.makewheels.video2022.data.model.WatchHistoryResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchHistoryRepository @Inject constructor(
    private val watchApi: WatchApi
) {
    suspend fun getWatchHistory(page: Int, pageSize: Int): Result<WatchHistoryResponse> = runCatching {
        val resp = watchApi.getWatchHistory(page, pageSize)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取观看历史失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun clearWatchHistory(): Result<Unit> = runCatching {
        val resp = watchApi.clearWatchHistory()
        if (!resp.isSuccess) throw Exception(resp.message ?: "清除观看历史失败")
    }
}
```

**Step 4: 运行测试验证通过**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "WatchHistoryRepositoryTest"
```

预期：所有测试通过

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/makewheels/video2022/data/repository/WatchHistoryRepository.kt
git add android/app/src/test/java/com/github/makewheels/video2022/data/repository/WatchHistoryRepositoryTest.kt
git commit -m "feat(android): 添加 WatchHistoryRepository 和测试"
```

---

## Task 3: 创建 WatchHistoryViewModel

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/watchhistory/WatchHistoryViewModel.kt`
- Create: `android/app/src/test/java/com/github/makewheels/video2022/ui/watchhistory/WatchHistoryViewModelTest.kt`

**Step 1: 写测试（失败）**

```kotlin
package com.github.makewheels.video2022.ui.watchhistory

import com.github.makewheels.video2022.data.model.WatchHistoryItem
import com.github.makewheels.video2022.data.model.WatchHistoryResponse
import com.github.makewheels.video2022.data.repository.WatchHistoryRepository
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
class WatchHistoryViewModelTest {

    private lateinit var repository: WatchHistoryRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun createHistoryItem(videoId: String) = WatchHistoryItem(
        videoId = videoId,
        title = "Video $videoId",
        coverUrl = "https://example.com/cover.jpg",
        watchTime = "2026-03-17 10:00:00"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WatchHistoryViewModel {
        return WatchHistoryViewModel(repository)
    }

    @Test
    fun `init - loads history on creation`() = runTest {
        val items = listOf(createHistoryItem("v1"), createHistoryItem("v2"))
        coEvery { repository.getWatchHistory(0, 20) } returns
                Result.success(WatchHistoryResponse(list = items, total = 2, page = 0, pageSize = 20))

        val vm = createViewModel()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.videos.size)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadMore - appends items to list`() = runTest {
        val page1 = listOf(createHistoryItem("v1"))
        val page2 = listOf(createHistoryItem("v2"))
        coEvery { repository.getWatchHistory(0, 20) } returns
                Result.success(WatchHistoryResponse(list = page1, total = 2, page = 0, pageSize = 20))
        coEvery { repository.getWatchHistory(1, 20) } returns
                Result.success(WatchHistoryResponse(list = page2, total = 2, page = 1, pageSize = 20))

        val vm = createViewModel()
        assertEquals(1, vm.uiState.value.videos.size)

        vm.loadMore()

        assertEquals(2, vm.uiState.value.videos.size)
    }

    @Test
    fun `clearHistory - clears list on success`() = runTest {
        val items = listOf(createHistoryItem("v1"))
        coEvery { repository.getWatchHistory(0, 20) } returns
                Result.success(WatchHistoryResponse(list = items, total = 1, page = 0, pageSize = 20))
        coEvery { repository.clearWatchHistory() } returns Result.success(Unit)

        val vm = createViewModel()
        assertEquals(1, vm.uiState.value.videos.size)

        vm.clearHistory()

        assertEquals(0, vm.uiState.value.videos.size)
        assertFalse(vm.uiState.value.showClearDialog)
    }

    @Test
    fun `showClearDialog and dismissClearDialog - toggles dialog state`() = runTest {
        coEvery { repository.getWatchHistory(0, 20) } returns
                Result.success(WatchHistoryResponse(list = emptyList(), total = 0, page = 0, pageSize = 20))

        val vm = createViewModel()
        assertFalse(vm.uiState.value.showClearDialog)

        vm.showClearDialog()
        assertTrue(vm.uiState.value.showClearDialog)

        vm.dismissClearDialog()
        assertFalse(vm.uiState.value.showClearDialog)
    }
}
```

**Step 2: 运行测试验证失败**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "WatchHistoryViewModelTest" 2>&1 | head -30
```

预期：失败，找不到 `WatchHistoryViewModel` 类

**Step 3: 创建 ViewModel 实现**

```kotlin
package com.github.makewheels.video2022.ui.watchhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.model.WatchHistoryItem
import com.github.makewheels.video2022.data.repository.WatchHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchHistoryUiState(
    val videos: List<WatchHistoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val showClearDialog: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class WatchHistoryViewModel @Inject constructor(
    private val repository: WatchHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchHistoryUiState())
    val uiState = _uiState.asStateFlow()

    private val pageSize = 20

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.getWatchHistory(0, pageSize)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        videos = resp.list,
                        isLoading = false,
                        hasMore = resp.list.size >= pageSize
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = it.message
                    )
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            val page = state.videos.size / pageSize
            repository.getWatchHistory(page, pageSize)
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        videos = _uiState.value.videos + resp.list,
                        isLoading = false,
                        hasMore = resp.list.size >= pageSize
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }

    fun showClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = true)
    }

    fun dismissClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = false)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearWatchHistory()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        videos = emptyList(),
                        showClearDialog = false,
                        hasMore = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        showClearDialog = false,
                        errorMessage = it.message
                    )
                }
        }
    }
}
```

**Step 4: 运行测试验证通过**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "WatchHistoryViewModelTest"
```

预期：所有测试通过

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/makewheels/video2022/ui/watchhistory/WatchHistoryViewModel.kt
git add android/app/src/test/java/com/github/makewheels/video2022/ui/watchhistory/WatchHistoryViewModelTest.kt
git commit -m "feat(android): 添加 WatchHistoryViewModel 和测试"
```

---

## Task 4: 更新 WatchHistoryScreen 接入 ViewModel

**Files:**
- Modify: `android/app/src/main/java/com/github/makewheels/video2022/ui/watchhistory/WatchHistoryScreen.kt`

**Step 1: 更新 Screen 实现**

替换整个文件内容为：

```kotlin
package com.github.makewheels.video2022.ui.watchhistory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHistoryScreen(
    onVideoClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: WatchHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // 加载更多检测
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 3
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && uiState.hasMore && !uiState.isLoading) {
                viewModel.loadMore()
            }
        }
    }

    if (uiState.showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearDialog() },
            title = { Text("清除观看历史") },
            text = { Text("确定要清除所有观看历史吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory() }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearDialog() }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("观看历史") },
                actions = {
                    if (uiState.videos.isNotEmpty()) {
                        IconButton(onClick = { viewModel.showClearDialog() }) {
                            Icon(Icons.Default.Delete, contentDescription = "清除观看历史")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading && uiState.videos.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.videos.isEmpty() -> {
                    Text(
                        text = "暂无观看历史",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.videos, key = { "${it.videoId}-${it.watchTime}" }) { item ->
                            WatchHistoryCard(item = item, onClick = { onVideoClick(item.videoId) })
                        }
                        if (uiState.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchHistoryCard(item: WatchHistoryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title ?: "未命名视频",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "观看时间: ${item.watchTime ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add android/app/src/main/java/com/github/makewheels/video2022/ui/watchhistory/WatchHistoryScreen.kt
git commit -m "feat(android): WatchHistoryScreen 接入 ViewModel"
```

---

## Task 5: 添加底部导航栏入口

**Files:**
- Modify: `android/app/src/main/java/com/github/makewheels/video2022/ui/components/BottomNavBar.kt`

**Step 1: 添加历史 Tab**

在 `bottomNavItems` 列表中，"我的"和"设置"之间添加历史项：

```kotlin
val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "首页", Icons.Filled.Home),
    BottomNavItem(Screen.Notification, "通知", Icons.Filled.Notifications),
    BottomNavItem(Screen.Playlist, "播放列表", Icons.Filled.PlaylistPlay),
    BottomNavItem(Screen.Upload, "上传", Icons.Filled.AddCircle),
    BottomNavItem(Screen.MyVideos, "我的", Icons.Filled.VideoLibrary),
    BottomNavItem(Screen.WatchHistory, "历史", Icons.Filled.History),
    BottomNavItem(Screen.Settings, "设置", Icons.Filled.Settings),
)
```

**Step 2: 导航到 Screen**

确保在 `Screen.kt` 中有 `WatchHistory` 定义（已存在，无需修改）。

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/github/makewheels/video2022/ui/components/BottomNavBar.kt
git commit -m "feat(android): 底部导航栏添加观看历史入口"
```

---

## Task 6: 运行完整测试并构建验证

**Step 1: 运行所有相关测试**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "WatchHistory*"
```

预期：所有测试通过

**Step 2: 构建验证**

```bash
cd android && ./gradlew :app:assembleDebug
```

预期：构建成功

**Step 3: 最终 Commit（如有未提交更改）**

```bash
git status
# 如果有未提交的更改
git add -A
git commit -m "feat(android): 完成观看历史功能"
```

---

## 完成检查清单

- [ ] WatchApi 添加了 getWatchHistory 和 clearWatchHistory 方法
- [ ] WatchHistoryRepository 创建并通过测试
- [ ] WatchHistoryViewModel 创建并通过测试
- [ ] WatchHistoryScreen 接入 ViewModel
- [ ] 底部导航栏添加"历史"入口
- [ ] 所有测试通过
- [ ] Debug 构建成功