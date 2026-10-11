# Video GPU Transcode Worker

独立原型，未接入生产。需求及设计见 [需求入口](../docs/requirements/2026-10-gpu-cloudfunction-transcode/README.md)。

## 本地媒体验证

需要 Python 3.12、uv、FFmpeg/ffprobe。所有生成的媒体用系统临时目录，验证后自动清理。

```powershell
uv run --project transcode-worker video-transcode-worker check-media <input.mp4> --encoder libx264 --resolution 720p
```

GPU 主机上使用 `--encoder h264_nvenc`。软件编码成功只证明公共媒体流程，不代表 NVENC 或 FC 可用。

按工作区规范，运行 uv 时将 `UV_PROJECT_ENVIRONMENT` 和 `UV_CACHE_DIR` 指向任务专属系统临时目录，并用 try/finally 清理；不要在项目中留下临时环境。`uv.lock` 是持久依赖清单。

## 容器原型

```text
docker build -t video-transcode-worker:prototype transcode-worker
```

FFmpeg 7.1.1 + nv-codec-headers 12.1.14.0（最低驱动要求以对应头文件官方说明为准）。镜像只是待云端验证的候选，不代表目标卡型/驱动兼容。构建含 GPL 的 FFmpeg/libx264，分发镜像时需按其许可提供相应源码及许可信息。

环境变量：

| 变量 | 用途 |
|---|---|
| OSS_BUCKET / OSS_ENDPOINT | 固定存储目标，不从任务接收 |
| OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET | 取密注入的 OSS 凭证 |
| OSS_SESSION_TOKEN | 可选临时凭证 token；原型调用前注入有效凭证，不用于长期无人值守运行 |
| INVOCATION_SECRET | 调用 Worker 的鉴权密钥 |
| CALLBACK_URL / CALLBACK_SECRET | 测试回调接收端及 X-Callback-Secret；不得指向生产 CPU 回调 |
| PORT | 默认 9000 |
| TRANSCODE_TIMEOUT | FFmpeg 超时秒数，默认 3600；需和 FC 超时/上传预算协调 |

`POST /invoke`（或 FC 自定义容器的 `/`）执行任务并在完成后响应；`GET /health` 健康检查。请求携带 `X-Invocation-Secret`，示例正文：

```json
{
  "jobId": "job1",
  "transcodeId": "tc1",
  "inputKey": "videos/user/202610/video/raw/file/file.mp4",
  "outputDir": "videos/user/202610/video/transcode/tc1",
  "resolution": "720p"
}
```

回调发送 `{"jobId":"job1"}`。发布前先上传 ts，最后上传 m3u8；重试已有完整产物时只重发回调。失败返回 500；原型不实现生产失败状态或自动 MPS 回退。

## 云端验证门槛

先验证 FC 异步调用是否保留鉴权头，以及自定义容器 `/` 请求语义；限制单实例单并发，禁止并发重试同一任务。进程内锁不提供跨实例幂等，临时凭证也未实现自动续期；这些必须在生产接入前解决。

实测目标 GPU 的 NVENC、OSS、长任务、冷启动、费用及画质；通过后再新增后端 provider 和独立 GPU 回调。当前应用分流、客户端和 CPU 回调均不改。

## 测试

`uv run --project transcode-worker pytest transcode-worker/tests -p no:cacheprovider`。

用 `PYTHONDONTWRITEBYTECODE=1` 防止在源目录生成字节码。媒体集成用例需要 FFmpeg，缺失时标记 skipped；不能把 skipped 当通过。
