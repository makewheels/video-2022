
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

| 参数                  | 说明                          | 示例                     |
| --------------------- | ----------------------------- | ------------------------ |
| trafficConsumeString  | 供人类读的流量                | 693.02 MB                |
| trafficConsumeInBytes | 视频所消耗流量，单位字节bytes | 726685236                |
| videoId               | 视频id                        | 63a1a40bcc99952a50decc3f |
