# YouTube接口

> 文档地图：[README](../../README.md) > [API接口文档](../../README.md#api-接口文档) > 本文档

YouTube模块独立运行，默认地址为 `https://youtube.videoplus.top:5030`。

## 接口列表

| 接口 | 方法 | 路径 | 认证 |
|------|------|------|------|
| 新建YouTube视频 | POST | /video/create（主服务） | 需要 |
| 获取文件扩展名 | GET | /youtube/getFileExtension | 不需要 |
| 获取视频信息 | GET | /youtube/getVideoInfo | 不需要 |

## 接口详情

### 新建YouTube视频

通过主服务创建YouTube类型的视频。

**请求**
```
POST /video/create
Header: token: {your_token}
Content-Type: application/json
```

**请求体**
```json
{
    "videoType": "YOUTUBE",
    "youtubeUrl": "https://youtu.be/OJzbRMXx5O4"
}
```

---

### 获取YouTube文件扩展名

调用YouTube服务获取视频文件格式。

**请求**
```
GET https://youtube.videoplus.top:5030/youtube/getFileExtension?youtubeVideoId={youtubeVideoId}
```

**参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| youtubeVideoId | String | 是 | YouTube视频ID |

**响应示例**
```json
{
    "extension": "webm"
}
```

---

### 获取YouTube视频信息

透传Google YouTube Data API返回的视频详情。

**请求**
```
GET https://youtube.videoplus.top:5030/youtube/getVideoInfo?youtubeVideoId={youtubeVideoId}
```

**参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| youtubeVideoId | String | 是 | YouTube视频ID |

**响应示例**
```json
{
    "kind": "youtube#video",
    "id": "j4LtMumQGHc",
    "snippet": {
        "title": "Video Title",
        "description": "Video description...",
        "channelTitle": "Channel Name",
        "publishedAt": { "value": 1649507106000 },
        "thumbnails": {
            "default": { "url": "https://i.ytimg.com/vi/.../default.jpg", "width": 120, "height": 90 },
            "medium": { "url": "https://i.ytimg.com/vi/.../mqdefault.jpg", "width": 320, "height": 180 },
            "high": { "url": "https://i.ytimg.com/vi/.../hqdefault.jpg", "width": 480, "height": 360 },
            "standard": { "url": "https://i.ytimg.com/vi/.../sddefault.jpg", "width": 640, "height": 480 },
            "maxres": { "url": "https://i.ytimg.com/vi/.../maxresdefault.jpg", "width": 1280, "height": 720 }
        },
        "tags": ["tag1", "tag2"]
    },
    "contentDetails": {
        "duration": "PT3M43S",
        "definition": "hd",
        "dimension": "2d"
    },
    "statistics": {
        "viewCount": 850738,
        "likeCount": 4604,
        "commentCount": 3197
    }
}
```