# 自建函数部署清单（P3 执行）

代码在 `functions/cpu` 与 `functions/gpu`。部署统一走 GitHub Actions（新增 workflow 或并入 deploy.yml），禁止控制台手工部署。本文件列出 FC 函数配置要求，实施时以实际 workflow 为准。

## 函数规格

| 项 | CPU 函数 | GPU 函数 |
|---|---|---|
| 名称建议 | `video-2022-{env}-transcode-cpu` | `video-2022-{env}-transcode-gpu` |
| 运行时 | custom-container（Debian + 系统 ffmpeg） | custom-container（nvidia-ffmpeg 基础镜像） |
| GPU | 无 | `fc.gpu.ampere.1`，显存 6144MB（1024 倍数），vCPU 3（≤ 显存GB/2），内存 12G（≤ 显存GB×2048） |
| CPU 版规格 | 2 vCPU / 4G | — |
| 执行超时 | 1800s 内按 deadline 配置 | 同左，上限 1800s |
| 临时盘 | ≥ 4G（原片 + 产物），不够调 diskSize | ≥ 4G |
| 最小实例 | 0（无预留/常驻） | 0（无预留/常驻） |
| 并发 | instanceConcurrency 1，账户级并发上限走配额，超出由后端排队 | 同左 |
| 触发器 | HTTP，authType anonymous；请求校验 X-Invoke-Secret | 同左 |
| 异步重试 | maxAsyncRetryAttempts = 0（应用层统一重试，防叠乘） | 同左 |
| 日志 | SLS logConfig 指向专用 logstore（旧函数未配日志的教训） | 同左 |
| 网络 | 不绑 VPC；OSS 走同地域内网 endpoint | 同左 |

## 环境变量（Infisical 注入，不进 Git/镜像/日志）

| 变量 | 说明 |
|---|---|
| `OSS_ACCESS_KEY_ID` / `OSS_SECRET_KEY` | 最小权限子账号：仅视频 bucket 对 `videos/` 前缀读写 |
| `CALLBACK_SECRET` | 回调后端的 X-Callback-Secret（与后端 `aliyun.cf.transcode.callback-secret` 一致） |
| `INVOKE_SECRET` | 后端调用的 X-Invoke-Secret（与后端 `aliyun.cf.transcode.invoke-secret` 一致） |

## 后端配套配置

- `aliyun.cf.transcode.cpu.url` / `gpu.url`：函数 HTTP 触发器 URL（配置化，不硬编码）
- `aliyun.cf.transcode.invoke-secret` / `callback-secret`
- `transcode.pipeline=self-hosted`：新链路总开关
- `aliyun.mps.fallback-template.720p` / `.1080p`：兜底模板 ID（新建，保留帧率/声道/色彩，不使用历史 baseline 单声道模板；未配置则兜底直接明确失败）

## 版本与回滚

- 镜像按 digest 部署，同时打时间戳 tag 与 latest；回滚改回上一 digest
- 函数配置变更走 workflow（版本化）；保留旧版本号便于别名回切
- 上线顺序：部署函数 → 后端灰度开关 → 开发环境全链路验证 → 小范围生产

## 预算控制（首轮 ≤ 20 元，当前余额 6.76 元）

- 单任务短样本估算 0.1-0.3 元；估算到 16 元（或余额 20%）停止新增，预留 4 元给在途
- 每批任务记录 verification.md；账单延迟不当 0 元
- GPU 冷启动 ~10-30s，短样本为主，合并档位验证以减少任务数
