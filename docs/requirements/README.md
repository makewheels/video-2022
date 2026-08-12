# 需求文档索引

新功能以“一个需求一个稳定目录”管理。一个需求可以跨多个 PR，但必须有独立目标、验收标准，并能够独立上线或回滚。

| 需求 | 状态 | 入口 |
|---|---|---|
| 评测数据改为可读 JSON | PR #108 待合并 | [2026-08-video-agent-evaluation-json](2026-08-video-agent-evaluation-json/) |
| Video Agent 评测与 Langfuse | v1 已完成，生产闭环待接入 | [2026-08-video-agent-evaluation](2026-08-video-agent-evaluation/) |

## 目录约定

```text
docs/requirements/YYYY-MM-<feature>/
├── README.md            # 状态、文档导航、PR/结果入口
├── requirements.md      # 目标、范围和验收标准
├── plan.md              # 本次实施步骤
└── verification.md      # 实际验证、上线状态和已知问题
```

跨需求且随代码演进的当前设计放在 `docs/design/`，不再与一次性实施计划合并。历史需求中已经存在的 `design-and-plan.md` 保持原状，避免无意义搬迁。

`docs/CHANGELOG.md` 只记录已经交付的时间线，并链接需求目录和 PR，不复制设计或执行过程。

历史平铺计划文档已整体迁入 `docs/归档/plans/`，仅作背景参考，不再新增。
