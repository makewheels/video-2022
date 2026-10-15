# 接续记录

## 2026-10-15 文档与分支导航整理

新增[总体进度与剩余清单](overview/status.md)，按S00–S24区分交付/部分/阻塞/未开始，与已上线转码工程分开。
主题文档移动到overview/deployment/http/user/video/playback/tasks，根目录只留五个入口，修复相对链接，保留固定SHA历史证据URL。
已prune本地过期remote-tracking缓存；远端API确认删除前7个分支，删除#187/#188已覆盖遗留后5个，未动4个待合并工作分支。
#194已合并63fb35cb；#195/#196/#197需跟随新master收尾。#198按变化构建/缓存Worker原型镜像，必要CI须查最终。
本次纯文档导航调整，检查路径/链接/内容保留，不重复业务测试。


## 2026-10-15 晚间接续：S08.e 统一播放装配

master已拉取公司提交；#192精确head29c921a4必要CI38709053755成功后合并6cfcf375。
#189/#190随依赖合并自动收尾，#187/#188逐文件无独有实现，已关闭并注明由#192覆盖。
新增[显式统一播放装配](playback/backend-watch-routing.md)，保留默认路由集合、生产入口和数据边界。
本地Python3.12.13完整465passed/0skipped/1既有warning、Ruff通过，真实随机隔离Mongo。
待本PR必要CI；#192合并master CI38724271097/部署38724271123须查询最终。
交接路径handoff-2026-10-15-1700尚未在远端发现；继续以代码/PR/现行计划为据。
旧夜间worker/report调度已按用户要求禁用，不恢复；当前用户重新授权会话内继续后端及转码。
S03权限/真实FC网络仍阻塞，不开放Mongo公网、不生产切流迁库。


## 2026-10-15 连续推进：S07.b

#184公开视频列表head6d179d3c及#185发布错误定位head1f8b0a97均已提交，CI须查最终。
分支feat-serverless-search-category基于#184，新增[搜索/分类](video/search-category.md)，
受影响模块focused30passed、Ruff通过、1warning；JDK21算术边界探针已实际执行。
没有整套本机重跑或真实FC验证，不把旧Java源码/算术探针当完整HTTP验收。
本轮已连续完成观看身份、共享ID/token、用户HTTP/TCP、头像/OSS/STS、公开列表、
搜索分类及发布诊断多个阶段；剩余核心为播放、上传/可靠任务、其它业务和云端门槛。

## 2026-10-15 连续推进：S07.a

#183精确head1bcb5e97必要CI38671383841通过后合并2026014e，PR E2E按条件skipped。
分支feat-serverless-public-video-list实现[公开视频列表及统一后端组合](video/public-video-implementation.md)。
完整本地361passed/2deselected（Windows符号链接权限）/1warning，Ruff通过，包含
统一后端真实TCP。Spring7.0.5数值与Mongo5.0.3分页已直接Java探针/字节码核对；
本机离线Java夹具重跑因Maven插件缓存未解析而未执行，不能说通过。
下一步仍需用户外壳CORS/编码/FC与云网络验收，S07继续搜索/频道等，不宣布完成切流。

## 2026-10-15 连续推进：S06.k

#181新head d1675608必要CI38670457268通过后合并b52e0257；#182精确head879bf22b
必要CI38670616632通过后合并4e3f4beb，PR E2E均按条件skipped，不代替master E2E。
分支feat-serverless-avatar-lifecycle补[头像生命周期及OSS/STS适配](user/avatar-lifecycle.md)，
十二条UserController/client/session路径现已具备独立应用实现；旧生产入口仍未切换。
本地完整348passed/2deselected（Windows符号链接权限）/1warning、Ruff通过。
已运行锁定Java OSS3.16.3/core4.6.2合成黄金探针；没有真实STS/对象访问或生产写入。
后续可推进S07公开视频列表，同时补用户外壳CORS/编码/FC和真实网络验收；不能标S06完成。

## 2026-10-15 连续推进：S06.j

#180必要CI38669584312通过后精确head8e856a84合并7ef5bdad。#181初head bf72100b
必要CI38669795999通过；为解决与#180的接续文档冲突，合入依赖与master后head
d1675608，必须等新head CI，不沿用旧head通过结论。
分支feat-serverless-user-http复用上述依赖完成[九条用户路径及真实TCP](user/user-http-application.md)。
完整本地328passed/2deselected（符号链接权限）/1warning；再增4份Java黄金对照后
focused19passed，Ruff通过。工厂真实装配时钟/随机源/共享ID及异常日志，仍不替换
生产fc_handler/web入口。继续头像创建/凭证/完成链路；完整FC、CORS与网络未验。

## 2026-10-15 连续推进：S06.i

client/session已提交PR#180，精确head8e856a84；本地15passed，必要CI仍需查最终。
独立分支feat-serverless-identifiers基于master1183ae20，实现[Long ID与共享token生成](user/identifiers.md)。
已运行项目锁定Hutool5.8.25/JDK21黄金探针，本地Mongo新增6项及架构8项14passed，
Ruff通过。token共享分配用原id_counters的内部键及原字段；尚未装配生产node/时区，
真实Mongo版本/拓扑/写关注和Java共存时的单写者交接仍待验，不宣称完整登录上线。

## 2026-10-15 连续推进：S06.h

用户要求连续分阶段推进，不逐阶段等待确认。#179必要CI38667775210全部通过，
PR E2E按条件skipped，精确head079031fe已合并1183ae20。基于该master实现
[client/session持久化分配](user/client-session.md)，新增7项及架构8项共15passed、Ruff通过。
无生产入口/配置变化。下一阶段是旧Long ID和多实例Snowflake token生成；已用
项目锁定Hutool5.8.25运行临时Java探针取得固定格式样本，默认节点实际由机器派生，
不误按其他版本文档宣称默认固定1:1。后续需避免FC各实例进程内序列冲突。

## 2026-10-15 日间：S06.g2 登录提交

S06.g1 PR#178精确head6a9c99da必要CI全部通过（PR两个E2E按条件skipped），已合并
4481ece9。继续分支feat-serverless-login-submit，实现[验证码提交及用户/钱包写入](user/login-submit.md)。
真实临时Mongo回归308passed/2deselected（Windows符号链接权限）/1warning、Ruff通过；
新增9项覆盖错误、生产/非生产、换token、钱包保留和部分失败状态。无生产HTTP装配。
后续先做ID/token生成器与HTTP组合验证；事务/并发恢复尚未完成，不能称完整登录。
本阶段依赖S06.g1，回退与未验边界见独立阶段文档。

## 2026-10-15 日间重新启动：S06.g1

用户明确要求分阶段继续。基于master a2f8b150，分支feat-serverless-verification-code，
完成[验证码申请能力](user/verification-code-request.md)：现有记录不刷新，新记录保持原字段；
真实隔离Mongo验证，无生产路由/短信/索引/云配置改动。新增及架构12passed、Ruff通过；
全套270passed/3skipped/2failed（Windows符号链接权限）/1warning，详见阶段文档。
下一步为验证码提交、用户/token/钱包写入和恢复能力，完成后统一装配用户入口。
S03发布权限/S04云网络仍阻塞，不沿用已结束的夜间时间授权。

额度中断、上下文压缩或更换执行环境后，先读本页，再核对 Git、PR、CI。记录必须随阶段和重要状态更新，不能只在额度用完时补写；不保存密钥、用户数据或临时下载内容。

## 当前快照（2026-10-15 07:31，S06.f2已合并，夜间验收收尾）

- S02 [#148](https://github.com/makewheels/video-2022/pull/148) 已合并，merge `42a0ebeb0a2865adf3c6e1d8791bb276d970d8e9`；最终 [CI 38549335594](https://github.com/makewheels/video-2022/actions/runs/38549335594) success，所有必要 job 通过，API/浏览器 E2E 按 PR 条件 skipped。Linux 原型 79 passed，双包两次可重现、入口与 native 冒烟通过。
- S03 工具 [#149](https://github.com/makewheels/video-2022/pull/149) 已合并，merge `2eb0065e94795073f87ca8b9bef30d9784990276`；最新 head `7fc243c4` 的 [CI 38555210822](https://github.com/makewheels/video-2022/actions/runs/38555210822) success，Linux 原型 107 passed/1 warning，全部必要 job 通过、PR 两个 E2E skipped。云端发布与回退仍未验，不能称 S03 全部完成。
- 验收文档 [PR #150](https://github.com/makewheels/video-2022/pull/150) 已合并为 `53f275b8`。S05.a [PR #151](https://github.com/makewheels/video-2022/pull/151) 最新 head `20a71229` 的 [CI 38581828673](https://github.com/makewheels/video-2022/actions/runs/38581828673) success 后合并为 `33b1a68f`；PR E2E 按既有条件 skipped。
- S05.b [PR #152](https://github.com/makewheels/video-2022/pull/152) 最新 head b479d27b 的 CI 38584373965 全部必要 job success 后合并为 `5cf8716c`，PR E2E skipped；本地 25 项离线对照回归/Ruff/500 行通过。
- S05.c [PR #153](https://github.com/makewheels/video-2022/pull/153) 最新 head `6d9cdbcb` 的 [CI 38586272991](https://github.com/makewheels/video-2022/actions/runs/38586272991) 全部必要 job success 后已合并为 `7ff9a3f1`；PR E2E skipped。首 CI 日期失败已修正，UTC 本机551/北京时间focused5通过，最终 Linux 后端也通过；具体计数/证据保留 verification。
- S05.d [PR #154](https://github.com/makewheels/video-2022/pull/154) a1fcefd1 的 CI38587849569 必要项success后合并为fc54aee1；PR E2E skipped。
- S05.e [PR #155](https://github.com/makewheels/video-2022/pull/155) 最新 head f8fe8541 的 CI38589941727 全部必要job success后合并为12092139；PR E2E skipped。登录HTTP6项/Checkstyle已过，生产判定/TTL清理/并发未验。
- S03.b [PR #156](https://github.com/makewheels/video-2022/pull/156) 01198f60 的 CI38591679417 全部必要job success后已合并8a6b5304（PR E2E skipped）。候选role/trust仅配置，未创建/代入，真实发布回退仍阻塞。
- S05.f [PR #157](https://github.com/makewheels/video-2022/pull/157) a656f79c 的 CI38594738912 全部必要job success后合并fbb8502a（PR E2E skipped）。真实生产判定配置/索引夹具3passed，未验证实际生产配置/TTL删除时机/并发。
- S06.a #158已在全部必要CI38597432860成功后合并39af8559（PR E2E skipped）；[旧用户读取能力](user/user-reads.md)：纯端口/用例/HTTP值编码，4份固定Java HTTP与Python对照已过，完整Python165passed/Mongo1skipped，Java4passed/Checkstyle/Ruff/500行/空白通过。没有Mongo用户适配器或应用业务路由，不能称用户API已在FC迁移。
- S06.b1 #159 a9933666 的CI38620516871全部必要job成功后合并`286c3e7d`（PR E2E skipped），Java3项基线已固化。
- S06.b2 #160 f5640c6a 的CI38622565458必要项全通过后合并9fa11d75（PR E2E skipped）；Mongo只读适配器176passed，未装配HTTP，未验证BSON类型/完整请求日志/生产网络仍为门槛。
- 频道测试修复#161 head748bdd06的CI38624666455必要项全通过后合并2d9cf7f0（PR E2E skipped）；#160合并master CI38624255599/部署38624255442均success。
- S06.c1 #162 head37ce40db必要CI38626919450全通过后合并f98e91e2（PR E2E skipped），头像匿名鉴权缺口已记录，未修业务。
- S06.c2 #163 headd0ed7984必要CI38628826927全通过后合并104d54a7（PR E2E skipped）。#162合并master CI38628443843/部署38628443893均success。
- S06.c3 #164 head61ff5e55必要CI38630860497全通过后合并0ca7e09c（PR E2E skipped）；#163合并master CI38630544702/部署38630544827均success。
- S06.d1 #165 headc4f77f86必要CI38633115869全通过后合并e514ff69（PR E2E skipped）；#164合并master CI38632657026/部署38632657032均success。
- S06.d2 #166 head063a0e1d必要CI38635180591全通过后合并2017e3e5（PR E2E skipped）；#165合并master CI38634877692/部署38634877557均success。
- S06.d3 #167 head4f8d788f必要CI38637312136全通过后合并175ff4eb（PR E2E skipped）；#166合并master CI38636953114/部署38636952960均success。
- S06.d4 #168 head162eb6f0，CI38639197445首次后端在Maven插件解析失败（测试未执行）；官方POM本机HTTP200后仅重试失败任务，attempt2全部必要job成功，已合并708ac2b8，PR E2E skipped。
- S06.d5 #169 headf85cae57必要CI38641932763全部通过后合并af2c068f（PR E2E skipped）；#168合并master CI38641368850及Java部署38641369038均success，先前镜像代理失败后新部署已通过。
- 当前分支`feat-serverless-profile-update`基于af2c068f，S06.e1 [资料更新用例/适配](user/profile-update.md)：Java黄金HTTP14项+Mongo更新6项/Checkstyle，完整Python247passed/0skipped/1warning、Ruff/500行/空白通过。保持UTF-16/双字段替换/显式null、仅三字段更新、ObjectId不回退/零匹配不upsert；写入仅隔离测试库，未接生产路由/日志。
- #167合并master CI38639010341 success，但Java部署38639008711因Caddy镜像代理Bad Gateway失败；仅通过Actions重试失败job一次，attempt2仍同因失败，不继续碰运气、不直接操作服务器，不断言现役生产运行状态。新master CI/部署以实际查询为准。
- 06:05报告脚本已增强六类成果/验收边界和固定提交证据链接；本机独立headless Chrome实际点击打印/详情并验桌面/手机无整页横溢、打印CSS通过。仅系统任务临时HTML/环境，未提前打开用户可见浏览器；06:05起仍须生成/打开实际报告并核验。
- #158合并后master CI38620293672失败：浏览器频道等待选择器strict mode匹配2元素，79passed/1failed；既有Java部署38620293649成功。#159合并master CI38622137273已success（实际API/浏览器E2E通过）；部署38622137369另查询。旧失败仍有确定的并存元素证据，独立修复测试竞态。

- 本机 Git 直接访问 GitHub 临时失败，使用当前系统代理 127.0.0.1:7890 的单次 git 参数已 fetch 至合并提交；没有修改全局代理/Git 配置。
- 实际读取：本机 Infisical session 现在可用；prod `/` 存在 FC_DEPLOY_AK_ID/SECRET，`/oss` 存在数据 OSS 身份；dev 没有这些部署键。部署 RAM 用户 video-2022-fc-deploy 的已有 policy 允许 FC 函数/版本/别名/触发器/并发相关 API，无 OSS 权限。只读 FC 列表没有 serverless API 函数。密钥仅在内存/受限系统临时配置，未写 Git/报告。
- S03 将由 Actions 复用现有 OIDC，独立 OSS 前缀上传/回读后把已验证 ZIP 字节交给 FC，避免扩大现有 RAM 权限。仍需验证 Actions 权限、实际发布、同包晋级/回退。生产与 Mongo 保持现状。
- 用户新授权：夜间自主接续至 2026-10-15 08:00；06:05 起在浏览器打开 HTML 汇报。macOS launchd 已注册，同一会话每 15 分钟接续，账户额度不足等待重试；报告独立每 5 分钟更新；配置在 `~/Library/Application Support/video2022-night-run-20261014/`，报告为 `~/Documents/video2022-2026-10-15-夜间进度.html`。注册/语法/限额查询/HTML生成已验，实际触发运行记录查该目录日志。
- 当前稳定检查点：本地 CPython 3.12.12 + 锁定 web/deployment 组，Ruff 通过、103 passed、真实 Mongo 1 skipped、既有 warning 1，500 行/空白门禁通过。SDK 间接依赖 pycryptodome 4.0.0 仅发布 cp315 wheel，已限制 <4 并锁定可用 3.24.1；API ZIP 仍排除此组。
- 调度实际修复：18:45 日历已触发，但独立 CLI resume 遇到现有 writer 冲突；改成共享本机服务 Unix WebSocket RPC，18:52 实测原会话 turn/start 送达，工作中 heartbeat 跳过不并行。19:00 实际 tick 正常跳过；限额/线程读回已验。
- 云端只读检查发现旧 `fc.cn-beijing` 入口返回 InvalidVersion；用官方 CLI 3.4.11 dry-run 回读目标 `fcv3.cn-beijing.aliyuncs.com` 后修正 SDK，并实际得到目标 FunctionNotFound。ListProvisionConfigs 对不存在函数也返回 FunctionNotFound，按不存在处理；创建后及各次冒烟继续确认无预留配置，不忽略 Forbidden。最新本地完整 104 passed/1 skipped/1 warning，随后发布回归 28 passed，Ruff 通过。
- 实际隔离 publish [38577826254](https://github.com/makewheels/video-2022/actions/runs/38577826254) 对 `2eb0065e` 失败：CI 来源门槛、两组 Infisical Actions OIDC 取密、Linux 构建/入口冒烟通过，云操作返回 AccessDenied。只读核对 OSS 身份 video-2022-oss-data-dev 的 Custom policy 仅允许 GetObject/ListObjects/GetObjectMeta/GetBucketInfo，无 PutObject；附加 STS AssumeRole 权限不等于对象写入权限，当前没有配置发布专用 role。FC 身份无 OSS 权限。没有扩权或更换生产身份重试。发布日志没有操作级上下文，不能仅凭错误断定具体被拒绝 API；现有身份缺少上传权限已证实。
- 发布后只读 GetFunction 返回 FunctionNotFound，隔离 FC 未创建；没有 version/alias/receipt，真实晋级与回退未执行。23:21现有读取身份实际分页列该提交前缀为0对象；不推断其他前缀或账单。拟议隔离role的只读GetRole返回EntityNotExist.Role，未创建或扩权。S03/S04 保留阻塞，下一独立阶段为 S05 契约台账。

## 夜间验收当前检查点

- #176 headfaf37bd6必要CI38653142301全通过后合并846cb3b8；PR E2E skipped。#175 master CI38652998286/部署38652998328均success，新master CI38654503711/部署38654503779须查最终。
- [夜间验收与恢复清单](overview/night-review.md)汇总已完成/未验/失败及安全恢复；S03/S04与完整S06仍未完成。最近完整本地Python297passed，不为纯文档状态更新重复测试。
- 07:30实时五小时额度93%已用、周88%已用；优先保存/验收/最终报告，不使用付费额外或重置卡。

## S06.f2 历史检查点

- #175 head36d13246必要CI38651719556全通过后合并24377e59（PR E2E skipped）；#174 master CI38651370309/部署38651370246均success。新master CI38652998286/部署38652998328待查最终。
- [隔离更新HTTP组合](user/profile-update-http.md)新增15项，完整Python297passed/0skipped/1既有warning、Ruff/500行/空白通过。旧成功/长度/绑定/401黄金响应与真实Mongo更新次数/日志实测，只有测试应用，不改现有入口、不生产写入。

## S06.e3 历史检查点

- #174 head88c28b14必要CI38649966353全通过后合并01856f2f（PR E2E skipped）；#173 master CI38649789900/部署38649789809均success。新master CI38651370309/部署38651370246待查最终。
- [未装配JSON解码](user/profile-json-decoder.md)旧Java HTTP10项/Checkstyle、完整Python282passed/0skipped/1既有warning、Ruff/500行/空白通过。保留数字词法/重复键、拒绝容器和尾随内容，成功后组合纯用例与实际隔离Mongo写；UTF-8以外/极端输入仍未验，不装配生产入口。

## S06.f1 历史检查点

- #173 head1f815d72必要CI38648327035全通过后合并55263bf9（PR E2E skipped）；#172 master CI38648114989/部署38648115203均success。新master CI38649789900/部署38649789809待查最终。
- [隔离ASGI组合](user/profile-http-composition.md)新增9项，完整Python264passed/0skipped/1既有warning，Ruff/500行/空白通过。实际Mongo用户/文件/日志，5份黄金HTTP/3种401/日志失败恢复通过；签名替代，仅测试应用，不改现有入口，不等于FC或S06完成。

## S06.d7 历史检查点

- #172 head8dcd57a1必要CI38646677594全通过后合并44adaba1（PR E2E skipped）；#171 master CI38646352161/部署38646352029均success。新master CI38648114989/部署38648115203待查最终。
- 真实旧HTTP/隔离Mongo特殊header基线Java5项、完整Python255passed/0skipped/1既有warning，Ruff/500行/空白通过。含点名日志失败但业务成功保留，$前缀名原名落库；Python固定诊断由未验改为已验证边界，无生产入口装配。

## S06.d6 历史检查点

- #171 head397c91b0必要CI38645020667全通过后合并db56fbe1（PR E2E skipped）；#170 master CI38644545566/部署38644545434均success。新master CI38646352161/部署38646352029待查最终。
- [同步Mongo日志适配](http/mongo-request-journal.md)真实Java BSON基线7项、完整Python253passed/0skipped/1既有warning，Ruff/500行/空白通过。只在随机隔离库写入；原入口不接入，特殊键/真实网络不确定写入尚未验。
- 报告06:05:08由调度实际生成并成功调用系统open；打开标记与六类状态/固定提交链接已核验。浏览器控制工具不可用，实际屏幕显示未确认；此前独立headless交互/布局QA通过不等于可见页面验收。后续刷新文件，不重复开标签。

## S06.e2 历史检查点

- #170 head5ec65e3d必要CI38643314470全通过后合并ca38fea1（PR E2E skipped）；#169合并master CI38642888158/部署38642888150均success。新master CI38644545566/部署38644545434待查最终。
- [JSON绑定基线](user/profile-json-binding.md)真实Spring/隔离Mongo8项与Checkstyle通过：整数/布尔转字符串、未知字段忽略；容器/缺失正文等绑定失败不写资料。仅测试/文档，Python解码/路由仍未实现。

## 用户约束（接续不能丢失）

- 全部现有业务、接口、用户操作及历史播放一致，不新增需求外业务字段或规则。
- 轻量 Python、无常驻保活、响应越快越好；没有批准具体毫秒阈值，不重复要求选框架/数值。
- 本轮继续现有 Mongo，TableStore 只属未来可能性；未授权迁库位置或采购固定资源。
- 外壳与业务隔离，基于阿里云但不绑定公共逻辑；将来可替换云和 PostgreSQL/Mongo 适配器。
- Git 唯一源码，发布从 Git 经 CI 单向构建/发布；允许 OSS 存产物，禁止只在 OSS/控制台存在自研源码。
- 小阶段、多 PR、持续记录接续；已授权范围内自主推进，不在每阶段重新问是否开始。

## 当前阻塞与独立工作

- 本机 Infisical session 和 Actions OIDC 已实际成功；OSS 发布写权限阻塞已证实。后续需可审阅的隔离前缀最小权限发布身份/role 配置及 Actions 验证，不能把读取身份升级为全 bucket 写身份。当前不擅自改 RAM、不反复重试同一已知无写权限身份，先推进 S05。
- 现有 Mongo 的跨云访问、最小权限和真实 FC 冷启动未验；不擅自开放公网/改防火墙/搬库。
- 本机 Docker daemon 不可用；CI 隔离 Mongo 已验证，不能因此声称生产网络可达。
- Mongo 写库/投递间隙尚未解决；S01/S02/S05 可以先推进，不绕过恢复要求。

## 接续步骤

1. #152至#176均已合并，不重复。先核对docs-serverless-night-review独立PR/head/必要CI并收尾验收记录；07:45后不新开大阶段，08:00截止。06:05起实际报告生成/打开并验可见页面；后续完整用户HTTP隔离装配或日志/签名适配，不开放半成品生产路由、不双写。
2. 当前 master CI/E2E 已通过，但首次 publish 因 OSS 写权限失败；先解决独立发布身份权限并经审阅/验证，再运行 `gh workflow run deploy-serverless.yml --ref master -f mode=publish`；锁定实际 run 的 head，等待并检查全程日志。失败按真实错误在独立 fix 分支修复，再经 PR/CI/master CI 重试；不把旧 head 通过当新提交证据。
3. publish 成功后保存非秘密 receiptKey、commit、ZIP SHA、version、同包晋级/远端回读结果。首次没有前版不能假造回退；第二次可追溯发布后用新版 receiptKey 执行 workflow rollback，实际读回 alias/version 与冒烟。
4. S03 通过后才开始 S04 FC HTTP/冷热/原生扩展/现有 Mongo 网络；不擅自开放数据库公网、改防火墙、迁库或采购固定资源。无法安全接通则记录阻塞，推进独立 S05 契约台账。
5. 所有时间判断用实际北京时间。06:05 后运行调度报告并打开浏览器；07:45 后不开始大阶段，08:00 停止并保存检查点。额度不足时先保存、push，再由定时接续重试。

## 已查依据与未确认边界

- 阿里云 Python 运行时：https://help.aliyun.com/zh/functioncompute/python3/ 。查阅时 Python 3.12 为公测、Debian 11 x86_64；具体地域与实际 FC 配置仍需核验。
- Python 代码包：https://help.aliyun.com/zh/functioncompute/deploy-a-code-package-1 ；第三方依赖：https://help.aliyun.com/zh/functioncompute/install-third-party-dependencies-for-a-function 。依赖需与目标 Linux/架构匹配，不能上传 Windows wheel。
- uv 导出：https://docs.astral.sh/uv/concepts/projects/export/ 。候选 wheel target 为 `x86_64-manylinux_2_28`。
- 本机 Docker daemon 不可用；远程查询候选 `python:3.12.12-slim-bullseye` 镜像返回 not found。因此未验证 Debian 11 容器；Linux runner 冒烟也不能替代 FC/Debian 11 实测。若需要容器，先确认真实存在且合适的镜像，不假称已通过。
- S03/S04 才核实云资源、网络/权限、真实冷/热与 Mongo 首连。隔离发布已尝试但 FC 未创建；未核实 OSS 残留或账单，不能断言零费用。manifest 是完整性记录，不是数字签名，后续发布必须从受控 CI 获得可信摘要。
- S03 工具回退点为 S02 merge `42a0ebeb`；运行时回退以受控 receipt 的前版本为准，首次没有旧版本。现役 Java 业务未替换或切流。

## 检查点规则

- 稳定小改动完成时写本地 commit；准备 PR、长操作或中断前推送可接续分支，记录提交、PR、未提交改动及下一条操作。
- 记录命令、结果/CI 链接和必要摘要，明确 passed/skipped/pending/failed/未运行；不复制巨大日志或秘密。
- 半成品留在工作分支并标注，不把未跑测试的阶段标完成。CI 等待时不无理由重跑全套或不断提交状态刷新。
- 接续先 `git status --short`、fetch、查询 PR head/CI；未提交改动先确认归属，禁止 reset/覆盖后再调查。
- 阶段收尾写“实际成果、剩余项、回退点、下一阶段”；系统临时目录不是唯一工作记录。不承诺额度恢复后平台自动执行，恢复工作以此记录为准。

## 当前执行补充（2026-10-14 23:14，S05.e）

- 当前分支 `test-serverless-login-http-contract`，基于已合并 S05.b `5cf8716c`；S05.c #153 已合并，S05.d #154 已合并为 fc54aee1，最新查询为准。不能合并未完成 CI。
- S05.e [登录 HTTP 夹具](user/login-http-fixtures.md) 6 项集成测试+Checkstyle/空白通过，测试是真实隔离 Mongo/完整管线、固定身份；生产配置/TTL清理/并发未验。
- 此历史执行补充已完成：#154/#155均合并，当前以页首S03.b快照为准。不要回到已合并 #149/#152 重复实现。

## 当前执行补充（2026-10-14 23:55，S05.f）

- 分支 `test-serverless-production-login-contract`，基于 master12092139，独立补生产判定与TTL索引HTTP夹具；真实临时Mongo/真实EnvironmentService配置绑定，3passed/Checkstyle/空白通过，临时进程/目录清理。
- S03.b #156 已合并；已取消被替代旧head e297348c 的同PR CI38591558651，避免旧run占用并阻塞新head；没有取消masterCI或部署，不沿用旧head验收。
- S05.f #157 已合并fbb8502a；此为历史补充，以页首快照为准。TTL后台删除/生产部署/并发仍待，不使用生产账号或真实验证码。

## 2026-10-15 连续推进：S08.a 播放签名

#184精确head6d179d3c必要CI38672340446通过后合并5e27f648。
#185旧head1f8b0a97必要CI38672655387通过，但master更新后合并门禁要求新检查；
已合master为d1b8323a并推送，须等新CI。#186同理更新到c034d3a5，不沿用旧head验收。
独立分支feat-serverless-playback-authorization基于master5e27f648，实现
[旧文件签名与私密播放授权](playback/playback-authorization.md)。实际直接编译当前Java服务源
取得3组黄金HMAC，新增22项及架构8项共30passed、Ruff通过。尚无播放HTTP/媒体
清单/计费迁移；下一步getWatchInfo及主/子m3u8，不能宣称已可播放。

## 2026-10-15 连续推进：S08.b 播放信息

分支feat-serverless-watch-info基于S08.a，实现[播放信息独立HTTP能力](playback/watch-info.md)。
真实隔离Mongo新增6项、连用户HTTP/架构24passed，Ruff通过；无生产入口切换。
签名/播放详情已实现不等于URL后续路径已迁移。继续HLS子清单与主清单、302/计费。

## 2026-10-15 连续推进：S08.c/d HLS 清单

#185 更新后的必需 CI 通过并合并为 06cb03e7。#186/#187/#188 在原 head 的必需 CI
通过后，因 master 更新再次同步基线并推送，必须等待新 head 的检查，未绕过保护。
[子清单](playback/hls-child.md) 提交 #189；[主清单](playback/hls-master.md) 在独立后续分支实现。
本地播放相关 64 项实际通过、Ruff 通过，包含真实临时 Mongo 与 JDK 帧率算术核对。
主/子清单业务能力尚未注册 HTTP，生产入口不变。下一阶段是 HTTP 合约、
file/access 的重定向与可靠计费写入；OSS 权限和实际 FC/Mongo 网络验收仍待解决。
