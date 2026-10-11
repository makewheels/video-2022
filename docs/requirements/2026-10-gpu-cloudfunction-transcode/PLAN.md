# 自建云函数点播转码与 MPS 兜底

> 状态：待整体确认的草案。未实施、未部署、未删除云资源。
> 2026-10-07 初稿；2026-10-11 根据需求讨论更新。

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

### P0：只读盘点和可行性门槛

- [ ] 重新检查工作树及 AGENT/CONTRIBUTING，保留用户变更。基于当前主分支开工作分支，整体方案确认前不推送 master。
- [ ] 获取北京旧 FC 的名称/类型/版本、运行时、镜像、匿名/签名触发器、异步重试、配额、执行上限、最小/最大实例、日志与其它业务引用；不打印秘密。
- [ ] 获取旧 CPU 函数源码和实际 ffmpeg 参数；没有源码则记录只读可获取范围，不能把 quality=keep 解释成已验证无损或保留 HDR。
- [ ] 核实选定 FC GPU 规格实际支持的 NVENC/解码能力，镜像含正确驱动运行时与 ffmpeg 编译特性，读取当前官方文档并记录链接/日期。
- [ ] 确认 SDR/HLG/PQ/动态 HDR 的编码、封装、播放器和 MPS 兜底路径；旧 baseline 模板不满足 HDR，必须核实新模板/参数覆盖能力。
- [ ] 写明资源和单价估算，复用已有仓库，明确 20 元预算控制方案、并发配额与版本回滚步骤。

进入 P1：需求不冲突，HDR 必要媒体信息有可行路径，关键未知项有明确验证计划；不可行的内容给出证据，不能自行降级。

### P1：后端任务、函数和状态实现

| 文件/模块 | 修改要求 |
|---|---|
| transcode/TranscodeLauncher.java | 自建 ffprobe，按显示尺寸选档；先建全档任务再提交；新旧开关及 CPU/GPU 分流，不盲用像素面积判断竖屏 |
| video/bean/entity/MediaInfo.java | 新增真实帧率、色彩/HDR/位深/旋转/SAR/音轨信息，新增字段可空，旧记录仍可读取 |
| transcode/factory/AliyunCfGPUTranscodeImpl.java、TranscodeFactory.java | GPU 实现 TranscodeService，注入非空且明确异常，使用统一新任务协议 |
| transcode/cloudfunction/CloudFunctionTranscodeService.java | URL 与鉴权配置化，明确异步受理响应和 request ID；旧 CPU 路径兼容 |
| transcode/bean/Transcode.java、TranscodeRepository.java、状态常量 | attempt、deadline、重试/兜底及真实输出信息，条件更新和正确成功判定；未知 provider 不默认成功 |
| transcode/TranscodeController.java | 新内部回调按 task 查 provider，校验密钥/attempt，旧回调保留至旧任务排空 |
| transcode/TranscodeCallbackService.java | 先校验产物再幂等登记，最后更新就绪；恢复中断登记；错误终态不计入成功 |
| transcode/aliyun/AliyunMpsService.java、AliyunMpsTranscodeImpl.java | 符合媒体保留要求的有限兜底，至多一次提交；不改历史模板，不保留新 480p 生成分支 |
| cover/、video/service/RawFileService.java | 新云封面、字段级更新及有限兜底，LOCAL/YouTube 行为不回退 |
| watch/play/WatchService.java、transcode/M3u8Util.java | 仅发布成功档，真实 metadata；如选 fMP4，正确处理 init/map/audio、签名与权限 |
| 文件删除及存储模块 | 仅新增格式必须的支持，init/audio 不能遗漏；不批量删除历史产物 |

建议新增 functions/ 下的 CPU/GPU 源码、Dockerfile、依赖锁及部署清单，具体目录遵循仓库约定；新文件不能只放本机临时目录。Python 用 uv，Node 用 pnpm，依赖复用全局缓存/硬链接。函数运行临时文件先注册 finally 清理，路径验证属于本次系统临时目录；OSS 失败产物只清理对应 attempt，不能按 video 根目录删除。

进入 P2：函数协议、状态机、数据库恢复、HDR 路径与目录策略完整，相关测试可运行。

### P2：LOCAL、客户端与自动化

- [ ] cli/video_cli/local_transcode.py：档位及实际尺寸对齐，probe 信息完整，保留本机软件编码；移除无条件 SDR/音轨改写，特殊格式不能静默走收费云端。
- [ ] cli/video_cli/commands/video.py：上传和 finish 参数传递完整，先登记所有档位再完成单档，失败清楚显示。
- [ ] transcode/local/LocalTranscodeService.java、dto/CreateLocalTranscodeRequest.java：真实输出 metadata、兼容字段扩展、重复 finish 幂等、无云调用。
- [ ] 原有 API 字段继续兼容，增加字段同步 web/console/Android/iOS/CLI 的使用处和文档；仅改必要媒体协议，不扩播放器产品功能。
- [ ] 按 verification.md 的 T01–T18 完成必要测试，重点覆盖并发/重试/晚到回调，而非只验证 mock 方法调用一次。

进入 P3：本地相关测试/构建通过、无关键失败，成本估算在预算内，方案已整体确认。

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
