# video-2022 AI Agent

自然语言视频助手：把 `video-cli` 包装成稳定工具面，用模型驱动的 tool-use 循环（`assistant.py`）回答和执行操作，并附带版本化评测体系。

目标不是替代现有 CLI，而是让用户可以问：

- `我上传了几个视频？`
- `我最早上传的视频是什么？`
- `AI 教程播放量是多少？`
- `把 ./demo.mp4 上传成私密视频`

## 目录结构

```text
ai-agent/
├── README.md
├── pyproject.toml / uv.lock   # uv 管理的依赖
├── docs/
│   └── archive/               # 历史计划与实施快照（仅背景参考）
├── evals/
│   ├── README.md              # 评测数据说明与命令入口
│   ├── datasets/              # 版本化 Dataset（smoke / regression / multi-turn）
│   ├── judges/                # LLM Judge rubric
│   ├── schema/                # 用例 JSON Schema
│   ├── sources/               # 场景来源登记
│   └── legacy/                # 旧 seed（仅追溯，代码不加载）
├── fixtures/
│   └── videos.json            # 离线 fixture 数据
├── tests/                     # pytest（离线，CI 运行）
└── video_agent/
    ├── __main__.py            # CLI 入口：ask / chat / eval / serve
    ├── assistant.py           # agent 循环
    ├── client.py              # 模型客户端（OpenAI-compatible）与工具 schema
    ├── tools.py               # 工具执行层（fixture / cli 两种后端）
    ├── config.py              # 环境变量驱动的统一配置
    ├── server.py              # FastAPI 服务（serve 默认）
    ├── server_optimized.py    # serve --optimized（MongoDB 会话持久化、错误处理）
    ├── session_manager.py / context_manager.py / error_handler.py / trace.py
    └── evaluation/            # 评测子包：dataset / graders / judge / langfuse / runner / user_simulator
```

## 安装

```bash
cd ai-agent
uv sync
```

离线 fixture 后端不需要真实视频服务和 OSS；调用真实模型需要 `VIDEO_AGENT_LLM_*` 凭证（见下）。

## 使用

```bash
# 单次提问（fixture 离线后端）
uv run python -m video_agent ask "我上传了几个视频？" --backend fixture

# 交互式对话
uv run python -m video_agent chat --backend fixture

# HTTP 服务（默认 8765 端口；--optimized 启用 MongoDB 会话持久化版本）
uv run python -m video_agent serve --backend fixture --port 8765

# 评测（需要 LLM 凭证；评测数据说明见 evals/README.md）
uv run python -m video_agent eval --suite smoke --trials 1
```

真实后端（`--backend cli`）需要可用的 `video-cli` 登录态，可用 `VIDEO_CLI_BASE_URL` / `VIDEO_CLI_TOKEN` 覆盖。

写操作（上传、删除等）默认要求确认；显式放行用 `--confirm-write` 或 `VIDEO_AGENT_CONFIRM_WRITE=true`。

## 模型配置

不绑定供应商，任何 OpenAI-compatible `/chat/completions` 服务均可：

```bash
export VIDEO_AGENT_LLM_PROVIDER=<provider>
export VIDEO_AGENT_LLM_API_KEY=<key>
export VIDEO_AGENT_LLM_BASE_URL=<base-url>
export VIDEO_AGENT_LLM_MODEL=<model>
```

兼容别名：`OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL` 等，`VIDEO_AGENT_LLM_*` 优先级最高。

## 测试

```bash
uv run pytest tests/ -v
```

除 session manager 测试需要本地 MongoDB（`mongodb://localhost:27017`）外，其余测试全部离线运行（fixture + mock）。CI 的 `AI Agent 测试` Job 自带 MongoDB service。

## 文档导航

- 评测体系当前设计：`../docs/design/video-agent-evaluation.md`
- 评测数据与命令：`evals/README.md`
- 需求与验收记录：`../docs/requirements/2026-08-video-agent-evaluation*`
- 历史计划与实施快照：`docs/archive/`（仅背景参考）
