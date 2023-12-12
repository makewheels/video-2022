
## 7. App

### 1. 检查App版本

```
GET /app/checkUpdate
```

请求参数

| 参数     | 说明                | 示例    |
| -------- | ------------------- | ------- |
| platform | 平台：android / ios | android |

返回

```json
{
    "code": 0,
    "message": "success",
    "data": {
        "downloadUrl": "https://baidu.com",
        "versionInfo": "最新版本信息：alpha内测，2022年4月25日20:41:46",
        "compareVersion": false,
        "versionName": "1.0.0",
        "versionCode": 1,
        "isForceUpdate": false
    }
}
```

| 参数           | 说明                                                         | 示例                                                         |
| -------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| downloadUrl    | apk包下载地址                                                | http://abcdefg.com/video-2022-prod/packages/andoird/1.0.0-release.apk |
| versionInfo    | 版本描述信息                                                 |                                                              |
| compareVersion | 客户端是否把当前版本与最新版本对比，看是否更新，仅用于测试安装覆盖 | false                                                        |
| versionName    | 版本                                                         | 1.0.0                                                        |
| versionCode    | 版本号                                                       | 1                                                            |
| isForceUpdate  | 是否强制更新                                                 | false                                                        |
