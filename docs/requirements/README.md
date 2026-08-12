# 需求文档索引

新功能以“一个需求一个稳定目录”管理。一个需求可以跨多个 PR，但必须有独立目标、验收标准，并能够独立上线或回滚。

| 需求 | 状态 | 入口 |
|---|---|---|
| Video Agent 评测与 Langfuse | v1 已完成，生产闭环待接入 | [2026-08-video-agent-evaluation](2026-08-video-agent-evaluation/) |

## 目录约定

```text
docs/requirements/YYYY-MM-<feature>/
├── README.md            # 状态、目标、文档导航、PR/结果入口
├── design-and-plan.md   # 小中型需求可合并；大型需求可拆为 design.md + plan.md
└── verification.md      # 实际验证、上线状态和已知问题
```

`docs/CHANGELOG.md` 只记录已经交付的时间线，并链接需求目录和 PR，不复制设计或执行过程。

现有 `docs/plans/` 是历史平铺文档。新需求不再往其中新增文件；旧文档按需求分组、链接检查和人工复核后渐进迁移，不做一次性无审阅搬迁。
