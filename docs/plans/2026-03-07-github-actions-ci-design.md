# GitHub Actions CI/CD 设计

> 日期：2026-03-07

## 目标

每次 PR 和 push to master 自动运行测试，确保代码质量。

## 两个 Workflow

### 1. `ci.yml` — 单个 Workflow，两个 Job

**Job 1: 集成测试**
- **触发**：push to master, pull_request
- **Services**：MongoDB 7 + Redis 7（GitHub Actions service containers）
- **命令**：`mvn test -pl video -Pspringboot -Dtest='!...e2e.**'`
- **Secrets**：不需要（外部服务全部 mock）
- **测试数**：~450

**Job 2: E2E 测试**
- **触发**：同上（与集成测试并行）
- **Services**：MongoDB 7 + Redis 7
- **命令**：`mvn test -pl video -Pspringboot -Dtest='...e2e.**'`
- **Secrets**：7 个阿里云凭证
  - `ALIYUN_OSS_VIDEO_ACCESS_KEY_ID`
  - `ALIYUN_OSS_VIDEO_SECRET_KEY`
  - `ALIYUN_OSS_DATA_ACCESS_KEY_ID`
  - `ALIYUN_OSS_DATA_SECRET_KEY`
  - `ALIYUN_MPS_ACCESS_KEY_ID`
  - `ALIYUN_MPS_SECRET_KEY`
  - `ALIYUN_APIGATEWAY_IP_APPCODE`

## 技术要点

- 集成测试使用 `application-test.properties`，所有外部服务通过 `@MockitoBean` mock
- E2E 测试使用 `application-e2e.properties`，真实调用阿里云 OSS
- MongoDB 和 Redis 通过 service containers 提供，localhost 直连
- JDK 21 + Maven
