# App接口

> 文档地图：[README](../../README.md) > [API接口文档](../../README.md#api-接口文档) > 本文档

> 认证：除标注「公开」的接口外，需要请求头携带 `token`。无效 token 返回 HTTP 403。
>
> 详细业务流程见对应的 [业务文档](../业务/)。

## 接口列表

| 接口 | 方法 | 路径 | 认证 |
|------|------|------|------|
| 检查App更新 | GET | /app/checkUpdate | 不需要 |

## 接口详情

### 检查App更新

客户端检查是否有新版本可用。

**请求**
```
GET /app/checkUpdate?platform={platform}
```

**参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| platform | String | 是 | 平台：android / ios |

**响应示例**
```json
{
    "code": 0,
    "data": {
        "downloadUrl": "https://baidu.com",
        "versionInfo": "最新版本信息：alpha内测，2022年4月25日20:41:46",
        "compareVersion": false,
        "versionName": "1.0.0",
        "versionCode": 1,
        "isForceUpdate": false
    },
    "message": "成功"
}
```

| 字段 | 说明 |
|------|------|
| downloadUrl | 安装包下载地址 |
| versionInfo | 版本描述信息 |
| compareVersion | 客户端是否需要对比版本号决定是否更新（测试用） |
| versionName | 版本名称 |
| versionCode | 版本号（整数） |
| isForceUpdate | 是否强制更新 |
