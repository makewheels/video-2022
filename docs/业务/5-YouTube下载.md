# YouTube 下载

> 文档地图：[README](../../README.md) > [关键设计](../1-关键设计.md) > 本文档

本文档描述 YouTube 视频搬运的完整业务流程。系统通过部署在**阿里云香港区域的云函数**（独立 Spring Boot 服务）来绕过网络限制，使用 yt-dlp 下载 YouTube 视频和封面，再上传至国内阿里云 OSS。

---

## 1. 端到端流程

```mermaid
sequenceDiagram
    participant User as 用户
    participant Video as video 服务 (国内)
    participant CF as youtube 云函数 (香港)
    participant YT as YouTube
    participant OSS as 阿里云 OSS

    User->>Video: POST /video/create<br>{videoType:"YOUTUBE", youtubeUrl}
    Video->>Video: VideoCreateService.create()<br>创建 Video + File 对象 (MongoDB)
    Video->>Video: 解析 youtubeUrl → youtubeVideoId
    Video-->>User: 返回 watchId, watchUrl, shortUrl

    Note over Video: 新线程 handleCreateYoutube()

    Video->>CF: GET /youtube/getFileExtension<br>?youtubeVideoId=xxx
    CF->>CF: yt-dlp --get-filename -o %(ext)s
    CF-->>Video: 返回文件后缀 (如 webm)

    Video->>CF: POST /youtube/transferVideo (异步)<br>Header: X-Fc-Invocation-Type: Async
    CF->>YT: yt-dlp -o {filepath} {videoId}
    YT-->>CF: 下载视频文件

    CF->>Video: GET /file/getUploadCredentials<br>获取 STS 上传凭证
    Video-->>CF: 返回 bucket, endpoint, accessKeyId, secretKey, sessionToken, key
    CF->>OSS: 分片上传视频文件 (1MB/片)
    CF->>Video: GET /file/uploadFinish<br>回调：文件上传完成
    CF->>Video: GET /video/rawFileUploadFinish<br>回调：业务源文件上传完成

    Note over Video: rawFileUploadFinish 触发转码流程

    Video->>Video: RawFileService.onRawFileUploadFinish()<br>→ 发起转码 + 跳过截帧

    Note over Video: 并行：获取元数据 + 搬运封面

    Video->>CF: GET /youtube/getVideoInfo<br>?youtubeVideoId=xxx
    CF->>YT: YouTube Data API v3<br>videos.list(snippet,contentDetails,statistics)
    YT-->>CF: 返回视频元数据
    CF-->>Video: 返回 videoInfo JSON

    Video->>Video: 提取 title, description, publishTime<br>保存到 Video 实体

    Note over Video: 新线程 coverLauncher.createCover()

    Video->>Video: CoverLauncher.handleYoutubeCover()<br>从 videoInfo 提取 thumbnail URL
    Video->>CF: POST /youtube/transferFile (异步)<br>{downloadUrl: thumbnail URL}
    CF->>YT: HttpUtil.downloadFile(thumbnailUrl)
    YT-->>CF: 下载缩略图
    CF->>OSS: 分片上传封面
    CF->>Video: GET /cover/youtubeUploadFinishCallback<br>?coverId=xxx
    Video->>Video: CoverCallbackService<br>设置 cover.status = READY
```

---

## 2. 云函数部署

### 2.1 基础镜像 (`Dockerfile-youtube-base`)

基于 `centos:7`，包含以下组件：

| 组件 | 版本 | 安装方式 |
|------|------|----------|
| Java | OpenJDK 11 | `yum install java-11-openjdk` |
| OpenSSL | 1.1.1n | 源码编译到 `/usr/local/openssl` |
| Python | 3.10.13 | 源码编译，依赖自定义 OpenSSL<br>软链接 `/usr/local/bin/python3.10` → `/usr/bin/python3` |
| FFmpeg | yum 版 | 通过 rpmfusion-free 仓库安装 |
| yt-dlp | pip 最新版 | `pip3.10 install yt-dlp` |

> Python 必须使用自编译的 OpenSSL 1.1.1n，否则 yt-dlp 的 HTTPS 请求会失败。编译参数：`./configure -C --with-openssl=/usr/local/openssl --with-openssl-rpath=auto`

### 2.2 服务镜像 (`Dockerfile-youtube-service`)

```dockerfile
FROM registry.cn-hongkong.aliyuncs.com/b4/video-2022-youtube-base-image:latest
COPY youtube-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","-Dfile.enconding=utf-8","/app.jar"]
```

- **镜像仓库**：`registry.cn-hongkong.aliyuncs.com/b4/video-2022-youtube-base-image`（阿里云香港 ACR）
- **服务端口**：`5037`（`application.properties` 配置）
- **云函数地址**：`https://video-youtube-video-hongkong-iabsngmqbv.cn-hongkong.fcapp.run`
- **健康检查端点**：`GET /healthCheck` → 返回 `healthCheck2-{timestamp}`

### 2.3 构建流程

```bash
# 1. Maven 打包 youtube 模块
mvn package -P youtube

# 2. 构建基础镜像（仅首次或依赖变更时）
docker build -f Dockerfile-youtube-base -t registry.cn-hongkong.aliyuncs.com/b4/video-2022-youtube-base-image:latest .

# 3. 构建服务镜像
docker build -f Dockerfile-youtube-service -t <service-image-tag> .
```

---

## 3. YouTube 元数据获取

### 3.1 获取文件后缀

**海外云函数端** (`youtube` 模块 `YoutubeService.getFileExtension`)：

```bash
yt-dlp --get-filename -o %(ext)s --restrict-filenames {youtubeVideoId}
```

返回值如 `webm`。若命令结果包含 `\n` 会被去除。

**国内服务端** (`video` 模块 `YoutubeService.getFileExtension`)：

当前硬编码返回 `"webm"`，原 HTTP 调用已注释掉。实际文件后缀在 `handleCreateYoutube()` 中通过调海外接口获取后会更新。

### 3.2 获取视频信息

**海外云函数端**使用 Google YouTube Data API v3：

```java
YouTube.Videos.List request = youTube.videos()
    .list(Lists.newArrayList("snippet", "contentDetails", "statistics"))
    .setId(idList)
    .setKey("AIzaSyA4x7iV1uzQqWnoiADcHikWshx01tvZEtg");
```

请求的 part：
- **snippet**：title, description, publishedAt, thumbnails
- **contentDetails**：时长、清晰度等
- **statistics**：播放量、点赞数等

**国内服务端**调用海外接口中转：`GET {youtubeServiceUrl}/youtube/getVideoInfo?youtubeVideoId=xxx`

### 3.3 元数据使用

在 `VideoCreateService.handleCreateYoutube()` 中：

```java
// 标题和描述 → Video 实体
video.setTitle(snippet.getString("title"));
video.setDescription(snippet.getString("description"));

// 发布时间 → YouTube 子对象
JSONObject publishedAt = snippet.getJSONObject("publishedAt");
int timeZoneShift = publishedAt.getInteger("timeZoneShift");
long value = publishedAt.getLong("value");
// 时区转换：UTC+timeZoneShift → 系统默认时区
ZoneId zoneId = ZoneId.of("UTC+" + timeZoneShift);
video.getYouTube().setPublishTime(publishTime);
```

---

## 4. 封面迁移流程

### 4.1 触发时机

封面创建在 `handleCreateYoutube()` 中以**新线程**发起，与视频下载**并行执行**，无需等待源视频上传完成：

```java
// VideoCreateService.handleCreateYoutube() 第150行
new Thread(() -> coverLauncher.createCover(user, video)).start();
```

### 4.2 封面处理逻辑 (`CoverLauncher.handleYoutubeCover`)

1. 设置 `cover.provider = CoverProvider.YOUTUBE`（值为 `"YOUTUBE_COVER"`）
2. 从 `videoInfo.snippet.thumbnails.standard.url` 提取缩略图下载地址
3. 从下载地址中解析文件后缀（如 `jpg`）
4. 通过 `OssPathUtil.getCoverKey()` 生成 OSS key
5. 调用 `youtubeService.transferFile()` 向海外服务器提交封面搬运任务
6. 回调地址：`GET /cover/youtubeUploadFinishCallback?coverId={coverId}&token={token}`

### 4.3 封面回调 (`CoverCallbackService.youtubeUploadFinishCallback`)

1. 根据 `coverId` 查询 Cover 实体
2. 对于 `YOUTUBE_COVER` 类型，**跳过** OSS 文件存在性校验（仅 `ALIYUN_CLOUD_FUNCTION` 和 `ALIYUN_MPS` 才校验）
3. 设置 `cover.status = CoverStatus.READY`，记录 `finishTime`

### 4.4 跳过截帧

在 `RawFileService.launchTranscode()` 中（第101行），YouTube 视频跳过普通截帧：

```java
// 意图：YouTube 视频已有封面，不需要截帧
if (!VideoType.YOUTUBE.equals(newVideo.getStatus())) {
    coverLauncher.createCover(user, newVideo);
}
```

> ⚠️ **已知 Bug**：此处比较的是 `newVideo.getStatus()`（转码状态），应为 `newVideo.getVideoType()`。由于 `status` 值（如 `TRANSCODING`）永远不等于 `"YOUTUBE"`，条件恒为 `true`，导致 YouTube 视频也会执行截帧。但因封面已创建，`coverId` 已设置，实际影响取决于是否有幂等保护。

---

## 5. 与普通上传的差异

| 维度 | 普通上传 (`USER_UPLOAD`) | YouTube 搬运 (`YOUTUBE`) |
|------|---------------------------|--------------------------|
| **videoType** | `"USER_UPLOAD"` | `"YOUTUBE"` |
| **文件来源** | 用户直传 OSS | 海外云函数 yt-dlp 下载后上传 |
| **文件后缀** | 由 `rawFilename` 解析 | 通过 yt-dlp 获取（默认 `webm`） |
| **元数据** | 用户手动填写 | 自动从 YouTube API 获取 title, description |
| **封面来源** | 阿里云 MPS 截帧 (`ALIYUN_MPS_SNAPSHOT`) | YouTube 缩略图搬运 (`YOUTUBE_COVER`) |
| **封面时机** | 源文件上传完成后截帧 | 创建视频时立即并行搬运 |
| **调用方式** | 同步上传 | 异步调用云函数 (`X-Fc-Invocation-Type: Async`) |
| **YouTube 子对象** | 空对象（构造函数默认初始化） | 填充 videoId, url, videoInfo, publishTime |
| **publishTime** | 无 | 从 YouTube snippet.publishedAt 转换 |
| **上传凭证** | 客户端直接获取 | 海外云函数回调国内接口获取 STS 凭证 |

### 5.1 创建流程差异

**普通上传**：`create()` → 返回上传凭证 → 用户上传 → `rawFileUploadFinish` → 转码 + 截帧

**YouTube 搬运**：`create()` → 新线程 `handleCreateYoutube()` → 获取后缀 → 提交下载任务(异步) → 获取元数据 → 搬运封面(新线程) → 回调触发转码

---

## 6. 数据模型

### 6.1 YouTube 实体 (`video.bean.entity.YouTube`)

```java
@Data
public class YouTube {
    @Indexed
    private String videoId;        // YouTube 视频 ID（如 "dQw4w9WgXcQ"）
    private String url;            // 原始 YouTube URL
    private JSONObject videoInfo;  // YouTube Data API 完整返回（snippet + contentDetails + statistics）
    private Date publishTime;      // YouTube 视频发布时间（已转换为系统时区）
}
```

作为 `Video` 实体的嵌套对象（`private YouTube youTube`），在 `Video` 构造函数中默认初始化为空对象。

### 6.2 Video 实体中的相关字段

```java
@Document
public class Video {
    private String videoType;      // "USER_UPLOAD" | "YOUTUBE"（VideoType 常量）
    private YouTube youTube;       // YouTube 元数据子对象
    private String title;          // YouTube 搬运时自动填充
    private String description;    // YouTube 搬运时自动填充
    // ...
}
```

### 6.3 VideoVO 展示字段

```java
public class VideoVO {
    private Date youtubePublishTime;       // YouTube 发布时间
    private String youtubePublishTimeString; // 格式化后的发布时间字符串
}
```

### 6.4 CreateVideoDTO

```java
public class CreateVideoDTO {
    private String videoType;    // "YOUTUBE"
    private String youtubeUrl;   // 前端传入的 YouTube URL
}
```

### 6.5 Cover 相关常量

| 常量 | 值 | 说明 |
|------|----|------|
| `CoverProvider.YOUTUBE` | `"YOUTUBE_COVER"` | 封面来源为 YouTube 缩略图 |
| `CoverStatus.CREATED` | `"CREATED"` | 初始状态 |
| `CoverStatus.READY` | `"READY"` | 封面就绪 |

### 6.6 搬运任务 JSON 结构

**transferVideo 请求体**：

```json
{
  "missionId": "nanoId",
  "youtubeVideoId": "dQw4w9WgXcQ",
  "key": "oss/path/to/file.webm",
  "provider": "ALIYUN_OSS",
  "fileId": "file-xxx",
  "watchId": "abc123",
  "videoId": "video-xxx",
  "getUploadCredentialsUrl": "https://host/file/getUploadCredentials?fileId=...&token=...",
  "fileUploadFinishCallbackUrl": "https://host/file/uploadFinish?fileId=...&token=...",
  "businessUploadFinishCallbackUrl": "https://host/video/rawFileUploadFinish?videoId=...&token=..."
}
```

**transferFile 请求体**（封面搬运）：

```json
{
  "missionId": "nanoId",
  "key": "oss/path/to/cover.jpg",
  "provider": "ALIYUN_OSS",
  "fileId": "file-xxx",
  "downloadUrl": "https://i.ytimg.com/vi/xxx/sddefault.jpg",
  "getUploadCredentialsUrl": "https://host/file/getUploadCredentials?...",
  "fileUploadFinishCallbackUrl": "https://host/file/uploadFinish?...",
  "businessUploadFinishCallbackUrl": "https://host/cover/youtubeUploadFinishCallback?coverId=...&token=..."
}
```

---

## 7. 边界情况

### 7.1 文件后缀不匹配

**问题**：yt-dlp `--get-filename` 报告后缀为 `webm`，但实际下载的文件可能是 `mkv`。

**现有处理**（`youtube` 模块 `YoutubeService.downloadYoutubeVideo`，第123–130行）：

```java
if (file.exists()) {
    // 正常上传
} else {
    // 预期文件不存在时，遍历目录取第一个文件
    List<File> loopFiles = FileUtil.loopFiles(file.getParentFile());
    Assert.notEmpty(loopFiles, "file NOT exist = " + file.getAbsolutePath());
    file = loopFiles.get(0);
}
```

> 注意：此 workaround 会导致上传到 OSS 的 key 后缀与实际文件格式不一致。

### 7.2 yt-dlp 下载命令执行两次

在 `downloadYoutubeVideo` 方法中，`executeAndPrint(downloadCmd)` 被调用了**两次**（第103–104行），疑似为重试机制或遗留代码。

### 7.3 YouTube URL 解析

`YoutubeService.getYoutubeVideoIdByUrl()` 支持两种格式：

- `https://www.youtube.com/watch?v=XRWFWB2BP_s&list=PLAhTBeRe8Ih...` → 解析 query 参数 `v`
- `https://youtu.be/tci5eYHwjMc?t=72` → 从 path 截取

不支持：`youtube.com/embed/xxx`、`youtube.com/shorts/xxx` 等格式。

### 7.4 网络与地域限制

- 云函数部署在**阿里云香港区域**以访问 YouTube
- 使用 Google YouTube Data API v3 获取元数据，需要有效的 API Key
- 视频异步调用 (`X-Fc-Invocation-Type: Async`)，无超时限制在调用端控制
- 封面搬运也通过异步调用发起

### 7.5 上传机制

海外云函数的 `AliyunOssService` 使用 OSS **分片上传**：

- 分片大小：**1 MB**（`1024 * 1024L` 字节）
- 最小分片（非末尾）：100 KB
- 分片号范围：1–10000
- 使用 STS 临时凭证（从国内服务器回调获取）
- 上传完成后删除本地临时文件

### 7.6 已知 Bug

| 位置 | 描述 |
|------|------|
| `RawFileService` 第101行 | `VideoType.YOUTUBE.equals(newVideo.getStatus())` 应为 `.getVideoType()`，导致 YouTube 视频可能重复创建封面 |
| `VideoService.getVideoList` 第148行 | 同样的 `getStatus()` vs `getVideoType()` 混用问题 |
| `Dockerfile-youtube-service` 第3行 | `-Dfile.enconding=utf-8` 拼写错误，应为 `-Dfile.encoding=utf-8` |

---

## 源码位置

| 类 | 路径 |
|----|------|
| YoutubeService (video 模块) | `video/src/main/java/com/github/makewheels/video2022/video/service/YoutubeService.java` |
| VideoCreateService | `video/src/main/java/com/github/makewheels/video2022/video/service/VideoCreateService.java` |
| CoverLauncher | `video/src/main/java/com/github/makewheels/video2022/cover/CoverLauncher.java` |
| CoverCallbackService | `video/src/main/java/com/github/makewheels/video2022/cover/CoverCallbackService.java` |
