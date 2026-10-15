# S06.c1 资料、频道与头像旧契约

范围：现有UserController/UserService/UserRepository、InterceptorConfiguration、OSS适配器源码与真实隔离HTTP夹具。只固化既有行为，不实现Python资料/头像业务，不修改线上规则。请求全经过真实Spring管线和隔离Mongo，OSS仅mock，不能作为真实STS/OSS上传验收。

## 方法、身份和结果

| 方法/路径 | 参数与身份 | 现有结果 |
|---|---|---|
| POST /user/updateProfile | JSON nickname/bio；CheckToken保护 | code0/data null；超长code1，昵称先于简介验证 |
| GET /user/getMyProfile | CheckToken保护 | User全字段，返回token null，头像动态签名；记录不改 |
| GET /user/getChannel | 必需userId；不要求登录 | 8字段ChannelVO；不存在code1/用户不存在 |
| GET /user/createAvatarFile | 当前未登记CheckToken | 每次新File，返回fileId/key，尚不绑定头像 |
| GET /user/getAvatarUploadCredentials | 必需fileId；当前未登记CheckToken | 类型/存在→owner→OSS凭证；返回原凭证字段并追加fileId |
| GET /user/avatarUploadFinish | 必需fileId；当前未登记CheckToken | 类型/存在→owner→OSS对象存在→用户引用更新 |

HTTP业务结果均200；updateProfile无token为HTTP401/code401。不能因方法是GET而声称没有写入：创建/完成头像有明确业务写，通用请求日志另有写入。缺参数、畸形JSON等完整异常矩阵仍待补。

## 资料和频道

- nickname非null时Java String.length上限30，bio上限200，不trim；15个非BMP emoji昵称通过，16个拒绝。该限制是UTF-16 code unit，不是Python len或Swift可见字符数量。超长返回后不更新字段/时间。
- 两字段一起写入，缺失与显式null都为null，不是PATCH；`{}`清空两字段。成功更新updateTime，token保持。不能把CLI只传一个字段解释成保留另一个。
- ChannelVO字段：userId/nickname/avatarUrl/bannerUrl/bio/subscriberCount/videoCount/isSubscribed。null计数对频道返回0；isSubscribed固定false（登录用户也不是自动订阅查询）。getMyProfile返回User的计数语义，不作频道的补0。
- avatarFileId非空、对应File存在且key非空时生成24小时签名；否则回退存储的旧avatarUrl。读取路径没有按File.fileType、uploader或对象存在性再验证；这是源码边界，不擅自加强或改变历史头像。夹具验证了其他owner文件仍被签名、缺文件回退。
- getMyProfile清空返回token并替换返回avatarUrl，不写回用户记录；getUserById旧路径仅清空token，不能直接当成动态头像资料接口。

## 头像写入/凭证顺序

- createAvatarFile：新ID、fileType=AVATAR、uploaderId=当前用户、key=`avatar/<userId>/<fileId>`，保存File并返回fileId/key。重复调用创建不同文件，不写user.avatarFileId、不调用OSS。
- credentials与finish先检查File存在且类型AVATAR，否则31/文件不存在；再比owner，否则33/文件和用户不匹配。失败不调用OSS；凭证provider返回null为34/生成对象存储上传凭证失败。
- BaseOssService源码中STS请求时长3小时、固定现有role，凭证字段bucket/key/endpoint/accessKeyId/secretKey/sessionToken/expiration，Service追加fileId。本阶段只验证mock结果原样保留/追加，没有实际签发或验证role权限，文档不含真实凭据。
- finish仅验证doesObjectExist；不存在31，存在则更新用户avatarFileId和updateTime。保留旧avatarUrl/token，不更新FileStatus/metadata；重复完成仍执行检查/更新。没有在此路径新增文件大小、MIME、MD5、图像解码或跨文件关联限制。

## 已证实的鉴权缺口（不能冒充已修复）

InterceptorConfiguration只登记updateProfile/getMyProfile，未登记三个头像路由。隔离HTTP验证：无token创建仍成功，File.uploaderId=null，key为`avatar/null/<fileId>`；无token查询不存在文件仍31，对存在AVATAR文件因空用户ID调用equals而HTTP200/code1/未知错误，未调用OSS。此为既有缺陷记录，不是认可该行为安全；本PR不修权限、不改对外规则，后续安全修复须独立评审并与迁移行为分开。测试仅写入一次性隔离库，不在生产重放匿名创建。

## 各端调用核对（源码，不是设备验收）

- Web useSettingsPage：读取资料，提交时trim后空字符串转null；先创建文件→取凭证→ali-oss multipartUpload→完成→重读资料。image/*及5MiB限制属于Web校验，不能添加为旧后端已有规则。ChannelPage另取subscription状态，不能改变getChannel固定false语义。
- Android UserApi/UserRepository已封装profile/channel/update；本次未发现这份UserApi含头像三路，不能宣称移动端头像上传已覆盖。
- iOS SettingsScreen提交空字符串为nil，非空不trim；ChannelScreen调用getChannel。未做运行设备验证。
- CLI user update-profile只把已提供的选项放JSON，缺失字段在旧后端会清空；channel命令调用getChannel。此次不改其现有交互。

验收见[verification.md](../verification.md)。后续补跨端/缺参异常/实际凭证与头像上传、请求日志/上下文和Python用例；S06不能据此标完成。回退删除测试/文档，无运行代码、资源或用户数据变化。
