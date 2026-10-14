# S06.c2 资料与频道纯读取用例（未装配）

Profiles依赖既有UserReads和实际需要的两个能力：AvatarFiles按旧fileId取得key，AvatarUrls按key及秒数生成URL。公共参数/结果都是普通字符串、整数和不可变快照，不接Mongo/OSS/FC对象，不读环境、不创建客户端、不写记录。没有实现空的写入端口或新增业务路由。

- mine接收调用者已经认证的userId；再读用户，返回token=null及动态avatarUrl的新快照，保留原计数/null/日期/其他字段。传入字符串不是鉴权证明，完整token上下文尚未装配。
- channel返回旧8字段ChannelSnapshot，null计数补0，其他计数包括负值保持；isSubscribed固定false。不存在编码HTTP200/code1/用户不存在/data null。
- 非空avatarFileId查文件key；非空key签名86400秒。空ID、缺文件或空key回退旧avatarUrl；签名器直接返回null时保留null，不额外回退，provider异常不伪装为缺失。沿用旧读取行为，不在公共用例新增owner/type/对象存在性校验。
- 编码继续旧application/json及Result格式，本人资料复用原User字段编码；没有启用路由、上传凭证、更新资料/头像或修复旧匿名头像鉴权缺口。

## 证据与剩余门槛

扩展UserReadGoldenHttpContractTest，真实Spring HTTP+隔离Mongo共9项通过/Checkstyle通过，导出5份固定合成profile/channel响应，见serverless/contracts/profile-read。原4份user-read夹具不变。完整Python193passed/0skipped/1既有warning、Ruff/500行/空白通过。5份离线对照、缺失/空key/空签名/错误传播、计数规则，以及真实Mongo用户适配器+替代头像端口的4份组合对照均通过。

签名是固定example.invalid替代值，未生成真实凭证或请求OSS；头像文件Mongo适配器、OSS签名适配器、请求上下文/日志/错误矩阵和业务路由仍待，不能称资料API已在FC迁移。继续遵守生产不切流/不迁库；旧匿名头像缺口见profile-avatar-contract.md，单独评审而非顺带改行为。

回退仅删除这些未装配能力与测试/夹具，无在线入口、持久任务或数据影响。下一步先收尾本PR必要CI，再独立实现头像元数据适配或完整HTTP上下文契约。
