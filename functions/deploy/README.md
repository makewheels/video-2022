# 自建函数部署与回滚

部署入口为 `.github/workflows/deploy-functions.yml`，仅通过 GitHub Actions 构建、发布和回滚。已删除无引用的本机初始化清单，配置以 `fc_upsert.sh` 为唯一来源。

| 配置 | CPU | GPU |
|---|---|---|
| 名称 | video-2022-{env}-transcode-cpu | video-2022-{env}-transcode-gpu |
| 运行时 | custom-container | custom-container，FFmpeg 7.1.5 固定构建 |
| 规格 | 2 vCPU / 4 GB | fc.gpu.tesla.1，16 GB GPU / 8 vCPU / 32 GB 内存 |
| 临时盘 / 超时 | 10 GB / 1800 秒 | 10 GB / 1800 秒 |
| 单实例并发 | 1 | 1 |
| 函数并发上限 | TRANSCODE_CPU_MAX_CONCURRENCY，初值 2 | TRANSCODE_GPU_MAX_CONCURRENCY，初值 2 |
| 预留实例 | 无 | 无 |
| 异步重试 | 0，应用负责有限重试 | 0，应用负责有限重试 |

函数级 reservedConcurrency 是并发配额，与收费预留实例不同。部署通过 PutConcurrencyConfig/PutAsyncInvokeConfig 显式配置，再 Get API 交叉核对，不靠创建命令退出码判断生效。SLS 使用 video-2022-logs / transcode-fn。

Infisical `video-2022-secrets` 对应环境递归读取 `/` 和 `/oss`：FC_DEPLOY_AK_ID/SECRET、ALIYUN_OSS_VIDEO_ACCESS_KEY_ID/SECRET_KEY、CALLBACK_SECRET、ALIYUN_CF_TRANSCODE_INVOKE_SECRET、TRANSCODE_CALLBACK_HOST 和两项并发上限。密钥只在 Runner/FC 运行环境，镜像与 Git 不保存。

后端 callback-secret 与函数 CALLBACK_SECRET 必须一致；CPU/GPU INVOKE_SECRET 与后端 invoke-secret 一致。回调仅允许配置主机的 HTTPS `/transcode/cloudFunctionCallback`；输入/输出限定当前视频目录。后台总开关与媒体、重试、恢复配置贯通 Infisical → deploy.yml 白名单 → compose → properties。

首次构建使用带日期的镜像标签；函数 GetFunction 记录 resolvedImageUri digest。回滚运行同一 workflow，设置 skip_build=true 与 image_tag=上一已验证标签；禁止复用 latest。CPU/GPU 可分别选择。服务器部署和回滚同样使用现有 Actions，禁止手工修改线上服务。

首轮累计预算最多 20 元。2026-10-12 已查询上一轮新 CPU/GPU 账单约 3.39 元，账户余额 2.72 元（仍有出账延迟）。本轮计划新增收费验证不超过 1 元，预留剩余余额给在途及其它已有业务；各批次实际值以 verification.md 为准。不创建企业镜像仓库、常驻或预留 GPU。
