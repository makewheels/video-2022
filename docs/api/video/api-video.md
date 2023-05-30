
## 2. 视频

### 创建视频
```text
POST /video/create
```
请求示例
```json
{
    "originalFilename": "VID_20220319_131135.mp4",
    "type": "USER_UPLOAD"
}
```
| 参数             | 说明                                         | 示例                    |
| ---------------- | -------------------------------------------- | ----------------------- |
| originalFilename | 原始文件名，主要用于：上传对象存储的文件后缀 | VID_20220319_131135.mp4 |
| type             | 类型：用户上传 / YouTube                     | USER_UPLOAD / YOUTUBE   |

返回示例

```json
{
    "code": 0,
    "message": "success",
    "data": {
        "watchId": "1601779335300788224",
        "shortUrl": "https://a4.fit/6356",
        "videoId": "63a1806f882a4230c02d0a36",
        "watchUrl": "https://videoplus.top/watch?v=1601779335300788224",
        "fileId": "63a1806f882a4230c02d0a35"
    }
}
```

| 参数     | 说明                           | 示例                                              |
| -------- | ------------------------------ | ------------------------------------------------- |
| fileId   | 文件id，接下来用于上传对象存储 | 63a1806f882a4230c02d0a35                          |
| watchUrl | 观看地址                       | https://videoplus.top/watch?v=1601779335300788224 |
| shortUrl | 短连接                         | https://a4.fit/6356                               |
| videoId  | 视频id                         | 63a1806f882a4230c02d0a36                          |

### 获取上传凭证

通过阿里云对象存储STS接口，赋予临时accessKey

```text
GET /file/getUploadCredentials
```

请求

| 参数   | 说明   | 示例                     |
| ------ | ------ | ------------------------ |
| fileId | 文件id | 63a1806f882a4230c02d0a35 |

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

| 参数         | 说明                                        | 示例                                               |
| ------------ | ------------------------------------------- | -------------------------------------------------- |
| bucket       | 存储桶                                      | 63a1806f882a4230c02d0a35                           |
| accessKeyId  |                                             | STS.NURKKUmgubZ1ugs1fZZqrTqLj                      |
| secretKey    |                                             | 3zdCPTmzEhVq7CMy1kpRjpzhESRPpMD5viNADtB3C3bq       |
| endpoint     | 对象存储公网地址                            | oss-cn-beijing.aliyuncs.com                        |
| provider     | 供应商，之前用过百度云，现在只有阿里云OSS了 | ALIYUN_OSS                                         |
| sessionToken | STS需要的                                   | CAISogJ1q6Ft5B2yfSjIr5bnAPHh......                 |
| expiration   | 过期时间                                    | 2022-12-28T13:16:37Z                               |
| key          | 上传对象存储路径：用户id+视频id             | videos/638cf9b80/63a02d0a38/original/63a1d0a38.mp4 |

### 通知文件上传完成

```text
GET /file/uploadFinish?fileId=624face951b82a5445c4f048
```