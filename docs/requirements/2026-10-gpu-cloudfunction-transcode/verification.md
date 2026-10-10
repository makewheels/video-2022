# 实际验证

## 2026-10-10 开工调查

- 基于 master `6393da26` 建立 `feat-gpu-transcode-integration`。
- 找到 [cf-ffmpeg-transcode](https://github.com/makewheels/cf-ffmpeg-transcode) 源码。仓库最后更新时间为 2022 年，Master 的公开源码回调仅发送 jobId，未携带当前后端要求的 `X-Callback-Secret`，不能当作当前部署版本的完整契约。
- Windows 有 Java/Maven/FFmpeg/uv/Docker 客户端；Docker Linux 引擎未运行。
- Infisical 网络自适应工具的直连、香港/北京/本地代理均未连通；随后使用 fallon 代理获得 HTTP 200。本机登录态过期，自动浏览器登录失败；本机 infra 中也没有工具说明所指的管理员恢复文件。当前未取得阿里云凭证，不能确认 FC 权限、地域供应或实际 GPU 成本。
- 不读取或输出密钥真值；未创建云资源、未修改生产配置、未部署。

## 本次实际运行

- `uv run --locked --project transcode-worker ruff check transcode-worker --no-cache`：通过。
- `uv run --locked --project transcode-worker pytest transcode-worker/tests -p no:cacheprovider -v`：26 通过，无 skipped。包含 3 条真实 FFmpeg 转码与整份 HLS 解码：横屏有音轨、竖屏无音轨、小尺寸不放大，均核对 H.264/AAC 与尺寸。
- Python 环境、依赖缓存及生成媒体使用任务专属系统临时目录并自动清理。
- `git diff --check`、文件长度门禁通过。
- 初次 CI：原有后端、前端、CLI、AI Agent、Android、iOS、质量门禁及构建通过，API/浏览器 E2E 按 PR 规则 skipped；Worker 的 26 条 Linux 测试通过。GPU 候选镜像实际构建成功，末尾检查因 runner 未安装 rg 而失败，已改用 grep 并补充容器内 Python 模块导入检查，待复跑。
- 本机 RTX A2000 Laptop GPU 实际 NVENC：系统 FFmpeg 要求 API 13.1、驱动仅支持 13.0，首次失败。改用系统临时目录中的 Gyan FFmpeg 7.1.1 后，横屏有音轨、竖屏无音轨、小尺寸不放大 3 个样本硬编码并完整解码通过，临时二进制和媒体均清理。未升级驱动或替换系统 FFmpeg。
- NVENC 首轮 6 秒样本未按 5 秒边界切成多片，补充 forced-idr 参数；软件测试改为 6 秒并要求至少两片，26 条复跑通过。硬编码复测因 GitHub 下载超时未完成，改用香港代理下载固定版本后，3 个样本均生成 2 段 ts，并整份解码通过；核验 H.264、尺寸和可选 AAC 音轨。所有下载与媒体使用系统临时目录并清理。

## 未确认项

- 26 条 pytest 中媒体验证使用 libx264；另有本机 NVENC 实测，但 FC、OSS 及费用/画质比较未跑，本机结果不能代替目标 FC 规格验证。
- Docker 镜像本地未构建，原因是引擎未运行；已由 PR CI 构建候选镜像。编入 NVENC 的检查仍不能证明目标 FC 卡型/驱动可用。
- 现有后端代码、CPU/MPS/LOCAL 分流和客户端未修改；后端整套测试本次未在本地执行。
- 当前原型的单进程锁不能提供跨实例幂等，STS 未自动续期，FC 异步鉴权头透传待确认；生产接入必须先解决这些门槛。
