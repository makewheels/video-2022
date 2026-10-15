# 需求文档索引

新功能以“一个需求一个稳定目录”管理。一个需求可以跨多个 PR，但必须有独立目标、验收标准，并能够独立上线或回滚。

| 需求 | 状态 | 入口 |
|---|---|---|
| Python 后端迁移阿里云 FC | 分层/构建已交付，用户/视频/播放部分实现；FC发布/网络阻塞，未切流 | [2026-10-14-serverless-backend](2026-10-14-serverless-backend/README.md) |
| GPU 云函数自建转码 | CPU/GPU链路已发布且有云端验收；真实素材/移动HDR及部分故障矩阵待验 | [2026-10-07-gpu-cloudfunction-transcode](2026-10-07-gpu-cloudfunction-transcode/README.md) |
| 评测数据改为可读 JSON | 已合并（PR #108） | [2026-08-12-video-agent-evaluation-json](2026-08-12-video-agent-evaluation-json/) |
| Video Agent 评测与 Langfuse | v1 已完成，生产闭环待接入 | [2026-08-12-video-agent-evaluation](2026-08-12-video-agent-evaluation/) |
| 项目文件结构审计与重组 | 待人工复核（PR #109） | [2026-08-12-project-structure-review](2026-08-12-project-structure-review/) |
| Langfuse 项目隔离 | 已完成（PR #110） | [2026-08-13-video-langfuse-project-isolation](2026-08-13-video-langfuse-project-isolation/) |

## 目录约定

需求目录使用 `YYYY-MM-DD-<feature>`，日期表示需求建档日，不随后续实施或验收日期变化。存量月级目录按首次引入 Git 的提交日期补齐日，文档正文中的历史业务日期保持原样。

```text
docs/requirements/YYYY-MM-DD-<feature>/
├── README.md            # 状态、文档导航、PR/结果入口
├── requirements.md      # 目标、范围和验收标准
├── plan.md              # 本次实施步骤
└── verification.md      # 实际验证、上线状态和已知问题
```

跨需求且随代码演进的当前设计放在 `docs/design/`，不再与一次性实施计划合并。历史需求中已经存在的 `design-and-plan.md` 保持原状，避免无意义搬迁。

`docs/CHANGELOG.md` 只记录已经交付的时间线，并链接需求目录和 PR，不复制设计或执行过程。

历史平铺计划文档已整体迁入 `docs/归档/plans/`，仅作背景参考，不再新增。
