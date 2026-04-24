# AI 视频助手实现说明

## Overview

本实现为 video-2022 新增一个独立的 `ai-agent` 原型目录，用于验证“自然语言 -> 工具调用 -> 中文回答 -> eval 回归”的闭环。

## Solution Approach

实现保持三层：

1. `planner.py`：把用户问题转成结构化 intent。
2. `tools.py`：把 intent 映射到现有 `video-cli` 或离线 fixture。
3. `assistant.py`：执行工具、消歧、组织回答。

这样做的原因是模型输出不稳定，而业务工具必须稳定。LLM 可以换，工具和 eval 不应该跟着重写。

## Component Structure

```text
video_agent/
├── __main__.py       # ask/eval 命令入口
├── assistant.py      # 执行 plan 并生成回答
├── eval_runner.py    # eval harness 和 grader
├── llm.py            # OpenAI-compatible planner client
├── planner.py        # heuristic planner + LLM planner fallback
└── tools.py          # fixture/cli backend 工具封装
```

## Key Components

### Planner

第一版默认使用 `heuristic`，保证离线 eval 稳定。也支持 `--planner llm`，通过 OpenAI-compatible `/chat/completions` 调任意模型供应商。

配置优先级：

1. `VIDEO_AGENT_LLM_*`
2. `OPENAI_*`
3. `MINIMAX_*`

`--planner minimax` 只是兼容别名，不是架构绑定。

### Tool Layer

`VideoTools` 有两个 backend：

- `fixture`：读取 `fixtures/videos.json`，用于本地 eval。
- `cli`：通过 `python -m video_cli.main` 调用现有 CLI，继承 `VIDEO_CLI_BASE_URL` 和 `VIDEO_CLI_TOKEN`。

已实现工具：

- `list_my_videos`
- `get_video_detail`
- `get_video_status`
- `get_video_traffic`
- `watch_history`
- `upload_video`
- `search_public_videos`
- `comment_count`
- `list_comments`
- `list_playlists`
- `unread_notification_count`
- `list_notifications`
- `like_status`
- `share_stats`
- `create_share`

### Upload Flow

真实上传走 HTTP + OSS：

1. `POST /video/create`
2. `GET /file/getUploadCredentials`
3. `oss2.Bucket.put_object_from_file`
4. `GET /file/uploadFinish`
5. `GET /video/rawFileUploadFinish`
6. 可选 `POST /video/updateInfo`

上传默认需要 `--confirm-write`，避免一句自然语言直接改写用户数据。

## Security Measures

- 不读取或写入本机 Claude/OpenClaw 密钥文件。
- 不把 API key 写入仓库。
- 上传等写操作默认只返回确认请求。
- `--backend fixture` 是默认值，eval 不访问真实用户数据。
- `--backend cli` 只通过现有 `video-cli` 和用户已有 token 访问服务。

## Testing

离线 eval：

```bash
cd ai-agent
python -m video_agent eval --backend fixture
```

单条 smoke test：

```bash
python -m video_agent ask "我最早上传的视频是什么？" --backend fixture
```

## Configuration

```bash
VIDEO_CLI_BASE_URL=http://localhost:5022
VIDEO_CLI_TOKEN=...
VIDEO_AGENT_LLM_PROVIDER=minimax
VIDEO_AGENT_LLM_API_KEY=...
VIDEO_AGENT_LLM_BASE_URL=https://api.minimaxi.com/v1
VIDEO_AGENT_LLM_MODEL=MiniMax-M2.7
```

## Dependencies

- `requests`：真实上传时调用后端 API。
- `oss2`：真实上传到阿里云 OSS。

## Known Limitations

- LLM planner 只做 intent routing，工具调用仍由本地代码执行。
- 当前 eval 是小样本，覆盖 MVP 场景，不代表生产质量。
- `--backend cli` 的真实上传依赖 OSS 凭证字段和当前 Web 端一致。
- 没有实现多轮状态管理；重名视频时只返回候选，让上层继续追问。

## Next Steps

1. 把 `video count/first/latest/summary/upload` 补进原有 `cli/`，减少 agent wrapper 里的业务分页逻辑。
2. 增加真实测试服务 seed 脚本，让 `--backend cli` eval 可自动跑。
3. 为 trace 增加耗时、成本、错误类型。
4. 增加 LLM judge 评估回答风格和过度承诺。
