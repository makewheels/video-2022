# Langfuse 项目隔离需求

> 状态：已完成
> 日期：2026-08-12
> 关联需求：[2026-08-video-agent-evaluation](../2026-08-video-agent-evaluation/)

## 背景

`2026-08-video-agent-evaluation` 交付时，video-2022 的 Smoke、Regression、Multi-turn 三套 Dataset 及全部 Experiment Run 都写入了 Langfuse 的 `speakup` project（属于另一个产品的观测空间）。两个产品的 Dataset、Trace、Score 混在同一 project 中：

- video-2022 的评测数据污染 speakup 的观测视图，反之亦然；
- 无法按 project 独立管理 video-2022 的 API Key、成员和保留策略；
- Run URL 中的 project 段是 `speakup`，与文档宣称的 video-2022 评测对象不符。

## 目标

1. 在腾讯云自托管 Langfuse（`3.224.1`）中为 video-2022 建立独立 project，并签发该项目专用的 API Key。
2. 密钥只写入 Git 忽略的本地环境文件（`ai-agent/.env`），不得出现在终端输出、日志、文档、代码或 Git 历史中。
3. 将 Smoke / Regression / Multi-turn 三套 Dataset 同步到新 project，并重新执行现有评测，产出属于新 project 的 Run。
4. 验证新产生的 Dataset、Run、Trace、Score 的 URL 均属于 video-2022 project，且不再写入 speakup。
5. speakup 中的历史数据不删除，仅在文档中标记为"错误 project 下的废弃基线"。
6. 更新原评测文档，使数据归属的表述与实际一致。

## 范围

- 包含：Langfuse project 与 API Key 的开通、本地环境文件更新、Dataset 重新同步、三套评测重跑、URL 归属验证、文档新建与更新。
- 不包含：删除 speakup project 中的任何历史数据；修改评测代码、数据集内容、grader 或 Judge 逻辑；变更评测对象模型供应商的商务配置。

## 验收标准

| # | 标准 | 验证方式 |
|---|---|---|
| 1 | 存在名为 `video-2022` 的 Langfuse project | Langfuse API 查询 |
| 2 | 存在 video-2022 project 专用 API Key，且其权限范围仅覆盖 video-2022 | 用新 Key 调 `/api/public/projects` 只返回 video-2022 |
| 3 | `ai-agent/.env` 被 gitignore，密钥未进入 Git 追踪与终端输出 | `git check-ignore`、`git status`、命令输出审计 |
| 4 | 三套 Dataset 在新 project 中且 item 数为 15 / 49 / 5，无重复 | Langfuse API 查询 |
| 5 | Smoke（3 trials）、Regression（1 trial）、Multi-turn + Judge（1 trial）重跑完成 | Langfuse Dataset Run API 查询 |
| 6 | 新 Dataset / Run / Trace / Score URL 的 project 段均为 video-2022 | URL 抽查 + API 归属查询 |
| 7 | 评测期间 speakup project 无新增 Dataset / Run | speakup 侧 API 无法被新 Key 访问，以 project 隔离机制保证 |
| 8 | speakup 历史 Dataset / Run 保持存在，未被删除 | Langfuse UI/API 复核 |
| 9 | `uv run pytest -q` 全绿 | 本地测试 |
| 10 | 新需求文档四件（README、requirements、design、verification）+ 原文档更新 | 文档审阅 |
