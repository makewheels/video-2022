# Langfuse 项目隔离

> 状态：已完成
> 日期：2026-08-12
> 分支：`fix/video-langfuse-project-isolation`

## 目标

把 video-2022 评测数据从 Langfuse `speakup` project 迁入独立 `video-2022` project：专用 API Key、三套 Dataset 重新同步、评测重跑、URL 归属验证；speakup 历史数据保留为错误 project 下的废弃基线。

## 文档

- [需求与验收标准](requirements.md)
- [设计](design.md)
- [验证与结果链接](verification.md)

## 结果速览

- 新 project：[video-2022](http://101.42.94.17:30030/project/cmspo70oy0003w507w6izfis7)
- 重跑（qwen3-max / fixture / `0d782215`）：Smoke 15/15 `pass^3`；Regression 45/49；Multi-turn + Judge 2/5
- 84/84 trace、687/687 score 归属 video-2022 project，speakup 零新增写入
- speakup 3 个历史 Dataset、7 个历史 Run 全部保留未删除
- 密钥仅在 gitignored 的 `ai-agent/.env`，未进入 Git 与任何输出

## 关联

- 前置需求：[2026-08-video-agent-evaluation](../2026-08-video-agent-evaluation/)（其 `results.md` 链接已标记为错误 project 下的废弃基线）
