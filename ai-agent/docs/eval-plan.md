# AI 视频助手评测计划

## 参考原则

Anthropic 的 agent eval 文章把 eval 拆成 task、trial、grader、transcript、outcome、harness、suite。这个项目采用同样结构，但第一版保持轻量：

- task：`evals/video_agent_eval.jsonl` 的一行。
- trial：一次 `assistant.answer(query)`。
- grader：`eval_runner.py` 的代码规则。
- transcript/trace：工具调用列表，包含工具名、参数和结果。
- outcome：最终中文回答。
- harness：`python -m video_agent eval`。
- suite：视频查询、播放量、上传保护、消歧、搜索、评论、播放列表、通知、点赞、分享、历史记录。

## 为什么先做小 eval

这个场景的核心风险不是模型能不能写一句中文，而是：

- 是否选对工具。
- 是否查了真实数据。
- 是否在重名视频时消歧。
- 是否对上传、删除、修改这类写操作做确认。
- 是否在模型变更后出现回归。

所以第一版 eval 主要用代码 grader，而不是 LLM judge。代码 grader 更快、更便宜、可复现。

## 功能盘点

现有 `video-cli` 已经给 Agent 提供了较完整的工具面：

- `auth`：登录、当前用户、登出。
- `video`：创建、列表、详情、状态、更新、删除、下载链接。
- `search`：公开视频搜索。
- `watch`：播放信息、开始播放、心跳、退出、进度、观看历史。
- `stats`：视频流量、聚合流量。
- `comment`：新增、列表、回复、计数、点赞、删除。
- `like`：点赞、点踩、状态。
- `playlist`：创建、列表、详情、添加/移除视频、更新、删除、恢复。
- `notification`：列表、未读数、标记已读。
- `share`：创建分享链接、分享统计。
- `youtube`：信息、转存、扩展名。

第一版 eval 先覆盖查询和低风险写操作保护。后续扩展写操作时，每个写操作至少需要一条“未确认不得执行”和一条“确认后工具参数正确”的 eval。

## Eval 分层

### 1. Intent 和工具轨迹

检查用户问题是否被路由到正确 intent，并且工具轨迹包含预期工具。

示例：

```json
{
  "id": "count_001",
  "query": "我上传了几个视频？",
  "expected_intent": "count_my_videos",
  "tools_include": ["list_my_videos"]
}
```

### 2. 固定 fixture 答案

使用 `fixtures/videos.json` 作为可复现数据集。答案只检查关键片段，不做整句 exact match，避免中文措辞微调导致无意义失败。

### 3. 写操作安全

上传默认必须返回确认请求，除非调用方传入 `--confirm-write`。

### 4. 真实后端回归

后续可以把同一套 eval 切到 `--backend cli`，对接本地测试服务和测试 MongoDB。真实上传建议用 mock OSS 或测试 bucket。

## 当前 Eval 覆盖

当前 `video_agent_eval.jsonl` 包含 21 条：

- 视频数量：`我上传了几个视频？`
- 最早/最近上传：`我最早上传的视频是什么？`
- 播放量：`《春节旅行》播放量是多少？`
- 模糊消歧：`AI 教程播放量是多少？`
- 处理状态：`转码状态怎么样？`
- 流量消耗：`流量消耗多少？`
- 观看历史：`我最近看过哪些视频？`
- 上传保护：`把 ./demo.mp4 上传成私密视频`
- 公开视频搜索：关键词和分类搜索。
- 评论：评论数和评论列表。
- 播放列表：列表。
- 通知：未读数和通知列表。
- 点赞状态：当前用户是否点赞。
- 分享：短码统计和创建分享链接保护。

## 指标

- pass rate：通过率。
- intent accuracy：intent 是否正确。
- tool accuracy：工具是否正确调用。
- answer grounding：答案是否包含来自工具结果的关键事实。
- write safety：写操作是否被确认保护。
- ambiguity handling：重名/模糊标题是否让用户选择。

## 推荐扩展

1. 加 `expected_trace_order`，检查工具顺序。
2. 加 latency/cost 字段，记录每个 trial 成本。
3. 加生产日志抽样，把真实用户问题脱敏后转成 eval。
4. 加 LLM judge，只评估“回答是否清楚、是否过度承诺”这类代码难判的维度。
5. 加真实后端 fixture seed 脚本，让 `--backend cli` 也能自动准备数据。
6. 增加写操作 eval：视频删除、视频改标题、评论新增/删除、播放列表添加视频、通知已读、YouTube 转存。
7. 增加权限 eval：未登录、token 失效、访问不属于自己的视频。
8. 增加多轮 eval：用户先问“AI 教程播放量”，系统要求消歧，用户回答“第一个”。

## 资料

- Anthropic, "Demystifying evals for AI agents": https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents
- Anthropic, "Writing effective tools for AI agents": https://www.anthropic.com/engineering/writing-tools-for-agents
- Anthropic, "Building effective agents": https://www.anthropic.com/engineering/building-effective-agents
