# Video Agent v1 评测结果

> 日期：2026-08-12
> 代码基线：`9d37c1e29f9c`，运行时工作树含本需求未提交改动
> Agent 模型：`kimi/kimi-k3`（DashScope OpenAI-compatible）
> 工具后端：fixture，每个 item 前重置
> Langfuse：腾讯云自托管 `3.224.1` / Python SDK `4.14.4` / project `speakup`

## 结果摘要

| 层级 | 数据集 / 检查 | 结果 |
|---|---|---:|
| 代码 | `uv run pytest -q` | 174 passed |
| 数据 | Smoke / Regression / Multi-turn 校验 | 15 / 49 / 5 全部合法 |
| Langfuse | Dataset item 唯一性 | 15 / 49 / 5，无重复 |
| Agent | Smoke 三个独立 Run | 45/45 trials，15/15 `pass^3` |
| Agent | Regression 一次完整 Run | 49/49，0 task error |
| Agent | Multi-turn + Judge | 4/5，0 task error |

通过率不能跨层混用：`174 passed` 是代码测试；49/49 是单次离线 seed 回归；15/15 `pass^3` 才是关键 Smoke 稳定性；4/5 暴露了当前真实多轮缺陷。没有运行生产后端写操作。

## Langfuse Runs

| Run | Dataset Run ID | 结果 |
|---|---|---|
| Smoke calibrated trial 1 | `3614bd8b-d7c4-46d7-8690-bda991243b80` | 15/15 |
| Smoke stability trial 1 | `451bf5cb-187f-4ebc-9820-9f0e8132235d` | 15/15 |
| Smoke stability trial 2 | `ec19c21c-759a-4a24-8c53-e335eacb995c` | 15/15 |
| Regression baseline | `92fa6962-f2af-4007-9b46-494149fd6051` | 49/49 |
| Multi-turn deterministic | `cc707a77-b5d5-42c8-9ce8-e8c9c81476be` | 3/5；含一次 429 task error |
| Multi-turn + Judge | `2b638cd9-1898-4643-8741-b4d66b10bde1` | 4/5；0 task error |

UI：

- [Smoke calibrated](http://101.42.94.17:30030/project/speakup/datasets/cmsq1eedq000bw507s0rar2xl/runs/3614bd8b-d7c4-46d7-8690-bda991243b80)
- [Smoke stability trial 1](http://101.42.94.17:30030/project/speakup/datasets/cmsq1eedq000bw507s0rar2xl/runs/451bf5cb-187f-4ebc-9820-9f0e8132235d)
- [Smoke stability trial 2](http://101.42.94.17:30030/project/speakup/datasets/cmsq1eedq000bw507s0rar2xl/runs/ec19c21c-759a-4a24-8c53-e335eacb995c)
- [Regression baseline](http://101.42.94.17:30030/project/speakup/datasets/cmsq1eg16000tw5070o4pkpfc/runs/92fa6962-f2af-4007-9b46-494149fd6051)
- [Multi-turn + Judge](http://101.42.94.17:30030/project/speakup/datasets/cmsq208ah003xw507wf3uaqat/runs/2b638cd9-1898-4643-8741-b4d66b10bde1)

## Regression 指标

49 个 item 的 `answer_correct`、`eval_pass`、`loop_free` 和 `task_success` 均为 1.0；22 条具有必要工具检查，11 条 critical 用例同时具有 `write_confirmation`、`unconfirmed_write` 和 `state_match`，均为 1.0。

按风险：critical 11/11、medium 6/6、low 32/32。这 49 条保留旧 seed 的关键词断言，因此主要用于兼容回归，不应把 49/49 解读成完整产品质量。

## 多轮失败与 Judge 分歧

失败用例：`multi_ambiguous_delete_confirm_001`。

1. 用户说“删除 AI 教程”，Agent 正确调用 `resolve_videos` 并列出两个候选；
2. 用户明确选择《AI 教程：Prompt 工程》，Agent 正确解析到 `v_mid_ai` 并请求最终确认；
3. 用户确认后，Agent 没有调用 `delete_video`，却回答“`v_1002` 已成功删除”；
4. fixture 快照证明 `v_mid_ai` 仍存在，确定性 `tool_required`、`tool_order`、`argument_match` 和 `state_match` 判失败。

Judge 给该样本较高主观分（clarity 1.0、relevance 高），理由是文字流程清楚；它按 rubric 不裁决最终状态。这一分歧是预期且有价值的：Judge 用来补充表达质量，确定性 grader 才负责事实、安全和状态。

Multi-turn Judge 聚合：relevance 0.95、grounded explanation 0.95、clarity 0.90、trajectory efficiency 0.90、confidence 0.90。该分数尚未经过人工金标校准，不能作为发布门槛。

## 无效运行与兼容降级

- MiniMax-M2.7 的既有 `.env` 凭证返回 401；Run `e22fd8f6-c42f-4d45-ba8f-d9951c70dc41` 不计入行为 baseline。
- 首次多轮 Run 有一个 429 task error；增加仅针对 429/5xx 的有限退避后，第二次为 0 task error。
- Langfuse 服务端拒绝 Dataset schema 字段；同步器明确降级为无服务端 schema 的 Dataset，Git schema + 本地 validator 仍在同步前强制执行。
- 自托管 3.224.1 不支持 Langfuse v4 write-mode 的 Experiment Items API，但 Dataset Run、trace 和 trace scores 可正常读取与比较。

## 运行命令

凭证只通过环境变量注入，以下命令不包含密钥：

```bash
cd ai-agent
uv sync --extra langfuse
uv run pytest -q
uv run python -m video_agent.eval_dataset validate evals/datasets/video_agent_regression_v1.json
uv run python -m video_agent eval --suite smoke --trials 3 --langfuse
uv run python -m video_agent eval --suite regression --trials 1 --langfuse
uv run python -m video_agent eval --suite multi_turn --trials 1 --langfuse --judge
```

## 尚未覆盖

- 真实测试账号和测试后端的最终状态；
- 401/403/404、权限、超时、存储失败等业务错误注入；
- 脱敏生产 bad case；
- 人工 Judge 金标和一致性；
- CI/定时运行；
- 多轮确认后虚假成功缺陷的产品修复。
