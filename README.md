# Video 2022

一个视频分享平台后端服务，支持视频上传、转码、播放、YouTube 下载等功能。

**示例视频**: https://oneclick.video/w?v=17GO2A

**上传入口**: https://oneclick.video/upload.html

**验证码**: 111

## 文档地图

> 所有文档入口索引，方便快速定位。

| 文档 | 说明 |
|------|------|
| **本文件 (README.md)** | 项目概览、快速开始、架构说明 |
| [关键设计](docs/1-关键设计.md) | 系统架构总览、业务文档导航中心 |
| [MongoDB 表结构](docs/归档/2-MongoDB表结构.md) | 所有 Collection 的字段定义 |
| [部署指南](docs/归档/4-部署.md) | 生产环境部署步骤 |
| [运维手册](docs/归档/5-运维.md) | 数据库迁移脚本、运维操作 |
| **API 接口文档** | 见下方 [API 接口文档](#api-接口文档) 章节 |
| **业务文档** | 见下方 [业务文档](#业务文档) 章节 |

## 快速开始

### 环境要求
- Java 21
- Maven 3.8+
- MongoDB 6.0+
- Redis 7.0+

### 构建与运行

```bash
# 1. 安装依赖 (macOS)
brew install mongodb-community redis openjdk@21
brew services start mongodb-community
brew services start redis

# 2. 配置密钥
cp .env.example .env
# 编辑 .env 填入阿里云 AccessKey 等密钥
# 不配置密钥也能启动，但 OSS、转码等功能不可用

# 3. 构建
mvn clean package -pl video -Pspringboot -Dmaven.test.skip=true

# 4. 启动
export $(grep -v '^#' .env | grep -v '^$' | xargs)
java -jar video/target/video-0.0.1-SNAPSHOT.jar

# 5. 访问
# 上传页面: http://localhost:5022/upload.html
# 健康检查: http://localhost:5022/healthCheck
```

默认端口：`5022`，配置文件：`video/src/main/resources/application.properties`

---

## 项目架构

多模块 Maven 项目：
- `video/` - 核心视频服务模块 (Spring Boot 应用)
- `youtube/` - YouTube 视频下载服务模块 (独立部署到阿里云函数计算)

### 核心模块分包结构 (video 模块)

包路径前缀：`com.github.makewheels.video2022`

| 包名 | 职责 |
|------|------|
| `user/` | 用户认证（手机验证码登录）、Session 管理、Client 管理 |
| `video/` | 视频实体、创建、状态管理、元数据 |
| `file/` | 文件上传、OSS STS 凭证、TsFile（HLS 分片）管理 |
| `transcode/` | 视频转码，工厂模式，支持 MPS / 云函数 / GPU 云函数 |
| `watch/` | 视频播放、心跳上报、播放进度保存 |
| `playlist/` | 播放列表 CRUD、排序 |
| `cover/` | 视频封面提取与管理 |
| `oss/` | 阿里云 OSS 服务封装（视频 bucket + 数据 bucket） |
| `finance/` | 账单、钱包、流量费用统计 |
| `basebean/` | 基础实体类 |
| `etc/` | 杂项工具（IP 查询、异常日志、健康检查、App 更新） |
| `springboot/` | Spring Boot 配置类（拦截器、全局异常处理） |
| `system/` | 系统级工具 |
| `utils/` | 通用工具类 |

### 技术栈

- **Java 21** + **Spring Boot 4.0.3**
- **MongoDB** - 主数据库（存储视频、用户、文件、转码等所有业务数据）
- **Redis** - 缓存（IP 地理位置查询缓存、验证码存储）
- **阿里云 OSS** - 视频文件存储（video bucket）+ 日志/库存存储（data bucket）
- **阿里云 MPS** - 视频转码服务（480p/720p/1080p HLS）
- **阿里云函数计算** - GPU 云函数转码、MD5 计算
- **阿里云 API 网关** - IP 地理位置查询
- **Thymeleaf** - 前端页面模板引擎

### 云服务依赖

| 服务 | 用途 | 环境变量 |
|------|------|----------|
| OSS (视频 bucket) | 存储原始视频、转码后 HLS 文件、封面 | `ALIYUN_OSS_VIDEO_*` |
| OSS (数据 bucket) | 存储 OSS 访问日志、库存报告 | `ALIYUN_OSS_DATA_*` |
| MPS 转码 | 视频转码为多分辨率 HLS 格式 | `ALIYUN_MPS_*` |
| API 网关 | IP → 地理位置查询 | `ALIYUN_APIGATEWAY_IP_APPCODE` |

---

## 关键设计

详见 [关键设计文档](docs/1-关键设计.md)

### 视频上传流程

```
用户 → POST /video/create（创建视频记录）
     → GET /file/getUploadCredentials（获取 STS 临时凭证）
     → 直传 OSS（前端使用 STS 凭证直接上传到阿里云）
     → GET /file/uploadFinish（通知后端上传完成）
     → GET /video/rawFileUploadFinish（触发转码）
     → 转码服务异步处理 → 回调通知完成
     → HLS 文件写入 OSS → 数据库更新状态
```

### 视频播放流程

```
用户 → GET /w?v={watchId}（打开播放页面）
     → GET /watchController/getWatchInfo（获取视频信息）
     → GET /watchController/getMultivariantPlaylist.m3u8（自适应码率主播放列表）
     → GET /watchController/getM3u8Content.m3u8（单分辨率 TS 分片列表）
     → GET /file/access?sign=...（带签名访问 TS 分片文件）
     → POST /heartbeat/add（定时心跳上报播放进度）
```

### 转码服务 (工厂模式)

位置：`video/src/main/java/.../transcode/`

三种转码实现，按优先级选择：
1. `AliyunCfGPUTranscodeImpl` - GPU 云函数 (最快)
2. `AliyunMpsTranscodeImpl` - 阿里云 MPS
3. `AliyunCfTranscodeImpl` - 普通云函数

输出格式：HLS（.m3u8 + .ts 分片），支持 480p / 720p / 1080p 三种分辨率。

---

## API 接口文档

所有接口以 `http://localhost:5022` 为基础 URL。需要认证的接口通过 HTTP Header `token: {value}` 传递登录令牌。

| 模块 | 文档 | 说明 |
|------|------|------|
| 用户 | [用户接口](docs/api/1-用户接口.md) | 验证码登录、用户信息查询 |
| 上传 | [上传视频接口](docs/api/2-上传视频接口.md) | 创建视频、获取上传凭证、上传完成通知 |
| YouTube | [YouTube 接口](docs/api/3-YouTube接口.md) | YouTube 视频下载与导入 |
| 播放 | [播放视频接口](docs/api/4-播放视频接口.md) | HLS 播放、心跳、进度 |
| 转码 | [转码接口](docs/api/5-转码接口.md) | 转码回调 |
| 播放列表 | [播放列表接口](docs/api/6-播放列表接口.md) | 播放列表 CRUD |
| App | [App 接口](docs/api/7-App接口.md) | 客户端更新检查 |
| 统计 | [统计接口](docs/api/8-统计接口.md) | 流量消耗统计 |

### 认证方式

```bash
# 1. 请求验证码
curl "http://localhost:5022/user/requestVerificationCode?phone=18812345678"

# 2. 提交验证码获取 token（测试环境验证码固定为 111）
curl "http://localhost:5022/user/submitVerificationCode?phone=18812345678&code=111"
# 响应中的 data.token 即为登录令牌

# 3. 后续请求携带 token
curl -H "token: {your_token}" "http://localhost:5022/video/getMyVideoList"
```

---

## 业务文档

> 每个业务域的详细设计文档，包含 Mermaid 流程图、代码调用链、数据模型。

| 文档 | 说明 |
|------|------|
| [视频上传与去重](docs/业务/1-视频上传与去重.md) | 上传流程、STS 凭证、MD5 去重、文件链接 |
| [视频转码](docs/业务/2-视频转码.md) | 工厂模式、MPS/云函数/GPU、状态机、回调 |
| [视频播放](docs/业务/3-视频播放.md) | HLS 自适应码率、签名访问、心跳、进度恢复 |
| [视频存储](docs/业务/4-视频存储.md) | OSS 双 Bucket、路径规则、Presigned URL、日志/库存 |
| [YouTube 下载](docs/业务/5-YouTube下载.md) | 云函数部署、yt-dlp、文件传输、封面迁移 |
| [用户与设备](docs/业务/6-用户与设备.md) | 手机认证、Token、拦截器、设备识别 |
| [封面提取](docs/业务/7-封面提取.md) | 帧提取、YouTube 缩略图、签名 URL |
| [计费系统](docs/业务/8-计费系统.md) | 流量费用、钱包、账单、自动计费任务 |
| [播放列表](docs/业务/9-播放列表.md) | CRUD、排序、分享 |
| [系统服务](docs/业务/10-系统服务.md) | ID 生成、钉钉通知、异常处理、IP 定位、定时任务 |

---

## MongoDB 表结构

[MongoDB 表结构](docs/2-MongoDB表结构.md)

---

## 部署

[部署指南](docs/4-部署.md)

### Docker 部署

```bash
# 构建镜像
docker build -f video/Dockerfile-video -t video-2022:latest .

# 运行容器
docker run -d -p 5022:5022 --name video-2022 video-2022:latest
```

---

## 其它文档

- [运维手册](docs/归档/5-运维.md)
- [变更日志](docs/归档/6-变更日志.md)

---

## 开发规范

- **禁止直接 push master**，所有变更必须通过 PR 合并
- 一个功能/修复对应一个 PR
- 每个 PR 合并后，在 [CHANGELOG.md](docs/CHANGELOG.md) 记录关键变更
- PR 标题格式：`类型: 简述`（类型：feat / fix / test / docs / refactor / chore）

---

## 开发记录

完整变更日志见 [CHANGELOG.md](docs/CHANGELOG.md)

| PR | 内容 |
|----|------|
| [#18](https://github.com/makewheels/video-2022/pull/18) | 测试套件全面改进 — 删除垃圾测试、新增 250+ 测试、Playwright 行为测试 |
| [#17](https://github.com/makewheels/video-2022/pull/17) | 前端全面优化 — 导航栏、首页重设计、响应式、移动端适配 |
| [#16](https://github.com/makewheels/video-2022/pull/16) | E2E 端到端测试 — 登录、上传、修改、播放、播放列表 |
| [#15](https://github.com/makewheels/video-2022/pull/15) | UX 优化 — Toast 提示、输入验证、空状态提示 |
| [#14](https://github.com/makewheels/video-2022/pull/14) | 前端重新设计 — YouTube 风格、深色/浅色主题 |
| [#13](https://github.com/makewheels/video-2022/pull/13) | 综合测试套件 — 130 个测试、12 个测试类 |
| [#12](https://github.com/makewheels/video-2022/pull/12) | 文档目录整理 — 老文档移入归档 |
| [#11](https://github.com/makewheels/video-2022/pull/11) | 业务文档 — 10 篇业务流程文档（3,668 行） |
| [#10](https://github.com/makewheels/video-2022/pull/10) | Spring Boot 4.x 兼容性修复 + 文档更新 |
| [#9](https://github.com/makewheels/video-2022/pull/9) | Spring Boot 4.x 启动修复 |
| [#8](https://github.com/makewheels/video-2022/pull/8) | Copilot 配置迁移 + Skills 体系 |
| [#7](https://github.com/makewheels/video-2022/pull/7) | Spring Boot 3.4.1 → 4.0.3 |
| [#6](https://github.com/makewheels/video-2022/pull/6) | 清理 CLAUDE.md |
| [#5](https://github.com/makewheels/video-2022/pull/5) | 密钥管理迁移到 .env |
| [#4](https://github.com/makewheels/video-2022/pull/4) | Java 21 支持 |
| [#3](https://github.com/makewheels/video-2022/pull/3) | Spring Boot 2.7.11 → 3.4.1 大版本升级 |
| [#2](https://github.com/makewheels/video-2022/pull/2) | Lombok 与 Java 21 编译修复 |

---

## License

MIT
