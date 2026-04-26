# AI 视频助手实施说明

## Overview

本实现为 video-2022 构建一个完整的 AI Agent，支持自然语言管理视频平台。基于模型驱动的 tool-use 循环，不绑定特定模型供应商。

## 架构

```
用户输入（自然语言）
    ↓
assistant.py（agent 循环）
    ↓  ←→ client.py（模型客户端，OpenAI-compatible）
    ↓  ←→ tools.py（工具执行，38 个工具）
    ↓
video-cli / fixture → 后端 API
```

三层设计：
1. **client.py**：模型抽象层，OpenAI-compatible `/chat/completions` 格式，支持任意供应商（MiniMax / DeepSeek / OpenAI / Moonshot / ...）
2. **tools.py**：工具执行层，38 个工具覆盖全部 CLI 命令组，支持 fixture（离线）和 cli（真实后端）两种模式
3. **assistant.py**：Agent 循环，模型决定何时调工具、何时追问、何时确认

## v0.2.0 变更（2026-04-26）

### 新增文件

| 文件 | 说明 |
|------|------|
| `video_agent/config.py` | 统一配置管理，环境变量驱动 |
| `video_agent/client.py` | 模型客户端 + 38 个工具 JSON Schema 定义 |
| `video_agent/server.py` | FastAPI HTTP API 服务 |

### 重写文件

| 文件 | 变更 |
|------|------|
| `video_agent/assistant.py` | 从意图路由改为模型驱动的 tool-use 循环，支持流式输出和多轮对话 |
| `video_agent/tools.py` | 从 15 个工具扩展到 38 个，覆盖全部 CLI 命令组 |
| `video_agent/__main__.py` | 新增交互式对话模式（`chat` 命令）、HTTP 服务模式（`serve` 命令），优化 ask/eval |
| `video_agent/eval_runner.py` | 增强 grader，写操作安全检查 |

### 扩展数据

| 文件 | 变更 |
|------|------|
| `fixtures/videos.json` | 从 4 个视频扩展到 8 个，新增音乐/游戏/科技/体育类，丰富评论/通知/播放列表/观看历史 |
| `evals/video_agent_eval.jsonl` | 从 21 条扩展到 49 条，覆盖查询、搜索、消歧、写操作保护、跨实体查询、边界场景 |

### Tool Coverage

当前 38 个工具覆盖 CLI 全部 11 个命令组：

- **video**：list, detail, status, traffic, update, delete, upload, download-url
- **comment**：count, list, add, delete, like, replies
- **playlist**：list, detail, create, delete, update, add-video, remove-video
- **notification**：unread-count, list, mark-read, mark-all-read
- **like**：status, like, dislike
- **share**：stats, create
- **watch**：history, progress, clear-history
- **search**：search（支持关键词 + 分类）
- **stats**：aggregate traffic
- **auth**：me
- **youtube**：info, transfer

## Configuration

```bash
# 模型选择（必填）
export VIDEO_AGENT_LLM_PROVIDER=minimax       # minimax | deepseek | openai | moonshot | ...
export VIDEO_AGENT_LLM_API_KEY=your-key
export VIDEO_AGENT_LLM_BASE_URL=https://api.minimaxi.com/v1  # 可选，自动推断
export VIDEO_AGENT_LLM_MODEL=MiniMax-M2.7      # 可选，自动推断

# 工具后端
export VIDEO_AGENT_BACKEND=fixture             # fixture（离线）| cli（真实后端）
export VIDEO_CLI_BASE_URL=http://localhost:5022
export VIDEO_CLI_TOKEN=your-token
```

## Testing

```bash
cd ai-agent

# 离线单条
python -m video_agent ask "我上传了几个视频？" --backend fixture

# 离线流式
python -m video_agent ask "AI 教程播放量是多少？" --backend fixture -s

# 离线 eval（49 条）
python -m video_agent eval --backend fixture

# 交互式对话
python -m video_agent chat --backend fixture

# HTTP API 服务
python -m video_agent serve --backend fixture

# 真实后端（需要 CLI 登录）
python -m video_agent ask "我上传了几个视频？" --backend cli
python -m video_agent chat --backend cli

# 指定模型
python -m video_agent ask "..." --backend fixture --model deepseek-chat
```

## Known Limitations

- 多轮对话：当前 `chat` 命令支持，但 context 管理简单（累积消息，无自动压缩）
- Streaming：助手的 chat_stream 有流式输出，但 eval 模式用非流式
- 真实上传：依赖 oss2 和有效的 STS 凭证
- 写操作：默认需要 `--confirm-write`，fixture 模式下不会实际写入

## Next Steps

1. 接入 MiniMax API 并跑一轮完整 eval，确认模型切换后通过率
2. 增加真实后端 seed 脚本，让 `--backend cli` eval 可自动跑
3. 多轮对话的 session 持久化（Redis 或文件）
4. Web Chat UI 集成到现有前端
5. 增加 LLM judge 评估回答风格和过度承诺
6. 生产日志抽样，把真实用户问题脱敏后转成 eval
