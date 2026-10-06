# GPU 云函数自建转码（计划，未实施）

> 2026-10-07 记录。本篇只做规划，不改现有转码逻辑、不部署。

## 目标

用阿里云函数计算 FC GPU 实例 + 自建 ffmpeg（NVENC 硬编码）替代 MPS，砍掉最贵的那部分转码成本。

## 现有转码方式（4 种）

分流入口：`TranscodeLauncher.getTranscodeProvider`，provider 常量在 `transcode/contants/TranscodeProvider.java`。

| provider | 实现类 | 触发条件 | 状态 |
|---|---|---|---|
| ALIYUN_MPS | `AliyunMpsTranscodeImpl` | 非 h264 / 降分辨率 / 码率>13Mbps；封面截帧 | 在用，贵 |
| ALIYUN_CLOUD_FUNCTION | `AliyunCfTranscodeImpl` | 生产环境 + h264 + 同分辨率 + 码率≤13M | 在用，便宜。函数代码不在本仓库，URL 写死在 `CloudFunctionTranscodeService` |
| ALIYUN_CLOUD_FUNCTION_GPU | `AliyunCfGPUTranscodeImpl` | `getTranscodeProvider` 不会返回它 | 仅常量 + 空壳类（预留坑位，未实现 `TranscodeService`） |
| LOCAL | `LocalTranscodeService` | `transcodeMode=LOCAL`，video-cli 本机 ffmpeg 转 HLS 回传 | 2026-06 上线 |

## 方案

1. 新建 FC GPU 函数：自定义容器镜像（ffmpeg + NVENC），入参与回调沿用现有 CPU 云函数协议（bucket / inputKey / outputDir / videoId / transcodeId / jobId / resolution / callbackUrl…）
2. 补全 `AliyunCfGPUTranscodeImpl` 实现 `TranscodeService`，回调复用现有链路（同 `AliyunCfTranscodeImpl.callback` 的模式）
3. 调整 `TranscodeLauncher` 分流：MPS 的转码场景改走 GPU 函数
4. 新函数 URL 走环境配置，不写死在代码里

## 阶段

- **阶段 1**：GPU 函数接管 MPS 的三个转码场景（非 h264 / 降分辨率 / 高码率）——成本痛点所在
- **阶段 2（可选）**：CPU 云函数场景也统一到 GPU 函数，收拢成一条通道；按成本核算结果决定要不要做
- 封面截帧是否也迁出 MPS：实施时单独评估

## 可行性要点（实施时以阿里云文档为准核实）

- FC GPU 实例（Tesla T4）支持自定义容器镜像，ffmpeg NVENC h264/hevc 硬编码是官方典型场景 → 能做
- 计费按 vCPU / 内存 / GPU 时长 + 调用次数分别算；先用「¥/小时 ÷ 每小时能转的视频分钟数」换算 ¥/分钟，对比 MPS 单价后再定，不拍脑袋
- 主要坑：GPU 实例冷启动慢（拉镜像 + 分卡），镜像越大越慢；可用预配置并发 / 预留实例缓解，但预留会持续计费
- NVENC 同码率画质略低于 x264，需实测定码率参数

## 风险

- 冷启动延迟：转码是异步任务，影响的是完成时长而非可用性，可接受
- GPU 按量计费的最小计费粒度要查清，短任务可能吃亏
- 函数最大执行时长需确认能覆盖最长的源视频

## 发版

统一走 GitHub Actions（`ci.yml` / `deploy.yml` / `release.yml`），禁止直连服务器，与 AGENT.md 现有约定一致；旧发版方式废弃不再用。
