# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个视频分享平台后端服务，支持视频上传、转码、播放、YouTube下载等功能。

## 构建命令

```bash
# 编译整个项目
mvn clean compile

# 打包 video 模块 (SpringBoot 应用)
mvn clean package -pl video -Pspringboot

# 打包 youtube 模块
mvn clean package -pl youtube -Pyoutube

# 打包云函数 (assembly 方式，用于阿里云函数计算)
mvn clean package -pl video -Passembly

# 运行测试
mvn test -pl video
```

## 运行项目

```bash
# 开发环境运行 (需要本地 MongoDB 和 Redis)
cd video
mvn spring-boot:run

# 或直接运行 jar
java -jar video/target/video-0.0.1-SNAPSHOT.jar
```

默认端口: `5022`，配置文件: `video/src/main/resources/application.properties`

## 项目架构

多模块 Maven 项目：
- `video/` - 核心视频服务模块 (Spring Boot 应用)
- `youtube/` - YouTube 视频下载服务模块 (独立部署到阿里云函数计算)

### 核心模块分包结构 (video 模块)

| 包名 | 职责 |
|------|------|
| `user/` | 用户认证、Session管理、Client管理 |
| `video/` | 视频实体、创建、状态管理 |
| `file/` | 文件上传、OSS凭证、TsFile管理 |
| `transcode/` | 视频转码 (工厂模式，支持 MPS/云函数/GPU云函数) |
| `watch/` | 播放、心跳、进度保存 |
| `playlist/` | 播放列表管理 |
| `cover/` | 视频封面处理 |
| `oss/` | 阿里云 OSS 服务封装 |
| `finance/` | 账单、钱包、费用统计 |

### 技术栈

- **Java 21** + **Spring Boot 3.4.1**
- **MongoDB** - 主数据库
- **Redis** - 缓存、Session存储
- **阿里云 OSS** - 视频文件存储
- **阿里云 MPS** - 视频转码服务
- **阿里云函数计算** - 云函数转码

## 关键设计

### 视频上传流程
1. 前端请求上传凭证 → 后端返回 STS 临时凭证
2. 前端直传 OSS → 通知后端上传完成
3. 后端获取视频信息 → 触发转码
4. 转码完成 → 保存 HLS 信息到数据库

### 转码服务 (工厂模式)
位置: `video/src/main/java/.../transcode/factory/TranscodeFactory.java`

三种转码实现，按优先级选择：
1. `AliyunCfGPUTranscodeImpl` - GPU 云函数 (最快)
2. `AliyunMpsTranscodeImpl` - 阿里云 MPS
3. `AliyunCfTranscodeImpl` - 普通云函数

### 登录拦截器
位置: `video/src/main/java/.../springboot/interceptor/InterceptorConfiguration.java`

需要登录的接口通过 `CheckTokenInterceptor` 拦截，Session 存储在 Redis。

### 签名验证
云函数回调使用 HmacSHA256 签名验证，详见 `docs/1-关键设计.md`。

## 配置说明

### 环境变量 (.env)

项目使用 `.env` 文件管理密钥，启动时由 `DotenvEnvironmentPostProcessor` 自动加载。

```bash
# 复制模板
cp .env.example .env
# 编辑 .env 填入实际密钥
```

所需密钥参见 `.env.example`。不配置密钥也能启动，但对应功能不可用。

生产环境可直接设置系统环境变量，优先级：`.env` 文件 > 系统环境变量 > 默认空值。

### 外部依赖

- MongoDB (localhost:27017)
- Redis (localhost:6379)

## 踩坑记录

### bce-java-sdk Jackson 版本冲突
百度云 SDK (`bce-java-sdk`) 自带 Jackson 2.10.x，与 Spring Boot 3.4 管理的 Jackson 2.18.x 冲突，导致 `NoClassDefFoundError: com/fasterxml/jackson/core/util/JacksonFeature`。已通过删除百度云 SMS 功能彻底解决。如果将来引入自带旧 Jackson 的第三方 SDK，需在 `<exclusions>` 中排除 `jackson-core`、`jackson-databind`、`jackson-annotations`。

### MongoDB `password` 属性不能为空字符串
`spring.data.mongodb.password` 类型是 `char[]`，如果用 `${MONGODB_PASSWORD:}` 占位（空默认值），Spring Boot 绑定时抛 NPE。解决方案：开发环境不配置 username/password（本地 MongoDB 无需认证），生产环境用 `${MONGODB_PASSWORD}`（无默认值，未设置时 fail fast）。

### 测试编译问题
`JavaFunctionLineCounterTest.java` 使用 JUnit 4 (`org.junit.Test`) 但项目只引入了 JUnit 5，打包时需 `-Dmaven.test.skip=true`。