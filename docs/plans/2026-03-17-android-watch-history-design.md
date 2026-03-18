# Android 观看历史功能设计

## 概述

为 Android 客户端补全观看历史功能，使其与 iOS 客户端功能保持一致。

## 当前状态

- 后端 API 已完成：`GET /watchHistory/getMyHistory`、`DELETE /watchHistory/clear`
- Android 仅有 UI 框架，缺少 ViewModel、Repository 和 API 调用
- Android 底部导航栏无"历史"入口

## 架构设计

```
WatchHistoryScreen (UI层)
       ↓
WatchHistoryViewModel (业务逻辑层)
       ↓
WatchHistoryRepository (数据访问层)
       ↓
WatchApi (API接口)
```

## 文件变更清单

### 新增文件

1. **WatchHistoryRepository.kt** (`data/repository/`)
   - `getWatchHistory(page: Int, pageSize: Int): Result<WatchHistoryResponse>`
   - `clearWatchHistory(): Result<Unit>`

2. **WatchHistoryViewModel.kt** (`ui/watchhistory/`)
   - UiState: videos, isLoading, hasMore, showClearDialog, errorMessage
   - loadHistory(), loadMore(), clearHistory()

### 修改文件

1. **WatchApi.kt** (`data/api/`)
   - 添加 `getWatchHistory(page, pageSize)` API
   - 添加 `clearWatchHistory()` API

2. **WatchHistoryScreen.kt** (`ui/watchhistory/`)
   - 移除本地 data class（使用 model 包中的）
   - 接入 ViewModel
   - 添加加载更多逻辑

3. **BottomNavBar.kt** (`ui/components/`)
   - 在"我的"和"设置"之间添加"历史" Tab
   - 使用 `Icons.Filled.History` 图标

### 保留文件

- **WatchHistory.kt** (`data/model/`) — 已有数据模型，无需修改

## API 接口

```kotlin
// WatchApi.kt 新增
@GET("/watchHistory/getMyHistory")
suspend fun getWatchHistory(
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int
): ApiResponse<WatchHistoryResponse>

@DELETE("/watchHistory/clear")
suspend fun clearWatchHistory(): ApiResponse<Unit>
```

## 功能清单

| 功能 | 描述 |
|------|------|
| 获取观看历史 | 分页加载，每页 20 条，按时间倒序 |
| 加载更多 | 滚动到底部自动加载下一页 |
| 清除历史 | 显示确认对话框，确认后清除 |
| 空状态展示 | 无历史记录时显示提示 |
| 错误处理 | 显示错误消息 |

## 导航入口

在底部导航栏添加"历史" Tab，位置在"我的"和"设置"之间，与 iOS 保持一致。

## 测试计划

- WatchHistoryRepository 单元测试
- WatchHistoryViewModel 单元测试