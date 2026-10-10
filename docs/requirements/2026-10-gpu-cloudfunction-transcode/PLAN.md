# GPU 云函数自建转码（完善计划，择期实施）

> 首次记录：2026-10-07；完善：2026-10-10。状态：规划完成，择期实施。
> 本次仅完善文档，未实现、未创建云资源、未部署、未跑 GPU 实测。以下方案为实施建议，实测门槛通过后才决定是否切换生产。

## 目标

用阿里云函数计算 FC GPU 实例 + 自建 FFmpeg（NVENC 硬编码）替代 MPS 的重编码场景，验证是否能降低总成本，同时保持播放、分辨率选择、OSS 产物及客户端接口兼容。降本是待验证目标，不预先承诺 GPU 一定更便宜。

范围外：新分辨率、新编码输出、播放器改造、LOCAL 转码改造。媒体信息读取和封面提取仍使用 MPS，不能删除其依赖或凭证。不修改当前环境识别和分辨率生成规则；需要新增业务字段、状态或协议时先在设计中明确并确认。

## 现有转码方式（4 种）

分流入口：`TranscodeLauncher.getTranscodeProvider`，provider 常量在 `transcode/contants/TranscodeProvider.java`。

| provider | 实现类 | 触发条件 | 状态 |
|---|---|---|---|
| ALIYUN_MPS | `AliyunMpsTranscodeImpl` | 非 h264 / 降分辨率 / 码率>13Mbps；封面截帧 | 在用，贵 |
| ALIYUN_CLOUD_FUNCTION | `AliyunCfTranscodeImpl` | 生产环境 + h264 + 无需降分辨率 + 码率≤13000kbps | 现有通道。函数代码不在本仓库，URL 写死在 `CloudFunctionTranscodeService`；实际费用待核对 |
| ALIYUN_CLOUD_FUNCTION_GPU | `AliyunCfGPUTranscodeImpl` | `getTranscodeProvider` 不会返回它 | 仅常量 + 空壳类（预留坑位，未实现 `TranscodeService`） |
| LOCAL | `LocalTranscodeService` | `transcodeMode=LOCAL`，video-cli 本机 ffmpeg 转 HLS 回传 | 2026-06 上线 |

## 方案

### 当前代码接入点与问题

Java 源码基准目录：`server/video/src/main/java/com/github/makewheels/video2022/`。

1. `TranscodeFactory` 已有 GPU 分支，但 GPU 空类未实现 `TranscodeService`，无法作为有效策略注入。
2. `TranscodeController.aliyunCloudFunctionTranscodeCallback` 校验 `X-Callback-Secret` 后固定选择 CPU provider，只读取 jobId。GPU 不能直接照搬该成功回调。
3. `AliyunCfTranscodeImpl.transcode` 在调用云函数后才写入 jobId/status，快速回调可能先到；GPU 接入需先持久化任务关联，再提交异步调用。
4. CPU callback 无条件置 FINISHED；`TranscodeCallbackService.onTranscodeFinish` 先更新视频状态，再读取/登记 m3u8 和 ts。GPU 完成处理需验证产物并防止重复登记，不能仅收到回调就认定可播放。
5. 当前按分辨率分别发起 720p/1080p 任务，保持该粒度及生成规则；首版不合并为一次多档任务，也不新增 480p。

### GPU Worker

- 建议放入本仓库独立 `transcode-worker/`，Python 服务 + 固定版本 FFmpeg/CUDA 镜像；实施时确认目录并接入 CI。
- 核对现有入参语义：bucket、endpoint、inputKey、outputDir、videoId、transcodeId、jobId、resolution、width、height、videoCodec、audioCodec、quality、callbackUrl。先取得 CPU 函数源码或经核验的输入/输出样例，不能只凭 Java 参数推断完整协议。
- 输出兼容现有链路的 H.264/AAC HLS；无音轨、竖屏、旋转、奇数尺寸、可变帧率和不同输入编码必须实测，保持比例。`quality=keep` 不能直接当 NVENC 参数，码率/preset 根据样本比较决定。
- 优先验证硬编码；硬解码与 GPU 缩放按所选卡及输入格式实测，不假设全部输入都能全程 GPU 处理。软件解码成本也纳入核算。
- 子进程使用参数数组，不拼 shell；bucket/key、输出前缀和回调地址限定为可信配置，运行身份仅授予必要 OSS 权限。
- 每任务使用独立系统临时目录，注册 finally 清理，异常/超时/取消均清理；磁盘预估覆盖原片、产物及重试空间，永久产物落 OSS。
- 所有 ts 上传完整后才发布 m3u8，最后发送完成回调。部分上传不得认定成功；失败及回调丢失需可查询、告警和恢复。

### 后端、分流与回退

- GPU 实现接入现有接口和工厂。函数地址、启用开关、调用超时等走环境配置，默认关闭 GPU；凭证和回调密钥通过现有取密体系注入。
- 建议 GPU 独立回调入口，复用 secret 校验及完成后的业务流程，保持 CPU 协议兼容。成功/失败载荷及错误分类在实施设计中定稿，不直接新增业务字段。
- 提交前持久化 jobId 和任务关联；分别验证调用接受、执行成功、OSS 产物完整、登记完成。提交失败或结果不明不能盲目重复发起付费任务，应先查任务状态。
- 按 jobId/provider 核验当前任务，处理重复/迟到回调、失败后再成功等情况；不得重复登记 ts、重复触发 webhook 或造成状态回退。未完成登记允许安全重试。
- 复用现有状态和仓储；如现有状态不足以表达失败/重试，先确认兼容设计，不能擅自新增状态语义。失败、超时、回调丢失不能永久卡在 TRANSCODING。
- 开关关闭时保持旧分流；启用后仅经验证的三个重编码场景进入 GPU，其余继续 MPS/CPU。首版建议单实例单任务，限制总并发；更高并发另行实测。
- 首轮使用人工受控重试至 MPS。自动回退作为后续决策项，先确定尝试次数、任务身份、费用上限及产物隔离。
- GPU 与 MPS 不得同时写同一输出目录；回退前确认旧任务终止、隔离产物和迟到回调。若需要调整现有路径，先写明设计与兼容影响。

## 阶段

- **阶段 1**：GPU 函数接管 MPS 的三个转码场景（非 h264 / 降分辨率 / 高码率）——成本痛点所在
- **阶段 2（可选）**：CPU 云函数场景也统一到 GPU 函数，收拢成一条通道；按成本核算结果决定要不要做
- 封面截帧是否也迁出 MPS：实施时单独评估

## 云能力核实与开工前调查

2026-10-10 查阅官方资料：

- [音视频处理最佳实践](https://help.aliyun.com/zh/functioncompute/best-practices-for-audio-and-video-processing-1)：有 NVENC 转码和 GPU 缩放示例，证明平台具备此类能力，不代表本账号选定规格可直接运行。
- [创建 GPU 容器函数](https://www.alibabacloud.com/help/en/functioncompute/creating-a-gpu-function/)：支持容器镜像，需配置 GPU/CPU/内存/磁盘；实例回收后本地磁盘数据不保留。
- [按量付费](https://help.aliyun.com/zh/functioncompute/pay-as-you-go-billing-methods)及[计费概述](https://help.aliyun.com/zh/functioncompute/billing-overview-of-fc)：GPU 执行时长按秒计费；总成本仍取决于资源规格、实例模式及其他云产品费用，不能拿单项 GPU 价格当总费用或套用过期优惠。
- [异步调用](https://help.aliyun.com/zh/functioncompute/fc-asynchronous-invocation)及[异步任务](https://help.aliyun.com/zh/functioncompute/asynchronous-task)：存在重试与结果处理机制，需评估查询、去重及失败通知；FC 接收请求不等于转码成功。

开工时重新核实并记录：OSS 真实地域、同地域 GPU 供应/配额、卡型与 NVENC/NVDEC 可用性、驱动/镜像兼容性、函数最大执行时间、磁盘上限、镜像仓库网络、异步调用/查询能力、调用鉴权及实际单价。不预先锁定 Tesla T4，也不把事件存活时间当执行超时。

从近期任务汇总输入编码、体积、时长、分辨率、耗时、失败率及 MPS 账单，记录样本区间及分位数；文档不写凭证或私有视频访问地址。

## 成本与体验实测

同一原片、相同输出档位，以当前 MPS 为基线。覆盖三个迁移场景、短/长视频、高分辨率、运动/细节画面、竖屏、无音轨及常见非 H.264 编码。样本数量及最长视频覆盖范围根据实际任务分布确定。

每个样本记录：输入参数、输出档位、GPU 规格、镜像版本、冷/热启动、排队/下载/编码/上传/回调登记耗时、产物大小、成功与重试次数、实际费用及播放结果。

`每输入视频分钟成本 = 总费用 / 输入分钟数`，两通道输出档位数量保持一致；另列每输出分钟成本。总费用包括 FC 各资源计费、OSS 请求/读写/可能的数据取回、网络、日志、镜像及失败重试；预留/常驻的闲置成本单列。公式估算用于选型，最终比较以账单为准。

画质做同片并排检查、音画同步及拖动播放验证，有条件补充 VMAF/SSIM；不预设 NVENC 相同码率下必然优于或劣于 MPS。冷启动可能增加完成时长，是否可接受由实测决定。

首轮建议按需实例、最小实例数为 0；是否常驻另行决策。生产切换前结合基线确定可接受的成本收益、完成耗时、画质及失败率门槛，未定门槛或无实测证据不切换。

## 实施顺序与交付物

| 阶段 | 工作 | 退出条件 |
|---|---|---|
| 0 调查与设计 | 核实云资源、协议、账单；确定参数、回调、重试与输出隔离 | 补齐本目录 README.md、requirements.md、verification.md；在 docs/design/ 记录设计；关键兼容决策确认 |
| 1 云端原型 | 最小镜像验证 NVENC、OSS、异步执行和 HLS；MPS 同片对比 | 有实际 GPU 转码及账单证据，决定是否继续 |
| 2 后端接入 | provider、配置、回调、幂等及失败处理；默认关闭 | 相关测试和构建通过，客户端契约兼容 |
| 3 受控验证 | 少量真实任务，核验三场景、异常恢复与旧通道回归 | 达到约定成本/体验门槛，完成回退演练 |
| 4 发布与观察 | 经 GitHub Actions 发布，受控开启并观察 | 实际结果写 verification.md，交付后更新 CHANGELOG |

不承诺固定日期或工期；阶段 1 若不能降本或平台限制不合适则停止，保留现有链路。

## 测试与验收

现有可复用测试：`CloudFunctionTranscodeServiceTest`、`TranscodeCallbackServiceTest`、`LocalTranscodeServiceTest`。这些仅是现有入口，本次未执行，不能证明 GPU 可用。

- 后端：三个分流场景及边界、开关关闭、工厂注入、提交失败/结果不明、快速回调、鉴权失败、重复/迟到回调、失败重试、视频已删除和产物缺失。
- Worker：编码/缩放、无音轨/竖屏、坏文件、磁盘不足、OSS 失败、进程超时、部分上传、回调失败重试及临时目录清理。
- 集成：720p/1080p HLS 完整可播放、ts 登记及状态正确、首尾分片/拖动/音画同步；CPU、MPS、LOCAL、封面和媒体信息读取回归。
- 云端：冷/热启动、最长代表视频、并发受限、异步重试、实际费用和告警。Mock 不能代替这些实测。
- 发版：相关测试、构建及项目要求的 CI 全通过，业务/API 文档同步，不增加未确认的客户端字段或行为。

验收逐项标记“已有覆盖 / 本次实际跑通 / 未跑或被阻塞”，记录命令、环境、结果及限制。以上均为待实施项。

## 发版

统一走 GitHub Actions（`ci.yml` / `deploy.yml` / `release.yml`），禁止直连服务器，与 AGENT.md 现有约定一致；旧发版方式废弃不再用。

Worker、云函数权限、镜像版本和配置需有可复现的部署配置；具体 IaC 工具及工作流在阶段 0 确定。配置变更备注记录时间、原因和旧值，但不记录密钥。

观察成功率、积压/超时、完成耗时、重复回调、GPU 利用率及每日费用，告警阈值按基线确定。

回滚先关闭 GPU 分流，让新任务回旧通道；在途 GPU 任务仍保留 Worker 和回调入口直至完成或明确失败。再回退应用/镜像时，确认版本能处理在途任务。失败任务隔离后再交 MPS；保留已完成产物，垃圾产物按核验清单清理，不做全量删除或数据迁移。

## 待实施清单

- [x] 完善计划，核对代码接入点及官方能力资料。
- [ ] 核实账号/地域/GPU 供应、完整 CPU 协议及 MPS 账单。
- [ ] 确定样本与成本/体验门槛，确认云端原型预算。
- [ ] 完成原型、接入、测试和回退演练。
- [ ] 决定是否生产切换；阶段 2、封面迁移及自动 MPS 回退另行决定。

延期期间不提前创建资源或购买常驻 GPU；择期开工从阶段 0 开始，并重新核实届时价格及限制。
