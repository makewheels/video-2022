# 移除 Redis 依赖 — 设计文档

## 问题

项目当前使用 Redis 做 5 件事：验证码存储、Token 会话缓存、IP 地理位置缓存、分布式 ID 生成。项目已使用 MongoDB 作为主数据库，保留 Redis 增加了运维复杂度和部署依赖。

## 方案

**完全移除 Redis**，所有功能迁移到 MongoDB：

| 原 Redis 用途 | 迁移方案 | TTL |
|---------------|---------|-----|
| 验证码 | MongoDB `verification_codes` 集合 + TTL 索引 | 10 分钟 |
| Token 会话 | 直接查 MongoDB `user` 集合（去掉 Redis 缓存层） | 无 |
| IP 地理位置 | MongoDB `ip_cache` 集合 + TTL 索引 | 6 小时 |
| 短 ID 生成 | MongoDB `id_counters` 集合 + `findAndModify` + `$inc` | 按 date/timeUnit 自动归档 |
| 长 ID 生成 | 同上 | 同上 |

## 架构变更

### 1. 验证码（UserRedisService → MongoDB）

新建 `VerificationCode` Document：
```java
@Document("verification_codes")
public class VerificationCode {
    String id;
    String phone;
    String code;
    Date createdAt;  // TTL 索引，10分钟过期
}
```

创建 TTL 索引：`db.verification_codes.createIndex({createdAt: 1}, {expireAfterSeconds: 600})`

### 2. Token 会话（去缓存层）

当前流程：`getUserByToken()` → Redis 缓存 → MongoDB fallback
新流程：`getUserByToken()` → 直接查 MongoDB（token 字段已有索引）

移除 `UserRedisService` 类。

### 3. IP 缓存（RedisService → MongoDB）

新建 `IpCache` Document：
```java
@Document("ip_cache")
public class IpCache {
    String id;
    String ip;
    JSONObject locationInfo;
    Date createdAt;  // TTL 索引，6小时过期
}
```

### 4. ID 生成（Redis INCR → MongoDB findAndModify）

新建 `IdCounter` Document：
```java
@Document("id_counters")
public class IdCounter {
    @Id
    String key;       // e.g. "shortId:2026-03-13" or "longId:20260313-1600"
    long counter;
}
```

使用 `MongoTemplate.findAndModify()` + `Update.inc("counter", 1)` + `upsert=true` 实现原子递增。

### 5. 删除的文件

- `RedisConfiguration.java`
- `RedisService.java`
- `RedisKey.java`
- `RedisTime.java`
- `UserRedisService.java`
- `RedisServiceTest.java`
- pom.xml 中 `spring-boot-starter-data-redis` 和 `lettuce-core`
- application*.properties 中 `spring.redis.*` 配置
- CI ci.yml 中所有 Redis service 容器

### 6. 修改的文件

- `UserService.java` — 验证码逻辑改用 MongoDB
- `IdService.java` — INCR 改用 MongoDB findAndModify
- `IpService.java` — 缓存改用 MongoDB
- `BaseIntegrationTest.java` — 移除 Redis 清理逻辑
- `UserServiceTest.java` — 适配新实现

## 风险

- **ID 生成性能**：MongoDB findAndModify 比 Redis INCR 慢约 2-5x，但对于当前流量完全够用
- **Token 查询频率**：每个请求都查 MongoDB，但 token 字段有索引，单次查询 <1ms
- **TTL 精度**：MongoDB TTL 后台线程每 60 秒检查一次，过期可能延迟最多 60 秒（可接受）
