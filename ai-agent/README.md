# video-2022 AI Agent

这个目录是一版独立的自然语言视频助手原型。它把现有 `video-cli` 包成稳定工具，再在上面提供 planner、执行器和 eval harness。

目标不是替代现有 CLI，而是让用户可以问：

- `我上传了几个视频？`
- `我最早上传的视频是什么？`
- `AI 教程播放量是多少？`
- `把 ./demo.mp4 上传成私密视频`

## 目录结构

```text
ai-agent/
├── README.md
├── requirements.txt
├── docs/
│   ├── eval-plan.md
│   └── implementation.md
├── evals/
│   └── video_agent_eval.jsonl
├── fixtures/
│   └── videos.json
└── video_agent/
    ├── __main__.py
    ├── assistant.py
    ├── eval_runner.py
    ├── llm.py
    ├── planner.py
    └── tools.py
```

## 安装

```bash
cd ai-agent
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

如果只跑离线 eval，不需要真实视频服务、不需要模型 API、不需要 OSS。

## 离线试用

```bash
python -m video_agent ask "我上传了几个视频？" --backend fixture
python -m video_agent ask "我最早上传的视频是什么？" --backend fixture
python -m video_agent ask "AI 教程播放量是多少？" --backend fixture
python -m video_agent eval --backend fixture
```

## 真实 CLI 后端

先保证原有 CLI 可用：

```bash
cd ../cli
pip install -e .
video-cli auth login --phone 13800138000 --code 111
```

然后回到 `ai-agent`：

```bash
python -m video_agent ask "我上传了几个视频？" --backend cli
python -m video_agent ask "最近上传的视频状态怎么样？" --backend cli
```

可以用环境变量覆盖服务地址和 token：

```bash
VIDEO_CLI_BASE_URL=http://localhost:5022 \
VIDEO_CLI_TOKEN=your-token \
python -m video_agent ask "我上传了几个视频？" --backend cli
```

## LLM Planner

默认 planner 是确定性规则，方便 eval 稳定复现。要接真实模型，用通用 `llm` planner：

```bash
export VIDEO_AGENT_LLM_PROVIDER=minimax
export VIDEO_AGENT_LLM_API_KEY=...
export VIDEO_AGENT_LLM_BASE_URL=https://api.minimaxi.com/v1
export VIDEO_AGENT_LLM_MODEL=MiniMax-M2.7
python -m video_agent ask "AI 教程播放量是多少？" --backend fixture --planner llm
```

不绑定 MiniMax。换成任何 OpenAI-compatible `/chat/completions` 供应商时，只替换这组变量：

```bash
export VIDEO_AGENT_LLM_PROVIDER=openrouter
export VIDEO_AGENT_LLM_API_KEY=...
export VIDEO_AGENT_LLM_BASE_URL=https://openrouter.ai/api/v1
export VIDEO_AGENT_LLM_MODEL=...
```

兼容别名：

```bash
OPENAI_API_KEY / OPENAI_BASE_URL / OPENAI_MODEL
MINIMAX_API_KEY / MINIMAX_BASE_URL / MINIMAX_MODEL
```

优先级是 `VIDEO_AGENT_LLM_*` 最高。`--planner minimax` 仍可用，但只是兼容旧命令，推荐统一使用 `--planner llm`。

## 当前覆盖的自然语言能力

基于现有 `video-cli`，第一版已覆盖：

- 我的视频：数量、最早上传、最近上传、列表查询。
- 视频详情：播放量、处理状态、流量消耗。
- 上传：真实上传实现已接入，但默认确认保护。
- 搜索：公开视频关键词/分类搜索。
- 评论：评论数、评论列表。
- 播放列表：播放列表列表。
- 通知：未读数量、通知列表。
- 点赞：当前用户点赞/点踩状态。
- 分享：分享短码统计、创建分享链接确认保护。
- 观看：观看历史。

现有 CLI 还有 YouTube 转存、评论新增/删除/点赞、播放列表增删改、通知已读、视频更新/删除、点赞/点踩等写操作。建议后续统一纳入确认机制和更细 eval。

## 上传

上传是写操作，默认不会真实执行。真实上传需要：

```bash
python -m video_agent ask "把 ./demo.mp4 上传成私密视频" \
  --backend cli \
  --confirm-write
```

真实上传流程：

1. `/video/create`
2. `/file/getUploadCredentials`
3. OSS 上传
4. `/file/uploadFinish`
5. `/video/rawFileUploadFinish`

## Eval 思路

这版参考 Anthropic 的 agent eval 建议：

- task：一个用户问题和成功标准
- trial：一次运行
- grader：代码规则检查 intent、答案片段、工具轨迹
- transcript/trace：记录 planner 和工具调用
- outcome：最终答案和工具返回的数据
- harness：`video_agent eval`

当前内置 eval 覆盖 21 条任务，包括用户举例和补充功能：视频数量、最早/最近上传、播放量、状态、流量、观看历史、上传保护、公开视频搜索、评论、播放列表、通知、点赞状态、分享统计、分享创建保护。

参考资料：

- Anthropic, "Demystifying evals for AI agents": https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents
- Anthropic, "Writing effective tools for AI agents": https://www.anthropic.com/engineering/writing-tools-for-agents
- Anthropic, "Building effective agents": https://www.anthropic.com/engineering/building-effective-agents
