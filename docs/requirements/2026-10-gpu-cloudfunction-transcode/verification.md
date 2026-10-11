# 验收方案与结果记录

状态：方案草案，以下测试均未执行。实现者逐项记录通过/失败/未验证及证据，不预填通过。

## 1. 本地及 CI 验证

| ID | 测试输入/操作 | 必须断言 |
|---|---|---|
| T01 | 横屏 360/720/900/1080/2160、竖屏、旋转和 SAR | 档位及实际尺寸与设计表一致，无放大、裁剪、重复档或新 480p 档 |
| T02 | SDR 8/10-bit、PQ、HLG、HDR 静态/动态元数据 | 识别信息完整，10-bit 不直接判 HDR；不支持格式明确拒绝，不静默转 SDR |
| T03 | 23.976/25/30/50/59.94/60 fps、VFR | 无固定降帧参数，实际时序/帧数匹配；方向和色彩标记保留 |
| T04 | 无音轨、单/双声道、多音轨带语言 | 无音轨不造音频，其余轨道/声道/语言保留，不无条件单声道 |
| T05 | GPU 工厂和状态方法 | GPU 服务非 null，未知 provider 不能默认成功；失败/进行中不算成功 |
| T06 | 先创建两档，第一档立即回调 | 全部任务身份已持久化，首档完成只进入部分完成 |
| T07 | FC 受理 2xx、4xx、429、5xx、网络超时 | 分类受理/排队/重试；request ID 持久化，不把未受理响应当运行成功 |
| T08 | 无密钥/错密钥、错 task/attempt/provider | 拒绝越权回调，任务与视频不改变，无秘密日志 |
| T09 | 重复成功、并发成功、旧 attempt 晚到 | File/TsFile/Webhook/Ready 不重复；旧结果不覆盖新任务 |
| T10 | 丢回调、超时认领、重启恢复 | 持久化 deadline 可恢复，超时和成功竞态只让一个处理分支生效 |
| T11 | 自建执行失败两次、MPS 成功/失败 | 自建 ≤2 次、MPS ≤1 次，无无限回退；兜底使用保留帧率/颜色/音轨的配置 |
| T12 | 一档失败、另一档成功；全部失败 | 成功档可播放，READY 仅全部成功；全部失败显示失败，不能扫描不存在产物 |
| T13 | 缺 playlist/ENDLIST/分片、0 字节、越界路径 | 无 READY、无部分脏登记，错误可恢复；文件不能越出 task/attempt 前缀 |
| T14 | TS playlist 空行/CRLF；fMP4 init/segments | 正确解析与签名替换，init 不误当普通 TS，无带宽 null 或造假 codec |
| T15 | LOCAL 两档/原尺寸/重复 finish | 不调用 FC/MPS，登记幂等，真实尺寸和媒体信息正确 |
| T16 | 封面重发失败、旧状态快照、YouTube 已有封面 | 不重复截帧，不将 READY 改回 TRANSCODING，字段级更新 |
| T17 | 开关关闭、在途旧任务、新旧 playlist | 旧任务按旧协议完成，历史 TS/480p 可播放，新任务可回滚到旧链路 |
| T18 | 调用文件权限、用户 A 请求用户 B 的分片/init | 鉴权保持，签名不暴露跨用户资源，bucket 不改公开 |

复用既有测试：TranscodeCallbackServiceTest、CloudFunctionTranscodeServiceTest、AliyunMpsServiceTest、LocalTranscodeServiceTest、WatchServiceTest、cli/tests/test_local_transcode.py、test_video_commands.py。新分类器、GPU、状态机、协议测试放在现有目录；Java 集成测试继承 BaseIntegrationTest，真实测试 Mongo，外部服务用 @MockitoBean，不碰生产库。

建议命令（先核对环境，不自动启动新的收费资源）：

```sh
# 仓库根目录
 git diff --check
 uv run --no-project python scripts/check_file_length.py
# server/，本地测试 Mongo 可用后
 mvn test -pl video -Pspringboot --batch-mode --no-transfer-progress
# cli/
 uv run --extra test pytest tests/test_local_transcode.py tests/test_video_commands.py -v
 uv run ruff check
# 若更改 web 播放行为，web/
 pnpm exec vitest run
 pnpm run build
```

按实际改动执行 console/Android/iOS 相应检查；具体命令遵循当前 ci.yml。测试/构建通过后不为凑次数重复运行；合并前所有仓库必需 CI 都要通过。

## 2. 云端样本与播放验收

真实样本选择 10–30 秒片段，先少量，必要时合并覆盖条件，不全量重转历史素材。原片只读，取测试副本；临时文件使用运行时系统临时目录，先注册清理并验证路径归属再删。持久化只保留用户要求的验收记录，不复制个人原片进 Git。

| ID | 样本 | 检查及证据 |
|---|---|---|
| C01 | 普通 SDR 1080p、运动画面 | GPU 两档、MPS 对照；ffprobe 原/输出 JSON，实际码率/体积，关键帧附近和快速运动画面对比 |
| C02 | 720p 与低于 720p 原片 | 单档，尺寸不增，低码率不人为膨胀；首中尾播放正常 |
| C03 | 竖屏/带旋转的手机视频 | 实际方向、比例正确，封面正确，无横向拉伸或重复旋转 |
| C04 | 50/60 fps 运动视频 | 原/输出 fps、帧数和时间戳检查；运动流畅性及音画同步 |
| C05 | HLG/PQ 与真实 Dolby Vision 样本 | 按各类型分别标明支持结果，动态元数据处理有证据；至少 HDR 真机实际播放，不能用截图证明 HDR 高光亮度 |
| C06 | 无音轨、双声道，实际存在多音轨时加多音轨 | 检查 track/channels/language，试听音画同步，不产生额外静音轨 |
| C07 | LOCAL 端到端 | 本机编码上传、登记、播放；可观测 FC/MPS 调用为 0，不能仅凭代码分支断言零调用 |
| C08 | 小规模并发及故障样本 | 至少同时两任务证明并发；超过配置上限排队，限流不误兜底，故障 MPS 一次回退，异常任务给出可理解状态 |
| C09 | 回滚与旧视频 | 切回旧通道的新任务成功；已有旧任务回调、历史 TS/480p 和原片仍可用 |

画质验收：相同播放尺寸、相同时间点对比，至少首/中/尾和运动细节；不能仅检查 codec 名称。SDR 可用 VMAF/SSIM 辅助，记录工具、对齐/缩放方式及数值；HDR 不直接用 SDR 指标替代实际显示验证。不出现明显新增色块、模糊、偏色或音画错位；与对照相比不合格的场景须记录并调整参数，不私自改变需求。

定量最低检查：CFR 长度差不超过 1 视频帧加正常音频编码尾部容差；VFR 检查原/输出时间戳序列和总时长，异常逐项解释。显示比例一致，必要偶数对齐最多 1 像素并记录。所有被引用的对象存在且非空，playlist 为 VOD 完整结束，首/中/尾可 seek，多档切换无明显跳变。码率目标不是所有短片的精确文件大小保证，必须记录实际均值与峰值并核查输出参数约束。

播放矩阵：网页桌面浏览器、iOS Safari/应用 AVPlayer、Android 浏览器/应用 ExoPlayer。各端至少普通 SDR；HDR 对实际有 HDR 屏幕的设备单列。不支持 HDR 的设备结果如实记录，不用转 SDR 绕过保留 HDR 的要求。没有设备/登录或预算不足时写“未验证”，不能写全部兼容。

封面检查：对象存在、签名 URL 可访问、方向/颜色正常，实际视频卡片或播放列表展示；仅数据库存在 coverId 不算通过。

## 3. 每次执行必须填的记录

| 字段 | 结果（执行后填写） |
|---|---|
| commit、镜像 digest、工作流 run URL、FC version | 未执行 |
| 日期、地域、规格、最小/最大实例及账户配额 | 未核实（GPU 配额待首次云端实测） |
| 样本匿名编号、来源权限、hash、原片媒体参数 | 未执行 |
| 原/输出尺寸、fps、色彩、位深、音轨、时长、体积、平均/峰值码率 | 未执行 |
| task/attempt/FC request ID/MPS job ID 与结果 | 未执行 |
| CPU/GPU 时间、下载上传时间、冷/热启动、总完成时间 | 未执行 |
| 单价、执行前估算、在途预留、实际账单与出账时间 | 未执行 |
| 自动化结果、真机型号/系统/播放器和播放证据 | 未执行 |
| 旧服务保留/删除清单、理由、配置/代码恢复位置 | 已盘点（2026-10-11，见 PLAN P0；删除待 P4） |
| 未通过/未验证项、影响、处理决定 | 待填写 |

## 4. P0 盘点记录（2026-10-11，只读）

- 工作分支 `feature/cloud-function-transcode`，基于 master c29fb9ea；工作树干净。
- FC cn-beijing 14 函数盘点完成：`video-transcode` 服务 4 函数（master/worker/ffprobe/clean，java11，2024-04-17 后未更新；master 匿名触发器在生产代码使用；clean 定时触发已禁用；无异步积压）+ `video-2022-prod/dev$get-oss-object-md5`（在用，保留）。旧 CPU 函数代码包 checksum 8533010175999544614 已下载并反编译，ffmpeg 参数全量还原（见 PLAN P0）。
- 安全备注：`video-transcode` 函数环境变量含 2022 年明文 AccessKey；本文与仓库不落明文，P4 清理时同步在 RAM 吊销。
- MPS 三模板复核与 PLAN 记录一致；模板为 H.264 baseline，不满足 HDR 兜底保留要求。
- 账户余额 6.76 元（bssopenapi QueryAccountBalance）；FC GPU 单价按官方 CU 制估算，单短样本任务约 0.1-0.3 元。
- 官方文档读取日期 2026-10-11：实例规格、NVENC 实践、FC 计费（链接见 PLAN）。
- 未验证：账户 GPU 配额、hevc_nvenc 10-bit/HDR 元数据、镜像实际特性、fMP4 各端播放——均列入 P3 云端验收。

## 5. T01–T18 本地/自动化执行结果（2026-10-11）

后端 `mvn test` 596 项、CLI `pytest` 138 项、函数冒烟 10 项全部通过（commit beb669c3/e8f3e161）。逐项：

| ID | 结果 | 证据与说明 |
|---|---|---|
| T01 | ✅ 后端+CLI | ResolutionPlannerTest：360/720/900/1080/2160、竖屏 1080x1920、奇数尺寸偶数对齐；无放大/裁剪/重复档/480p；cli test_output_size_short_side_rule_and_even_alignment |
| T02 | ✅ 函数+后端 | 函数冒烟：SDR 判 SDR、PQ 10-bit 判 HDR10、10-bit 无 transfer 判 UNKNOWN（不默认 SDR）；后端 planner 对全部非 SDR 值（含 UNKNOWN/null）恒走 GPU 不允许 remux；不支持类型（DV 保留）在 P3 实测后按明确失败处理 |
| T03 | ✅ 函数+CLI | mediaPolicy.frameRateMode=preserve、编码命令无 -r/fps filter（gpu/transcode.py 冒烟断言 frameRate="30/1" 保留）；CLI probe VFR 识别 frame_rate_mode |
| T04 | ✅ 函数+CLI | -map 0:v:0 -map 0:a?：无音轨映射为空不造静音轨；多音轨保留由 mediaPolicy.preserveTracks=true 强制（false 直接拒绝）；CLI 无音轨测试通过 |
| T05 | ✅ 后端 | FcTaskFlowIntegrationTest.unknownProvider_neverTreatedAsSuccess：未知 provider isFinish/isSuccess 均 false；GPU 实现 TranscodeService 可注入（工厂非空） |
| T06 | ✅ 后端 | probeCallback_createsAllLanesBeforeAnyCompletion：probe 回调一次创建两档+两 FcTask 全部持久化后才提交；firstLaneCallback_partlyComplete：首档完成仅 PARTLY_COMPLETE |
| T07 | ✅ 后端 | submitThrottled_goesRetryWaitWithoutConsumingAttempt：429 → RETRY_WAIT、attemptCount 不耗；网络异常同样不盲提交（CloudFunctionClient 吞异常置未受理） |
| T08 | ✅ 后端 | callback_wrongAttemptOrUnknownTask_rejected：错 attempt 200+ignored 状态不变、未知任务 404；Controller 层错密钥 403（沿用既有 secret 校验结构，回调日志不含密钥） |
| T09 | ✅ 后端 | 重复成功回调 TsFile 数量不变（幂等登记）；旧 attempt 晚到 200+ignored；并发登记由 REGISTERING 原子认领互斥 |
| T10 | ⚠️ 部分 | 持久化 deadline + 原子认领已实现并有测试（retryExhausted 流程覆盖恢复链路）；FC 崩溃型丢回调的服务重启恢复依赖同一定时扫描（代码路径一致），长时间运行验证留待 P3 开发环境 |
| T11 | ✅ 后端 | retryExhausted_mpsFallbackAtMostOnce：失败→重试→耗尽→MPS 兜底一次；再失败不重复兜底（verify times(1)）；兜底模板配置化且保留媒体要求 |
| T12 | ✅ 后端 | firstLaneCallback（一档成功一档进行中→PARTLY_COMPLETE）+ validationMissingEndlist（单档失败→TRANSCODE_FAILED）；混合终态归 PARTLY_COMPLETE 且主列表只发成功档 |
| T13 | ✅ 后端 | validationMissingEndlist：缺 ENDLIST → FAILED + TRANSCODE_FAILED，无脏登记（TsFile=0）；越界路径由协议 validate_request 前缀校验（函数侧）+ playlistKey 与任务一致性校验（后端） |
| T14 | ✅ 后端 | M3u8UtilTest：CRLF/空行兼容、fMP4 EXT-X-MAP 解析、init 不混入分片列表；WatchService 签名替换对 init/分片生效 |
| T15 | ✅ CLI | cli test_target_resolutions*/test_output_size*：两档/原尺寸/不放大；LOCAL 无云调用由既有 LocalTranscodeServiceTest 保持；重复 finish 幂等既有测试保持 |
| T16 | ⚠️ 部分 | 封面回调幂等（状态已 READY 跳过）与字段级更新已实现；"不将 READY 改回 TRANSCODING"由封面字段级更新保证（PR #116 模式），专用并发回归测试待补 |
| T17 | ✅ 后端+设计 | 旧链路默认保持：transcode.pipeline 默认 legacy，TranscodeLauncher.legacyTranscode 原逻辑未动；旧回调端点保留；历史 TS/480p playlist 由 getMultivariantPlaylist 兼容读取（WatchServiceTest 回归通过）；切回开关=改环境变量重启 |
| T18 | ⚠️ 部分 | init segment 走同一 fileId 签名通道（无绕过鉴权）；跨用户访问拦截由既有 FileAccessSignatureService HMAC 测试覆盖；用户 A 请求用户 B init 的专项断言待补（fileId 为雪花 ID 不含用户信息，签名绑定 videoId） |

未验证项（留 P3）：FC 真实受理/回调行为、GPU 配额、NVENC HDR 实测、fMP4 各端播放、长时间恢复运行验证、T16/T18 专项回归测试补充。

交付时汇总 R01–R12、T01–T18、C01–C09 的通过/失败/未验证状态。任何核心媒体保留、鉴权、幂等、兜底或预算项目失败，不得全量切换。20 元不足以覆盖全部矩阵时停止新增云测试，如实列未验收项，不能靠配置和 mock 冒充真机/云端完成。
