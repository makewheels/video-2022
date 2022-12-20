# 目录
<!-- TOC -->
* [目录](#目录)
* [接口文档](#接口文档)
  * [1. 用户](#1-用户)
    * [请求验证码](#请求验证码)
  * [视频](#视频)
    * [创建视频](#创建视频)
    * [获取上传凭证](#获取上传凭证)
    * [](#)
* [变更日志](#变更日志)
  * [2022年11月27日21:18:08](#2022年11月27日21--18--08)
  * [2022年12月7日23:06:51](#2022年12月7日23--06--51)
  * [2022年12月10日13:33:26](#2022年12月10日13--33--26)
  * [2022年12月10日23:03:12](#2022年12月10日23--03--12)
  * [2022年12月10日23:34:23](#2022年12月10日23--34--23)
  <!-- TOC -->

# 接口文档
[![Run in Postman](https://run.pstmn.io/button.svg)](https://app.getpostman.com/run-collection/dced8657344813ee3fbc?action=collection%2Fimport)
## 1. 用户
### 请求验证码
```text
GET /user/requestVerificationCode?phone=13332221511
```

|   参数名  |  示例值   | 说明  |
|-----|-----|-----|
|  phone   |  15695389361   | 手机号 |


## 2. 视频
### 创建视频
```text
POST video/create
```
参数
```json
{
    "originalFilename": "VID_20220319_131135.mp4",
    "type": "USER_UPLOAD"
}
```
返回示例
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "watchId": "1605133235216236544",
        "shortUrl": null,
        "videoId": "63a1806f882a4230c02d0a36",
        "watchUrl": "http://localhost:5022/watch?v=1605133235216236544",
        "fileId": "63a1806f882a4230c02d0a35"
    }
}
```

### 获取上传凭证
```text
GET /file/getUploadCredentials?fileId=62617ceca14aee70195f4d33
```

### 返回示例

```json
{
    "code": 0,
    "message": "success",
    "data": {
        "bucket": "video-2022-dev",
        "accessKeyId": "STS.NURKKUmgubZ1ugs1fZZqrTqLj",
        "endpoint": "oss-cn-beijing.aliyuncs.com",
        "secretKey": "3zdCPTmzEhVq7CMy1kpRjpzhESRPpMD5viNADtB3C3bq",
        "provider": "ALIYUN_OSS",
        "sessionToken": "CAISogJ1q6Ft5B2yfSjIr5bnAPHhgLhU1Zjad0HC1WYPVv5eu7TniDz2IH9LeHVhB+4WsPQ0lW1U6vwdlplpTJtIfkHfdsp36LJe9A7kPYfPtZO74+RcgsyuRWHOVU6rhMSKOLn3FoODI6f9MAicVt6sVAGiJ1uYRFWAHcCjq/wON6Y6PGSRaT5BG60lRG9Lo9MbMn38LOukNgWQ7EPbEEtvvHgX6wo9k9PdpPeR8R3Dllb35/YIroDqWPieYtJrIY10XqXevoU0VNKYjncAtEgWrvcu3PMYp2qXhLzHXQkNuSfhGvHP79hiIDV+YqUHAKNepJD+76Yn5bCPxtqpm0gdZrAID3iFG5rb2szAFaauLc18a7f3NnOIiIjVbIk/RvX84JKDXhqAAWWasFC7Sr+7spVTPdYH09iDguAiZAq7pFhB+Qm0EZsmsh76XfqobuTrr4odGVYbd/9mgT5ly21FdSCnTcajHAGoc2dCRoFq2kBWE/ae4gR7qg/n8qiSZuarkld0LLjod9CmpXBt/9yXmEfzPxN3UVN5YuzEVlvY7wh+hGZ33SX7",
        "expiration": "2022-12-20T13:16:37Z",
        "key": "videos/638e1811c29e3b400dcf9b80/63a18b79882a4230c02d0a38/original/63a18b79882a4230c02d0a38.mp4"
    }
}
```



### 通知文件上传完成

```text
GET /file/uploadFinish?fileId=624face951b82a5445c4f048
```



## 3. YouTube

#### 新建搬运YouTube视频

```text
POST /video/create
```

传入

```json
{
    "type": "YOUTUBE",
    "youtubeUrl": "https://youtu.be/OJzbRMXx5O4"
}
```

#### 获取YouTube文件拓展名

```
GET https://youtube.videoplus.top:5030/youtube/getFileExtension?youtubeVideoId=j4LtMumQGHc
```

示例返回：

```json
{
    "extension": "webm"
}
```

#### 获取YouTube视频信息

```
GET https://youtube.videoplus.top:5030/youtube/getVideoInfo?youtubeVideoId=j4LtMumQGHc
```

示例返回

```json
{
    "snippet": {
        "publishedAt": {
            "dateOnly": false,
            "timeZoneShift": 0,
            "value": 1649507106000
        },
        "defaultAudioLanguage": "en",
        "localized": {
            "description": "At least 52 people were killed Friday in a Russian rocket attack on one of the easternmost train stations still operating in Ukraine. The shock of the deadly missile strike on the crowded railroad station has Ukraine's President Volodymyr Zelenskyy calling for a global response. Debora Patta reports.\n\n\"CBS Saturday Morning\" co-hosts Jeff Glor, Michelle Miller and Dana Jacobson deliver two hours of original reporting and breaking news, as well as profiles of leading figures in culture and the arts. Watch \"CBS Saturday Morning\" at 7 a.m. ET on CBS and 8 a.m. ET on the CBS News app.\n\nSubscribe to \"CBS Mornings\" on YouTube: https://www.youtube.com/CBSMornings \nWatch CBS News live: https://cbsn.ws/1PlLpZ7c​\nDownload the CBS News app: https://cbsn.ws/1Xb1WC8​\nFollow \"CBS Mornings\" on Instagram: https://bit.ly/3A13OqA\nLike \"CBS Mornings\" on Facebook: https://bit.ly/3tpOx00\nFollow \"CBS Mornings\" on Twitter: https://bit.ly/38QQp8B\nSubscribe to our newsletter: https://cbsn.ws/1RqHw7T​\nTry Paramount+ free: https://bit.ly/2OiW1kZ\n\nFor video licensing inquiries, contact: licensing@veritone.com",
            "title": "At least 52 people killed in Ukraine railroad station attack"
        },
        "description": "At least 52 people were killed Friday in a Russian rocket attack on one of the easternmost train stations still operating in Ukraine. The shock of the deadly missile strike on the crowded railroad station has Ukraine's President Volodymyr Zelenskyy calling for a global response. Debora Patta reports.\n\n\"CBS Saturday Morning\" co-hosts Jeff Glor, Michelle Miller and Dana Jacobson deliver two hours of original reporting and breaking news, as well as profiles of leading figures in culture and the arts. Watch \"CBS Saturday Morning\" at 7 a.m. ET on CBS and 8 a.m. ET on the CBS News app.\n\nSubscribe to \"CBS Mornings\" on YouTube: https://www.youtube.com/CBSMornings \nWatch CBS News live: https://cbsn.ws/1PlLpZ7c​\nDownload the CBS News app: https://cbsn.ws/1Xb1WC8​\nFollow \"CBS Mornings\" on Instagram: https://bit.ly/3A13OqA\nLike \"CBS Mornings\" on Facebook: https://bit.ly/3tpOx00\nFollow \"CBS Mornings\" on Twitter: https://bit.ly/38QQp8B\nSubscribe to our newsletter: https://cbsn.ws/1RqHw7T​\nTry Paramount+ free: https://bit.ly/2OiW1kZ\n\nFor video licensing inquiries, contact: licensing@veritone.com",
        "thumbnails": {
            "standard": {
                "width": 640,
                "url": "https://i.ytimg.com/vi/j4LtMumQGHc/sddefault.jpg",
                "height": 480
            },
            "default": {
                "width": 120,
                "url": "https://i.ytimg.com/vi/j4LtMumQGHc/default.jpg",
                "height": 90
            },
            "high": {
                "width": 480,
                "url": "https://i.ytimg.com/vi/j4LtMumQGHc/hqdefault.jpg",
                "height": 360
            },
            "maxres": {
                "width": 1280,
                "url": "https://i.ytimg.com/vi/j4LtMumQGHc/maxresdefault.jpg",
                "height": 720
            },
            "medium": {
                "width": 320,
                "url": "https://i.ytimg.com/vi/j4LtMumQGHc/mqdefault.jpg",
                "height": 180
            }
        },
        "title": "At least 52 people killed in Ukraine railroad station attack",
        "categoryId": "25",
        "channelId": "UC-SJ6nODDmufqBzPBwCvYvQ",
        "channelTitle": "CBS Mornings",
        "liveBroadcastContent": "none",
        "tags": [
            "CBS Saturday Morning",
            "CBS News",
            "video",
            "ukraine",
            "train",
            "railroad station",
            "deadly missile strike",
            "crowded railroad",
            "president volodymyr zelenskyy"
        ]
    },
    "kind": "youtube#video",
    "etag": "iM78paVC76ZjgNuvg6uPjBkRzR0",
    "id": "j4LtMumQGHc",
    "contentDetails": {
        "duration": "PT3M43S",
        "licensedContent": true,
        "caption": "false",
        "contentRating": {},
        "definition": "hd",
        "projection": "rectangular",
        "regionRestriction": {
            "blocked": [
                "AU",
                "CA",
                "JP"
            ]
        },
        "dimension": "2d"
    },
    "statistics": {
        "likeCount": 4604,
        "viewCount": 850738,
        "commentCount": 3197,
        "favoriteCount": 0
    }
}
```



## 4. 播放

## 5. 阿里云 云函数ffmpeg转码
### ffprobe 获取视频信息
```text
POST https://ffprobe-video-transcode-gysmioluyt.cn-beijing.fcapp.run
```
传入
```json
{
    "bucket": "video-2022-prod",
    "region": "cn-beijing",
    "endpoint": "oss-cn-beijing-internal.aliyuncs.com",
    "inputKey": "test/demo-src.mp4",
    "inputKey1": "videos/62565a16c3afe0646f9c67b9/6256a80e1273947edf8854c7/original/6256a80e1273947edf8854c7.webm"
}
```
### 发起转码
```text
POST https://transcoe-master-video-transcode-pqrshwejna.cn-beijing.fcapp.run
```
```json
{
    "bucket": "test-beijing-bucket",
    "endpoint": "oss-cn-beijing-internal.aliyuncs.com",
    "inputKey": "youtube-wedding/wedd.webm",
    "outputDir": "youtube-wedding/hls",
    "videoId": "videoIdGcyPQYa1z5VMAFXQKFUd",
    "transcodeId": "transId",
    "jobId": "jobIdpgKq0eEE",
    "resolution": "keep",
    "width": 1920,
    "height": 1080,
    "videoCodec": "h264",
    "audioCodec": "aac",
    "quality": "keep",
    "callbackUrl": "https://www.baidu.com/"
}
```
## 6. 统计

# 变更日志
## 2022年11月27日21:18:08

RSA密码加密计划

## 2022年12月7日23:06:51

重新接入阿里云web播放器

## 2022年12月10日13:33:26

加入自适应码率

## 2022年12月10日23:03:12
RequestUtil to DTO改造方案

## 2022年12月10日23:34:23
接入UserHolder，取消request获取user对象