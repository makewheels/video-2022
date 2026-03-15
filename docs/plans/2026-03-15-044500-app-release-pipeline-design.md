# App 打包发布 & 版本检查更新 设计文档

## 概述

实现 Android/iOS 应用的 CI/CD 打包流水线、上传到对象存储、以及客户端版本检查与更新机制。

## 决策记录

| 决策项 | 选择 |
|--------|------|
| 对象存储 | 阿里云 OSS（后端已集成） |
| 版本管理 | 后端 + MongoDB |
| CI/CD 触发 | workflow_dispatch（手动，填版本号） |
| Android 下载方式 | 应用内 OkHttp 下载 + 安装 |
| iOS 分发方式 | TestFlight |
| 版本检查时机 | APP 启动时 + 设置页手动检查 |
| 强制更新 | 支持（minSupportedVersionCode） |
| 管理接口鉴权 | ADMIN_API_KEY（请求头 X-Admin-Api-Key） |
| Android 签名 | 先用 debug 签名，后续配置 release |
| iOS 账号 | 先写框架，具体配置后续再补 |

---

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────┐
│  GitHub Actions (workflow_dispatch, 输入版本号)           │
│                                                          │
│  ┌──────────────┐    ┌──────────────┐                   │
│  │ Android Job  │    │   iOS Job    │                   │
│  │ gradlew      │    │ xcodebuild   │                   │
│  │ assembleRel  │    │ archive      │                   │
│  └──────┬───────┘    └──────┬───────┘                   │
│         │                    │                           │
│    Upload APK           Upload IPA                      │
│    to OSS               to TestFlight                   │
│         │                    │                           │
│         └────────┬───────────┘                          │
│                  ▼                                       │
│      POST /app/publishVersion                           │
│      (注册新版本到 MongoDB)                              │
└─────────────────────────────────────────────────────────┘

┌──────────────────┐       ┌──────────────────┐
│   Android App    │       │    iOS App       │
│                  │       │                  │
│ 启动 / 设置页    │       │ 启动 / 设置页    │
│      ↓           │       │      ↓           │
│ GET /app/        │       │ GET /app/        │
│ checkUpdate      │       │ checkUpdate      │
│      ↓           │       │      ↓           │
│ 有新版本？       │       │ 有新版本？       │
│  ↓Yes            │       │  ↓Yes            │
│ 应用内下载APK    │       │ 跳转TestFlight   │
│ + 安装           │       │                  │
└──────────────────┘       └──────────────────┘
```

---

## 2. 后端改动

### MongoDB Collection: `app_version`

```json
{
  "_id": "ObjectId",
  "platform": "android",
  "versionCode": 2,
  "versionName": "1.1.0",
  "versionInfo": "更新说明文本",
  "downloadUrl": "https://oss-bucket.../app-v1.1.0.apk",
  "isForceUpdate": false,
  "minSupportedVersionCode": 1,
  "createTime": "ISODate",
  "updateTime": "ISODate"
}
```

### 接口

#### `GET /app/checkUpdate`

- 参数: `platform` (android/ios), `versionCode` (当前客户端版本)
- 返回: 最新版本信息 + `hasUpdate` 布尔值
- 逻辑:
  - `versionCode < 最新 versionCode` → hasUpdate = true
  - `versionCode < minSupportedVersionCode` → isForceUpdate = true

#### `POST /app/publishVersion` (新增)

- 鉴权: 请求头 `X-Admin-Api-Key`
- Body: platform, versionCode, versionName, versionInfo, downloadUrl, isForceUpdate, minSupportedVersionCode
- 用途: CI/CD 构建完成后调用，注册新版本

---

## 3. Android 客户端改动

### 新增文件

- `data/api/AppApi.kt` — Retrofit 接口
- `ui/update/AppUpdateChecker.kt` — 版本检查逻辑
- `ui/update/UpdateDialog.kt` — 更新弹窗 (Compose)
- `ui/update/ApkDownloader.kt` — OkHttp 下载 APK + 安装

### 流程

1. `MainActivity.onCreate()` → `AppUpdateChecker.check()`
2. 请求 `/app/checkUpdate?platform=android&versionCode=当前版本`
3. 有更新 → 显示 `UpdateDialog`
   - 强制更新: 不可关闭，只有"立即更新"
   - 可选更新: "立即更新" + "稍后再说"
4. 点击更新 → OkHttp 下载 APK（应用内进度条）→ 触发安装

### 权限

- `REQUEST_INSTALL_PACKAGES` (安装 APK)
- `WRITE_EXTERNAL_STORAGE` (Android < 10 兼容)

### 设置页改动

- 动态读取版本号（从 BuildConfig）
- 添加"检查更新"按钮

---

## 4. iOS 客户端改动

### 改动

- `APIClient.swift` 新增 `checkUpdate()` 方法
- 新增 `UpdateCheckView.swift` — SwiftUI Alert
- App 入口启动时调用版本检查
- 设置页: 动态版本号 + "检查更新"按钮

### iOS 更新流程

- 检测到新版本 → 弹窗提示
- 强制更新: 弹窗不可关闭
- 可选更新: 可关闭
- 点击"更新" → 跳转 TestFlight 链接

---

## 5. CI/CD 流水线

### 新增 Workflow: `.github/workflows/release.yml`

**触发方式**: `workflow_dispatch`

**输入参数**:
- `version_name` — 如 "1.1.0"
- `version_code` — 如 2
- `platform` — android / ios / both
- `force_update` — true / false
- `version_info` — 更新说明

### Android Job

1. Checkout 代码
2. Setup JDK 21
3. 动态修改 `build.gradle.kts` 中的版本号
4. `./gradlew assembleDebug` (先用 debug 签名，后续改 release)
5. 用 ossutil 上传 APK 到阿里云 OSS
6. 调用 `POST /app/publishVersion` 注册版本

### iOS Job

1. Checkout 代码
2. `xcodegen generate`
3. 动态修改 `project.yml` 中的版本号
4. `xcodebuild archive` + export
5. 上传到 TestFlight (框架预留，配置后续补)
6. 调用 `POST /app/publishVersion` 注册版本

### 需要的 GitHub Secrets

| Secret | 用途 |
|--------|------|
| `ALIYUN_OSS_ACCESS_KEY_ID` | OSS 上传 |
| `ALIYUN_OSS_ACCESS_KEY_SECRET` | OSS 上传 |
| `OSS_BUCKET` | OSS Bucket 名称 |
| `OSS_ENDPOINT` | OSS Endpoint |
| `ADMIN_API_KEY` | 后端管理接口鉴权 |
| `BACKEND_API_URL` | 生产环境后端地址 |
| `ANDROID_KEYSTORE_BASE64` | Android 签名 (后续) |
| `ANDROID_KEY_PASSWORD` | Android 签名 (后续) |
| `ANDROID_STORE_PASSWORD` | Android 签名 (后续) |
| `APP_STORE_CONNECT_API_KEY` | iOS TestFlight (后续) |
