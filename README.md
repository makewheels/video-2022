# 目录
<!-- TOC -->
* [目录](#目录)
* [接口文档](#接口文档)
  * [1. 用户](#1-用户)
    * [请求验证码](#请求验证码)
  * [2. 视频](#2-视频)
    * [创建视频](#创建视频)
    * [获取上传凭证](#获取上传凭证)
    * [返回示例](#返回示例)
    * [通知文件上传完成](#通知文件上传完成)
  * [3. YouTube](#3-youtube)
      * [新建搬运YouTube视频](#新建搬运youtube视频)
      * [获取YouTube文件拓展名](#获取youtube文件拓展名)
      * [获取YouTube视频信息](#获取youtube视频信息)
  * [4. 播放](#4-播放)
  * [5. 阿里云 云函数ffmpeg转码](#5-阿里云-云函数ffmpeg转码)
    * [ffprobe 获取视频信息](#ffprobe-获取视频信息)
    * [发起转码](#发起转码)
  * [6. 统计](#6-统计)
* [变更日志](#变更日志)
  * [2022年11月27日21:18:08](#2022年11月27日21--18--08)
  * [2022年12月7日23:06:51](#2022年12月7日23--06--51)
  * [2022年12月10日13:33:26](#2022年12月10日13--33--26)
  * [2022年12月10日23:03:12](#2022年12月10日23--03--12)
  * [2022年12月10日23:34:23](#2022年12月10日23--34--23)
  <!-- TOC -->

# 关键逻辑

## 转码





## 播放





# 接口文档

[![Run in Postman](https://run.pstmn.io/button.svg)](https://app.getpostman.com/run-collection/dced8657344813ee3fbc?action=collection%2Fimport)

## 1. 用户

### 请求验证码

请求短信验证码，然后后端会在Redis缓存，有过期时间

```text
GET /user/requestVerificationCode
```

|   参数  | 说明   | 示例值 |
|-----|-----|-----|
|  phone   | 手机号 | 15695389361 |

![](docs/imgs/user-requestVerificationCode-RDM.jpg)

### 提交验证码

```
GET /user/submitVerificationCode
```

| 参数  | 说明   | 示例值      |
| ----- | ------ | ----------- |
| phone | 手机号 | 15695389361 |
| code  | 验证码 | 4736        |

```json
{
    "code": 1,
    "message": "fail",
    "data": null
}
```



### 根据token获取用户

```
GET /user/getUserByToken
```

| 参数  | 说明 | 示例值                               |
| ----- | ---- | ------------------------------------ |
| token | 令牌 | 54c323bd-5b45-401c-b8ea-5fb31302c424 |











## 2. 视频

### 创建视频
```text
POST video/create
```
请求示例
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

返回示例

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
        "expiration": "2022-12-28T13:16:37Z",
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

传参

| 参数           | 说明            | 示例值      |
| -------------- | --------------- | ----------- |
| youtubeVideoId | YouTube的视频id | j4LtMumQGHc |

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

传参

| 参数           | 说明            | 示例值      |
| -------------- | --------------- | ----------- |
| youtubeVideoId | YouTube的视频id | j4LtMumQGHc |

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



## 5. 阿里云 云函数 ffmpeg 转码

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
    "outputDir":"videos/62511690c3afe0646f9c670b/62626cff1fb6c600b2a7f14d/transcode/720p",
    "videoId":"62626cff1fb6c600b2a7f14d",
    "audioCodec":"aac",
    "resolution":"720p",
    "quality":"keep",
    "bucket":"video-2022-prod",
    "jobId":"829bb555d5b14123ad3cf129519701de",
    "endpoint":"oss-cn-beijing-internal.aliyuncs.com",
    "width":1280,
    "callbackUrl":"https://videoplus.top/transcode/aliyunCloudFunctionTranscodeCallback",
    "inputKey":"videos/62511690c3afe0646f9c670b/62626cff1fb6c600b2a7f14d/original/62626cff1fb6c600b2a7f14d.yv",
    "transcodeId":"62626d7e1fb6c600b2a7f14e",
    "height":720,
    "videoCodec":"h264"
}
```
## 6. 统计

### 统计某视频流量消耗

观众观看视频，所消耗流量

```
GET /statistics/getTrafficConsume?videoId=63a1a40bcc99952a50decc3f
```

返回示例

```json
{
    "code": 0,
    "message": "success",
    "data": {
        "trafficConsumeString": "693.02 MB",
        "videoId": "63a1a40bcc99952a50decc3f",
        "trafficConsumeInBytes": 726685236
    }
}
```

| 返回字段              | 说明                          | 示例值                   |
| --------------------- | ----------------------------- | ------------------------ |
| trafficConsumeString  | 供人类读的流量                | 693.02 MB                |
| trafficConsumeInBytes | 视频所消耗流量，单位字节bytes | 726685236                |
| videoId               | 视频id                        | 63a1a40bcc99952a50decc3f |

# 数据库表结构

## video

### 重要字段

```json
{
    "_id":"63954cdfdee0d14ef70074bc",
    "userId":"638e1b7ccc41ab5499df37bf",
    "originalFileId":"63954cdfdee0d14ef70074bb",
    "originalFileKey":"videos/638e1b7ccc41ab5499df37bf/63954cdfdee0d14ef70074bc/original/63954cdfdee0d14ef70074bc.mp4",
    "watchCount":1,
    "duration":"53908",
    "coverId":"63954cfbdee0d14ef70074c0",
    "coverUrl":"https://video-2022-prod.oss-cn-beijing.aliyuncs.com/videos/638e1b7ccc41ab5499df37bf/63954cdfdee0d14ef70074bc/cover/63954cfbdee0d14ef70074c0.jpg",
    "watchId":"1601779335300788224",
    "watchUrl":"https://videoplus.top/watch?v=1601779335300788224",
    "shortUrl":"https://a4.fit/6356",
    "title":"农夫山泉价格",
    "description":"",
    "width":1920,
    "height":1080,
    "videoCodec":"h264",
    "audioCodec":"aac",
    "bitrate":15508,
    "type":"USER_UPLOAD",
    "provider":"ALIYUN_OSS",
    "status":"READY",
    "createTime":"2022-12-11T03:22:07.824Z",
    "updateTime":"2022-12-11T03:23:07.020Z",
    "expireTime":"2023-01-10T03:22:07.824Z",
    "isPermanent":false,
    "isOriginalFileDeleted":false,
    "isTranscodeFilesDeleted":false,
    "transcodeIds":[
        "63954cfbdee0d14ef70074c3",
        "63954cfcdee0d14ef70074c4"
    ]
}
```



### 通过阿里云获取的视频信息 mediaInfo

```json
"mediaInfo": {
    "async": false,
    "input": {
      "bucket": "video-2022-prod",
      "location": "oss-cn-beijing",
      "object": "videos/638e1b7ccc41ab5499df37bf/63954cdfdee0d14ef70074bc/original/63954cdfdee0d14ef70074bc.mp4"
    },
    "jobId": "794c2269c2af4f1abe79d10c57a9fbd0",
    "creationTime": "2022-12-11T03:22:35Z",
    "state": "Success",
    "properties": {
      "duration": "53.908500",
      "fileSize": "104502461",
      "streams": {
        "audioStreamList": {
          "audioStream": [
            {
              "channelLayout": "stereo",
              "codecTagString": "mp4a",
              "index": "1",
              "bitrate": "96.001",
              "timebase": "1/48000",
              "codecTimeBase": "1/48000",
              "codecTag": "0x6134706d",
              "duration": "53.908500",
              "channels": "2",
              "sampleFmt": "fltp",
              "codecLongName": "AAC (Advanced Audio Coding)",
              "startTime": "0.000000",
              "codecName": "aac",
              "lang": "eng",
              "samplerate": "48000"
            }
          ]
        },
        "videoStreamList": {
          "videoStream": [
            {
              "avgFPS": "59.337578",
              "hasBFrames": "1",
              "colorRange": "pc",
              "bitrate": "15375.053",
              "codecTimeBase": "968897/114984000",
              "duration": "53.827611",
              "dar": "0:1",
              "networkCost": {},
              "startTime": "0.000000",
              "colorTransfer": "smpte170m",
              "lang": "eng",
              "height": "1080",
              "level": "40",
              "sar": "0:1",
              "profile": "High",
              "codecTagString": "avc1",
              "fps": "60.0",
              "index": "0",
              "timebase": "1/90000",
              "codecTag": "0x31637661",
              "pixFmt": "yuvj420p",
              "codecLongName": "H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10",
              "width": "1920",
              "colorPrimaries": "bt470bg",
              "codecName": "h264"
            }
          ]
        },
        "subtitleStreamList": {
          "subtitleStream": []
        }
      },
      "format": {
        "duration": "53.908500",
        "numPrograms": "0",
        "size": "104502461",
        "formatName": "mov,mp4,m4a,3gp,3g2,mj2",
        "bitrate": "15508.123",
        "startTime": "0.000000",
        "formatLongName": "QuickTime / MOV",
        "numStreams": "2"
      },
      "fps": "60.0",
      "width": "1920",
      "bitrate": "15508.123",
      "fileFormat": "QuickTime / MOV",
      "height": "1080"
    }
  }
```

### Java Bean

```java
@Data
@Document
public class Video {
    @Id
    private String id;

    @Indexed
    private String userId;
    @Indexed
    private String originalFileId;
    private String originalFileKey;

    private Integer watchCount;
    private Long duration;      //视频时长，单位毫秒
    private String coverId;
    private String coverUrl;

    @Indexed
    private String watchId;
    private String watchUrl;
    private String shortUrl;
    private String title;
    private String description;

    private Integer width;
    private Integer height;
    private String videoCodec;
    private String audioCodec;
    private Integer bitrate;

    @Indexed
    private String type;
    @Indexed
    private String provider;    //它就是对象存储提供商，和file是一对一关系

    @Indexed
    private String youtubeVideoId;
    private String youtubeUrl;
    private JSONObject youtubeVideoInfo;
    private Date youtubePublishTime;

    @Indexed
    private String status;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;

    @Indexed
    private Date expireTime;
    @Indexed
    private Boolean isPermanent;                //是否是永久视频
    private Boolean isOriginalFileDeleted;      //源视频是否已删除
    private Boolean isTranscodeFilesDeleted;    //ts转码文件是否已删除
    private Date deleteTime;                    //什么时候删的

    private JSONObject mediaInfo;

    private List<String> transcodeIds;

    public boolean isYoutube() {
        return StringUtils.equals(type, VideoType.YOUTUBE);
    }

    public boolean isReady() {
        return StringUtils.equals(status, VideoStatus.READY);
    }
}

```



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