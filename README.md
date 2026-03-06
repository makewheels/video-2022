# Video 2022

一个视频分享平台后端服务，支持视频上传、转码、播放、YouTube 下载等功能。

**示例视频**: https://oneclick.video/w?v=17GO2A

**上传入口**: https://oneclick.video/upload.html

**验证码**: 111

## 快速开始

### 环境要求
- Java 21
- Maven 3.8+
- MongoDB 6.0+
- Redis 7.0+

### 构建命令

```bash
# 编译整个项目
mvn clean compile

# 打包 video 模块 (SpringBoot 应用)
mvn clean package -pl video -Pspringboot

# 打包 youtube 模块
mvn clean package -pl youtube -Pyoutube

# 运行测试
mvn test -pl video
```

### 运行项目

```bash
# 开发环境运行 (需要本地 MongoDB 和 Redis)
cd video
mvn spring-boot:run

# 或直接运行 jar
java -jar video/target/video-0.0.1-SNAPSHOT.jar
```

默认端口：`5022`，配置文件：`video/src/main/resources/application.properties`

---

## 项目架构

多模块 Maven 项目：
- `video/` - 核心视频服务模块 (Spring Boot 应用)
- `youtube/` - YouTube 视频下载服务模块 (独立部署到阿里云函数计算)

### 核心模块分包结构 (video 模块)

| 包名 | 职责 |
|------|------|
| `user/` | 用户认证、Session 管理、Client 管理 |
| `video/` | 视频实体、创建、状态管理 |
| `file/` | 文件上传、OSS 凭证、TsFile 管理 |
| `transcode/` | 视频转码 (工厂模式，支持 MPS/云函数/GPU 云函数) |
| `watch/` | 播放、心跳、进度保存 |
| `playlist/` | 播放列表管理 |
| `cover/` | 视频封面处理 |
| `oss/` | 阿里云 OSS 服务封装 |
| `finance/` | 账单、钱包、费用统计 |

### 技术栈

- **Java 21** + **Spring Boot 3.4.1**
- **MongoDB** - 主数据库
- **Redis** - 缓存、Session 存储
- **阿里云 OSS** - 视频文件存储
- **阿里云 MPS** - 视频转码服务
- **阿里云函数计算** - 云函数转码

---

## 关键设计

[关键设计](docs/1-关键设计.md)

### 视频上传流程
1. 前端请求上传凭证 → 后端返回 STS 临时凭证
2. 前端直传 OSS → 通知后端上传完成
3. 后端获取视频信息 → 触发转码
4. 转码完成 → 保存 HLS 信息到数据库

### 转码服务 (工厂模式)
位置：`video/src/main/java/.../transcode/factory/TranscodeFactory.java`

三种转码实现，按优先级选择：
1. `AliyunCfGPUTranscodeImpl` - GPU 云函数 (最快)
2. `AliyunMpsTranscodeImpl` - 阿里云 MPS
3. `AliyunCfTranscodeImpl` - 普通云函数

---

## API 接口文档

[![Run in Postman](https://run.pstmn.io/button.svg)](https://app.getpostman.com/run-collection/dced8657344813ee3fbc?action=collection%2Fimport)

- [用户接口](docs/api/1-用户接口.md)
- [上传视频接口](docs/api/2-上传视频接口.md)
- [YouTube 接口](docs/api/3-YouTube 接口.md)
- [播放视频接口](docs/api/4-播放视频接口.md)
- [转码接口](docs/api/5-转码接口.md)
- [播放列表接口](docs/api/6-播放列表接口.md)
- [App 接口](docs/api/7-App 接口.md)
- [统计接口](docs/api/8-统计接口.md)

---

## MongoDB 表结构

[MongoDB 表结构](docs/2-MongoDB 表结构.md)

---

## 部署

[部署指南](docs/4-部署.md)

### Docker 部署

```bash
# 构建镜像
docker build -t video-2022:latest .

# 运行容器
docker run -d -p 5022:5022 --name video-2022 video-2022:latest
```

### Docker Compose

```bash
docker-compose up -d
```

---

## 其它文档

- [待办](docs/3-待办.md)
- [部署](docs/4-部署.md)
- [运维](docs/5-运维.md)
- [变更日志](docs/请勿删除/6-变更日志.md)
- [Java 8 Stream Api Examples](docs/7-java8-stream-examples.md)

---

## 开发环境配置

### 1. 安装依赖

```bash
# macOS
brew install mongodb-community redis openjdk@21

# 启动服务
brew services start mongodb-community
brew services start redis
```

### 2. 配置密钥

```bash
# 复制环境变量模板
cp .env.example .env
# 编辑 .env 填入实际的阿里云、百度云等密钥
```

不配置密钥也能启动项目，但 OSS、转码、短信等功能不可用。

### 3. 运行项目

```bash
cd video
mvn spring-boot:run
```

---

## License

MIT
