# 接续记录

额度中断、上下文压缩或更换执行环境后，先读本页，再核对 Git、PR、CI。记录必须随阶段和重要状态更新，不能只在额度用完时补写；不保存密钥、用户数据或临时下载内容。

## 当前快照（2026-10-14）

| 项目 | 当前记录 |
|---|---|
| 业务审计基线 | `8e4283e5`；生产仍是 Java，Python 原型未接生产流量 |
| 已合并文档 | #141 年月日命名、#142 迁移审计；S00 #144、CI 警告 #145/#146 已合并；本轮基线 `e5fff0c5`，接续先 fetch 核对 |
| 原型分支/提交 | `feat-serverless-api-prototype`，`9b7af7eb47d981cebf8d2a375f021424890ec44d`；含确认提交 `633066df` |
| 原型 PR | [#143](https://github.com/makewheels/video-2022/pull/143)，已合并，merge `7c2c26d6c85ece34280c82c065ea6467a5f909f6`；不是完整业务迁移 |
| 原型 CI | [38510090583](https://github.com/makewheels/video-2022/actions/runs/38510090583) success；Linux + 隔离 Mongo 实际 17 passed，API/浏览器 E2E skipped |
| 本地证据 | S01 安装后 wheel 上 43 passed、真实 Mongo 1 skipped；两稳定入口和 Ruff 通过；原初始化比较不是 FC 冷启动 |
| 本轮分支 | `feat-serverless-portable-layers`，基于 `e5fff0c5`；提交/PR/CI 以该分支 Git 历史及 PR 描述的最终验证记录为准 |
| 当前阶段 | S01 现有原型分层实现及本地验收完成；合并门禁以 PR 实际 CI 为准，不等于生产业务迁移完成 |
| 本轮截止 | 用户要求 2026-10-14 17:00（Asia/Shanghai）前收尾；提前完成即可结束，不扩展 S02/S03 |
| 生产/资源 | 未切流、未迁库、未创建此次付费原型资源、未退役 Java |

## 用户约束（接续不能丢失）

- 全部现有业务、接口、用户操作及历史播放一致，不新增需求外业务字段或规则。
- 轻量 Python、无常驻保活、响应越快越好；没有批准具体毫秒阈值，不重复要求选框架/数值。
- 本轮继续现有 Mongo，TableStore 只属未来可能性；未授权迁库位置或采购固定资源。
- 外壳与业务隔离，基于阿里云但不绑定公共逻辑；将来可替换云和 PostgreSQL/Mongo 适配器。
- Git 唯一源码，发布从 Git 经 CI 单向构建/发布；允许 OSS 存产物，禁止只在 OSS/控制台存在自研源码。
- 小阶段、多 PR、持续记录接续；已授权范围内自主推进，不在每阶段重新问是否开始。

## 当前阻塞与独立工作

- 本机 Infisical CLI session 过期，已请求用户恢复；**不代表 Actions OIDC 不可用**。发布优先复用 Git 管理的 Actions 取密，核验身份权限，不因本机登录停止独立工作。
- 现有 Mongo 的跨云访问、最小权限和真实 FC 冷启动未验；不擅自开放公网/改防火墙/搬库。
- 本机 Docker daemon 不可用；CI 隔离 Mongo 已验证，不能因此声称生产网络可达。
- Mongo 写库/投递间隙尚未解决；S01/S02/S05 可以先推进，不绕过恢复要求。

## 下一步

1. 通过 `gh pr view feat-serverless-portable-layers` 查询本轮 PR、head 和完整 CI；描述补充本地/CI 的最终证据后按门禁合并，不预写自己的 SHA。若已合并，不重做 S01。
2. 当前代码已有 `business/prototype.py`、`ports/probe.py`、通用 HTTP Application、FC/ASGI/Mongo 适配器和 bootstrap。只处理原型真实需要的端口，OSS/任务等随业务阶段加入。
3. S01 后进入 S02：干净 Git 提交在 Linux 构建并记录来源；本轮 wheel 验证不是 S02 的 Git 来源/不可变产物方案。S03 才做隔离环境自动发布和回退。
4. S04 验证 FC 冷/热与 Mongo 首连；S05 补齐多值参数/header 等契约。不能以本地线程测试代替 FC 配置/生产网络。
5. 若 17:00 时外部门禁仍未完成，保存准确状态和下一条命令后收尾，不强行合并或把未跑项目写通过。测试依赖已有的 TestClient 弃用提示仍存在，另见 CI 警告清单，不能混为本阶段失败或声称消失。

## 检查点规则

- 稳定小改动完成时写本地 commit；准备 PR、长操作或中断前推送可接续分支，记录提交、PR、未提交改动及下一条操作。
- 记录命令、结果/CI 链接和必要摘要，明确 passed/skipped/pending/failed/未运行；不复制巨大日志或秘密。
- 半成品留在工作分支并标注，不把未跑测试的阶段标完成。CI 等待时不无理由重跑全套或不断提交状态刷新。
- 接续先 `git status --short`、fetch、查询 PR head/CI；未提交改动先确认归属，禁止 reset/覆盖后再调查。
- 阶段收尾写“实际成果、剩余项、回退点、下一阶段”；系统临时目录不是唯一工作记录。不承诺额度恢复后平台自动执行，恢复工作以此记录为准。
