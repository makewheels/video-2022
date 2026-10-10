# GPU 云函数自建转码

状态：阶段 0 调查与阶段 1 原型开发中；未接入生产分流，未部署。

- [原始计划与分阶段门槛](PLAN.md)
- [本轮实施范围与验收](requirements.md)
- [当前 Worker 设计](../../design/gpu-transcode-worker.md)
- [实际验证与阻塞](verification.md)

本轮先交付可本地验证的独立 Worker。通过真实 GPU、OSS 和成本比较后，再实施后端 provider、回调和分流，避免未经验证就影响现有转码。
