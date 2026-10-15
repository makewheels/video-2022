# 验收方案与结果记录

状态：代码修复、发布及可执行的功能验收已完成（2026-10-14）。最终结果见第 12 节；前面的记录是按日期保留的历史快照。真实原片主观画质、移动端/HDR 真机和完整故障矩阵仍未验证，不能声称全部媒体及设备验收完成。

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

## 3. 执行记录索引

| 字段 | 结果（执行后填写） |
|---|---|
| commit、镜像 digest、工作流 run URL、FC version | 第 8、10–12 节；最终代码 b07df837，函数镜像 20261013-002459-0017 |
| 日期、地域、规格、最小/最大实例及账户配额 | 第 8、12 节；cn-beijing、T4、并发 2、预留 0，实际配额已读 |
| 样本匿名编号、来源权限、hash、原片媒体参数 | 第 8–11 节；均为本验收用户私密合成样片 |
| 原/输出尺寸、fps、色彩、位深、音轨、时长、体积、平均/峰值码率 | 第 8、10、11 节记录实际媒体参数及体积；完整逐片峰值码率仍未验收 |
| task/attempt/FC request ID/MPS job ID 与结果 | 第 8–12 节；真实两次自建失败、唯一 MPS job 及重启恢复 |
| CPU/GPU 时间、下载上传时间、冷/热启动、总完成时间 | 第 8、10 节有 GPU duration/并行时间窗；完整分阶段冷/热性能未验收 |
| 单价、执行前估算、在途预留、实际账单与出账时间 | 第 7–12 节；最终已知新函数日账单 4.48822 元，非含 OSS/MPS 的精确任务总额 |
| 自动化结果、真机型号/系统/播放器和播放证据 | 第 12 节；完整 CI 和真实桌面 Chrome 通过，移动端/HDR 真机未验证 |
| 旧服务保留/删除清单、理由、配置/代码恢复位置 | 第 7、12 节；旧 4 FC/RAM 用户已删，备份保留；NAS/VPC 保留 |
| 未通过/未验证项、影响、处理决定 | 第 12 节逐项标明；无原片/设备时不补造通过证据 |

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

## 6. 独立 Worker 原型验证记录（PR #119，2026-10-10 合并）

以下为 `transcode-worker/`（独立对照实现）的验证记录，由 PR #119 带入：

- `uv run --locked --project transcode-worker pytest transcode-worker/tests -p no:cacheprovider -v`：26 通过，无 skipped。包含 3 条真实 FFmpeg 转码与整份 HLS 解码：横屏有音轨、竖屏无音轨、小尺寸不放大，均核对 H.264/AAC 与尺寸。
- Python 环境、依赖缓存及生成媒体使用任务专属系统临时目录并自动清理；`git diff --check`、文件长度门禁通过。
- CI（run 38039407654）：Worker 26 条 Linux 测试、GPU 候选镜像构建、NVENC 编码器枚举、容器 Python 导入均通过；原有后端/前端/CLI/AI Agent/Android/iOS/质量门禁/构建通过；API 与浏览器 E2E 按 PR 规则 skipped。
- 本机 RTX A2000 Laptop GPU 实际 NVENC：系统 FFmpeg 要求 API 13.1、驱动仅支持 13.0，改用系统临时目录中固定版本 FFmpeg 7.1.1；横屏有音轨、竖屏无音轨、小尺寸不放大 3 个样本硬编码并完整解码通过，forced-idr 保证分片边界；临时二进制和媒体已清理。
- 未确认项：媒体验证主要用 libx264（NVENC 由本机实测补足）；FC、OSS 及费用/画质对比未跑，本机结果不能代替目标 FC 规格验证；原型单进程锁不提供跨实例幂等、STS 未自动续期——生产接入以 functions/ 主实现为准（含 FcTask 跨实例幂等）。

## 7. 2026-10-12 续验与修复（执行中）

### 已复核的线上事实

- 原主实现 PR #120 已合并；#121–#134 为部署、NVENC 与 init 分片修正。原线上 commit `1c23e959deaa79e5ac9af7f5b2251bd389763800`。
- 原部署：[服务器部署](https://github.com/makewheels/video-2022/actions/runs/38134701608)、[函数部署](https://github.com/makewheels/video-2022/actions/runs/38134236787)、[master CI](https://github.com/makewheels/video-2022/actions/runs/38134234031) 均 success。
- 原函数 CPU digest `sha256:04649b35ca2115074feee9edcf1c38f186e7dcceae31ac81a8241fbde6da5028`；GPU digest `sha256:9b601631234c007f043d8bbea5873f5320ed56a3c3d0e3b1bceff449a2f68624`。后续修复需重新部署，不能把这些 digest 当成新代码证明。
- 原生产短样本 `v_2979658100001490` / watch `2VU1CS`：原记录显示 READY、CPU remux、主/子列表、分片 302→OSS 200。此证据仅为原协议/文件访问，不代表本次修复、真实浏览器或完整 HDR 验收。
- 4 个旧 `video-transcode$...` 函数不再出现在 cn-beijing ListFunctions。恢复代码在私有 OSS `oss-data-bucket/backup/video-transcode-fc-20261011/`，4 包各 37,643,990 字节。旧 AK 已删除；2026-10-12 确认专用 RAM 用户无 AK/组，仅余 System AliyunOSSFullAccess，解绑后删除，GetUser 返回 EntityNotExist.User。
- 保留 CPU/GPU 新函数、prod/dev md5、MPS 模板和其它业务。NAS/VPC 尚未删除，需完整依赖/恢复核查；旧函数已删后的新任务 legacy 回滚不再调用旧 URL，但实际回滚未验。

### 本地检查与边界

- 最终本地后端 609 项通过，0 失败/错误/跳过；覆盖任务恢复、全部档位任务先持久化、LOCAL 上传云 MD5 为零、播放授权及 MPS 兜底。
- 函数 22 项通过，含真实软件编码替代 NVENC 后的 HEVC 静态 SEI 补全、fMP4、首帧 mastering/content-light 交叉比较和完整解码。此结果证明封装/元数据处理，不代替 FC NVENC 或 HDR 真机。
- CLI 139 项与函数 22 项合计 161 项通过，包含真实 LOCAL HDR10 fMP4 静态元数据和完整解码。
- 私密播放/跨视频 init/封面/精确帧率/MPS 专项 42 项通过；609 项全量回归包含后续 LOCAL 与任务准备改动。GitHub 必需 CI 待验证。
- Dolby Vision、HDR10+ 和未知类型明确拒绝；>10-bit 无验证能力时明确失败。原首帧探测并不能证明所有动态类型全片均支持。

### 费用与新增批次上限

- 2026-10-12 QueryInstanceBill（2026-10-11 DAILY）：新 CPU 0.022 元、新 GPU 3.37073 元，合计 3.39273 元；账单涉及整日函数使用，不把其当作单样本精确成本。
- 2026-10-12 查询账户余额 2.72 元；已有其它业务费用，余额变化不能全部归本任务。2026-10-12 当日新函数尚无完整账单，不记为 0 元。
- 本轮新增付费短样本保守上限 1 元，随批次复核；停止线为累计测试估算接近 16 元或账户保留不足，不扩大 20 元总预算，不开预留实例。

### 最新验收状态

| 范围 | 状态与剩余项 |
|---|---|
| T01–T09、T11–T15 | 既有自动化与新增可靠性回归通过；以最终 CI 验证新 commit |
| T10 | 持久化恢复路径已有回归；真实丢回调/进程重启仍未完整验收 |
| T16/T18 | 已补封面幂等、跨视频 init/私密播放专项，正在执行 |
| T17 | 旧数据读取代码保留；旧 FC 已删除，新任务回滚入口修正，真实回滚未验 |
| C01–C04/C06 | 原短 CPU 样本及原 NVENC 标签测试仅部分覆盖；本次矩阵待云端小批验证 |
| C05 | 静态 HDR10 本地 SEI/封装通过；云端与 HDR 真机未验，动态格式拒绝不冒充保留 |
| C07 | LOCAL 自动化通过，真实上传与云处理调用为零尚待现场观测 |
| C08 | MPS/排队/有限次数自动化通过；并发与真实故障兜底未完整验收 |
| C09 | 历史播放与原片待线上复核；实际回滚未验 |

完整云端与真机门槛仍未全部满足，不能声称所有需求完整完成。


## 8. 2026-10-13 发布及真实微样本验收（执行中）

- 修复 PR #135 已合并，服务端 commit `5bae26717f640d288c5ea6e9448dfe8d115d6205`；来源 commit `6326ba4664a6336182cde7e3c9dade5bc3f8aab1`。
- [完整手动 CI](https://github.com/makewheels/video-2022/actions/runs/38276087873) 和 [PR 必需 CI](https://github.com/makewheels/video-2022/actions/runs/38276022780) 均通过。手动 CI 的 API、浏览器 E2E 实际运行通过，无跳过。
- [后端发布](https://github.com/makewheels/video-2022/actions/runs/38278246731) 成功，Docker 运行环境 APP_VERSION 回读匹配上述 merge commit。
- [函数发布](https://github.com/makewheels/video-2022/actions/runs/38278017623) 成功；镜像标签 `20261013-002459-0017`，CPU digest `sha256:3abc777473042a1fc23f52cd2d34075366c581229ce6b961b6a63c2c4846a2d4`，GPU digest `sha256:0e0b983910e29929e0b8244c97a937a684252664d7e7cfc5de1ef208a7048434`。
- FC 回读：CPU 2 vCPU/4GB，GPU T4 16GB/8 vCPU/32GB；两函数并发上限 2、预留实例 0、平台重试 0。Beijing 实际 GPU 按需配额 3 卡、总配额 10 卡。
- 首次构建成功但发布失败：受限临时密钥项目中的非敏感值 `2` 被 GitHub 当秘密屏蔽，导致跨 job 镜像标签 output 被丢弃。旧镜像未被替换；移除临时项目冗余并发默认值并显式指定已有标签重发成功。主生产身份仍只信任 master；临时 CI/函数身份分别只信任转码分支的对应工作流，使用仅含所需配置的独立项目。
- 4 个自建新上传微样本全部 READY，A 为 1080p/60 fps/双音轨 SDR，B 为静态 HDR10，C 为 59.94 fps HLG 竖屏，D 为旋转 90 度 SDR。它们是自生成的 2–4 秒功能微样本；不能代替 10–30 秒实际运动素材的主观画质或 HDR 真机亮度验收。

| 样本 | videoId / watchId | 原片 SHA-256 |
|---|---|---|
| A | `v_2982302700001035` / `2VW6AM` | `1938c74110db774fad78b93541caf9f2f9edfaab0927e6cacc00cd2482fdead7` |
| B | `v_2982302800001192` / `2VW78J` | `dfbbdc90397b56d851eee319b1569b7755f0487832eb1c65f8374185ec6f4105` |
| C | `v_2982302800003862` / `2VW7UX` | `9c9811b0940897c8fb43aaa4f5ce56986f75e573abee2a321c22f73e0adcd9d0` |
| D | `v_2982302800005174` / `2VW8KG` | `25acf89abf45c0df04dc22154d5f98c3be57aa098b789c5cae6b1d04ded3fe2e` |

- A 两档实际 NVENC：1280×720 / 1920×1080，均 60/1 fps、240 帧、4 秒、SDR 8-bit BT.709 limited；AAC 单声道 eng + 双声道 fra 完整保留，整份 HLS 解码通过。分别 2,529,710 / 7,566,419 字节，源片 11,640,799 字节；将原片缩放到对应档位并对齐 PTS 后 SSIM 为 0.996007 / 0.996407。数值只适用于此合成微样本。
- A 私密主播放列表：短签名 URL 返回两档完整列表；去掉授权字段则被拒绝。真实封面 HTTP 404 暴露 OSS key 使用本地 FileUtil.getParent，被转换成 `/app/nested:...` 绝对路径；FC 异步受理后由请求校验拒绝，最终超时失败，需后续修复。新回归直接调用自建 CoverLauncher，检查实际 submit、持久化 payload/attempt 及输出路径保持 `videos/` 对象 key；另补终态失败不生成未执行第三轮的计数断言。
- B 实测 480×270、25/1 fps、50 帧、2 秒、10-bit HDR10 BT.2020/PQ limited；mastering display 与 max-cll/max-fall 逐字段相同，AAC 单声道 eng 保留；产物 198,051 字节。
- C 实测 240×320、60000/1001 fps、120 帧、2.001 秒、10-bit HLG BT.2020 limited，无音轨；产物 89,032 字节。
- D 从带 rotation=90 的 320×240 原片转为 240×320、rotation=0、25/1 fps、75 帧、3 秒、无音轨；产物 159,218 字节。B/C/D 整份 HLS 解码与源/输出字段比对均通过；私密主播放签名校验通过，封面仍待修复。
- 新 A 在本机实际 Chrome 无头模式播放：readyState=4，视频 1280×720、duration=4.017028 秒、currentTime>0.5 秒、video.error=null，seek 到 75% 通过。此证明桌面播放，不代替 iOS/Android/HDR 屏幕验收。
- LOCAL 实际 CLI 上传 `v_2982323700001281` / `2VW8XL`：READY，两档 provider=LOCAL_TRANSCODE，无链接复用，FC 任务数 0、raw md5=null；创建时间 2026-10-12 16:55:49.209 UTC，封面完成 16:55:56.851 UTC。MPS ListJob 在对应时间段返回 0 任务；FC 指标交叉核查继续执行。
- 2026-10-13 最新余额 2.68 元；新 GPU 首批 5 次转码均首次成功，SLS 执行 duration 合计约 23.95 秒，最终收费仍以延迟账单为准。
- 未连接 Android 设备，devicectl 在本机不可用；iOS/Android 真机与 HDR 高光亮度未验。故障一次 MPS 兜底、实际通道回滚及封面恢复继续验收，云端阶段未验证内容仍不标通过。

- 最新本地后端全量 610 项通过（0 失败/错误/跳过），新增相对 OSS key 与终态尝试计数断言。真实四个封面任务原因为 `/app/nested:...` 路径，被异步函数请求校验拒绝；原失败记录保留，针对仅本轮 4 个样本创建独立 `-repair` COVER 请求交由既有持久化恢复队列处理，结果待回读。


## 9. 2026-10-13 傍晚续验

- PR #136 已合并，服务端版本 `e7d08b230d9176c28959a9f1da40879a855dea78`；[CI](https://github.com/makewheels/video-2022/actions/runs/38382627376) 通过。自动部署因镜像代理获取 Caddy referrers 返回 502 失败；后续 [旧通道发布](https://github.com/makewheels/video-2022/actions/runs/38384861689) 和 [恢复自建通道](https://github.com/makewheels/video-2022/actions/runs/38386043936) 均成功。Docker APP_VERSION 与 TRANSCODE_PIPELINE=self-hosted 已回读。
- 四个样片的独立 COVER 修复任务 SUCCEEDED；A 修复任务 attemptCount=1，封面返回 200；原路径错误的失败记录保留。LOCAL 封面也返回 200。
- C07 实际观察补齐：LOCAL 上传对应 2026-10-12 16:55:49–16:55:58 UTC 的服务日志包含该 videoId，MD5/FC/MPS 提交日志均为 0；同期 SLS 自建函数日志与调用指标为 0，MPS ListJob 返回 0。不是仅由数据库零任务推断。
- C09 新任务回滚成功：`v_2983937900001076` / `2VYC63`，1280×720/25fps/4秒/单声道合成样片，READY、provider=ALIYUN_MPS_TRANSCODE、jobId=`8a1fb88eada644cab89fe0f34b708310`、FC 任务 0，主列表和封面 200。另一个 854×480 样片 `v_2983924200001761` 暴露旧通道无适用档位，不能把它记为通过；新增明确失败回归防永久 TRANSCODING。
- C08 实际两轮故障回调注入：`v_2983946900001731` / `2VYD73`，1280×720/60000:1001fps/10秒/无音轨，SHA-256 `5fbc33ab6549592f18801eb6f49abb6416420db2e21e06cb6c1ad2a58df99365`。只对该私密合成样片注入失败；真实两次 FC 提交及两次 FAILED 回调后 attemptCount=2，fallbackCount=1，重复最后失败返回 200 且不再提交。新封面无需数据修复即 SUCCEEDED、attemptCount=1。
- 上述 C08 MPS 返回 `InvalidParameter.NumberFormatInvalid`，因此任务明确 FAILED；本次不能记作兜底成功。定位为 `Video.Gop="6s"` 覆盖值，移除后需新样片端到端复验；原失败任务不重置兜底名额。
- 两个临时验收项目/身份与 GitHub 变量已删除。原生产 OIDC 身份信任范围未扩大；工作流临时分支条件正在移除。
- 2026-10-13 傍晚延迟 DAILY 账单：GPU 1.06249 元、CPU 0.011 元，共 1.07349 元；超过本轮初估 1 元 0.07349 元，仍低于原 20 元总预算。加上 10-11 的已知新函数日账单为 4.46622 元，不能当作含 OSS/MPS 的精确测试总额。余额 1.28 元；仅补小型 CPU/MPS 关键复验，不再新增 GPU 样片。ListInstances GPU 为 0。
- 仍未验证：iOS/Android/HDR 真机、真实 Dolby Vision 保留（现明确拒绝）、10–30秒实际运动素材主观画质、完整历史 TS/480p 与在途旧回调矩阵、真实进程重启与限流故障矩阵。不得宣称全部验收完成。

- 本次 `mvn test -pl video -Pspringboot` 独立系统临时构建：Surefire XML 汇总 532 项，0 失败/错误/跳过。与历史记录的全套 610 数量口径不同，不把两者混用；最终以本 PR 全套 CI 为准。


## 10. 发布收尾与精确帧率复核（2026-10-13）

- PR #137 已合并，[CI](https://github.com/makewheels/video-2022/actions/runs/38387925980) 全部必需项目通过；后端全套 611 项，包含 video 模块 532 项和 youtube 模块 79 项。历史 610 对应新增无档位边界测试之前的全套口径。
- 服务器版本 `428eaea2584f576d6191b363a87c9f1a4f5df885`。[发布](https://github.com/makewheels/video-2022/actions/runs/38388793186) 因 ACR TLS handshake timeout 两次失败，第三次执行成功；通过 Actions 发布，未手动替换服务器程序。
- 调用与回调密钥已轮换，临时 0600 文件用完清理。[函数配置发布](https://github.com/makewheels/video-2022/actions/runs/38387965085)、[服务端同步](https://github.com/makewheels/video-2022/actions/runs/38387975785) 成功；CPU/GPU 的 INVOKE_SECRET/CALLBACK_SECRET 与当前密钥库一致，服务端的两项 ALIYUN_CF_TRANSCODE_* 密钥一致。旧回调的 CALLBACK_SECRET 未进入部署白名单，正在补齐并拒绝空白密钥；正确旧任务派发保持。
- 桌面真实 Chrome：历史 `2VU1CS`、LOCAL `2VW8XL`、回滚 MPS `2VYC63` 都 readyState=4、currentTime>0.5、无 video.error，seek 到 75% 通过。HDR10 `2VW78J` 与 HLG `2VW7UX` 也真实解码播放，无错误；这是无头桌面解码，不代表 HDR 屏幕亮度或移动端兼容。
- 四张封面实际下载并视觉核查：SDR 1920×1080、HDR10 480×270、HLG 240×320、旋转后 240×320。HDR 封面经过 SDR tone-map；旋转图只旋转一次，未拉伸。原片/LOCAL/回滚样片的原始文件下载地址实际返回 206，读取 64 字节 MP4 头成功；不仅检查数据库/URL 存在。
- 初期两条 GPU 请求在同一秒结束，耗时 5353.28/5773.87ms，运行于两个独立实例，执行时间窗重叠；之后三条沿用这两个实例完成。证明实际并行，主动 429/饱和队列故障仍未完整验收。
- 新 C08 样片 `v_2983987700001264` / `2VYDXJ`，SHA-256 `dd1b806c1e15eeedb589e6a927e80ee271126e57f97f9283a01e9721c74ba371`。只向该私密合成样片注入两次失败回调；自建实际尝试数 2、兜底名额 1，重复最终回调不重提。MPS 唯一 jobId=`7cde8a57fe364d09a1d383d2d8ce07a0`，已实际 TranscodeSuccess，创建/完成 11:25:41/11:25:45 UTC。
- MPS 已生成完整产物：1280×720、60000/1001fps、600 帧、10.01 秒、8-bit BT.709 limited、无音轨，与源片一致；整份 HLS 解码通过。源/产物 13,266,594 / 4,835,924 字节；同尺寸、PTS 对齐后 SSIM All=0.969612。仅适用于此合成运动样片，不做跨样本 GPU/MPS 优劣结论。
- MPS 汇总 Properties.Fps=59，但视频流 Fps/AvgFPS=59.94006。旧验证误判降帧，任务仍 FALLBACK_PENDING；新验证优先流级精确值，真正降到 30fps 则立即失败。后端发布后应恢复登记同一已成功任务，不重置尝试、重新提交 MPS 或重复付费编码；最终 READY/浏览器结果待回读。
- 新本地 video 模块全量 541 项，0 失败/错误/跳过，包含 7 条回调认证及 2 条精确帧率回归。真实工作流登录步骤故障模拟：前两次失败第三次成功正常继续，持续失败三次后停止；全部 shell run 段 bash -n 通过。
- 最新账户余额 21.23 元；原任务 20 元总上限不变，不因余额增加扩大预算。没有新的 GPU 编码请求；本轮只新增小型 CPU/MPS 故障验证。
- 仍未验证：有语言标签音轨的真实 MPS 兜底、iOS/Android/HDR 真机、实际运动素材主观画质、完整历史 480p 与在途旧回调、主动限流/崩溃矩阵。4 条历史 480p 记录中未找到本验收用户的 READY 样片，不绕过权限取片；未完成项不得写为通过。


## 11. 持久化恢复与单音轨语言修复（2026-10-13）

- PR #138 的 [CI](https://github.com/makewheels/video-2022/actions/runs/38391429351) 必需项目全部通过，后端全套 620 项；[服务器发布](https://github.com/makewheels/video-2022/actions/runs/38392553506) 成功。当前代码 `21872105e41559151f6c16e2d98048f5f069e2ea`，仍为 self-hosted，生产沿用历史 dev profile/dev bucket，不切换存量数据路径。
- 原 59.94fps 兜底样片 `v_2983987700001264` 在服务更新重启后自动变为 READY，FC 任务 SUCCEEDED、attemptCount=2、fallbackCount=1、MPS jobId 仍 `7cde8a57fe364d09a1d383d2d8ce07a0`，actualFrameRate=59.94006。没有重置数据库状态、重发 MPS 或再次编码。这证明真实成功产物跨进程重启后的持久化登记恢复；不把它当成全部崩溃/竞态矩阵通过。
- 线上自建/旧转码/YouTube 封面三个回调的错误密钥均返回 403。现代调用/回调密钥回读一致；旧 CALLBACK_SECRET 已进入部署输入，但 Compose 未映射到容器，补齐 Compose 后再回读，不能只凭 Actions 成功声称所有密钥已生效。
- 单声道语言样片 `v_2984007200001314` / `2VYEMP`，SHA-256 `3cbb1ac0707e8f339392eb41645f6e1a97aac3ab725b2b3c5ce3e4bb43a77e82`：1280×720、25fps、250帧、10秒、BT.709 limited、AAC 单声道 48kHz eng。两次自建失败回调后，仅一次 MPS，jobId=`03decffadd394997abae09d38a0bd960`，READY、FC SUCCEEDED/count2、fallbackCount1，新 COVER 首次成功。
- MPS 产物尺寸/帧率/帧数/色彩/单声道/采样率匹配，源/产物 6,895,839 / 4,865,440 字节，完整 HLS 解码通过；AAC 帧计数 469→471，属新增编码 priming/tail，继续以实际 PTS/时长核对同步。TS 的 eng 标签确实丢失，原有验收不能据 READY 宣称语言保留。
- 真实已生成 MPS 产物加无 URI 的 HLS muxed AUDIO 声明，本地 ffprobe 主列表得到 language=eng/comment=English，无重新编码或改动 TS 字节。采用相同的标准声明在正式主列表保留已验证单音轨的语言/声道；MPS 实际 AAC 轨数、声道先核查，未知语言不猜，链接读取原视频，属性注入拒绝。线上主列表/浏览器确认待发布后执行。
- 本地 video 模块全量 546 项，失败/错误/跳过均为 0，新增真实输出声道不符拒绝、主列表语言声明、未知语言、属性注入及链接源归属回归；最终必需 CI 待本 PR 执行。
- 当前没有真实原片，继续使用自生成样片完成功能/编码基准；实际素材主观画质、iOS/Android/HDR 真机显示仍未验证，不以自生成画面代替。公开且 READY 的历史 480p 样片未找到，不绕过私密授权。

## 12. 最终发布与验收结论（2026-10-14）

### 发布和运行状态

- [PR #139](https://github.com/makewheels/video-2022/pull/139) 已合并，最终代码 `b07df837e14631250ffea9dbb0422d60cd22cda4`；[服务器发布](https://github.com/makewheels/video-2022/actions/runs/38480815481) 成功。只通过 Actions 更新程序和配置。
- [master 完整 CI](https://github.com/makewheels/video-2022/actions/runs/38480815451) 全部通过：后端 625 项、0 失败/错误/跳过（video 546 + youtube 79）；API E2E、浏览器 E2E 实际执行成功，前端、CLI、Worker、AI Agent、Android/iOS 单元测试和质量门禁均成功。移动端单元测试不代表真机播放。
- 只读 SSH 回读 Docker APP_VERSION 与 b07df837 一致、TRANSCODE_PIPELINE=self-hosted。Compose 补齐后，容器中的 CALLBACK_SECRET、ALIYUN_CF_TRANSCODE_CALLBACK_SECRET、ALIYUN_CF_TRANSCODE_INVOKE_SECRET 均非空且与当前生产密钥库一致；不公开值或指纹。此前的旧回调配置缺失已修复。
- CPU/GPU 继续使用第 8 节已发布镜像，无新增 GPU 编码。cn-beijing ListInstances 两函数均 0；并发上限 2、预留实例 0，实际两 GPU 实例重叠执行证据见第 10 节。
- 重新盘点确认旧 master/worker/ffprobe/clean 四函数均不存在，专用旧 RAM 用户不存在，新 CPU/GPU 与 prod/dev MD5 保留。旧私有 OSS 恢复包保留；NAS/VPC 和仍服务历史记录的回调代码保留，未把依赖不明的资源算成无用资源删除。
- 两个临时验收项目、身份和对应 GitHub 变量已删除；主生产 OIDC 信任仍仅 master。

### 最后一次真实播放与媒体检查

- `2VYDXJ` 和 `2VYEMP` 均 READY；真实 Chrome readyState=4、1280×720、currentTime>0.5、video.error=null，seek 到 75% 通过。无音轨样片没有新增真实音频流；Video.js 为该样片呈现的默认轨占位不作为音轨存在证据。
- `2VYEMP` 的正式签名主列表包含 muxed AUDIO 声明（无外部 URI），LANGUAGE=eng、CHANNELS=1；真实 Video.js audioTracks 得到 language/label=eng。读取正式主列表及其签名媒体，仅将 URI 改写到自动清理的本地测试副本后，ffprobe 得到 AAC/单声道/language=eng，视频 25/1 fps。主列表语言声明原样保留，TS 字节未重新编码。
- 音频起始 PTS=1.477333 秒、视频=1.520000 秒，差约 42.667ms，与 AAC priming 的两个 1024/48000 秒帧相符；帧数/时长记录见第 11 节。未进行真人听音验收。直接用当前 FFmpeg 读取含查询参数的接口 URI 会遇到 HLS 扩展名限制，桌面播放与上述媒体下载/探测均通过；不把探测工具的接口限制记为真实播放失败。
- 两个故障样片仍各为自建 attemptCount=2、fallbackCount=1，MPS job 保持原值。59.94fps 样片在前一实际发布重启时完成持久化恢复，本次继续播放成功；未重发或重复付费编码。

### 费用

- 2026-10-14 查询 2026-10-13 DAILY 延迟账单：新 CPU 0.033 元、新 GPU 1.06249 元，共 1.09549 元。加上 10-11 已知 3.39273 元，已知新函数日账单合计 4.48822 元；这是整日函数使用，不能当作含 OSS/MPS 或其它业务的精确测试总费用。
- 当次账户余额 20.80 元；余额变化包含其它业务/充值，不用它推导本任务支出。原 20 元预算保持不变。首批新增估算 1 元低于最终该日账单，保留这一偏差，不改写历史估算；没有继续扩大 GPU 样片矩阵。

### R01–R12、T01–T18、C01–C09 汇总

| 范围 | 最终状态 | 证据或限制 |
|---|---|---|
| R01/R02/R03/R04/R08/R12 | 通过已执行验证 | 自建探测/封面/两档、LOCAL 零云处理、有限两次自建/一次 MPS、真实 Actions 发布及回滚 |
| R05/R06 | 部分通过 | 合成样片的尺寸、帧率、静态 HDR/HLG、方向、音轨及 SSIM/体积已核查；真实素材主观画质、完整码率峰值和 HDR 屏幕未验证，动态 HDR 明确拒绝 |
| R07 | 部分通过 | 未重转历史；历史 720p TS 与原片实际读取/播放通过，完整历史 480p/在途旧回调未验证 |
| R09 | 部分通过 | 配额、上限、无预留、两实例实际并行通过；主动饱和/429 排队未实测 |
| R10 | 已记录，未扩大预算 | 已知函数账单见上；OSS/MPS 延迟费用不冒充精确总额 |
| R11 | 完成已确认无用资源清理 | 旧四 FC/旧专用 RAM 用户删除并保留恢复包；依赖不明 NAS/VPC 保留 |
| T01–T09、T11–T16、T18 | 自动化通过 | 最终完整 CI；实际档位、音轨、有限兜底、LOCAL 与私密签名见逐次记录 |
| T10 | 部分通过 | 自动化和成功 MPS 产物跨真实发布重启恢复通过；所有崩溃时间点/丢回调矩阵未验证 |
| T17 | 部分通过 | 真正切换 legacy 后新 720p MPS 成功并恢复 self-hosted；历史 720p 播放通过，480p/在途旧回调未验证 |
| C01–C04/C06 | 部分通过 | 合成微样片/10 秒故障样片覆盖尺寸、旋转、60/59.94fps、多音轨、语言和无音轨；真实原片及真人试听缺失 |
| C05 | 部分通过 | GPU HDR10/HLG 媒体字段、静态元数据、完整解码及桌面播放通过；HDR 真机/真实 Dolby Vision 未验，动态格式拒绝 |
| C07 | 通过 | 实际 LOCAL 上传、播放、日志/SLS/MPS 交叉确认零云处理 |
| C08 | 部分通过 | 两实例并发、实际两轮失败后唯一 MPS、重复回调不重提、跨发布重启恢复通过；主动 429/完整崩溃矩阵未验证 |
| C09 | 部分通过 | 实际 legacy 新任务、历史 720p/原片访问与桌面播放通过；低清 legacy 无档位明确失败，完整 480p/在途旧回调未验 |

用户已明确没有真实原片；本机没有可用移动端/HDR 测试设备，因此以上未验证项保留，不反复索要素材，也不以 mock、合成画面或 CI 单元测试替代。代码与可执行的验收收尾完成，完整真实素材/真机矩阵仍未完成。

## 13. 提交传输隐式重放修复（2026-10-15）

真实JDK21回环HTTP端点读完正文即关闭连接，新增发送次数断言在旧Hutool客户端失败：期望1次、实际2次，证明底层缓冲POST隐式重放。改为逐连接固定长度流式UTF-8 JSON，禁自动重定向；不改全局属性、任务/attempt身份、30秒超时、429排队或受理不确定恢复规则。

修复后8项传输测试通过；真实回环隔离Mongo的FcTaskFlowIntegrationTest11项、CoverTaskSubmissionTest1项也通过，共20项，0失败/错误/跳过，Maven validate附带Checkstyle通过。随后补301/303/307/308重定向及测试线程volatile，最终传输12项全部通过、0失败/错误/跳过，Checkstyle通过。构建源码/target/Mongo db/log均系统临时目录，finally停止自建Mongo并清理，未连接生产或触发付费任务。

本地证明EOF后不重放，不能代替真实HTTPS/FC主动429、30秒读超时、完整崩溃、真实素材与设备矩阵。PR/CI最终状态以远端查询为准。

## 14. CI Worker原型镜像按变化构建（2026-10-15）

用户指出的run38726640918/job116394017733实际为Worker原型验证，不是生产函数发布。该job7分8秒，原型镜像步骤11:21:38–11:27:42 UTC耗时6分4秒。每次裸docker build从源码编译FFmpeg且无跨runner层缓存，导致无关PR重复执行。

改为相关目录/CI/判断脚本变化及手工触发才Buildx构建、GitHub层缓存、本地load后验证NVENC枚举和Python导入；所有Worker/函数单元测试每次仍运行，job门禁不变。比较失败保守执行构建。6项本地unittest通过，含真实临时Git仓库PR分叉merge-base与移出目录重命名，diff失败/首次push/手工触发边界；Ruff/空白/YAML与远端CI结果另记。Docker daemon本机不可用，不冒称本机完整镜像构建通过，实际Buildx需本PR CI验证。
