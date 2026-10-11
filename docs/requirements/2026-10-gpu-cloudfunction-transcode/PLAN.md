# 自建云函数点播转码与 MPS 兜底

> 状态：执行中。2026-10-10 独立 Worker 原型完成（PR #119，未接入生产）；2026-10-11 P0 只读盘点完成，P1/P2 实现完成（PR #120）；云端部署、GPU 实测与旧资源清理未执行。
> 2026-10-07 初稿；2026-10-10 完善计划并启动原型；2026-10-11 根据需求讨论更新并完成 P0 盘点。

执行入口：[需求与阅读顺序](README.md) · [已确认需求](requirements.md) · [设计草案](../../design/cloud-transcode.md) · [验收矩阵](verification.md)

## 目标与已确认的决定

- 云端供其他用户上传使用，优先采用自建 FC 云函数；按任务需要和总费用选择 CPU 或 GPU，失败由 MPS 兜底。
- 保留 CLI 本地 FFmpeg 通道，供用户自己上传时节省云端转码费用，不强制改走云端。
- 只处理上线后新上传的视频；历史视频不重转、不删除已有播放文件，原片保留不动。
- 点播仅保留 720p、1080p 档位，清理其他档位的新任务生成逻辑及无用配置。低分辨率原片保持原尺寸，只生成一档，不放大、不生成重复档位。
- 尽量忠实于原视频：保留帧率、HDR/SDR、色彩表现、画面比例及音轨，不主动裁剪、降帧、转换动态范围或强制混成单声道。分辨率调整和有损压缩按点播方案执行。
- 按需并发，设置可配置的有限上限，依据账户配额与实测容量确定；超出部分排队，不开启常驻或预留 GPU。
- 首轮云端验证预算上限 20 元，包括 FC、镜像存储、OSS 操作及必要的 MPS 对照。足够得出结论即停止；执行前核对余额和实际计费。账单有延迟，不能仅靠余额控制预算。
- 北京区旧 FC 服务纳入清理：先核查代码引用、触发器、工作流、调用记录和待执行任务，确定无用后才删除。近期无调用不等于无用。

## 当前状态与证据

源码以 `server/video/src/main/java/com/github/makewheels/video2022/` 为根。

| 通道或任务 | 当前实现与核查结果 |
|---|---|
| MPS 转码 | `transcode/TranscodeLauncher.java`、`transcode/aliyun/AliyunMpsService.java`；非 H.264、降分辨率、高码率走 MPS，云端生成 720p/1080p |
| CPU FC | `transcode/cloudfunction/CloudFunctionTranscodeService.java`；生产环境符合条件的 H.264 走 FC，URL 写死，传 `quality=keep`，内部代码不在本仓库，实际编码参数待读取 |
| GPU FC | `transcode/factory/AliyunCfGPUTranscodeImpl.java` 为空壳，未实现 `TranscodeService`，分流未使用 |
| 封面 | `cover/CoverLauncher.java`、`video/service/RawFileService.java`；普通上传调用 MPS 截帧，YouTube 搬运已有封面，LOCAL 由客户端处理 |
| 媒体探测 | `transcode/TranscodeLauncher.java` 调用 MPS 获取媒体信息 |
| 本地转码 | `cli/video_cli/local_transcode.py`；x264 veryfast、CRF 23，无固定视频码率或峰值限制，AAC 128 Kbps，约 6 秒 HLS 分片，存在 480p 兜底分支 |
| 回调 | `transcode/TranscodeController.java` 固定派发 CPU provider，校验共享密钥；`AliyunCfTranscodeImpl.callback` 直接标成功，需要扩展失败结果和幂等处理 |

2026-10-11 通过 QueryTemplateList 只读查询北京区代码引用的 MPS 模板：

| 档位 | 视频目标码率 | 音频码率 | 其他配置 |
|---|---:|---:|---|
| 480p（新任务停用） | 1600 Kbps | 128 Kbps | baseline，24 fps，单声道，2 秒分片 |
| 720p | 4000 Kbps | 128 Kbps | baseline，单声道，2 秒分片 |
| 1080p | 12000 Kbps | 128 Kbps | baseline，双声道，2 秒分片 |

新云端编码先以 720p 4 Mbps、1080p 12 Mbps 为验证基准，不主动降低。1080p 偏向画质，视频本身约 5.4 GB/小时；实际体积需要实测。代码中的 13 Mbps 是分流阈值，不是输出码率。

旧模板的 baseline、声道和帧率限制不能机械照搬，否则可能改变原视频表现；历史模板不直接修改。

## 设计与可靠性要求

1. 自建函数代码、容器和部署配置纳入版本管理；URL、规格、并发上限及超时走环境配置。
2. ffprobe 探测和独立截帧优先 CPU；GPU 转码时评估顺带生成封面，以减少重复读取原片。CPU 单价较低不代表单个任务总费用必然更低。
3. 需要重新编码时采用 NVENC；符合条件且不需重编码的任务保留低成本处理方式。读取旧 CPU 函数实现后确定复用范围。
4. HDR 必须选择能保留位深、色彩及必要元数据的编码与封装，不直接套用 H.264 baseline，不在失败时静默转 SDR。HDR 与 MPS 兜底、各端播放器的完整兼容性需要实测。
5. 对齐 OSS 目录、播放列表、分片、关键帧和码率统计；目标码率、峰值及缓冲区参数由样本验证确定，不能直接照搬 x264 CRF 至 NVENC。
6. 先持久化任务身份和状态，再异步提交并检查响应，避免回调先到却找不到任务；回调按任务 provider 派发，校验身份和当前 attempt。
7. 成功、失败、超时分别处理；验证产物完整、登记完成后才能置就绪。重复和晚到回调不得重复登记或覆盖新任务状态。
8. 自建处理有限重试后仍失败，允许 MPS 回退一次。重试次数、退避和超时由技术设计明确；MPS 使用满足保留原视频要求的配置，防止无限回退和产物相互覆盖。
9. OSS 采用最小权限，密钥通过现有密钥管理系统注入，不写入 Git、镜像和日志。

## 原型阶段与调查补充（PR #119，2026-10-10）

- 独立对照实现 `transcode-worker/`（Python 服务 + 固定版本 FFmpeg/CUDA 候选镜像）已完成本地与 CI 验证，设计见 [Worker 设计](../../design/gpu-transcode-worker.md)，验证记录见 [verification.md 第 6 节](verification.md)；未接入生产分流。本 PLAN 主实现按 FC 函数形态推进（functions/），Worker 保留为对照。
- 2026-10-10 官方资料补充：[创建 GPU 容器函数](https://www.alibabacloud.com/help/en/functioncompute/creating-a-gpu-function/)（容器镜像、实例回收后本地磁盘不保留）、[异步调用](https://help.aliyun.com/zh/functioncompute/fc-asynchronous-invocation)与[异步任务](https://help.aliyun.com/zh/functioncompute/asynchronous-task)（重试与结果处理机制，FC 接收请求不等于转码成功）。
- 成本实测方法论：`每输入视频分钟成本 = 总费用 / 输入分钟数`，两通道输出档位数量保持一致；总费用含 FC 各资源、OSS 请求读写、网络、日志、镜像及失败重试，预留/常驻闲置成本单列。最终比较以账单为准，不拿单项 GPU 价格当总费用或套用过期优惠。
- 画质做同片并排检查、音画同步及拖动播放验证，有条件补充 VMAF/SSIM；不预设 NVENC 相同码率下必然优于或劣于 MPS；冷启动耗时是否可接受由实测决定。
- 生产切换门槛：结合基线确定可接受的成本收益、完成耗时、画质及失败率；未定门槛或无实测证据不切换。

## 实施阶段（整体确认后开始）

1. 补齐需求、当前设计和验证文档；只读盘点 FC 函数代码、计费、资源、配额及调用关系，列出保留/迁移/删除清单。
2. 实现 CPU 探测/封面、GPU 转码和有限 MPS 兜底，修正回调与就绪顺序，增加开关；保留 LOCAL 并对齐新的输出规则。
3. 本地及自动化验证通过后，在 20 元预算内进行少量云端样本测试，记录真实画质、体积、耗时、费用和播放结果。
4. 通过开关小范围启用新上传链路，观察后逐步切换；保留回滚能力，不处理历史视频。
5. 迁移完成、确认无引用及待执行任务后清理无用北京区 FC 服务；核查是否被其他业务复用，不扩大删除范围。

## 验收与待核查事项

- 覆盖 SDR、手机 HDR、运动画面、高帧率、竖屏、无音轨和低分辨率样本；SDR 与 MPS 对照，HDR 以原片表现及正确播放为依据。
- 验证仅生成适用档位或原尺寸单档、不放大，帧率、画面比例、色彩及音轨符合要求。
- 验证上传、异步处理、OSS 产物、回调、就绪和实际播放。配置存在、进程存在或自动化测试通过不等于端到端验收。
- 注入提交失败、执行失败、超时、重复/晚到回调及兜底失败；检查状态正确、MPS 回退至多一次且无重复登记。
- 核实执行时长、临时磁盘、GPU 编解码能力、HDR 元数据保留、账户配额和各端 HLS 能力；验证前不宣称所有格式均支持。
- 费用包含冷启动、读写原片、多档输出、重试和兜底。GPU 按配置规格与使用时长计费，不是实际利用率百分比；并发费用取决于总资源时长。
- 新链路可通过开关回滚；原片、历史播放产物和仍被调用的服务保持可用。

## 发版与资料

统一通过 GitHub Actions（ci.yml / deploy.yml / release.yml），遵守 AGENT.md，禁止直接操作服务器。代码实施前补齐 CONTRIBUTING.md 要求的需求、设计和验收文档；实施 PR 同步业务、接口、测试文档及 CHANGELOG。

- [NVENC 音视频处理实践](https://help.aliyun.com/zh/functioncompute/best-practices-for-audio-and-video-processing-1)
- [MPS 模板与码率单位](https://help.aliyun.com/en/mps/api-querytemplatelist)
- [FC 计费概述](https://help.aliyun.com/zh/functioncompute/billing-overview-of-fc)
- [实例规格与并发](https://help.aliyun.com/zh/functioncompute/instance-types-and-specifications)


## 逐项执行清单与阶段进入条件

每步完成后在 verification.md 记录证据和剩余问题；未满足进入条件不能跳到部署或删除。以下是待执行清单，不代表授权已开始实施。

### P0：只读盘点和可行性门槛（2026-10-11 完成）

- [x] 重新检查工作树及 AGENT/CONTRIBUTING，保留用户变更。分支 `feature/cloud-function-transcode`（基于 master c29fb9ea + 文档 99e2beb4），工作树干净。
- [x] 北京区 FC 盘点（FC 3.0 API，2026-10-11）：共 14 函数。与本业务相关：
  - `video-transcode$transcode-master`（java11，4 vCPU/4G/1800s/512M 盘）：匿名 HTTP 触发器 `transcoe-master-video-transcode-pqrshwejna.cn-beijing.fcapp.run`（生产 `CloudFunctionTranscodeService` 硬编码在用），VPC `vpc-2ze7ua7kz3yy4qohdg3kp` + NAS `113914bbf5-rsn28.cn-beijing.nas.aliyuncs.com:/` 挂 `/mnt/nas`，代码最后修改 2024-04-17，未配日志投递（logConfig 为空），无异步任务积压。
  - `video-transcode$transcode-worker`（java11，2G/300s）：匿名触发器，master 内网 URL 写死调用；收到 `{"cmd": "..."}` 直接 `RuntimeUtil.execForStr` 执行——master 拼接命令 + 匿名触发器构成安全隐患，清理时一并消除。
  - `video-transcode$ffprobe`（匿名触发器）：仓库代码无引用，候选清理。
  - `video-transcode$clean`：定时触发器已禁用（enable=false），候选清理。
  - `video-2022-prod/dev$get-oss-object-md5`（匿名触发器）：`Md5CfService` 在用，保留。
  - 其余函数（acme-deploy、dashcam-transfer、site-monitor、proxy、e2b、serverless-devs-check）属其它业务，不动。
  - **安全备注**：`video-transcode` 4 个函数环境变量含明文 AccessKey（2022 年创建），清单已记录但本文不落明文；清理该服务时必须同步在 RAM 吊销该 AK。
- [x] 旧 CPU 函数源码：FC 上拉取代码包（checksum 8533010175999544614），反编译还原（包名 `com.github.makewheels.cfffmpeg`，源码不在本仓库）：
  - master 流程：签名 URL 下载原片 → ffprobe → 判定（音频 codec≠AAC 或 [视频 codec≠H264 且需降分辨率] 才重编码，否则 `-c copy`）→ 音视频分离 → 分片（视频 8 秒 / 音频 128 秒）→ 逐片 POST worker（内网匿名 URL）转码 → concat 合并 → `-c copy` 切 HLS（hls_time=1，`{transcodeId}-%05d.ts`）→ 边转边传 outputDir → 回调 body 仅 `{jobId}` → 删任务目录。
  - 视频重编码命令实际为 `ffmpeg -i {seg} -c:v {videoCodec} [-vf scale=-2:720] {out}`：libx264 无码率参数（默认 CRF）、**分辨率硬编码 720p（请求 1080p 也输出 720p）**、无色彩/位深/方向参数。
  - `quality=keep` 仅表示"不改分辨率"，不能解释成无损或保留 HDR。ffmpeg 二进制来自 NAS `{nas_dir}/ffmpeg/ffmpeg`，非函数自带。
- [x] FC GPU 规格（官方文档 2026-10-11 核查）：GPU 实例仅支持容器镜像部署；规格族 `fc.gpu.ampere.1`（A10 24G vGPU 分卡，显存 1024MB 倍数，vCPU ≤ 显存GB/2，内存 ≤ 显存GB×2048）与 `fc.gpu.tesla.1`（T4）。官方音视频实践提供含 NVENC 的 ffmpeg 基础镜像（`serverless_devs/nvidia-ffmpeg`），命令形如 `-hwaccel cuda -hwaccel_output_format cuda -c:v h264_nvenc`、GPU 缩放用 `scale_cuda/scale_npp`。计费 CU 制：1 CU 标准价 0.00011 元（至 2026-08-27 优惠价 0.000088 元），tesla 活跃 GPU 2.1 CU/(GB·s)；按 6G 显存+3 vCPU+12G 内存估算约 17.4 CU/s ≈ 0.0015 元/s。待云端实测项：账户 GPU 配额、`hevc_nvenc` 10-bit/色彩元数据/动态 HDR 保留、镜像 `-encoders` 实际特性（文档：[实例规格](https://help.aliyun.com/zh/functioncompute/instance-types-and-specifications)、[NVENC 实践](https://help.aliyun.com/zh/functioncompute/best-practices-for-audio-and-video-processing-1)、[计费](https://help.aliyun.com/zh/functioncompute/billing-overview-of-fc)，2026-10-11 读取）。
- [x] HDR/封装/播放/兜底路径确认：
  - 自建：ffprobe 读 `color_transfer/primaries/space`、位深、side data → HEVC Main10（NVENC `hevc_nvenc` 待实测 / CPU 端 x265 可保 10-bit）+ HLS fMP4（EXT-X-MAP）或 TS（HEVC in TS 兼容性差，倾向 fMP4）→ 播放端 web 为 video.js 8（VHS 支持 fMP4，HEVC 解码取决于浏览器）、Android media3 ExoPlayer、iOS AVPlayer，均具备 fMP4 能力，HEVC 硬解逐端实测。
  - Dolby Vision 动态元数据：NVENC 不支持 RPU 透传编码，AV1/HEVC 软编待评估；不能保留的类型按设计要求明确失败，不静默转 SDR。
  - MPS 兜底：现有三模板均为 H.264 baseline，不满足 HDR 保留；兜底 HDR 视频需新建 MPS 模板（不改历史模板）或明确失败，P1 设计定稿。
  - 当前登记链路的既知缺陷（P1 修复清单）：`TranscodeLauncher` 逐档创建+提交（未先建全档）、`TranscodeCallbackService.onTranscodeFinish` 先算完成数再登记产物、`AliyunCfTranscodeImpl.callback` 不验产物直接成功、`Transcode.isFinishStatus/isSuccessStatus` 对未知 provider 默认 true、无超时恢复、旧回调仅 jobId 无法校验 attempt。
- [x] 资源/单价/预算：账户可用余额 **6.76 元**（2026-10-11 查询），低于 20 元预算上限；按上述单价，短样本（10-30s）GPU 单任务约 0.1-0.3 元、MPS 对照单次约 0.03 元，余额可支撑 20+ 个短任务。执行原则：估算到 16 元（或余额 20%）即停新增，账单延迟不当 0 元。并发：按需、最小实例 0、上限可配置，账户 GPU 配额在首次云端实测时核实。版本回滚：镜像按 digest 部署，服务器保留 `previous` tag；函数按版本/别名回滚。

**进入 P1 判定**：需求不冲突；HDR 媒体信息读取、编码、fMP4 封装路径存在且 DV 动态元数据为最大未知（有明确失败路径）；GPU 配额、NVENC HDR 实测有验证计划（P3 首批）。判定通过，进入 P1。

### P1：后端任务、函数和状态实现（2026-10-11 完成，commit 36f0c7ed/a83ff3ea/beb669c3）

| 文件/模块 | 修改要求 | 实现结果 |
|---|---|
| transcode/TranscodeLauncher.java | 自建 ffprobe，按显示尺寸选档；先建全档任务再提交；新旧开关及 CPU/GPU 分流 | ✅ `transcode.pipeline=self-hosted\|legacy` 开关；PROBE 走 CPU 函数，probe 回调后按显示短边选档（ResolutionPlanner），先持久化全部档位再逐档提交 |
| video/bean/entity/MediaInfo.java | 新增真实帧率、色彩/HDR/位深/旋转/SAR/音轨信息，新增字段可空 | ✅ 新增 displayWidth/Height、frameRate(分数)、frameRateMode、sar、rotation、pixFmt、bitDepth、color*、dynamicRange、audioTrackCount/audioTracks，全部可空 |
| transcode/factory/AliyunCfGPUTranscodeImpl.java、TranscodeFactory.java | GPU 实现 TranscodeService，使用统一新任务协议 | ✅ GPU/CPU 共用 AbstractSelfHostedTranscodeImpl（payload 快照、attempt 生成、受理分类）；callback 入口明确抛 UnsupportedOperationException |
| transcode/cloudfunction/CloudFunctionClient.java（替代旧 CloudFunctionTranscodeService 的调用部分） | URL 与鉴权配置化，异步受理响应和 request ID | ✅ `aliyun.cf.transcode.cpu/gpu.url` + invoke-secret 配置化；429 退避重试不耗 attempt；未配置抛异常禁用新链路；旧类保留供旧链路 |
| transcode/bean/Transcode.java、task/FcTask*（新增） | attempt、deadline、重试/兜底及真实输出信息，条件更新；未知 provider 不默认成功 | ✅ FcTask 承载 taskId/attemptId/deadline/payload 快照/原子认领；Transcode 增 currentProvider/fallbackCount/actual*；isFinish/isSuccess 未知 provider 返回 false |
| transcode/TranscodeController.java | 新内部回调按 task 查 provider，校验密钥/attempt，旧回调保留 | ✅ 新端点 POST /transcode/cloudFunctionCallback（X-Callback-Secret）；FcTaskCallbackService 按 taskId 查任务校验 attempt，晚到回调 200+ignored；旧端点保留 |
| transcode/TranscodeCallbackService.java | 先校验产物再幂等登记，最后更新就绪 | ✅ validateOutput（playlist/ENDLIST/分片/init 逐项 OSS 实物校验）→ 原子 claim REGISTERING → 幂等登记（File 按 key、TsFile 按 transcodeId+tsIndex 查重）→ FINISHED → 状态统计；FAILED 不计成功；全失败 TRANSCODE_FAILED |
| transcode/aliyun/AliyunMpsService.java、mps/MpsFallbackService.java（新增） | 符合媒体保留要求的有限兜底，至多一次；不改历史模板 | ✅ 兜底走独立模板配置（aliyun.mps.fallback-template.*，保留帧率/声道/色彩，未配置则明确失败）；HDR 源不允许兜底；fallbackCount 0→1 原子抢占 |
| cover/、video/service/RawFileService.java | 新云封面、字段级更新及有限兜底 | ✅ CoverLauncher 按 pipeline 开关走 CPU 函数 COVER（HDR tone-map，失败明确报错）；CoverService.applyFcCoverResult 字段级更新；LOCAL/YouTube 行为未动 |
| watch/play/WatchService.java、transcode/M3u8Util.java | 仅发布成功档，真实 metadata；fMP4 init/map/签名/权限 | ✅ 主列表过滤非成功档；RESOLUTION/CODECS/FRAME-RATE/VIDEO-RANGE 真实值（无数据不造假）；EXT-X-MAP URI 走签名 URL；init segment 以 tsIndex=-1 登记，删除级联自动覆盖 |
| 超时恢复（新增 FcTimeoutRecoveryService） | 持久化 deadline 恢复丢回调任务 | ✅ 每分钟扫描 SUBMITTED 超时与 RETRY_WAIT 到期，原子认领后重提交（同一 payload 新 attemptId），额度耗尽走兜底/终态失败 |

建议新增 functions/ 下的 CPU/GPU 源码、Dockerfile、依赖锁及部署清单，具体目录遵循仓库约定；新文件不能只放本机临时目录。Python 用 uv，Node 用 pnpm，依赖复用全局缓存/硬链接。函数运行临时文件先注册 finally 清理，路径验证属于本次系统临时目录；OSS 失败产物只清理对应 attempt，不能按 video 根目录删除。

**进入 P2 判定**：函数协议 v1（协议/回调/manifest/错误分类）、状态机（FcTask + Transcode 双层）、数据库恢复（deadline + 原子认领）、HDR 路径（probe 识别 → HEVC Main10 → fMP4，明确失败路径）、目录策略（既有 attempt 幂等覆盖 + result.json）完整；后端 596 测试 + 函数 10 冒烟测试通过。判定通过。

### P2：LOCAL、客户端与自动化（2026-10-11 完成，commit e8f3e161）

- [x] cli/video_cli/local_transcode.py：档位对齐短边决策表（低清单档 720p 标签原尺寸、移除 480p 兜底、不放大、偶数对齐）；probe 补显示尺寸/旋转/SAR/帧率/位深/色彩/动态范围；HDR 源本地走 libx265 Main10 + 色彩 tag 透传，不静默转 SDR；多音轨全量映射、无音轨不造静音轨、不降帧。
- [x] cli/video_cli/commands/video.py：createTranscode 请求携带完整媒体保留字段（可选）；显示尺寸真实输出；失败经异常清楚显示。
- [x] transcode/local/LocalTranscodeService.java、dto/CreateLocalTranscodeRequest.java：扩展字段可选兼容；重复 finish 幂等与无云调用由既有实现保持；扩展字段写入 mediaInfo。
- [x] API 字段兼容性：新增字段全部可选，web/console/Android/iOS 无需改动即可保持现状；播放协议变化（fMP4 init、VIDEO-RANGE）由 video.js 8 / ExoPlayer / AVPlayer 原生能力承接，未扩播放器产品功能；文档同步见业务/接口文档更新。
- [x] T01–T18 后端/CLI 可执行项完成（详见 verification.md 第 5 节）：T01–T05、T07–T15、T17 后端覆盖；T06 与 T09 部分场景、T10、T16、T18 部分/云端项按条目标注。

后端测试 596 项通过、CLI 138 项通过、函数冒烟 10 项通过、checkstyle 与 ruff 0 违规。

**进入 P3 判定**：本地相关测试/构建通过、无关键失败；成本估算（P0：单短样本任务约 0.1-0.3 元，余额 6.76 元可支撑 20+ 任务）在预算内；方案为用户已确认文档。P3 云端执行待续（含镜像构建、函数部署、Infisical 变量贯通、C01–C09 小批验证）。

### TODO 汇总（2026-10-11，按依赖排序）

1. [x] 合并 PR #119（独立 Worker 原型，CI 全绿，2026-10-11 已合并）
2. [ ] PR #120 CI 全绿后合并；master 部署保持 legacy 默认（行为零变化）
3. [ ] 恢复 Windows 本机 Infisical 登录（PR #119 遗留阻塞，需用户手动操作）
4. [ ] Infisical `video-2022-secrets` 配置 transcode-fn 路径：FC_DEPLOY_AK、函数 OSS 凭证、invoke/callback secret（P3 前置）
5. [ ] 新建 MPS 兜底模板（720p/1080p，保留帧率/声道/色彩），回填 `ALIYUN_MPS_FALLBACK_TEMPLATE_*` 配置
6. [ ] 跑 `deploy-functions.yml`（先 dev）：构建 CPU/GPU 镜像 → FC 创建函数（异步重试 0、SLS 日志）→ 核实账户 GPU 配额
7. [ ] 开发环境 `transcode.pipeline=self-hosted` 全链路验证（probe→选档→转码→回调→登记→播放）
8. [ ] 预算内小批云端验证（≤16 元停新增，余额 6.76 元）：C01–C09 + GPU NVENC HDR 实测 + fMP4 真机播放（T 系列未完成项一并覆盖）
9. [ ] 生产小范围启用 → 观察逐步扩大（P3 收尾）
10. [ ] 旧 FC 清理（P4）：`video-transcode` 4 函数删除 + RAM 明文 AK 吊销 + NAS/VPC 资源核查；保留 md5 函数与 MPS 兜底
11. [ ] T16/T18 专项回归测试补充（封面并发、跨用户 init segment 访问）

### P3：小批云验证与发布

- [ ] Actions 构建和部署带版本镜像/函数，开发环境先测；不得直接操作服务器或用控制台绕过发布流水线。
- [ ] 新环境变量贯通 Infisical、deploy.yml 显式传递白名单、docker-compose.yml、应用 properties、函数配置；无明文密钥提交。
- [ ] 按 C01–C09 小批验证；预算不足停新增测试，未真机验证项如实记录。
- [ ] 当前必需 CI 全通过后按仓库流程发 PR、更新 CHANGELOG，再进入生产小范围；凭真实上传/播放证据逐步扩大。
- [ ] 记录切回旧链路开关、旧函数版本/镜像恢复及在途任务处置；回滚不能删新旧原片或破坏已有视频。

进入 P4：新链路与回滚已验收，旧任务排空，删除对象明确无引用。

### P4：旧 FC 清理与交付

- [ ] 每个拟删函数都有 region、标识、依赖与调用证据、备份和恢复步骤；没有证据保持待核实，不因“看起来旧”就删除。
- [ ] 按已确认范围清理无用北京 FC，保留 MPS 兜底、仍有调用、在途任务及必要回滚资源。
- [ ] 删除后验证新上传、LOCAL、历史播放、封面、回调和相关其它业务无误，记录资源确已删除及预留确已释放。
- [ ] 文档写明实际完成与未验证项；未完成 HDR 或真实端播放不能标全功能完成。
