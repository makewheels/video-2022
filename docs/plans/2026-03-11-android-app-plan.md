# Android 原生客户端实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 video-2022 视频平台构建 Kotlin + Jetpack Compose 原生 Android 客户端，功能与 Web 端完全对齐（除统计页外）。

**Architecture:** 单模块 Compose App，MVVM 架构（Screen + ViewModel + Repository + Retrofit API）。Hilt 做依赖注入，OkHttp Interceptor 统一注入认证 token。视频播放用 Media3 ExoPlayer（原生 HLS），上传用 WorkManager + 阿里云 OSS SDK。

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, Retrofit, OkHttp, Gson, Media3 ExoPlayer, Coil, Hilt, WorkManager, Aliyun OSS Android SDK

**Design Document:** `docs/plans/2026-03-11-144100-android-app-design.md`

**后端 API Base URL:**
- Debug: `http://10.0.2.2:5022`（模拟器映射本机）
- Release: `https://oneclick.video`

**认证方式:** 所有需认证的 API 在 Header 中传 `token: {value}`

**API 统一响应格式:** `{ "code": 0, "message": "...", "data": {...} }`，code=0 表示成功

**分页方式:** `skip` + `limit`（非 page-based）

---

## Phase 1: 项目脚手架

### Task 1: 创建 Android 项目骨架

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`（项目级）
- Create: `android/gradle.properties`
- Create: `android/gradle/libs.versions.toml`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/VideoApp.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/MainActivity.kt`
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/main/res/values/themes.xml`

**Step 1: 用 Android SDK 命令行工具或手动创建项目结构**

`gradle/libs.versions.toml` 版本目录（统一管理所有依赖版本）：

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
compose-bom = "2024.12.01"
compose-compiler = "1.5.15"
navigation = "2.8.5"
hilt = "2.53.1"
hilt-navigation-compose = "1.2.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
gson = "2.11.0"
media3 = "1.5.1"
coil = "2.7.0"
coroutines = "1.9.0"
workmanager = "2.10.0"
aliyun-oss = "2.9.19"
lifecycle = "2.8.7"
datastore = "1.1.1"
ksp = "2.1.0-1.0.29"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.9.3" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-exoplayer-hls = { group = "androidx.media3", name = "media3-exoplayer-hls", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
workmanager = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workmanager" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version = "1.2.0" }
hilt-work-compiler = { group = "androidx.hilt", name = "hilt-compiler", version = "1.2.0" }
aliyun-oss = { group = "com.aliyun.dpa", name = "oss-android-sdk", version.ref = "aliyun-oss" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
junit = { group = "junit", name = "junit", version = "4.13.2" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
mockk = { group = "io.mockk", name = "mockk", version = "1.13.13" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolution {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}
rootProject.name = "video-2022"
include(":app")
```

项目级 `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.github.makewheels.video2022"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.github.makewheels.video2022"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5022\"")
            buildConfigField("String", "YOUTUBE_BASE_URL", "\"https://youtube.videoplus.top:5030\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"https://oneclick.video\"")
            buildConfigField("String", "YOUTUBE_BASE_URL", "\"https://youtube.videoplus.top:5030\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Media3 ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    // Image loading
    implementation(libs.coil.compose)

    // Async
    implementation(libs.coroutines.android)

    // WorkManager (background upload)
    implementation(libs.workmanager)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Aliyun OSS
    implementation(libs.aliyun.oss)

    // DataStore (token persistence)
    implementation(libs.datastore.preferences)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
}
```

`VideoApp.kt`:

```kotlin
package com.github.makewheels.video2022

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VideoApp : Application()
```

`MainActivity.kt`:

```kotlin
package com.github.makewheels.video2022

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.makewheels.video2022.ui.theme.VideoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoTheme {
                // 后续 Task 添加 NavHost
            }
        }
    }
}
```

`AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".VideoApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Video2022"
        android:usesCleartextTraffic="true">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Step 2: 验证项目可编译**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/
git commit -m "feat(android): scaffold project with Compose + Hilt + Media3 + Retrofit"
```

---

## Phase 2: 数据层（Model + API + Repository）

### Task 2: 数据模型

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/model/ApiResponse.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/model/User.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/model/Video.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/model/Comment.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/model/Playlist.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/model/WatchInfo.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/model/UploadModels.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/model/LikeStatus.kt`

**Step 1: 创建所有数据模型**

`ApiResponse.kt` — 统一 API 响应包装：

```kotlin
package com.github.makewheels.video2022.data.model

data class ApiResponse<T>(
    val code: Int,
    val message: String?,
    val data: T?
) {
    val isSuccess: Boolean get() = code == 0
}
```

`User.kt`:

```kotlin
package com.github.makewheels.video2022.data.model

data class User(
    val id: String,
    val phone: String?,
    val registerChannel: String?,
    val createTime: String?,
    val updateTime: String?,
    val token: String?
)
```

`Video.kt`:

```kotlin
package com.github.makewheels.video2022.data.model

// 视频列表项（对应 VideoVO）
data class VideoItem(
    val id: String,
    val watchId: String?,
    val title: String?,
    val description: String?,
    val status: String?,
    val visibility: String?,
    val watchCount: Int?,
    val duration: Long?,
    val createTime: String?,
    val createTimeString: String?,
    val watchUrl: String?,
    val shortUrl: String?,
    val type: String?,
    val coverUrl: String?,
    val youtubePublishTimeString: String?
)

// 视频列表响应
data class VideoListResponse(
    val list: List<VideoItem>,
    val total: Long
)

// 创建视频请求
data class CreateVideoRequest(
    val videoType: String,
    val rawFilename: String? = null,
    val youtubeUrl: String? = null,
    val size: Long? = null,
    val ttl: String = "PERMANENT"
)

// 创建视频响应
data class CreateVideoResponse(
    val watchId: String,
    val shortUrl: String,
    val videoId: String,
    val watchUrl: String,
    val fileId: String
)

// 更新视频信息请求
data class UpdateVideoInfoRequest(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val visibility: String? = null
)

// 视频状态响应
data class VideoStatus(
    val videoId: String,
    val status: String,
    val isReady: Boolean
)

// 更新观看设置请求
data class UpdateWatchSettingsRequest(
    val videoId: String,
    val showUploadTime: Boolean? = null,
    val showWatchCount: Boolean? = null
)
```

`WatchInfo.kt`:

```kotlin
package com.github.makewheels.video2022.data.model

data class WatchInfo(
    val videoId: String,
    val coverUrl: String?,
    val videoStatus: String?,
    val multivariantPlaylistUrl: String?,
    val progressInMillis: Long?
)

data class HeartbeatRequest(
    val videoId: String,
    val clientId: String,
    val sessionId: String,
    val videoStatus: String = "READY",
    val playerProvider: String = "ANDROID_EXOPLAYER",
    val clientTime: String,
    val type: String = "TIMER",
    val event: String? = null,
    val playerTime: Long,
    val playerStatus: String,
    val playerVolume: Float
)

data class ProgressResponse(
    val progressInMillis: Long?
)
```

`Comment.kt`:

```kotlin
package com.github.makewheels.video2022.data.model

data class Comment(
    val id: String,
    val videoId: String?,
    val userId: String?,
    val userPhone: String?,
    val content: String?,
    val parentId: String?,
    val replyToUserId: String?,
    val replyToUserPhone: String?,
    val likeCount: Int?,
    val replyCount: Int?,
    val createTime: String?,
    val updateTime: String?
)

data class AddCommentRequest(
    val videoId: String,
    val content: String,
    val parentId: String? = null
)

data class CommentCount(
    val count: Int
)
```

`Playlist.kt`:

```kotlin
package com.github.makewheels.video2022.data.model

data class Playlist(
    val id: String,
    val title: String?,
    val description: String?,
    val ownerId: String?,
    val visibility: String?,
    val deleted: Boolean?,
    val createTime: String?,
    val updateTime: String?
)

data class PlaylistItem(
    val videoId: String?,
    val watchId: String?,
    val title: String?,
    val coverUrl: String?,
    val watchCount: Int?,
    val videoCreateTime: String?
)

data class CreatePlaylistRequest(
    val title: String,
    val description: String? = null
)

data class UpdatePlaylistRequest(
    val playlistId: String,
    val title: String? = null,
    val description: String? = null,
    val visibility: String? = null
)

data class AddPlaylistItemRequest(
    val playlistId: String,
    val videoIdList: List<String>,
    val addMode: String = "APPEND"
)

data class DeletePlaylistItemRequest(
    val playlistId: String,
    val deleteMode: String = "BY_VIDEO_ID",
    val videoIdList: List<String>
)

data class MovePlaylistItemRequest(
    val playlistId: String,
    val videoId: String,
    val moveMode: String = "TO_INDEX",
    val toIndex: Int
)
```

`UploadModels.kt`:

```kotlin
package com.github.makewheels.video2022.data.model

data class UploadCredentials(
    val bucket: String,
    val accessKeyId: String,
    val endpoint: String,
    val secretKey: String,
    val provider: String,
    val sessionToken: String,
    val expiration: String,
    val key: String
)
```

`LikeStatus.kt`:

```kotlin
package com.github.makewheels.video2022.data.model

data class LikeStatus(
    val likeCount: Int,
    val dislikeCount: Int,
    val userAction: String? // "LIKE" | "DISLIKE" | null
)
```

**Step 2: Commit**

```bash
git add android/app/src/main/java/com/github/makewheels/video2022/data/model/
git commit -m "feat(android): add all data models matching backend API contracts"
```

---

### Task 3: Retrofit API 接口

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/api/UserApi.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/api/VideoApi.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/api/WatchApi.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/api/CommentApi.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/api/PlaylistApi.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/api/LikeApi.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/api/YouTubeApi.kt`

**Step 1: 创建所有 Retrofit 接口**

`UserApi.kt`:

```kotlin
package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query

interface UserApi {
    @GET("/user/requestVerificationCode")
    suspend fun requestVerificationCode(@Query("phone") phone: String): ApiResponse<Any?>

    @GET("/user/submitVerificationCode")
    suspend fun submitVerificationCode(
        @Query("phone") phone: String,
        @Query("code") code: String
    ): ApiResponse<User>

    @GET("/user/getUserByToken")
    suspend fun getUserByToken(@Query("token") token: String): ApiResponse<User>

    @GET("/client/requestClientId")
    suspend fun requestClientId(): ApiResponse<Map<String, String>>

    @GET("/session/requestSessionId")
    suspend fun requestSessionId(): ApiResponse<Map<String, String>>
}
```

`VideoApi.kt`:

```kotlin
package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import retrofit2.http.*

interface VideoApi {
    @POST("/video/create")
    suspend fun createVideo(@Body request: CreateVideoRequest): ApiResponse<CreateVideoResponse>

    @GET("/file/getUploadCredentials")
    suspend fun getUploadCredentials(@Query("fileId") fileId: String): ApiResponse<UploadCredentials>

    @GET("/file/uploadFinish")
    suspend fun uploadFinish(@Query("fileId") fileId: String): ApiResponse<Any?>

    @GET("/video/rawFileUploadFinish")
    suspend fun rawFileUploadFinish(@Query("videoId") videoId: String): ApiResponse<Any?>

    @POST("/video/updateInfo")
    suspend fun updateVideoInfo(@Body request: UpdateVideoInfoRequest): ApiResponse<VideoItem>

    @POST("/video/updateWatchSettings")
    suspend fun updateWatchSettings(@Body request: UpdateWatchSettingsRequest): ApiResponse<Any?>

    @GET("/video/getVideoDetail")
    suspend fun getVideoDetail(@Query("videoId") videoId: String): ApiResponse<VideoItem>

    @GET("/video/getMyVideoList")
    suspend fun getMyVideoList(
        @Query("skip") skip: Int,
        @Query("limit") limit: Int,
        @Query("keyword") keyword: String? = null
    ): ApiResponse<VideoListResponse>

    @GET("/video/getRawFileDownloadUrl")
    suspend fun getRawFileDownloadUrl(@Query("videoId") videoId: String): ApiResponse<Map<String, String>>

    @GET("/video/delete")
    suspend fun deleteVideo(@Query("videoId") videoId: String): ApiResponse<Any?>

    @GET("/video/getVideoStatus")
    suspend fun getVideoStatus(@Query("videoId") videoId: String): ApiResponse<VideoStatus>
}
```

`WatchApi.kt`:

```kotlin
package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import retrofit2.http.*

interface WatchApi {
    @GET("/watchController/getWatchInfo")
    suspend fun getWatchInfo(@Query("watchId") watchId: String): ApiResponse<WatchInfo>

    @POST("/heartbeat/add")
    suspend fun addHeartbeat(@Body request: HeartbeatRequest): ApiResponse<Any?>

    @GET("/progress/getProgress")
    suspend fun getProgress(
        @Query("videoId") videoId: String,
        @Query("clientId") clientId: String
    ): ApiResponse<ProgressResponse?>
}
```

`CommentApi.kt`:

```kotlin
package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import retrofit2.http.*

interface CommentApi {
    @POST("/comment/add")
    suspend fun addComment(@Body request: AddCommentRequest): ApiResponse<Comment>

    @GET("/comment/getByVideoId")
    suspend fun getByVideoId(
        @Query("videoId") videoId: String,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "time"
    ): ApiResponse<List<Comment>>

    @GET("/comment/getReplies")
    suspend fun getReplies(
        @Query("parentId") parentId: String,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 20
    ): ApiResponse<List<Comment>>

    @GET("/comment/delete")
    suspend fun deleteComment(@Query("commentId") commentId: String): ApiResponse<Any?>

    @GET("/comment/like")
    suspend fun likeComment(@Query("commentId") commentId: String): ApiResponse<Any?>

    @GET("/comment/getCount")
    suspend fun getCount(@Query("videoId") videoId: String): ApiResponse<CommentCount>
}
```

`PlaylistApi.kt`:

```kotlin
package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import retrofit2.http.*

interface PlaylistApi {
    @POST("/playlist/createPlaylist")
    suspend fun createPlaylist(@Body request: CreatePlaylistRequest): ApiResponse<Playlist>

    @POST("/playlist/updatePlaylist")
    suspend fun updatePlaylist(@Body request: UpdatePlaylistRequest): ApiResponse<Playlist>

    @GET("/playlist/deletePlaylist")
    suspend fun deletePlaylist(@Query("playlistId") playlistId: String): ApiResponse<Any?>

    @GET("/playlist/recoverPlaylist")
    suspend fun recoverPlaylist(@Query("playlistId") playlistId: String): ApiResponse<Any?>

    @GET("/playlist/getPlaylistById")
    suspend fun getPlaylistById(
        @Query("playlistId") playlistId: String,
        @Query("showVideoList") showVideoList: Boolean = false
    ): ApiResponse<Playlist>

    @GET("/playlist/getPlayItemListDetail")
    suspend fun getPlayItemListDetail(@Query("playlistId") playlistId: String): ApiResponse<List<PlaylistItem>>

    @GET("/playlist/getMyPlaylistByPage")
    suspend fun getMyPlaylistByPage(
        @Query("skip") skip: Int,
        @Query("limit") limit: Int
    ): ApiResponse<List<Playlist>>

    @POST("/playlist/addPlaylistItem")
    suspend fun addPlaylistItem(@Body request: AddPlaylistItemRequest): ApiResponse<Playlist>

    @POST("/playlist/deletePlaylistItem")
    suspend fun deletePlaylistItem(@Body request: DeletePlaylistItemRequest): ApiResponse<Any?>

    @POST("/playlist/movePlaylistItem")
    suspend fun movePlaylistItem(@Body request: MovePlaylistItemRequest): ApiResponse<Any?>

    @GET("/playlist/getPlaylistByVideoId")
    suspend fun getPlaylistByVideoId(@Query("videoId") videoId: String): ApiResponse<List<String>>
}
```

`LikeApi.kt`:

```kotlin
package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query

interface LikeApi {
    @GET("/videoLike/like")
    suspend fun like(@Query("videoId") videoId: String): ApiResponse<Any?>

    @GET("/videoLike/dislike")
    suspend fun dislike(@Query("videoId") videoId: String): ApiResponse<Any?>

    @GET("/videoLike/getStatus")
    suspend fun getStatus(@Query("videoId") videoId: String): ApiResponse<LikeStatus>
}
```

`YouTubeApi.kt`:

```kotlin
package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApi {
    @GET("/youtube/getFileExtension")
    suspend fun getFileExtension(@Query("youtubeVideoId") videoId: String): ApiResponse<Map<String, String>>

    @GET("/youtube/getVideoInfo")
    suspend fun getVideoInfo(@Query("youtubeVideoId") videoId: String): ApiResponse<Map<String, Any>>
}
```

**Step 2: Commit**

```bash
git add android/app/src/main/java/com/github/makewheels/video2022/data/api/
git commit -m "feat(android): add all Retrofit API interfaces for backend endpoints"
```

---

### Task 4: Hilt DI 模块 + 网络配置

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/di/NetworkModule.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/util/TokenManager.kt`

**Step 1: 创建 TokenManager**

```kotlin
package com.github.makewheels.video2022.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("token")
        private val CLIENT_ID_KEY = stringPreferencesKey("clientId")
        private val SESSION_ID_KEY = stringPreferencesKey("sessionId")
        private val USER_PHONE_KEY = stringPreferencesKey("userPhone")
    }

    fun getTokenSync(): String? = runBlocking {
        context.dataStore.data.map { it[TOKEN_KEY] }.first()
    }

    suspend fun setToken(token: String) {
        context.dataStore.data.map { it[TOKEN_KEY] }.first()
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun clearToken() {
        context.dataStore.edit { it.remove(TOKEN_KEY) }
    }

    fun getClientIdSync(): String? = runBlocking {
        context.dataStore.data.map { it[CLIENT_ID_KEY] }.first()
    }

    suspend fun setClientId(clientId: String) {
        context.dataStore.edit { it[CLIENT_ID_KEY] = clientId }
    }

    fun getSessionIdSync(): String? = runBlocking {
        context.dataStore.data.map { it[SESSION_ID_KEY] }.first()
    }

    suspend fun setSessionId(sessionId: String) {
        context.dataStore.edit { it[SESSION_ID_KEY] = sessionId }
    }

    suspend fun setUserPhone(phone: String) {
        context.dataStore.edit { it[USER_PHONE_KEY] = phone }
    }

    suspend fun getUserPhone(): String? {
        return context.dataStore.data.map { it[USER_PHONE_KEY] }.first()
    }

    fun isLoggedIn(): Boolean = getTokenSync() != null
}
```

**Step 2: 创建 NetworkModule**

```kotlin
package com.github.makewheels.video2022.di

import com.github.makewheels.video2022.BuildConfig
import com.github.makewheels.video2022.data.api.*
import com.github.makewheels.video2022.util.TokenManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenManager: TokenManager): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                tokenManager.getTokenSync()?.let { builder.addHeader("token", it) }
                tokenManager.getClientIdSync()?.let { builder.addHeader("clientId", it) }
                tokenManager.getSessionIdSync()?.let { builder.addHeader("sessionId", it) }
                chain.proceed(builder.build())
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @Named("main")
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @Named("youtube")
    fun provideYouTubeRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.YOUTUBE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides @Singleton
    fun provideUserApi(@Named("main") retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides @Singleton
    fun provideVideoApi(@Named("main") retrofit: Retrofit): VideoApi = retrofit.create(VideoApi::class.java)

    @Provides @Singleton
    fun provideWatchApi(@Named("main") retrofit: Retrofit): WatchApi = retrofit.create(WatchApi::class.java)

    @Provides @Singleton
    fun provideCommentApi(@Named("main") retrofit: Retrofit): CommentApi = retrofit.create(CommentApi::class.java)

    @Provides @Singleton
    fun providePlaylistApi(@Named("main") retrofit: Retrofit): PlaylistApi = retrofit.create(PlaylistApi::class.java)

    @Provides @Singleton
    fun provideLikeApi(@Named("main") retrofit: Retrofit): LikeApi = retrofit.create(LikeApi::class.java)

    @Provides @Singleton
    fun provideYouTubeApi(@Named("youtube") retrofit: Retrofit): YouTubeApi = retrofit.create(YouTubeApi::class.java)
}
```

**Step 3: 验证编译**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add android/app/src/main/java/com/github/makewheels/video2022/di/
git add android/app/src/main/java/com/github/makewheels/video2022/util/
git commit -m "feat(android): add Hilt DI module with Retrofit, OkHttp interceptor, TokenManager"
```

---

### Task 5: Repository 层

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/repository/UserRepository.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/repository/VideoRepository.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/repository/WatchRepository.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/repository/CommentRepository.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/repository/PlaylistRepository.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/data/repository/UploadRepository.kt`

**Step 1: 创建所有 Repository**

每个 Repository 封装对应的 API 调用，将 `ApiResponse` 解包为 `Result<T>`：

`UserRepository.kt`:

```kotlin
package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.UserApi
import com.github.makewheels.video2022.data.model.User
import com.github.makewheels.video2022.util.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userApi: UserApi,
    private val tokenManager: TokenManager
) {
    suspend fun requestVerificationCode(phone: String): Result<Unit> = runCatching {
        val response = userApi.requestVerificationCode(phone)
        if (!response.isSuccess) throw Exception(response.message ?: "请求验证码失败")
    }

    suspend fun submitVerificationCode(phone: String, code: String): Result<User> = runCatching {
        val response = userApi.submitVerificationCode(phone, code)
        if (!response.isSuccess) throw Exception(response.message ?: "验证码错误")
        val user = response.data ?: throw Exception("返回用户数据为空")
        user.token?.let { tokenManager.setToken(it) }
        tokenManager.setUserPhone(phone)
        user
    }

    suspend fun initClientAndSession() {
        if (tokenManager.getClientIdSync() == null) {
            val clientResp = userApi.requestClientId()
            clientResp.data?.get("clientId")?.let { tokenManager.setClientId(it) }
        }
        val sessionResp = userApi.requestSessionId()
        sessionResp.data?.get("sessionId")?.let { tokenManager.setSessionId(it) }
    }

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    suspend fun logout() {
        tokenManager.clearToken()
    }
}
```

`VideoRepository.kt`:

```kotlin
package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.LikeApi
import com.github.makewheels.video2022.data.api.VideoApi
import com.github.makewheels.video2022.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val videoApi: VideoApi,
    private val likeApi: LikeApi
) {
    suspend fun getMyVideoList(skip: Int, limit: Int, keyword: String? = null): Result<VideoListResponse> = runCatching {
        val resp = videoApi.getMyVideoList(skip, limit, keyword)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取视频列表失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun getVideoDetail(videoId: String): Result<VideoItem> = runCatching {
        val resp = videoApi.getVideoDetail(videoId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取视频详情失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun updateVideoInfo(request: UpdateVideoInfoRequest): Result<VideoItem> = runCatching {
        val resp = videoApi.updateVideoInfo(request)
        if (!resp.isSuccess) throw Exception(resp.message ?: "更新失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun deleteVideo(videoId: String): Result<Unit> = runCatching {
        val resp = videoApi.deleteVideo(videoId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "删除失败")
    }

    suspend fun getVideoStatus(videoId: String): Result<VideoStatus> = runCatching {
        val resp = videoApi.getVideoStatus(videoId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取状态失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun getLikeStatus(videoId: String): Result<LikeStatus> = runCatching {
        val resp = likeApi.getStatus(videoId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取点赞状态失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun like(videoId: String): Result<Unit> = runCatching {
        val resp = likeApi.like(videoId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "点赞失败")
    }

    suspend fun dislike(videoId: String): Result<Unit> = runCatching {
        val resp = likeApi.dislike(videoId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "踩失败")
    }
}
```

`WatchRepository.kt`:

```kotlin
package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.WatchApi
import com.github.makewheels.video2022.data.model.*
import com.github.makewheels.video2022.util.TokenManager
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchRepository @Inject constructor(
    private val watchApi: WatchApi,
    private val tokenManager: TokenManager
) {
    suspend fun getWatchInfo(watchId: String): Result<WatchInfo> = runCatching {
        val resp = watchApi.getWatchInfo(watchId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取播放信息失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun sendHeartbeat(
        videoId: String,
        playerTimeMs: Long,
        playerStatus: String,
        volume: Float
    ): Result<Unit> = runCatching {
        val request = HeartbeatRequest(
            videoId = videoId,
            clientId = tokenManager.getClientIdSync() ?: "",
            sessionId = tokenManager.getSessionIdSync() ?: "",
            clientTime = Instant.now().toString(),
            playerTime = playerTimeMs / 1000,
            playerStatus = playerStatus,
            playerVolume = volume
        )
        watchApi.addHeartbeat(request)
    }

    suspend fun getProgress(videoId: String): Result<Long?> = runCatching {
        val clientId = tokenManager.getClientIdSync() ?: return@runCatching null
        val resp = watchApi.getProgress(videoId, clientId)
        resp.data?.progressInMillis
    }
}
```

`CommentRepository.kt`:

```kotlin
package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.CommentApi
import com.github.makewheels.video2022.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepository @Inject constructor(
    private val commentApi: CommentApi
) {
    suspend fun getComments(videoId: String, skip: Int = 0, limit: Int = 20): Result<List<Comment>> = runCatching {
        val resp = commentApi.getByVideoId(videoId, skip, limit)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取评论失败")
        resp.data ?: emptyList()
    }

    suspend fun getReplies(parentId: String, skip: Int = 0, limit: Int = 20): Result<List<Comment>> = runCatching {
        val resp = commentApi.getReplies(parentId, skip, limit)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取回复失败")
        resp.data ?: emptyList()
    }

    suspend fun addComment(videoId: String, content: String, parentId: String? = null): Result<Comment> = runCatching {
        val resp = commentApi.addComment(AddCommentRequest(videoId, content, parentId))
        if (!resp.isSuccess) throw Exception(resp.message ?: "发表评论失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun deleteComment(commentId: String): Result<Unit> = runCatching {
        val resp = commentApi.deleteComment(commentId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "删除评论失败")
    }

    suspend fun likeComment(commentId: String): Result<Unit> = runCatching {
        commentApi.likeComment(commentId)
    }

    suspend fun getCount(videoId: String): Result<Int> = runCatching {
        val resp = commentApi.getCount(videoId)
        resp.data?.count ?: 0
    }
}
```

`PlaylistRepository.kt`:

```kotlin
package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.PlaylistApi
import com.github.makewheels.video2022.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistApi: PlaylistApi
) {
    suspend fun getMyPlaylists(skip: Int, limit: Int): Result<List<Playlist>> = runCatching {
        val resp = playlistApi.getMyPlaylistByPage(skip, limit)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取播放列表失败")
        resp.data ?: emptyList()
    }

    suspend fun getPlaylistDetail(playlistId: String): Result<List<PlaylistItem>> = runCatching {
        val resp = playlistApi.getPlayItemListDetail(playlistId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取播放列表详情失败")
        resp.data ?: emptyList()
    }

    suspend fun createPlaylist(title: String, description: String? = null): Result<Playlist> = runCatching {
        val resp = playlistApi.createPlaylist(CreatePlaylistRequest(title, description))
        if (!resp.isSuccess) throw Exception(resp.message ?: "创建播放列表失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun updatePlaylist(request: UpdatePlaylistRequest): Result<Playlist> = runCatching {
        val resp = playlistApi.updatePlaylist(request)
        if (!resp.isSuccess) throw Exception(resp.message ?: "更新播放列表失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> = runCatching {
        val resp = playlistApi.deletePlaylist(playlistId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "删除播放列表失败")
    }

    suspend fun addVideos(playlistId: String, videoIds: List<String>): Result<Unit> = runCatching {
        val resp = playlistApi.addPlaylistItem(AddPlaylistItemRequest(playlistId, videoIds))
        if (!resp.isSuccess) throw Exception(resp.message ?: "添加视频失败")
    }

    suspend fun removeVideo(playlistId: String, videoId: String): Result<Unit> = runCatching {
        val resp = playlistApi.deletePlaylistItem(DeletePlaylistItemRequest(playlistId, videoIdList = listOf(videoId)))
        if (!resp.isSuccess) throw Exception(resp.message ?: "移除视频失败")
    }

    suspend fun moveVideo(playlistId: String, videoId: String, toIndex: Int): Result<Unit> = runCatching {
        val resp = playlistApi.movePlaylistItem(MovePlaylistItemRequest(playlistId, videoId, toIndex = toIndex))
        if (!resp.isSuccess) throw Exception(resp.message ?: "移动视频失败")
    }
}
```

`UploadRepository.kt`:

```kotlin
package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.VideoApi
import com.github.makewheels.video2022.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadRepository @Inject constructor(
    private val videoApi: VideoApi
) {
    suspend fun createVideo(filename: String, size: Long): Result<CreateVideoResponse> = runCatching {
        val request = CreateVideoRequest(
            videoType = "USER_UPLOAD",
            rawFilename = filename,
            size = size
        )
        val resp = videoApi.createVideo(request)
        if (!resp.isSuccess) throw Exception(resp.message ?: "创建视频失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun createYoutubeVideo(youtubeUrl: String): Result<CreateVideoResponse> = runCatching {
        val request = CreateVideoRequest(
            videoType = "YOUTUBE",
            youtubeUrl = youtubeUrl
        )
        val resp = videoApi.createVideo(request)
        if (!resp.isSuccess) throw Exception(resp.message ?: "创建 YouTube 视频失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun getUploadCredentials(fileId: String): Result<UploadCredentials> = runCatching {
        val resp = videoApi.getUploadCredentials(fileId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取上传凭证失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun notifyUploadFinish(fileId: String, videoId: String): Result<Unit> = runCatching {
        videoApi.uploadFinish(fileId)
        videoApi.rawFileUploadFinish(videoId)
    }
}
```

**Step 2: Commit**

```bash
git add android/app/src/main/java/com/github/makewheels/video2022/data/repository/
git commit -m "feat(android): add repository layer wrapping all API calls with Result<T>"
```

---

## Phase 3: 主题 + 导航 + 登录

### Task 6: Material 3 主题

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/theme/Theme.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/theme/Color.kt`

**Step 1: 创建主题**

`Color.kt`:

```kotlin
package com.github.makewheels.video2022.ui.theme

import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF1976D2)
val OnPrimary = Color.White
val PrimaryContainer = Color(0xFFBBDEFB)
val Secondary = Color(0xFF455A64)
val Background = Color(0xFFF5F5F5)
val Surface = Color.White
val Error = Color(0xFFD32F2F)
```

`Theme.kt`:

```kotlin
package com.github.makewheels.video2022.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    secondary = Secondary,
    background = Background,
    surface = Surface,
    error = Error
)

@Composable
fun VideoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
```

**Step 2: Commit**

```bash
git add android/app/src/main/java/com/github/makewheels/video2022/ui/theme/
git commit -m "feat(android): add Material 3 theme with color scheme"
```

---

### Task 7: 导航框架

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/navigation/AppNavGraph.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/navigation/Screen.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/components/BottomNavBar.kt`
- Modify: `android/app/src/main/java/com/github/makewheels/video2022/MainActivity.kt`

**Step 1: 定义路由常量**

`Screen.kt`:

```kotlin
package com.github.makewheels.video2022.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Playlist : Screen("playlist")
    data object Upload : Screen("upload")
    data object MyVideos : Screen("myvideos")
    data object Settings : Screen("settings")
    data object Watch : Screen("watch/{watchId}") {
        fun createRoute(watchId: String) = "watch/$watchId"
    }
    data object Edit : Screen("edit/{videoId}") {
        fun createRoute(videoId: String) = "edit/$videoId"
    }
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
    data object YouTube : Screen("youtube")
}
```

**Step 2: 创建 BottomNavBar**

`BottomNavBar.kt`:

```kotlin
package com.github.makewheels.video2022.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.github.makewheels.video2022.navigation.Screen

data class BottomNavItem(val screen: Screen, val label: String, val icon: ImageVector)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "首页", Icons.Filled.Home),
    BottomNavItem(Screen.Playlist, "播放列表", Icons.Filled.PlaylistPlay),
    BottomNavItem(Screen.Upload, "上传", Icons.Filled.AddCircle),
    BottomNavItem(Screen.MyVideos, "我的", Icons.Filled.VideoLibrary),
    BottomNavItem(Screen.Settings, "设置", Icons.Filled.Settings),
)

@Composable
fun BottomNavBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.screen.route,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
```

**Step 3: 创建 NavGraph（先用占位 Screen）**

`AppNavGraph.kt`:

```kotlin
package com.github.makewheels.video2022.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.makewheels.video2022.ui.components.BottomNavBar

@Composable
fun AppNavGraph(navController: NavHostController, isLoggedIn: Boolean) {
    val startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route

    Scaffold(
        bottomBar = {
            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
            val showBottomBar = currentRoute in bottomNavItems.map { it.screen.route }
            if (showBottomBar) {
                BottomNavBar(navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                // 后续 Task 实现 LoginScreen
                PlaceholderScreen("登录")
            }
            composable(Screen.Home.route) {
                PlaceholderScreen("首页")
            }
            composable(Screen.Playlist.route) {
                PlaceholderScreen("播放列表")
            }
            composable(Screen.Upload.route) {
                PlaceholderScreen("上传")
            }
            composable(Screen.MyVideos.route) {
                PlaceholderScreen("我的视频")
            }
            composable(Screen.Settings.route) {
                PlaceholderScreen("设置")
            }
            composable(
                Screen.Watch.route,
                arguments = listOf(navArgument("watchId") { type = NavType.StringType })
            ) { entry ->
                val watchId = entry.arguments?.getString("watchId") ?: return@composable
                PlaceholderScreen("播放: $watchId")
            }
            composable(
                Screen.Edit.route,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType })
            ) { entry ->
                val videoId = entry.arguments?.getString("videoId") ?: return@composable
                PlaceholderScreen("编辑: $videoId")
            }
            composable(
                Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
            ) { entry ->
                val playlistId = entry.arguments?.getString("playlistId") ?: return@composable
                PlaceholderScreen("播放列表详情: $playlistId")
            }
            composable(Screen.YouTube.route) {
                PlaceholderScreen("YouTube 下载")
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name, style = MaterialTheme.typography.headlineMedium)
    }
}

// 需要导入
import androidx.navigation.compose.currentBackStackEntryAsState
```

**Step 4: 更新 MainActivity**

```kotlin
package com.github.makewheels.video2022

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.github.makewheels.video2022.navigation.AppNavGraph
import com.github.makewheels.video2022.ui.theme.VideoTheme
import com.github.makewheels.video2022.util.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoTheme {
                val navController = rememberNavController()
                AppNavGraph(
                    navController = navController,
                    isLoggedIn = tokenManager.isLoggedIn()
                )
            }
        }
    }
}
```

**Step 5: 验证编译并 Commit**

Run: `cd android && ./gradlew assembleDebug`

```bash
git add android/app/src/main/java/com/github/makewheels/video2022/navigation/
git add android/app/src/main/java/com/github/makewheels/video2022/ui/components/
git add android/app/src/main/java/com/github/makewheels/video2022/MainActivity.kt
git commit -m "feat(android): add Navigation Compose with BottomNavBar and all routes"
```

---

### Task 8: 登录页面

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/login/LoginScreen.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/login/LoginViewModel.kt`
- Modify: `android/app/src/main/java/com/github/makewheels/video2022/navigation/AppNavGraph.kt`（替换占位）

**Step 1: 创建 LoginViewModel**

```kotlin
package com.github.makewheels.video2022.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val phone: String = "",
    val code: String = "",
    val isCodeSent: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun updatePhone(phone: String) {
        _uiState.value = _uiState.value.copy(phone = phone, errorMessage = null)
    }

    fun updateCode(code: String) {
        _uiState.value = _uiState.value.copy(code = code, errorMessage = null)
    }

    fun requestVerificationCode() {
        val phone = _uiState.value.phone.trim()
        if (phone.length != 11) {
            _uiState.value = _uiState.value.copy(errorMessage = "请输入11位手机号")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            userRepository.requestVerificationCode(phone)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isCodeSent = true)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message)
                }
        }
    }

    fun submitVerificationCode() {
        val state = _uiState.value
        if (state.code.isBlank()) {
            _uiState.value = state.copy(errorMessage = "请输入验证码")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            userRepository.submitVerificationCode(state.phone.trim(), state.code.trim())
                .onSuccess {
                    userRepository.initClientAndSession()
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoginSuccess = true)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message)
                }
        }
    }
}
```

**Step 2: 创建 LoginScreen**

```kotlin
package com.github.makewheels.video2022.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoginSuccess) {
        if (uiState.isLoginSuccess) onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("视频平台", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(48.dp))

        OutlinedTextField(
            value = uiState.phone,
            onValueChange = viewModel::updatePhone,
            label = { Text("手机号") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        if (uiState.isCodeSent) {
            OutlinedTextField(
                value = uiState.code,
                onValueChange = viewModel::updateCode,
                label = { Text("验证码") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }

        uiState.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (uiState.isCodeSent) viewModel.submitVerificationCode()
                else viewModel.requestVerificationCode()
            },
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (uiState.isCodeSent) "登录" else "获取验证码")
            }
        }
    }
}
```

**Step 3: 在 NavGraph 中替换 Login 占位，并处理登录成功导航**

将 `composable(Screen.Login.route)` 中的 `PlaceholderScreen("登录")` 替换为：

```kotlin
composable(Screen.Login.route) {
    LoginScreen(onLoginSuccess = {
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Login.route) { inclusive = true }
        }
    })
}
```

**Step 4: Commit**

```bash
git add android/app/src/main/java/com/github/makewheels/video2022/ui/login/
git commit -m "feat(android): add login screen with phone + verification code flow"
```

---

## Phase 4: 首页 + 视频卡片

### Task 9: 首页视频列表

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/components/VideoCard.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/home/HomeScreen.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/home/HomeViewModel.kt`

**Step 1: 实现 VideoCard 公共组件（视频缩略图卡片）**

使用 Coil 加载封面图，显示标题、观看次数、时长。点击回调 onVideoClick(watchId)。

**Step 2: 实现 HomeViewModel**

- 加载 `getMyVideoList(skip=0, limit=20)`
- 支持下拉刷新（`isRefreshing` state）
- 支持分页加载更多（`loadMore()` 方法）

**Step 3: 实现 HomeScreen**

- `LazyColumn` 渲染 VideoCard 列表
- 下拉刷新用 `pullToRefresh`
- 滚动到底部自动加载更多
- 点击 VideoCard → 导航到 `Screen.Watch.createRoute(watchId)`

**Step 4: 在 NavGraph 中替换 Home 占位**

**Step 5: Commit**

```bash
git commit -m "feat(android): add home screen with video list, pagination, pull-to-refresh"
```

---

## Phase 5: 视频播放

### Task 10: ExoPlayer 播放页面

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/watch/WatchScreen.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/watch/WatchViewModel.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/watch/VideoPlayerComposable.kt`

**Step 1: 实现 WatchViewModel**

- `loadWatchInfo(watchId)` → 调用 `watchRepository.getWatchInfo()`
- 获取 `multivariantPlaylistUrl` 用于 ExoPlayer
- 获取 `progressInMillis` 用于断点续播
- 定时发送心跳 `sendHeartbeat()` 每 10 秒一次
- 加载点赞状态、评论数

**Step 2: 实现 VideoPlayerComposable**

- 用 `AndroidView` 包装 `PlayerView`
- 创建 `ExoPlayer` + `HlsMediaSource`
- 支持全屏切换（横竖屏）
- 支持手势调节亮度/音量（`GestureDetector`）
- 断点续播：`player.seekTo(progressInMillis)`

**Step 3: 实现 WatchScreen**

- 顶部：VideoPlayerComposable
- 下方：视频标题、描述、观看次数
- 点赞/踩按钮（LikeButtons 组件）
- 评论区入口（点击展开 BottomSheet）

**Step 4: 在 NavGraph 中替换 Watch 占位**

**Step 5: Commit**

```bash
git commit -m "feat(android): add video playback with ExoPlayer HLS, heartbeat, resume"
```

---

## Phase 6: 评论 + 点赞

### Task 11: 评论 BottomSheet

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/components/CommentSheet.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/components/CommentItem.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/components/LikeButtons.kt`

**Step 1: 实现 CommentItem**

显示：用户手机号（已脱敏）、评论内容、时间、点赞数、回复数。

**Step 2: 实现 CommentSheet**

- `ModalBottomSheet` 包裹评论列表
- `LazyColumn` 渲染 CommentItem
- 底部输入框 + 发送按钮
- 支持分页加载
- 支持回复（点击评论切换到回复模式）

**Step 3: 实现 LikeButtons**

- 赞/踩图标按钮 + 计数
- 调用 `videoRepository.like()` / `videoRepository.dislike()`
- 乐观更新 UI

**Step 4: 集成到 WatchScreen**

**Step 5: Commit**

```bash
git commit -m "feat(android): add comment BottomSheet and like/dislike buttons"
```

---

## Phase 7: 视频上传

### Task 12: 上传页面 + WorkManager 后台上传

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/upload/UploadScreen.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/upload/UploadViewModel.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/service/UploadWorker.kt`

**Step 1: 实现 UploadViewModel**

- 选择视频文件（`ActivityResultContract` for `MediaStore.Video`）
- 拍摄视频（Camera intent）
- 填写标题/描述
- 创建视频 → 获取凭证 → 启动 WorkManager UploadWorker
- 监听 WorkManager 进度

**Step 2: 实现 UploadWorker（继承 CoroutineWorker）**

- 接收参数：fileUri, fileId, videoId, bucket, endpoint, key, STS credentials
- 使用阿里云 OSS Android SDK 执行上传
- 上报进度到 WorkManager `setProgress()`
- 上传完成后调用 `uploadFinish` + `rawFileUploadFinish`
- 发送通知栏进度通知

**Step 3: 实现 UploadScreen**

- 两个入口按钮：「从相册选择」「拍摄视频」
- 选择后预览缩略图
- 标题/描述输入
- 上传按钮 → 提交后显示进度
- 通知栏展示上传进度

**Step 4: 在 NavGraph 中替换 Upload 占位**

**Step 5: Commit**

```bash
git commit -m "feat(android): add upload screen with WorkManager background upload and OSS SDK"
```

---

## Phase 8: 我的视频 + 编辑

### Task 13: 我的视频列表

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/myvideos/MyVideosScreen.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/myvideos/MyVideosViewModel.kt`

**Step 1: 实现 MyVideosViewModel**

- 加载 `getMyVideoList` 带搜索和分页
- 支持搜索 keyword
- 支持删除视频（确认弹窗）

**Step 2: 实现 MyVideosScreen**

- 搜索栏 + VideoCard 列表
- 每个卡片右下角菜单：编辑、删除
- 点击卡片 → Watch 页面
- 点击编辑 → Edit 页面
- 删除需确认弹窗

**Step 3: Commit**

```bash
git commit -m "feat(android): add my videos screen with search and delete"
```

---

### Task 14: 视频编辑页

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/edit/EditScreen.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/edit/EditViewModel.kt`

**Step 1: 实现 EditViewModel**

- 加载视频详情 `getVideoDetail(videoId)`
- 更新标题/描述/可见性 `updateVideoInfo()`
- 更新观看设置 `updateWatchSettings()`

**Step 2: 实现 EditScreen**

- 标题输入框
- 描述输入框
- 可见性下拉选择（PUBLIC/UNLISTED/PRIVATE）
- 显示上传时间/观看次数开关
- 保存按钮
- 底部红色删除按钮

**Step 3: Commit**

```bash
git commit -m "feat(android): add video edit screen with info update and visibility control"
```

---

## Phase 9: 播放列表

### Task 15: 播放列表页面

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/playlist/PlaylistScreen.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/playlist/PlaylistViewModel.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/playlist/PlaylistDetailScreen.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/playlist/PlaylistDetailViewModel.kt`

**Step 1: 实现 PlaylistViewModel + PlaylistScreen**

- 列表展示所有播放列表（`getMyPlaylistByPage`）
- 新建播放列表 Dialog
- 点击进入详情

**Step 2: 实现 PlaylistDetailViewModel + PlaylistDetailScreen**

- 加载播放列表中的视频 `getPlayItemListDetail()`
- 长按拖拽排序（`movePlaylistItem`）
- 滑动删除视频（`deletePlaylistItem`）
- 点击视频进入播放

**Step 3: Commit**

```bash
git commit -m "feat(android): add playlist management with drag-sort and detail view"
```

---

## Phase 10: YouTube 下载 + 设置

### Task 16: YouTube 下载页

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/youtube/YouTubeScreen.kt`
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/youtube/YouTubeViewModel.kt`

**Step 1: 实现 YouTubeViewModel + YouTubeScreen**

- URL 输入框
- 解析 YouTube 视频 ID
- 获取视频信息预览
- 「下载」按钮 → `createYoutubeVideo()`
- 显示下载状态

**Step 2: Commit**

```bash
git commit -m "feat(android): add YouTube download screen"
```

---

### Task 17: 设置页

**Files:**
- Create: `android/app/src/main/java/com/github/makewheels/video2022/ui/settings/SettingsScreen.kt`

**Step 1: 实现 SettingsScreen**

- YouTube 下载入口
- 当前登录手机号
- 退出登录按钮（清除 token，跳转 Login）
- 版本信息

**Step 2: Commit**

```bash
git commit -m "feat(android): add settings screen with logout and YouTube entry"
```

---

## Phase 11: 文档更新

### Task 18: 更新项目文档

**Files:**
- Modify: `README.md` — 新增 Android 部分
- Modify: `docs/1-关键设计.md` — 新增 Android 客户端架构段落
- Modify: `docs/api/7-App接口.md` — 补充 Android 客户端信息

**Step 1: 更新 README.md**

在现有内容后追加 Android 构建/运行说明：

```markdown
## Android 客户端

### 技术栈
Kotlin + Jetpack Compose + Material 3 + Media3 ExoPlayer + Hilt + Retrofit

### 构建
```bash
cd android
./gradlew assembleDebug
```

### 安装到模拟器
```bash
./gradlew installDebug
```

### 配置
- Debug 模式自动连接 `http://10.0.2.2:5022`（模拟器映射本机后端）
- Release 模式连接 `https://oneclick.video`
```

**Step 2: 更新 docs/1-关键设计.md**

追加 Android 客户端架构段落，包含：架构分层（MVVM）、导航结构、网络层设计、视频播放方案、上传方案。

**Step 3: 更新 docs/api/7-App接口.md**

补充 Android 客户端版本检查、registerChannel="ANDROID" 等。

**Step 4: Commit**

```bash
git add README.md docs/
git commit -m "docs: update project docs with Android client architecture and build instructions"
```

---

## Phase 12: 构建验证

### Task 19: 完整构建验证

**Step 1: 编译 Debug APK**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: 运行 Lint 检查**

Run: `cd android && ./gradlew lintDebug`
Expected: 无严重 error

**Step 3: 运行单元测试**

Run: `cd android && ./gradlew testDebugUnitTest`
Expected: 所有测试通过

**Step 4: Commit & Tag**

```bash
git tag -a android-v1.0.0 -m "Android client v1.0.0 initial release"
```
