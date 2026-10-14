# Controller 声明接口盘点

状态：静态提取；代码基线 `8e4283e5`。不代表接口已在 FC 验证。

共 40 个有映射的 Controller、139 处方法级映射声明，展开路径数组后 148 条路径记录。RequestMapping 未限定方法的条目保留 ANY；不把类级前缀计为接口。

只覆盖两个 Java 模块源码中的映射声明；`/`、网关 `/w?v=`、`/console/*`、静态资源以及框架生成的 OpenAPI/Swagger 等入口需要结合路由/配置另行验收。Controller 的依赖业务和迁移判断见 [audit.md](audit.md)。

S05 按业务补契约：已核对 [公共视频列表](public-video-list-contract.md) 的源码及各端调用，动态边界和对照工具仍待验。

下表不列参数、鉴权和响应类型，不能作为完整 API 契约。实施时必须补请求/响应黄金样本和各端调用覆盖；多种 Content-Type 接受范围不能仅凭路径列表判断。

## CommentController

源码：[server/video/src/main/java/com/github/makewheels/video2022/comment/CommentController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/comment/CommentController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| POST | `/comment/add` | 19 |
| GET | `/comment/getByVideoId` | 34 |
| GET | `/comment/getReplies` | 44 |
| GET | `/comment/delete` | 52 |
| GET | `/comment/like` | 57 |
| GET | `/comment/getCount` | 62 |

## CoverController

源码：[server/video/src/main/java/com/github/makewheels/video2022/cover/CoverController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/cover/CoverController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/cover/youtubeUploadFinishCallback` | 36 |
| POST | `/cover/local/createCover` | 51 |
| POST | `/cover/local/finishCover` | 59 |

## DeveloperAppController

源码：[server/video/src/main/java/com/github/makewheels/video2022/developer/controller/DeveloperAppController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/developer/controller/DeveloperAppController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| POST | `/developer/app/create` | 30 |
| GET | `/developer/app/list` | 38 |
| PUT | `/developer/app/{appId}/update` | 46 |
| DELETE | `/developer/app/{appId}/delete` | 56 |
| POST | `/developer/app/{appId}/regenerateSecret` | 64 |
| POST | `/developer/app/{appId}/testWebhook` | 72 |

## DeveloperTokenController

源码：[server/video/src/main/java/com/github/makewheels/video2022/developer/controller/DeveloperTokenController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/developer/controller/DeveloperTokenController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| POST | `/developer/token/create` | 24 |
| POST | `/developer/token/verify` | 31 |

## AppController

源码：[server/video/src/main/java/com/github/makewheels/video2022/etc/app/AppController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/etc/app/AppController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/app/checkUpdate` | 13 |
| POST | `/app/publishVersion` | 20 |

## StatisticsController

源码：[server/video/src/main/java/com/github/makewheels/video2022/etc/statistics/StatisticsController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/etc/statistics/StatisticsController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/statistics/getTrafficConsume` | 22 |
| GET | `/statistics/aggregateTrafficData` | 30 |

## FileController

源码：[server/video/src/main/java/com/github/makewheels/video2022/file/FileController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/file/FileController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/file/getOssObjectMetadataByKey` | 26 |
| GET | `/file/getUploadCredentials` | 35 |
| GET | `/file/uploadFinish` | 44 |
| GET | `/file/access` | 56 |

## NotificationController

源码：[server/video/src/main/java/com/github/makewheels/video2022/notification/NotificationController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/notification/NotificationController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/notification/getMyNotifications` | 15 |
| POST | `/notification/markAsRead` | 23 |
| POST | `/notification/markAllAsRead` | 31 |
| GET | `/notification/getUnreadCount` | 38 |

## DeveloperConsoleController

源码：[server/video/src/main/java/com/github/makewheels/video2022/openapi/oauth/DeveloperConsoleController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/openapi/oauth/DeveloperConsoleController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/developer/stats` | 38 |
| GET | `/developer/apps/{appId}/webhooks` | 48 |
| POST | `/developer/apps/{appId}/webhooks` | 58 |
| DELETE | `/developer/apps/{appId}/webhooks/{webhookId}` | 81 |

## DeveloperController

源码：[server/video/src/main/java/com/github/makewheels/video2022/openapi/oauth/DeveloperController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/openapi/oauth/DeveloperController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| POST | `/developer/register` | 35 |
| POST | `/developer/login` | 55 |
| GET | `/developer/me` | 72 |
| POST | `/developer/apps` | 89 |
| GET | `/developer/apps` | 120 |
| DELETE | `/developer/apps/{appId}` | 140 |

## OAuthController

源码：[server/video/src/main/java/com/github/makewheels/video2022/openapi/oauth/OAuthController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/openapi/oauth/OAuthController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| POST | `/oauth/token` | 38 |
| POST | `/oauth/revoke` | 59 |

## RateLimitController

源码：[server/video/src/main/java/com/github/makewheels/video2022/openapi/ratelimit/RateLimitController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/openapi/ratelimit/RateLimitController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/api/v1/rateLimit/status` | 18 |

## ApiCommentController

源码：[server/video/src/main/java/com/github/makewheels/video2022/openapi/v1/ApiCommentController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/openapi/v1/ApiCommentController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/api/v1/videos/{videoId}/comments` | 29 |
| POST | `/api/v1/videos/{videoId}/comments` | 40 |
| DELETE | `/api/v1/comments/{commentId}` | 61 |

## ApiNotificationController

源码：[server/video/src/main/java/com/github/makewheels/video2022/openapi/v1/ApiNotificationController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/openapi/v1/ApiNotificationController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/api/v1/notifications` | 21 |

## ApiPlaylistController

源码：[server/video/src/main/java/com/github/makewheels/video2022/openapi/v1/ApiPlaylistController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/openapi/v1/ApiPlaylistController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| POST | `/api/v1/playlists` | 40 |
| GET | `/api/v1/playlists` | 55 |
| GET | `/api/v1/playlists/{id}` | 67 |
| PATCH | `/api/v1/playlists/{id}` | 74 |
| DELETE | `/api/v1/playlists/{id}` | 94 |
| POST | `/api/v1/playlists/{id}/items` | 106 |
| DELETE | `/api/v1/playlists/{id}/items/{itemId}` | 123 |

## ApiUserController

源码：[server/video/src/main/java/com/github/makewheels/video2022/openapi/v1/ApiUserController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/openapi/v1/ApiUserController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/api/v1/users/me` | 27 |
| GET | `/api/v1/users/{userId}` | 44 |

## ApiVideoController

源码：[server/video/src/main/java/com/github/makewheels/video2022/openapi/v1/ApiVideoController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/openapi/v1/ApiVideoController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| POST | `/api/v1/videos` | 51 |
| GET | `/api/v1/videos` | 69 |
| GET | `/api/v1/videos/{videoId}` | 85 |
| PATCH | `/api/v1/videos/{videoId}` | 93 |
| DELETE | `/api/v1/videos/{videoId}` | 115 |
| POST | `/api/v1/videos/{videoId}/upload-credentials` | 129 |
| POST | `/api/v1/videos/{videoId}/upload-complete` | 144 |
| GET | `/api/v1/videos/{videoId}/transcode` | 158 |
| GET | `/api/v1/videos/{videoId}/play` | 167 |

## ApiWebhookController

源码：[server/video/src/main/java/com/github/makewheels/video2022/openapi/v1/ApiWebhookController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/openapi/v1/ApiWebhookController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| POST | `/api/v1/webhooks` | 36 |
| GET | `/api/v1/webhooks` | 53 |
| DELETE | `/api/v1/webhooks/{id}` | 61 |

## OssLogController

源码：[server/video/src/main/java/com/github/makewheels/video2022/oss/osslog/OssLogController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/oss/osslog/OssLogController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/oss-log/generateOssAccessLog` | 22 |

## PlaylistController

源码：[server/video/src/main/java/com/github/makewheels/video2022/playlist/PlaylistController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/playlist/PlaylistController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| POST | `/playlist/createPlaylist` | 33 |
| POST | `/playlist/updatePlaylist` | 45 |
| GET | `/playlist/deletePlaylist` | 57 |
| GET | `/playlist/recoverPlaylist` | 66 |
| GET | `/playlist/getPlaylistById` | 75 |
| GET | `/playlist/getPlayItemListDetail` | 86 |
| GET | `/playlist/getPlaylistByPage` | 95 |
| GET | `/playlist/getMyPlaylistByPage` | 105 |
| POST | `/playlist/addPlaylistItem` | 114 |
| POST | `/playlist/deletePlaylistItem` | 124 |
| POST | `/playlist/movePlaylistItem` | 134 |
| GET | `/playlist/getPlaylistByVideoId` | 144 |

## ShareController

源码：[server/video/src/main/java/com/github/makewheels/video2022/share/ShareController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/share/ShareController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/share/create` | 23 |
| GET | `/share/stats` | 28 |
| GET | `/share/s/{shortCode}` | 33 |

## ExceptionController

源码：[server/video/src/main/java/com/github/makewheels/video2022/springboot/exception/ExceptionController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/springboot/exception/ExceptionController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/exceptionLog` | 18 |
| ANY（RequestMapping 未限定） | `/exceptionLog/getById` | 19 |

## HealthCheckController

源码：[server/video/src/main/java/com/github/makewheels/video2022/springboot/health/HealthCheckController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/springboot/health/HealthCheckController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/healthCheck` | 15 |

## SpaController

源码：[server/video/src/main/java/com/github/makewheels/video2022/springboot/SpaController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/springboot/SpaController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| ANY（RequestMapping 未限定） | `/login` | 11 |
| ANY（RequestMapping 未限定） | `/auth/**` | 11 |
| ANY（RequestMapping 未限定） | `/upload` | 11 |
| ANY（RequestMapping 未限定） | `/edit/**` | 11 |
| ANY（RequestMapping 未限定） | `/watch/**` | 11 |
| ANY（RequestMapping 未限定） | `/youtube` | 11 |
| ANY（RequestMapping 未限定） | `/statistics` | 11 |
| ANY（RequestMapping 未限定） | `/my-videos` | 11 |
| ANY（RequestMapping 未限定） | `/channel/**` | 11 |
| ANY（RequestMapping 未限定） | `/settings` | 11 |

## SubscriptionController

源码：[server/video/src/main/java/com/github/makewheels/video2022/subscription/SubscriptionController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/subscription/SubscriptionController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/subscription/subscribe` | 16 |
| GET | `/subscription/unsubscribe` | 21 |
| GET | `/subscription/getStatus` | 26 |
| GET | `/subscription/getMySubscriptions` | 33 |

## TranscodeController

源码：[server/video/src/main/java/com/github/makewheels/video2022/transcode/TranscodeController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/transcode/TranscodeController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| POST | `/transcode/aliyunCloudFunctionTranscodeCallback` | 43 |
| POST | `/transcode/cloudFunctionCallback` | 62 |
| POST | `/transcode/local/createTranscode` | 86 |
| POST | `/transcode/local/finishTranscode` | 94 |

## ClientController

源码：[server/video/src/main/java/com/github/makewheels/video2022/user/client/ClientController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/user/client/ClientController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/client/requestClientId` | 19 |

## SessionController

源码：[server/video/src/main/java/com/github/makewheels/video2022/user/session/SessionController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/user/session/SessionController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/session/requestSessionId` | 17 |

## UserController

源码：[server/video/src/main/java/com/github/makewheels/video2022/user/UserController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/user/UserController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/user/requestVerificationCode` | 19 |
| GET | `/user/submitVerificationCode` | 25 |
| GET | `/user/getUserByToken` | 31 |
| GET | `/user/getUserById` | 41 |
| POST | `/user/updateProfile` | 47 |
| GET | `/user/getChannel` | 52 |
| GET | `/user/getMyProfile` | 61 |
| GET | `/user/createAvatarFile` | 69 |
| GET | `/user/getAvatarUploadCredentials` | 77 |
| GET | `/user/avatarUploadFinish` | 85 |

## CategoryController

源码：[server/video/src/main/java/com/github/makewheels/video2022/video/CategoryController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/video/CategoryController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/category/list` | 15 |

## VideoLikeController

源码：[server/video/src/main/java/com/github/makewheels/video2022/video/like/VideoLikeController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/video/like/VideoLikeController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/videoLike/like` | 21 |
| GET | `/videoLike/dislike` | 27 |
| GET | `/videoLike/getStatus` | 33 |

## SearchController

源码：[server/video/src/main/java/com/github/makewheels/video2022/video/SearchController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/video/SearchController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/search` | 21 |

## VideoController

源码：[server/video/src/main/java/com/github/makewheels/video2022/video/VideoController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/video/VideoController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| POST | `/video/create` | 35 |
| GET | `/video/rawFileUploadFinish` | 45 |
| POST | `/video/updateInfo` | 55 |
| GET | `/video/getVideoDetail` | 66 |
| GET | `/video/getMyVideoList` | 76 |
| GET | `/video/getPublicVideoList` | 86 |
| GET | `/video/getRawFileDownloadUrl` | 96 |
| GET | `/video/delete` | 109 |
| GET | `/video/getVideoStatus` | 120 |
| POST | `/video/updateWatchSettings` | 130 |

## HeartbeatController

源码：[server/video/src/main/java/com/github/makewheels/video2022/watch/heartbeat/HeartbeatController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/watch/heartbeat/HeartbeatController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| ANY（RequestMapping 未限定） | `/heartbeat/add` | 16 |

## WatchController

源码：[server/video/src/main/java/com/github/makewheels/video2022/watch/play/WatchController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/watch/play/WatchController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/watchController/addWatchLog` | 26 |
| GET | `/watchController/getWatchInfo` | 37 |
| GET | `/watchController/getM3u8Content.m3u8` | 47 |
| GET | `/watchController/getMultivariantPlaylist.m3u8` | 56 |

## PlaybackController

源码：[server/video/src/main/java/com/github/makewheels/video2022/watch/playback/PlaybackController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/watch/playback/PlaybackController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| POST | `/playback/start` | 20 |
| POST | `/playback/heartbeat` | 28 |
| POST | `/playback/exit` | 34 |

## ProgressController

源码：[server/video/src/main/java/com/github/makewheels/video2022/watch/progress/ProgressController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/watch/progress/ProgressController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/progress/getProgress` | 22 |

## WatchHistoryController

源码：[server/video/src/main/java/com/github/makewheels/video2022/watch/watchhistory/WatchHistoryController.java](../../../server/video/src/main/java/com/github/makewheels/video2022/watch/watchhistory/WatchHistoryController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/watchHistory/getMyHistory` | 19 |
| DELETE | `/watchHistory/clear` | 30 |

## HealthCheckController（youtube 模块）

源码：[server/youtube/src/main/java/com/github/makewheels/video2022/youtube/HealthCheckController.java](../../../server/youtube/src/main/java/com/github/makewheels/video2022/youtube/HealthCheckController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/2016-08-15/proxy/video-2022-hongkong.LATEST/video-2022-youtube/healthCheck` | 13 |
| GET | `/healthCheck` | 20 |

## YoutubeController（youtube 模块）

源码：[server/youtube/src/main/java/com/github/makewheels/video2022/youtube/YoutubeController.java](../../../server/youtube/src/main/java/com/github/makewheels/video2022/youtube/YoutubeController.java)

| 方法 | 声明路径 | 源码行 |
|---|---|---|
| GET | `/youtube/getFileExtension` | 17 |
| GET | `/youtube/getVideoInfo` | 27 |
| POST | `/youtube/transferVideo` | 35 |
| POST | `/youtube/transferFile` | 43 |
