# S03.b 隔离 OSS 权限配置与不可变上传前提

状态：可审阅配置及本地保护实现；RAM 角色未创建、未授予权限、Actions 未代入，发布仍阻塞。2026-10-14。

现有 OSS 数据身份只有读取权限；不能为发布升级为全 bucket 写身份。候选新增 `video-2022-serverless-release-oss` 角色，只信任现有 `video-2022-oss-data-dev` 用户，权限文档见 `serverless/deploy/oss-release-policy.json`。trust 模板中的 ACCOUNT_ID 必须来自实际 STS 同账户读回；模板不能原样提交 API。只读 GetRole 已返回 EntityNotExist.Role，不表示已创建。

角色只读 bucket versioning，GetObject/PutObject 仅限 `oss-data-bucket/video-2022-dev/serverless/releases/*`；无 FC、RAM 管理、ListObjects、DeleteObject、bucket 写入或生产前缀权限。当前客户端只用简单 PutObject/GetObject，不授予未调用的多段/ACL修改/标签/KMS能力。private ACL 由上传请求明确设置；候选 policy 本身不宣称能约束所有 ACL 值，云端授权/ACL需实测。

官方依据： [RAM 代入](https://www.alibabacloud.com/help/en/ram/user-guide/assume-a-ram-role) 要求调用者 AssumeRole 权限和目标 trust policy 同时允许；现有数据身份的系统 STS 权限不能替代目标角色/OSS授权。将来 Actions 使用900秒临时身份，回读 GetCallerIdentity 确认目标role，再调用发布客户端；不把临时凭据放入ZIP/receipt/函数环境。此 PR 未实现 STS 装配或 RAM provision workflow，不能称已解阻。

审阅发现新前提：[PutObject 官方协议](https://help.aliyun.com/zh/oss/developer-reference/putobject) 指出，启用或暂停 versioning 时 `x-oss-forbid-overwrite` 不生效。发布客户端必须在上传前读取 [GetBucketVersioning](https://help.aliyun.com/zh/oss/developer-reference/getbucketversioning)，只接受原始XML中没有Status子元素的从未开启状态。锁定 oss2 2.18.4 的公开解析器对带namespace的Enabled/Suspended也会返回None，因此不能信任该状态属性；适配器复用其认证请求路径并有界解析原始XML，接受官方namespace和无namespace形式，拒绝未知根/子元素/DTD/过大响应；Enabled/Suspended/未知状态或读取拒绝均在写入前失败。**不自动关闭 bucket versioning**，更不修改生产 bucket 配置。

bucket 管理员在查询与写入间改配置的外部竞态仍是控制面边界；候选角色无修改 versioning 权限，后续受控发布须确认没有并行 bucket 管理变更。保护不是对象锁/数字签名，不自动采购新存储或改保留规则。

后续实际门槛：先经受控 Actions 管理身份创建/授权候选role并回读 trust/policy摘要，配置 Git 固定role映射、实现STS装配；Actions验目标身份、versioning、隔离前缀上传/回读以及拒绝生产前缀写入，最后才重新publish。现有Actions身份无RAM管理授权，不擅自借本机高权限绕过。当前只验证配置范围/拒绝危险状态和失败脱敏；云端权限、晋级、回退仍未通过。

回退本 PR 恢复发布工具版本，未改变云资源。若后续授权角色，回退先停发布/撤销该role，再按实际对象清单处理，不删除未知生产对象。
