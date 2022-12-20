# 目录
<!-- TOC -->
* [目录](#目录)
* [接口文档](#接口文档)
  * [用户](#用户)
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
## 用户
### 请求验证码
```text
GET /user/requestVerificationCode?phone=13332221511
```

|   参数名  |  示例值   | 说明  |
|-----|-----|-----|
|  phone   |  15695389361   | 手机号 |


## 视频
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
/file/getUploadCredentials?fileId=62617ceca14aee70195f4d33
```

### 
```text
/file/uploadFinish?fileId=624face951b82a5445c4f048
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