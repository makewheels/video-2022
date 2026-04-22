# 测试文档

项目测试体系总览。当前仓库同时维护：

- Java 后端单元 / 集成测试
- Python API 端到端测试
- Python 浏览器端到端测试（Playwright）
- Web / CLI 单元测试

## 统计

> 统计基于当前仓库 `collect-only` / 源码扫描结果（2026-04-22）。

| 类型 | 目录 | 文件数 | 用例数 |
|------|------|--------|--------|
| Java 单元 / 集成测试 | `server/video/src/test/java` | 49 | 558 |
| Python API E2E | `test/api` | 12 | 75 |
| Python 浏览器 E2E | `test/browser` | 12 | 80 |
| Web 单元测试 | `web/tests` | 12 | 38 |
| CLI 单元测试 | `cli/tests` | 15 | 105 |
| CLI 冒烟测试 | `test/cli` | 2 | 7 |
| **合计** |  | **102** | **863** |

## 文档索引

| 模块 | 文档 / 目录 | 覆盖范围 |
|------|-------------|---------|
| 用户 | [1-用户模块测试](1-用户模块测试.md) | 登录注册、会话管理、客户端管理 |
| 视频 | [2-视频模块测试](2-视频模块测试.md) | 视频创建、更新、删除、就绪处理、评论、点赞 |
| 文件与存储 | [3-文件与存储测试](3-文件与存储测试.md) | 文件管理、OSS、访问日志、MD5 |
| 财务 | [4-财务模块测试](4-财务模块测试.md) | 计费、钱包、交易、流量费用 |
| 播放与统计 | [5-播放与统计测试](5-播放与统计测试.md) | 观看记录、心跳、播放进度、播放会话 |
| 播放列表 | [6-播放列表测试](6-播放列表测试.md) | 播放列表 CRUD、视频管理、权限校验 |
| 封面与转码 | [7-封面与转码测试](7-封面与转码测试.md) | 封面生成、转码回调、MPS、云函数 |
| 基础设施 | [8-基础设施测试](8-基础设施测试.md) | 参数校验、Redis、IP、ID 生成、场景测试 |
| API 端到端 | [9-E2E端到端测试](9-E2E端到端测试.md) | `test/api/` 下的 requests 测试 |
| 浏览器端到端 | [10-Playwright前端测试](10-Playwright前端测试.md) | `test/browser/` 下的 Python + Playwright 测试 |
| Web 单元测试 | `web/tests/` | React 组件 / 页面测试 |
| CLI 单元测试 | `cli/tests/` | Click 命令、配置、输出、客户端封装 |
| CLI 冒烟测试 | `test/cli/` | 基于已安装 CLI 的基础验证 |

## 运行方式

### Java 后端测试

```bash
cd server
mvn test -pl video -Pspringboot
```

### Python API E2E

```bash
python3 -m pytest test/api
```

### Python 浏览器 E2E

```bash
python3 -m pytest test/browser
```

### Web 单元测试

```bash
cd web
npx vitest run
```

### CLI 单元测试

```bash
cd cli
python3 -m pytest tests
```

## 当前目录结构

```text
server/video/src/test/java/    # Java 后端测试
test/api/                      # Python API 端到端测试
test/browser/                  # Python 浏览器端到端测试（Playwright）
test/cli/                      # CLI 冒烟测试
web/tests/                     # React 单元测试
cli/tests/                     # CLI 单元测试
```

> 历史文档里如果出现 `video/src/test/playwright`，那是旧路径；当前浏览器测试已经迁移到 `test/browser/`。
