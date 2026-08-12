# Langfuse 项目隔离验证与结果

> 状态：已完成
> 日期：2026-08-12
> 代码基线：`0d782215`（分支 `fix/video-langfuse-project-isolation`，仅文档改动）
> Langfuse：腾讯云自托管 `3.224.1` / Python SDK `4.14.4`

## 验收对照

| # | 验收标准 | 结果 |
|---|---|---|
| 1 | 存在独立 `video-2022` project | ✅ id `cmspo70oy0003w507w6izfis7`，org `personal` |
| 2 | 专用 API Key 且权限仅覆盖 video-2022 | ✅ project 级 Key（`pk-lf-2de36e79-…`）；用其调 `/api/public/projects` 只返回 `video-2022` |
| 3 | 密钥只写入 Git 忽略文件，不输出不提交 | ✅ 写入 `ai-agent/.env`；`git check-ignore` 命中 `ai-agent/.gitignore:1`；`git status` 无该文件；全程未在终端/日志回显 secret |
| 4 | 三套 Dataset 在新 project，15/49/5 无重复 | ✅ Public API 实测 15 / 49 / 5 |
| 5 | 三套评测重跑完成 | ✅ Smoke×3 trials、Regression×1、Multi-turn×1+Judge，进程退出码均 0 |
| 6 | Dataset/Run/Trace/Score 均属于 video-2022 | ✅ 84 条 item 的全部 trace（84/84）与全部 score（687/687）projectId 均为 video-2022；foreign-project 对象数 0 |
| 7 | 不向 speakup 写入 | ✅ 新 Key 为 project 级，机制上无法访问 speakup；speakup 侧 Dataset/Run 清单与历史记录逐一核对无新增 |
| 8 | speakup 历史数据未删除 | ✅ 3 个 Dataset、7 个历史 Run 全部存在（清单见下） |
| 9 | 测试全绿 | ✅ `uv run pytest -q`：178 passed |
| 10 | 文档交付 | ✅ 本目录四件 + 原评测文档废弃标记 |

UI 可达性：管理会话下新 project 首页、三套 Dataset、各 Run 页面及 speakup 历史 Run 页面均 HTTP 200。（本机无浏览器自动化权限，数据级验证以 Langfuse Public API 为准，UI 验证为路由可达性。）

## 新 Project 与结果链接

- Project：<http://101.42.94.17:30030/project/cmspo70oy0003w507w6izfis7>（名称 `video-2022`）

| Dataset | 链接 | items |
|---|---|---:|
| `video-2022/evals/smoke-v1` | <http://101.42.94.17:30030/project/cmspo70oy0003w507w6izfis7/datasets/cmsq8mzxa0065w507xz22poi8> | 15 |
| `video-2022/evals/regression-v1` | <http://101.42.94.17:30030/project/cmspo70oy0003w507w6izfis7/datasets/cmsq8n0om006nw507geqiqz56> | 49 |
| `video-2022/evals/multi_turn-v1` | <http://101.42.94.17:30030/project/cmspo70oy0003w507w6izfis7/datasets/cmsq8n2fv0083w507cfa6vkzj> | 5 |

| Run | 结果 | 链接 |
|---|---|---|
| Smoke trial 1 | 15/15 | <http://101.42.94.17:30030/project/cmspo70oy0003w507w6izfis7/datasets/cmsq8mzxa0065w507xz22poi8/runs/07768f8d-2ba3-425d-a301-3a9b3faeb0eb> |
| Smoke trial 2 | 15/15 | <http://101.42.94.17:30030/project/cmspo70oy0003w507w6izfis7/datasets/cmsq8mzxa0065w507xz22poi8/runs/7d42940b-08a0-45d9-b538-bbe12096ec53> |
| Smoke trial 3 | 15/15 | <http://101.42.94.17:30030/project/cmspo70oy0003w507w6izfis7/datasets/cmsq8mzxa0065w507xz22poi8/runs/b5aaa717-e0a9-4d1d-8f52-3c3c75a317c3> |
| Regression | 45/49，0 task error | <http://101.42.94.17:30030/project/cmspo70oy0003w507w6izfis7/datasets/cmsq8n0om006nw507geqiqz56/runs/323d4bdd-7f86-4f71-b9c1-b8acc313f8ee> |
| Multi-turn + Judge | 2/5，0 task error | <http://101.42.94.17:30030/project/cmspo70oy0003w507w6izfis7/datasets/cmsq8n2fv0083w507cfa6vkzj/runs/c1b39e6f-26f9-41bc-a705-3b59cc152a1a> |

## 重跑结果（新基线）

- 评测对象模型：`qwen3-max`（DashScope OpenAI-compatible），工具后端 fixture，每个 item 前重置；代码基线 `0d782215`。
- Smoke：3 个独立 Run 共 45/45 trial，15/15 case 达到 `pass^3`。
- Regression：45/49，0 task error。4 条失败均为 `answer_correct` 关键词缺失（回答表述与旧关键词断言不匹配），非工具或安全维度失败。
- Multi-turn + Judge：2/5，0 task error。3 条失败均为 `loop_free`（连续相同工具与参数的无进展调用）。Judge 聚合：relevance 0.90、grounded explanation 0.80、clarity 0.80、trajectory efficiency 0.50、confidence 0.93。

### 与原基线的可比性说明

原基线（kimi/kimi-k3，在 speakup project）为 Smoke 15/15 `pass^3`、Regression 49/49、Multi-turn 4/5。本次重跑前实测：原 MiniMax 凭证仍 401，DashScope 账号未开通 kimi 模型（"The product is not activated"），故改用同账号已开通的 `qwen3-max`。两次运行模型不同，分数不可直接对比；项目隔离结论不受影响。Regression 关键词断言差异（49/49 → 45/49）与 Multi-turn 循环失败（4/5 → 2/5）属于模型行为差异信号，是否构成产品回归需在固定模型下另行评审。

## speakup 历史数据清单（未删除）

以下对象经管理会话逐一核实仍存在，标记为"错误 project 下的废弃基线"，只读保留：

| Dataset（speakup 内） | Run | 说明 |
|---|---|---|
| `video-2022/evals/smoke-v1`（`cmsq1eedq000bw507s0rar2xl`） | `3614bd8b-d7c4-46d7-8690-bda991243b80` | Smoke calibrated trial 1 |
| 同上 | `451bf5cb-187f-4ebc-9820-9f0e8132235d` | Smoke stability trial 1 |
| 同上 | `ec19c21c-759a-4a24-8c53-e335eacb995c` | Smoke stability trial 2 |
| 同上 | `e22fd8f6-c42f-4d45-ba8f-d9951c70dc41` | MiniMax 401 无效运行（不计入 baseline） |
| `video-2022/evals/regression-v1`（`cmsq1eg16000tw5070o4pkpfc`） | `92fa6962-f2af-4007-9b46-494149fd6051` | Regression baseline |
| `video-2022/evals/multi_turn-v1`（`cmsq208ah003xw507wf3uaqat`） | `cc707a77-b5d5-42c8-9ce8-e8c9c81476be` | Multi-turn deterministic（含一次 429） |
| 同上 | `2b638cd9-1898-4643-8741-b4d66b10bde1` | Multi-turn + Judge |

speakup project 中 video-2022 相关 Dataset 共 3 个、Run 共 7 个，与原文档记录一致，无新增、无删除。

## 已知问题与限制

- 自托管 `3.224.1` 仍拒绝 Dataset 服务端 schema（已知 400），同步器按既有设计降级，Git schema + 本地 validator 兜底（与原需求一致，非本次引入）。
- `/api/public/scores` 不接受 `traceId` 过滤（该版本行为），score 归属审计改经 trace 内嵌 scores 完成，结论等效。
- 本机 computer-use 权限未就绪，浏览器 UI 自动化未执行；UI 验证以管理会话 HTTP 200 + Public API 数据级核对代替。
- 新 project 为本次工作前已存在（同日早些时候创建），本次在其上新建了专用 API Key；历史遗留的其他 Key 未做清理（不影响隔离性）。
