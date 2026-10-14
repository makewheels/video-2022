# S05.d 登录、旧 token 和观看身份契约台账

状态：源码/调用核对，动态 HTTP 对照和 Python 登录业务未实现；不是 S06 完成。基线 `5cf8716c`，日期 2026-10-14。资料/头像独立后续阶段补齐，不把 UserController 整体称已覆盖。

## 六个公开入口

成功 envelope 源码为 code=0/message=success；旧文档“成功”及 Redis 描述不能当实现事实。以下字段均为 Controller 声明，缺参/错误绑定的实际 HTTP 需下一夹具阶段补，不推断为 Python 默认 422。

| GET 路由 | 参数与返回 | 业务行为/副作用 |
|---|---|---|
| /user/requestVerificationCode | 必填 phone String；data=null | 查询 verification_codes 的 phone，存在就返回且不更新 createdAt；不存在生成四位 RandomUtil.randomNumbers，保存 phone/code/createdAt。源码记录日志，未发现此方法实际调用短信 SDK |
| /user/submitVerificationCode | 必填 phone/code String；data=User | 按 phone 查验证码；无记录抛码12“验证码已过期”；码不符且不满足非生产111则码11“验证码错误”。匹配后先删验证码，找/建用户、重置 token、保存用户、创建或取得钱包，再返回 |
| /user/getUserByToken | 必填 query token String；data=User 或错误 | token=null 服务返回 null，其余直接按 token 查询；无用户返回码13“登陆token校验未通过”。不改为JWT或重新签发旧凭证 |
| /user/getUserById | 必填 userId String；data=User/null | 查用户，存在则仅在返回对象上把 token 设 null；不写回数据库注销 token |
| /client/requestClientId | 无显式业务 query；data={clientId} | 每次创建 Mongo Client，保存创建时间、request.getRemoteAddr 和 User-Agent；@CrossOrigin 在该 Controller 方法，不能推断其他路由也相同 |
| /session/requestSessionId | 无显式业务 query；data={sessionId} | 每次创建 Mongo Session，保存创建时间、remoteAddr 和 User-Agent；不是“无写入的健康接口” |

验证码实体 phone 为 unique index，createdAt 为 expireAfter=600s 的 Mongo TTL index；submit 没有另行比较 createdAt 年龄。TTL 实际创建/删除时机、重复请求并发、重复提交/崩溃后的结果均需隔离 Mongo 动态验证，不能默认迁移成 Redis、自己加精确600秒拒绝或延长有效期。

非生产111仅在验证码记录已存在、environmentService.isProductionEnv=false 时绕过码匹配；isProductionEnv 比较配置 spring.profiles.active 与 Environment.PRODUCTION。不能省略“先存在记录”的前提，不能只按 Python debug 开关判断生产，不能让生产使用111。环境配置映射属于 S06 装配验收。

登录 token 由 IdUtil.getSnowflakeNextIdStr 生成，存 User.token；新登录覆盖旧 token。源码索引非 unique、无 token TTL，不能擅自改旧 token 格式/期限或一次登录生成另一认证体系。User 返回字段包含 id/phone/registerChannel/createTime/updateTime/token/nickname/avatarFileId/avatarUrl/bannerUrl/bio/subscriberCount/videoCount；构造时两项计数为0、时间取当前，完整 JSON/null/date 仍须夹具确认。

钱包在保存用户之后创建；createWallet 先查 userId，有则返回现有钱包，否则存新钱包，不能迁移成每次登录重置余额。验证码删除→用户保存→钱包保存并非本方法内事务；错误/崩溃恢复和并发唯一性必须按现有数据/所需恢复能力设计，不因 FC 短生命周期删掉步骤或假称已原子化。

## 请求上下文与调用端

PutTokenInterceptor 作用全部路径；UserService.getUserByRequest 优先非空 `token` header，否则取 query token 首值，无 Bearer 解析。六入口未列入 CheckTokenInterceptor 必须登录表；用户上下文仍可能查询 token。RequestLogInterceptor 写请求日志，异常管线写异常日志，所以验证码/身份/登录 GET 均不能用于无副作用生产影子重放。

| 客户端 | 已核对调用行为 |
|---|---|
| Web LoginPage | 先请求验证码再提交 phone/code，带 token 跳转 auth/callback 后保存；WatchPage clientId 存 localStorage，sessionId 存 sessionStorage，缺值才请求 |
| CLI auth | login 无 --code 时仅请求验证码；另一次带 --code 调用才提交并保存 token，没有本方法内交互收码流程；auth me 调 getUserByToken 且沿用 token |
| Android UserApi/UserRepository | Retrofit query phone/code/token；登录保存 token/phone；clientId 缺值才取，初始化观看身份每次取 sessionId；logout 清本地 token，不新增服务端注销 |
| iOS LoginScreen/APIClient | query phone/code 提交；APIClient 注入 token/clientId/sessionId headers。LoginScreen 实际调用 `/user/initClient` 和 `/user/initSession`，本次 Java 路由盘点没有找到这两个声明；AuthManager 在 Keychain 保存 client/session 并在注销清除。这是已发现的客户端/服务端路径差异，动态登录流程尚未验，不能虚构旧路由已通或在迁移中静默新增接口 |

## 来源和下一阶段

源码根 `server/video/src/main/java/com/github/makewheels/video2022/`：user/UserController、UserService、UserRepository、bean/User、bean/VerificationCode、client/*、session/*；system/environment/*、finance/wallet/WalletService、springboot/interceptor/*。调用源为上述各端文件。仅持久化语义摘要，不保存实际验证码、token、手机号或用户响应。

下一独立阶段：复用完整 Java HTTP/真实隔离 Mongo 夹具，覆盖请求复用、正确/错误/过期、非生产111与生产禁用、成功删码、token覆盖/旧token查询、钱包保留、公开用户返回token清空但数据库仍有效、client/session每请求保存与IP/UA；输出脱敏。需使用固定夹具，不索取真实用户凭据或调用生产短信/登录。再据结果实施 S06，受限字段与钱包一致性不能由形状工具替代。

本 PR 只台账与纠正已有接口文档事实；不改认证、生产配置或数据库。回退无运行/数据影响。
