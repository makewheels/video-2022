

## 4. 播放

#### 获取播放信息

```
GET /video/getWatchInfo?watchId=1509176752561631232
```



返回

```json
{
    "code":0,
    "message":"success",
    "data":{
        "id":"63954cdfdee0d14ef70074bc",
        "userId":"638e1b7ccc41ab5499df37bf",
        "watchCount":2,
        "duration":53908,
        "coverUrl":"https://video-2022-prod.oss-cn-beijing.aliyuncs.com/videos/638e1b7ccc41ab5499df37bf/63954cdfdee0d14ef70074bc/cover/63954cfbdee0d14ef70074c0.jpg",
        "watchId":"1601779335300788224",
        "watchUrl":"https://videoplus.top/watch?v=1601779335300788224",
        "shortUrl":"https://a4.fit/6356",
        "title":"农夫山泉价格",
        "description":"",
        "type":"USER_UPLOAD",
        "youtubeVideoId":null,
        "youtubeUrl":null,
        "youtubePublishTime":null,
        "status":"READY",
        "createTime":"2022-12-11T03:22:07.824+00:00",
        "createTimeString":"2022-12-11 11:22:07",
        "youtubePublishTimeString":null
    }
}
```

#### 获取m3u8自适应播放内容

```
GET /watchController/getMultivariantPlaylist.m3u8?videoId=63954cdfdee0d14ef70074bc&clientId=638e1d969cae0b13419384e9&sessionId=63a1ddc7752d1b03473363a5
```

返回

```
#EXTM3U

#EXT-X-STREAM-INF:BANDWIDTH=7032284,AVERAGE-BANDWIDTH=4064092
https://videoplus.top/watchController/getM3u8Content.m3u8?resolution=720p&videoId=63954cdfdee0d14ef70074bc&clientId=638e1d969cae0b13419384e9&sessionId=63a1ddc7752d1b03473363a5&transcodeId=63954cfbdee0d14ef70074c3

#EXT-X-STREAM-INF:BANDWIDTH=18344529,AVERAGE-BANDWIDTH=11883132
https://videoplus.top/watchController/getM3u8Content.m3u8?resolution=1080p&videoId=63954cdfdee0d14ef70074bc&clientId=638e1d969cae0b13419384e9&sessionId=63a1ddc7752d1b03473363a5&transcodeId=63954cfcdee0d14ef70074c4
```

#### 获取m3u8内容

```
GET /watchController/getM3u8Content.m3u8?resolution=720p&videoId=63954cdfdee0d14ef70074bc&clientId=638e1d969cae0b13419384e9&sessionId=63a1ddc7752d1b03473363a5&transcodeId=63954cfbdee0d14ef70074c3
```

返回

```
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-ALLOW-CACHE:YES
#EXT-X-TARGETDURATION:2
#EXT-X-MEDIA-SEQUENCE:0
#EXTINF:1.983333,
https://videoplus.top/file/access?resolution=720p&tsIndex=0&videoId=63954cdfdee0d14ef70074bc&clientId=638e1d969cae0b13419384e9&sessionId=63a1ddc7752d1b03473363a5&fileId=63954d0edee0d14ef70074e3&timestamp=1671552458573&nonce=CH3XxwVS5vhZVIh-rE-um&sign=153ab2e06d7b47a9baaa8561416a5720
#EXTINF:1.983333,
https://videoplus.top/file/access?resolution=720p&tsIndex=1&videoId=63954cdfdee0d14ef70074bc&clientId=638e1d969cae0b13419384e9&sessionId=63a1ddc7752d1b03473363a5&fileId=63954d0edee0d14ef70074e4&timestamp=1671552458573&nonce=4ICGuuh5Cs_jtTwqR-v-w&sign=c225c76a7b5d4615bd2815ca4fc08b1f
#EXTINF:1.983333,
https://videoplus.top/file/access?resolution=720p&tsIndex=2&videoId=63954cdfdee0d14ef70074bc&clientId=638e1d969cae0b13419384e9&sessionId=63a1ddc7752d1b03473363a5&fileId=63954d0edee0d14ef70074e5&timestamp=1671552458573&nonce=0iBJ1HC-d7v_uQqyc8w6a&sign=2fba1143590744b89c7c723169227288
#EXTINF:1.983333,
https://videoplus.top/file/access?resolution=720p&tsIndex=3&videoId=63954cdfdee0d14ef70074bc&clientId=638e1d969cae0b13419384e9&sessionId=63a1ddc7752d1b03473363a5&fileId=63954d0edee0d14ef70074e6&timestamp=1671552458574&nonce=_21VWJ2v4ecalnOZZ6Syk&sign=062830eb0de44196b36f77b36d2fc759
#EXTINF:1.983333,
https://videoplus.top/file/access?resolution=720p&tsIndex=4&videoId=63954cdfdee0d14ef70074bc&clientId=638e1d969cae0b13419384e9&sessionId=63a1ddc7752d1b03473363a5&fileId=63954d0edee0d14ef70074e7&timestamp=1671552458574&nonce=QVGtfA1oko99WdLkOM7gj&sign=616d468400954b5a9de8da6e596b5c0f
#EXT-X-ENDLIST
```

#### 心跳

```
POST /heartbeat/add
```

payload

```json
{
    "videoId":"63954cdfdee0d14ef70074bc",
    "clientId":"638e1d969cae0b13419384e9",
    "sessionId":"63a1ddc7752d1b03473363a5",
    "videoStatus":"READY",
    "playerProvider":"ALIYUN_WEB",
    "clientTime":"2022-12-20T16:11:47.181Z",
    "type":"TIMER",
    "event":null,
    "playerTime":820.37,
    "playerStatus":"pause",
    "playerVolume":1
}
```