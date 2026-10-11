# 自建云函数点播处理设计

> 草案，尚未实现。不能以本文断言生产具备相应能力。
> 对应需求：[需求目录](../requirements/2026-10-gpu-cloudfunction-transcode/README.md)。

## 1. 分辨率、方向与媒体策略

探测必须读取 coded width/height、SAR/DAR、旋转/display matrix、时长、avg/r frame rate、time base、帧时间戳、pixel format/bit depth、color primaries/transfer/space/range、HDR 静态和动态元数据、全部音轨和语言/默认轨。10-bit 本身不等于 HDR；PQ/HLG、BT.2020 和相关元数据组合需判断，未知情况不得默认为 SDR。

方向按实际显示尺寸计算；旋转只应用一次。非方形像素先按 DAR 推导等效显示尺寸。使用显示短边 D 决定档位，按比例缩放，不裁剪，不拉伸：

| 原片显示短边 D | 产物档位 | 尺寸规则 |
|---|---|---|
| D < 720 | 一档，逻辑标签 720p | 原尺寸；若编码需要偶数，只做必要的最多 1 像素边界调整并记录 |
| 720 ≤ D < 1080 | 720p 一档 | 短边 720，保持比例，不额外生成 1080p 重复档 |
| D ≥ 1080 | 720p、1080p | 短边分别 720、1080，保持比例 |

例：640×360→640×360 单档；1280×720→1280×720；1600×900→1280×720；1920×1080→1280×720、1920×1080；1080×1920→720×1280、1080×1920；3840×2160→两档，不输出 4K。超宽画面仍按短边策略，核实编码器尺寸限制，遇到限制不得偷偷裁剪。

SDR 优先 H.264；HDR 需支持 10-bit 的编码与正确的 HLS 封装，HEVC Main10 是待实测候选，不能据此声称所有 Dolby Vision 元数据可由 NVENC 保留。PQ/HLG、HDR10、HDR10+、Dolby Vision 分别记录支持/限制。不能保留原片必要元数据的类型必须停止并报告，不自行转普通 HDR 或 SDR。

不设置固定 -r 或 fps filter 来降低原帧率；CFR 保留原值，VFR 检查时间戳、时长、音画同步及无主动抽帧。保留显示方向和范围标记，不套用无条件 yuv420p。音频编码兼容性需要转换时保留声道、音轨、语言和同步；无音轨不造静音轨。

## 2. 编码与成本基准

云端目标视频码率起点为 720p 4 Mbps、1080p 12 Mbps。建议初测 VBR、maxrate 为目标的 1.5 倍、bufsize 为 maxrate 的 2 倍；这是实现初值，不是已批准的最终峰值规格。结果不合格先调整参数，不擅自降帧、转 SDR 或加大预算。

低于 720p、低码率或无需重编码的原片，不为了匹配目标码率而填充数据或人为增大文件；优先无重编码重封装，但先验证关键帧、切片和播放器兼容性。高帧率运动片在同码率下可能质量不足，报告样本证据，不能宣称相同码率即相同画质。

CPU 专门做探测、独立截帧以及无需重新编码的处理；GPU 做需要重编码的任务。探测与封面避免反复全量下载；内网 OSS 与 FC 同地域。封面保持现有取帧时点，探测 0 秒失败可取首个可解码帧；HDR 封面转换为正确的可显示图片不改变点播视频的 HDR 输出。已有搬运封面不重新生成。

本地 x264 CRF 23/veryfast 先保留为 SDR 基准，不强制照搬云端 12 Mbps；新媒体保留要求和输出档位规则必须对齐。软件 HDR 编码能力先核查，不能要求 macOS 本机 NVENC。耗时变化需写入验证结果。

## 3. HLS 与播放链路

SDR 可先保留当前 TS 路线。当前 M3u8Util、TranscodeCallbackService、WatchService 假设非注释行是 TS，主播放列表仅写带宽；HDR 若采用 fMP4，必须连同 EXT-X-MAP/init segment、分片登记、签名 URL、权限检查、删除逻辑一起适配，不能只改 ffmpeg -hls_segment_type。

优先每约 6 秒分段、在边界设置关键帧，多档边界一致；VFR 不强制改 CFR。读取 playlist 实际 EXTINF 校验时长，而非假定所有段恰好 6 秒。

产物继续使用 {transcodeId}.m3u8 和相对分片名；每个 attempt 有隔离的目录，成功后持久化实际 m3u8Key。playlist 最后上传，且包含 ENDLIST。新主列表只发布成功登记的档位，填写真实 RESOLUTION、CODECS、FRAME-RATE、VIDEO-RANGE 及带宽；没有音频时不能填造出的音频 codec。多音轨需 HLS rendition/group 和各端验证。

历史 TS/480p playlist 保持可读可播，新旧格式不要强制统一。播放文件及 init segment 仍通过现有权限和签名访问，不能绕过鉴权直接改为公开 bucket。

## 4. 函数协议 v1（新增，不破坏旧 CPU 回调）

建议新增内部 POST /transcode/cloudFunctionCallback；旧 /transcode/aliyunCloudFunctionTranscodeCallback 保留，仅处理既有旧任务。正式名称在实现文档中统一。

请求字段：

| 字段 | 约束 |
|---|---|
| schemaVersion | 固定 1 |
| operation | PROBE / COVER / TRANSCODE |
| taskId、attemptId、videoId | 服务端生成，必填，不把客户端自报身份当可信来源 |
| transcodeId | TRANSCODE 必填；探测和封面使用自己的任务关联 |
| bucket、endpoint、inputKey | 服务端可信配置/数据生成，限制允许的 bucket 与源目录 |
| outputDir | 当前 task/attempt 的专属前缀，不允许写原片及其他视频目录 |
| profile、mediaPolicy | 档位、实际尺寸、编码/动态范围/帧率规则、码率/切片参数 |
| callbackUrl | 仅允许服务端指定地址，禁止任意外部 URL |

通过 SDK/IAM/签名的 FC 调用鉴权，异步任务协议按实际 FC 类型核查。回调携带 X-Callback-Secret，密钥从运行环境注入，不写请求日志或镜像。限制请求体大小和字段，校验任务与 attempt；callback body 的 provider 不作为路由授权依据。

回调 body：schemaVersion、taskId、attemptId、operation、status（SUCCEEDED/FAILED）、outputManifestKey 或 errorCode/errorMessage。

协议示例（占位符是示例，不是可直接部署的真实任务）：

```json
{
  "schemaVersion": 1,
  "operation": "TRANSCODE",
  "taskId": "task_example",
  "attemptId": "attempt_1",
  "videoId": "video_example",
  "transcodeId": "tr_example",
  "bucket": "configured-bucket",
  "endpoint": "configured-internal-endpoint",
  "inputKey": "trusted-source-key",
  "outputDir": "trusted-prefix/tr_example/attempt_1",
  "profile": "720p",
  "mediaPolicy": {
    "outputWidth": 1280,
    "outputHeight": 720,
    "dynamicRange": "SDR",
    "frameRateMode": "preserve",
    "targetBitrateBps": 4000000,
    "maxBitrateBps": 6000000,
    "bufferBits": 12000000,
    "segmentSeconds": 6,
    "preserveAudioTracks": true
  },
  "callbackUrl": "https://configured-host/transcode/cloudFunctionCallback"
}
```

成功回调示例：

```json
{
  "schemaVersion": 1,
  "operation": "TRANSCODE",
  "taskId": "task_example",
  "attemptId": "attempt_1",
  "status": "SUCCEEDED",
  "outputManifestKey": "trusted-prefix/tr_example/attempt_1/result.json"
}
```

manifest 至少包含 schemaVersion、task/attempt/transcode ID、playlistKey、全部 objects（key/size/类型）、实际 width/height、durationMs、codec/profile、pixelFormat/bitDepth、color 信息、dynamicRange、帧率分数及 CFR/VFR、音轨清单、实际 average/max bitrate。具体字段及单位必须在实现前定为 DTO/JSON schema，CPU、GPU、后端、CLI 共用契约测试。frame rate 用分数如 60000/1001，不浮点取整；bitrate 一律 bps，时长一律 ms。

PROBE 成功返回媒体结果 manifest，COVER 返回图片 key/实际尺寸；失败只返回任务身份与脱敏错误。错密钥 403；未知任务 404；非法字段 400；重复或已经失效的 attempt 返回 2xx 且记录 ignored，不触发重试风暴。

现有 `jobId`、provider 和状态字段的兼容映射必须写清楚；不能让新协议破坏旧函数回调。

成功 manifest 必须列出实际 playlist、分片、init/audio 对象及探测信息；服务端交叉校验目录和 OSS 实物，不能凭 SUCCEEDED 直接置 READY。错误日志脱敏。重复回调返回稳定结果；旧 attempt 不得改现有任务。

## 5. 状态机、重试与恢复

逻辑阶段：QUEUED → SUBMITTED/RUNNING → OUTPUT_VALIDATING → REGISTERING → SUCCEEDED。失败进入 RETRY_WAIT；耗尽后 FALLBACK_PENDING → FALLBACK_RUNNING → SUCCEEDED/FAILED。项目已有状态常量可映射这些阶段，但终态和成功必须区分。

建议初始最多 2 次自建执行（首次 + 1 次重试），重试退避 10 秒并抖动，MPS 最多提交 1 次。兜底认领必须数据库原子化；提交响应不确定时先通过可关联的任务信息查证，不盲目再次提交，也不能把网络超时当作已确定没有计费任务。参数配置化、启动检查范围。确定性损坏文件、违反媒体策略或无权限不盲目重试；兜底无法满足媒体保留要求就明确失败。

先建立本视频全部档位任务，再提交任一档，避免第一档快回调误判全部完成。task 记录 attempt、原 provider、当前 provider、FC request ID、MPS job ID、retry/fallback count、阶段、deadline、错误和实际产物信息；采用数据库条件更新领取 attempt/登记权，不能用 JVM 内存锁解决多实例问题。

登记使用可恢复的写入或确定性对象标识，避免回调重发产生重复 File/TsFile/Webhook。只有产物验证和所有登记完成才能标记单档成功；视频按成功数量进入部分完成或 READY。失败终态不能计入成功数量；全部失败进入 TRANSCODE_FAILED；成功+失败保留可播放部分且显示失败信息，不宣布全部成功。

执行超时可从探测时长及实测速度估计，受 FC 最大执行时间约束；超出能力不得缩短视频来通过。持久化 deadline，定时恢复丢回调任务，服务端重启后可恢复；超时认领与晚到回调互斥。先核实 FC 自带异步重试，避免平台和应用重试叠乘。

LOCAL 同样先登记后成功，重复 finish 幂等；不增加收费云 fallback。封面失败不把已可播放视频倒退；仅更新相应字段，不用旧快照整文档覆盖状态。

## 6. 配置、部署、预算与清理

配置至少包括总开关（旧链路/自建）、CPU/GPU 函数标识/地址、回调密钥、并发/最小实例、重试上限、兜底开关、deadline、目标码率及 HLS 参数。缺配置则禁用新路径并报清楚原因，不静默使用匿名函数 URL。

并发根据实际账户 region GPU 配额、单实例资源和压测设置可配置上限。最小实例 0、无预留。达到上限排队；账户限流做退避，不把排队/429 直接算作执行失败并提交昂贵 MPS。

复用北京镜像仓库和 Infisical/OIDC 工作流，新增专门 FC 构建/部署步骤或 workflow；镜像使用版本/digest，不能仅 latest。新增配置必须贯通 Infisical → Actions 的变量白名单 → compose/FC 环境 → 应用读取，不能只改 application.properties。代码提交 master 会触发部署，整体方案确认前不要推送到 master。

首轮测试先记录单价和预计资源秒数，按小批提交，建议估算花费到 16 元停止新增任务并预留 4 元给在途任务；若保守估算可能超过 20 元则不开下一批。不得开付费常驻资源，不得为了测试创建未经核算的收费企业镜像仓库。只停止本次测试流量，不阻断正常业务；最终账单未出时写估算及待核对，不能把延迟账单当 0 元。

清理前列出 cn-beijing 每个相关函数/旧 service 的代码引用、触发器、最后调用、异步积压、预留、其它业务依赖、保留/删除理由。备份已脱敏配置与可恢复代码/镜像版本、列出恢复步骤；含秘密的备份仅入密钥管理系统。新链路及回滚路径验证完成后，删除确定无用的资源。旧函数仍承担在途/历史回调或回滚时不得删除。保留 MPS 权限、必要 pipeline 和兜底模板，不把 MPS 一并退役。


## 7. 实现命令与媒体证据

获取源/输出信息的基准命令（文件路径由程序参数传递，不在 shell 中拼接不可信文件名）：

```sh
ffprobe -v error -show_format -show_streams -of json INPUT
ffprobe -v error -select_streams v:0 -show_frames -of json INPUT
```

第二条只对短测试样本执行或限制读取区间，避免全片 JSON 占满磁盘。源/输出完整媒体证据按 verification.md 记录；输出文件名不要泄露用户信息。

FFmpeg 使用参数数组启动，检查 return code，保留脱敏错误末尾，执行超时可终止整个子进程组并清理。构造命令时按探测结果选择 codec/pixel format/color metadata/audio mapping，不给出无条件套用全部格式的万能命令。GPU 镜像验证 `ffmpeg -encoders`/`-filters` 只能证明编译支持；必须用实际 GPU 编码样本证明运行支持。

HDR HLS 选型、动态元数据保留、各端读取、MPS 兜底属于同一门槛。任何一项不满足都记录为该格式未支持，不能把“输出文件标了 BT.2020”或“能打开”当作完整 HDR 验收。
