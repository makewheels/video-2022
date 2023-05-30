
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

后面最终是调用的 Google YouTube 的接口

```
GET https://youtube.videoplus.top:5030/youtube/getVideoInfo?youtubeVideoId=j4LtMumQGHc
```

传参

| 参数           | 说明            | 示例值      |
| -------------- | --------------- | ----------- |
| youtubeVideoId | YouTube的视频id | j4LtMumQGHc |

示例返回，直接透传YouTube返回内容

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