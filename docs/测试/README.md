# 测试文档

项目测试套件总览。本项目包含 Java 后端单元/集成测试、端到端测试以及 Playwright 前端测试三类测试体系。

## 统计

| 类型 | 文件数 | 用例数 |
|------|-------|-------|
| Java 单元/集成测试 | 35 | 400 |
| Java E2E 测试 | 5 | 22 |
| Playwright 前端测试 | 10 | 88 |
| **合计** | **50** | **510** |

## 文档索引

| 模块 | 文档 | 测试文件数 | 用例数 | 覆盖范围 |
|------|------|-----------|-------|---------|
| 用户 | [1-用户模块测试](1-用户模块测试.md) | 3 | 23 | 登录注册、会话管理、客户端管理 |
| 视频 | [2-视频模块测试](2-视频模块测试.md) | 5 | 54 | 视频创建、更新、就绪处理、文件去重链接、YouTube 集成 |
| 文件与存储 | [3-文件与存储测试](3-文件与存储测试.md) | 5 | 55 | 文件管理、访问日志、MD5 校验、OSS 操作、路径工具 |
| 财务 | [4-财务模块测试](4-财务模块测试.md) | 5 | 54 | 计费、钱包、交易、单价、OSS 访问费用 |
| 播放与统计 | [5-播放与统计测试](5-播放与统计测试.md) | 4 | 36 | 观看记录、心跳进度、流量统计、播放会话 |
| 播放列表 | [6-播放列表测试](6-播放列表测试.md) | 1 | 22 | 播放列表 CRUD、视频管理、权限校验 |
| 封面与转码 | [7-封面与转码测试](7-封面与转码测试.md) | 4 | 37 | 封面生成、转码回调、阿里云 MPS、云函数转码 |
| 基础设施 | [8-基础设施测试](8-基础设施测试.md) | 8 | 115 | 参数校验、Redis、钉钉通知、IP 定位、ID 生成、冒烟测试、上传场景 |
| E2E 端到端 | [9-E2E端到端测试](9-E2E端到端测试.md) | 5 | 22 | 登录流程、视频上传、视频修改、视频观看、播放列表 |
| Playwright 前端 | [10-Playwright前端测试](10-Playwright前端测试.md) | 10 | 88 | 行为交互、E2E 流程、前端样式、全局配置、登录/上传/观看/播放器页、UX 组件 |

## 运行方式

### Java 测试

```bash
# 运行全部 Java 测试
export $(grep -v '^#' .env | grep -v '^$' | xargs) && mvn test -pl video -Pspringboot -Dtest="com.github.makewheels.video2022.**" -Dlog4j.configuration=file:///tmp/log4j.properties

# 运行单个测试类
export $(grep -v '^#' .env | grep -v '^$' | xargs) && mvn test -pl video -Pspringboot -Dtest=com.github.makewheels.video2022.user.UserServiceTest -Dlog4j.configuration=file:///tmp/log4j.properties
```

### Playwright 前端测试

```bash
cd video/src/test/playwright && npx playwright test
```

## 项目结构

```
video/src/test/
├── java/com/github/makewheels/video2022/
│   ├── user/           # 用户模块测试
│   ├── video/          # 视频模块测试
│   ├── file/           # 文件模块测试
│   ├── oss/            # OSS 存储测试
│   ├── finance/        # 财务模块测试
│   ├── watch/          # 播放模块测试
│   ├── playlist/       # 播放列表测试
│   ├── cover/          # 封面测试
│   ├── transcode/      # 转码测试
│   ├── etc/            # 基础设施测试 (check/redis/ding/statistics)
│   ├── utils/          # 工具类测试
│   ├── scenario/       # 场景测试
│   └── e2e/            # E2E 端到端测试
└── playwright/tests/   # Playwright 前端测试
```
