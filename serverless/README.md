# Serverless API 性能原型

状态：P1 原型，**不是生产后端**。需求：[迁移入口](../docs/requirements/2026-10-14-serverless-backend/README.md)。原 Java 服务、业务域名和媒体函数不受影响。

同一个只读应用提供两个入口：

- `video_serverless.fc_handler.handler`：FC 内置 Python，HTTP event → 响应映射，无 Web Server。
- `video_serverless.web:app`：FastAPI + Uvicorn，自定义运行时/代码包，不需要自建容器。

两种部署包分别使用基础依赖和 `web` extra；不能把带 FastAPI 的全依赖原型包当作已经精简过的生产 Handler 包。Python 版本必须选目标地域实际支持的版本并在相同 Linux 环境构建，不上传 Windows 本机依赖到 FC。

## S01 分层（2026-10-14）

稳定入口仍是上面的 Handler 和 `web:app`，实现按下列方向依赖：

```text
FC / ASGI 外壳 → 通用 HTTP Application → business.Prototype → ports.PublicVideoProbe
                        ↑                                        ↑
              bootstrap 配置/装配                  adapters.mongo 具体实现
```

- `business/` 和 `ports/` 不导入 HTTP、框架、云 SDK 或数据库驱动，只交换普通 Python 结果；依赖边界由测试检查。
- `application.py` 保留既有路由、鉴权、状态/正文与错误脱敏；`http.py` 只保留通用 Request/Response。FC event/Base64/response envelope 在 `adapters/fc.py`，FastAPI 在 `adapters/asgi.py`。
- `bootstrap.py` 按进程读取配置并在线程锁保护下装配一次应用；配置变更通过实例替换生效。Mongo 适配器只在首次读时加载驱动并建池，空请求和健康请求不连接数据库。
- 连接池配置与单条 PUBLIC 视频 ID 投影保持原型原值；Mongo 查询和配置不泄漏到公共用例。实例 ID/序号仅为进程内诊断。
- 目前只实现 Mongo；替换测试适配器不等于已实现 PostgreSQL。没有 OSS/任务用例，不创建空接口目录。

回归同时覆盖两个外壳、并发首次装配/连接复用、错误脱敏和依赖规则。完整构建 wheel 后，在已安装 wheel 上验证两个稳定入口并运行测试，防止只在源码目录能导入。具体结果见 [验收记录](../docs/requirements/2026-10-14-serverless-backend/verification.md)。

## 入口与权限

| 路径 | 行为 |
|---|---|
| `GET /healthCheck` | `ok <毫秒时间戳>`，沿用 Java 文本形式；无 Mongo 访问 |
| `GET /_prototype/empty` | 带鉴权的空请求，用于分离平台开销 |
| `GET /_prototype/mongo-read` | 带鉴权、只读一条 PUBLIC 视频的 ID 投影；不返回 ID、视频内容或连接信息 |

后两个入口要求环境变量 `PROTOTYPE_TOKEN` 和请求头 `X-Prototype-Token`；未配置或错误值拒绝。实例标识/调用序号只用于受保护诊断，不增加业务字段。未实现的原业务路径返回 404，不代理到 Java，不宣称完成迁移。

Mongo 配置为 `MONGODB_URI`、`MONGODB_DATABASE`，必须由取密系统注入。云端使用只读数据库用户；本原型不创建索引、记录、任务或用户。设置最小实例数 0，不通过监控请求保活；测试流量也会产生费用。

## 本地开发与测试

安装 `web` extra 和 dev 依赖；环境/缓存放系统任务临时目录并注册 cleanup，关闭字节码/pytest/ruff 缓存。仓库 `uv.lock` 是持久依赖清单，允许保留。

```text
uv sync --project serverless --extra web
uv run --project serverless --extra web pytest -p no:cacheprovider
uv run --project serverless --extra web uvicorn video_serverless.web:app --host 127.0.0.1 --port 9000
```

运行前将 `UV_PROJECT_ENVIRONMENT` / `UV_CACHE_DIR` 指向系统临时目录；以上命令不代表允许在源目录创建 .venv。

## HTTP 测量

本地新进程模块初始化对比：

```text
uv run --project serverless --extra web video-serverless-startup --count 20
```

每个样本使用全新解释器，分别统计 import 和进程总耗时；它不包含 FC 平台、数据库认证或网络，不能作为 FC 首次请求时间。

```text
uv run --project serverless video-serverless-benchmark https://PROTOTYPE_HOST/_prototype/empty --count 20
uv run --project serverless video-serverless-benchmark https://PROTOTYPE_HOST/_prototype/mongo-read --count 20
```

Token 从环境读取，不放 URL、命令参数或结果文件。输出 JSON 包含每次真实 HTTP 耗时、状态、错误率和实例调用序号；少于 20 个成功样本不报告 P95。失败请求不混入成功延迟统计。工具不跟随重定向，避免泄露 token 或测成其他目标。

`contracts/public-video-list.observed-shape.json` 是从现有线上公开列表取得的单样本字段类型基线，不含视频内容、ID 或签名 URL。null 只表示本样本为空，不能推断字段永远为空；完整接口契约和跨端兼容尚未验收。

实例首次被客户端看到不证明冷启动，进程序号也不包含 FC 平台创建时间；必须结合 FC 初始化日志、函数版本、内存/CPU、包大小、地域、网络和数据库配置确定冷启动样本。原型 Mongo 查询只测有代表性的基础读，不能替代视频列表、鉴权、签名或播放首帧性能验收。

## 部署边界

本阶段没有自动发布流水线，不修改生产配置。未来部署必须走 GitHub Actions，独立函数/环境、受保护入口、无常驻、有限并发；现有 Mongo 网络尚未验证，不能使用腾讯云私网地址假定 FC 可达。切流前仍需全量业务兼容和恢复验收。

CI 已增加独立原型测试任务，使用临时环境和隔离 Mongo 服务，不取生产密钥；本地未配置真实 Mongo 时该集成项必须显示 skipped，不能当作已通过。
