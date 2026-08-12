# Langfuse 项目隔离设计

> 日期：2026-08-12
> 关联：[requirements.md](requirements.md)

## 总体思路

评测代码本身已经是 project 中立的：`video_agent/evaluation/eval_langfuse.py` 只通过 `LANGFUSE_HOST` / `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` 三个环境变量决定写入哪个 project，Dataset 命名（`video-2022/evals/<suite>-v1`）也不含 project 归属。因此隔离修复不需要改代码，只需要：

1. 在 Langfuse 开通独立 project 与专用 API Key；
2. 把本地环境文件指向新 Key；
3. 重新同步 Dataset 并重跑评测；
4. 用 API 验证归属，并把结果固化到文档。

## Project 与 API Key 开通

- 实例：腾讯云自托管 Langfuse `3.224.1`。
- 开通路径：管理员账号登录 Web 会话，调用 Langfuse 前端使用的 tRPC 接口（`projects.create`、`projectApiKeys.create`）完成，与 UI 操作等价；不开通 organization 级 API Key，避免扩大权限面。
- Project 命名：`video-2022`，与仓库名一致。
- API Key 为 project 级（scope=PROJECT），只授予 video-2022；从机制上保证评测进程无法读写 speakup。

## 密钥管理

- 三个 `LANGFUSE_*` 变量只写入 `ai-agent/.env`；该文件被 `ai-agent/.gitignore` 第一条规则忽略，仓库内由 `ai-agent/.env.example` 提供占位说明。
-  provisioning 与验证脚本一律从 `.env` 读取密钥，不在终端、日志、文档中回显 `LANGFUSE_SECRET_KEY`。
- 历史 speakup 的 Key 不写入本仓库任何文件。

## Dataset 同步与评测重跑

- Dataset 事实源是 Git 中的 JSON（`ai-agent/evals/datasets/video_agent_{smoke,regression,multi_turn}_v1.json`），同步器幂等（稳定 UUID upsert），换 project 后直接重跑 `eval --suite <s> --langfuse` 即可完成同步 + Experiment。
- 重跑矩阵与废弃基线保持一致，便于对照：
  - Smoke：`--trials 3`（`pass^3` 稳定性）；
  - Regression：`--trials 1`；
  - Multi-turn：`--trials 1 --judge`。
- 评测对象模型：原基线所用模型在当前供应商账号下不可用（未开通/凭证失效），本次重跑使用同一供应商账号下已开通的旗舰文本模型；模型名记录在每次 Run 的 metadata 与本需求 verification 文档中。该变更不影响"项目隔离"结论，但与废弃基线的分数不可直接对比。

## 历史数据处理

- speakup project 中的 video-2022 历史 Dataset / Run 一律保留、不删除。
- 在原评测文档中将其标记为"错误 project 下的废弃基线"，新基线以 video-2022 project 中的 Run 为准。

## 验证设计

1. 用新 Key 调 Langfuse Public API：确认只能看到 video-2022 project（验收 #2、#7）。
2. Dataset API：确认三套 Dataset 位于新 project 且 item 数 15/49/5（验收 #4）。
3. Dataset Run API + URL 抽查：确认 Run/Trace/Score 属于新 project（验收 #6）。
4. 管理会话复核 speakup 中历史对象仍存在（验收 #8）。
5. `uv run pytest -q` 全绿（验收 #9）。
