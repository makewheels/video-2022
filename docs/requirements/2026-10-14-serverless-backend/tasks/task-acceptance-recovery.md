# M016 任务受理意图与恢复协议

[固定台账](../overview/remaining-work.md) · [候选架构](../../../design/serverless-backend.md) · [提交故障基线](task-submission-persistence.md) · [进程终止实验](process-crash-experiment.md)

状态：**可审阅设计交付，不是可靠任务运行时交付**。2026-10-30现场源码基线为合并#232后的`a78813b9`。推荐“完整意图先获托管持久受理，消费者重建既有任务，每次活跃状态转换先建立可靠唤醒护栏”；具体产品、有限保留的恢复窗口、权限和费用必须由M017实验证实。M018–M023尚未实现，不停用Java恢复、不创建云资源、不变更生产配置或数据库模型。

## 现有源码事实

| 边界 | 现有行为与迁移约束 | 源码 |
|---|---|---|
| 原片完成 | 存在性/视频状态/文件READY检查后启动`new Thread`，HTTP先返回`Result.ok()`；线程失败只记日志，不是持久受理 | [VideoService](../../../../server/video/src/main/java/com/github/makewheels/video2022/video/service/VideoService.java)、[RawFileService](../../../../server/video/src/main/java/com/github/makewheels/video2022/video/service/RawFileService.java) |
| 业务身份 | `fc-<operation>-<refId或videoId>`；PROBE用videoId，TRANSCODE用transcodeId，COVER用coverId。TRANSCODE记录ID另为`tr_fc_<videoId>_<resolution>` | [Submitter](../../../../server/video/src/main/java/com/github/makewheels/video2022/transcode/task/FcTaskSubmitter.java)、[Launcher](../../../../server/video/src/main/java/com/github/makewheels/video2022/transcode/TranscodeLauncher.java) |
| attempt | 构造默认count=0、max=2；现有启动器准备首个attempt时设count=1。业务失败重试才生成后缀并增加count；429和消息重投不等于新业务attempt | [FcTask](../../../../server/video/src/main/java/com/github/makewheels/video2022/transcode/task/FcTask.java)、[Callback](../../../../server/video/src/main/java/com/github/makewheels/video2022/transcode/task/FcTaskCallbackService.java) |
| 付费提交 | 先保存完整snapshot并CAS `CREATED→SUBMITTED`，再发HTTP；成功后只条件更新requestId，不能覆盖快速回调。**SUBMITTED实际上不证明平台已受理**，不能照字段注释推断 | [Submitter](../../../../server/video/src/main/java/com/github/makewheels/video2022/transcode/task/FcTaskSubmitter.java)、[Repository](../../../../server/video/src/main/java/com/github/makewheels/video2022/transcode/task/FcTaskRepository.java) |
| 输出隔离 | 既有outputBaseDir下`attempts/<attemptId>`；playlistKey/imageKey改为该目录加原basename，result manifest固定为该outputDir下`result.json` | [Submitter](../../../../server/video/src/main/java/com/github/makewheels/video2022/transcode/task/FcTaskSubmitter.java)、[Callback](../../../../server/video/src/main/java/com/github/makewheels/video2022/transcode/task/FcTaskCallbackService.java) |
| 多档竞态 | 所有Transcode及准备完的FcTask先保存，之后才逐档发送；不能让首档快速回调误以为全档已完成 | [Launcher](../../../../server/video/src/main/java/com/github/makewheels/video2022/transcode/TranscodeLauncher.java) |
| MPS兜底 | fallbackCount原子0→1，只允许一次；30分钟deadline。`FALLBACK_PENDING`由单独的30秒`pollFallbacks`扫描，不在FcTimeoutRecovery的超时集合；已有job仅查询 | [MPS fallback](../../../../server/video/src/main/java/com/github/makewheels/video2022/transcode/mps/MpsFallbackService.java)、[Recovery](../../../../server/video/src/main/java/com/github/makewheels/video2022/transcode/task/FcTimeoutRecoveryService.java) |

`fcTask`已有operation/videoId/refId、attemptId/count/max、provider/fcRequestId、status/deadline/errorMessage、payloadSnapshot/outputBaseDir/resultManifestKey/executionTimeoutMs/submitFailures、createTime/finishTime。优先复用，消息ID、投递次数和平台执行状态不取代它们。

## 完整意图不是“有一个taskId”

| 受理阶段 | 必须可恢复的既有输入 | 不能偷偷增加的行为 |
|---|---|---|
| 原片处理尚未创建FcTask | 旧调用命令及videoId；消费者按原RawFileService顺序回读Video/File，LOCAL/AUTO等分支保留 | 不把“完整”误解为冻结所有Video字段，不新增rawFileId版本校验、所有者规则或伪FcTask |
| 已准备PROBE/TRANSCODE/COVER | 既有完整任务身份、attempt和参数、原payloadSnapshot、输出基目录及超时；源key、bucket/endpoint、媒体策略等须足够重建缺失任务 | 不在缺任务时只凭ID猜profile、源对象、provider或默认媒体策略；不覆盖新attempt |
| 活跃任务唤醒 | 既有taskId、attemptId、目标status和deadline，以及执行片段类别；恢复仍回读权威业务记录 | 不把队列接收次数写成attemptCount，不新增业务状态、终态或统一重试规则 |

业务层只接普通值/能力，不接FC event、SDK receipt或BSON对象；消息适配器须无损往返既有Date、Integer/Long、缺失/null和payload内容，类型契约留M033/M018实测。沿用媒体payload的schemaVersion=1，不凭本设计添加业务版本语义。

入队前执行旧HTTP既有鉴权/绑定/检查顺序；异步内容只存必要输入或受控对象引用，不含用户token、STS凭证、调用/回调secret、视频字节或整份分片。现有CloudFunctionClient的secret在发送header里取配置，不在snapshot里；YouTube带token的旧URL等不能直接复制成通用任务载荷，另归对应任务调查。

## 推荐的受理、消费与确认协议

1. **受理端**先完成旧同步前置检查，生成完整意图，经托管服务取得持久受理确认后才返回原成功响应。不能先写Mongo后尽力通知，也不能把线程启动、SDK本地排队或HTTP连接成功当持久确认。受理异常/结果未知须沿既有错误路径显式记录并返回错误，不返回假成功；重试仍用原业务键，不为一次不确定响应创建新付费身份。
2. **消费端**用完整意图`createIfAbsent`重建缺失任务，再回读权威记录。只通知ID且任务不存在时不能ACK；反序、重复、终态及旧attempt不覆盖当前记录。任务准备仍保持全部档位先保存、后发送。
3. **每个活跃转换**先可靠登记覆盖目标deadline的唤醒，再用既有业务键/attempt/预期状态及观测deadline做CAS。CAS失败只产生可安全忽略的冗余唤醒，不产生第二次付费提交；平台登记失败则不开始该片段的付费副作用。新增deadline条件是对已有字段的并发约束，不是新业务字段。
4. **执行端**仅CAS赢家发送一次，保留原固定长度HTTP发送和禁止隐式重放的策略。2xx后只条件补fcRequestId；429沿原计数/等待；HTTP0/5xx或发送中崩溃保持未知受理，等原deadline/核对，不立即重新付费。
5. **ACK是传输确认，不是业务成功。** 只有任务已终结/确属过期消息，或当前片段已持久落地且其后续活跃deadline有可靠护栏时，才确认本条投递。保存任务、调用返回或打印日志本身不足以ACK。无法证明后继护栏时先补当前状态的可靠唤醒；失败保留消息重投并显式告警，不静默吃掉。
6. **延迟消息处理**回读状态并比较attempt/目标状态/deadline；旧消息无权触发新attempt的失败或提交。提前投递按实际deadline重新可靠安排，不靠进程sleep；到期消息只触发当前应执行的恢复片段。数据库/对象服务临时失败用传输重投，不额外增加业务attempt。

上述“登记护栏→CAS→片段→ACK”是推荐的不变量，不是现有Java已经实现的能力。所有初始创建、重试、回调登记及MPS交接都必须遵循，不能只在首次SUBMITTED补一个计时器。托管调度与Mongo也没有跨系统事务；登记先于CAS，并在重复/陈旧投递时校验权威状态，才避免把该间隙搬到另一个位置。

原片入口没有现成FcTask时，持久托管活动必须保留完整命令直到下一可恢复边界，不允许消费者只留一个无法重建的通知。若候选产品无法满足此条件，M017结论应为不满足；需要技术收件箱/outbox时先单列字段/索引方案确认，不能为了赶进度暗建集合。

## 所有需要护栏的转换

| 转换/片段 | 沿用的业务语义 | 护栏后要恢复的动作 |
|---|---|---|
| 新建/重建CREATED | 同一业务键和已准备attempt、完整snapshot；保留原创建/部分写入顺序 | 同attempt认领发送，不另建收费任务 |
| CREATED→SUBMITTED | executionTimeoutMs显式值沿用；缺失才按旧逻辑至少300000ms。PROBE/COVER准备300秒，转码准备按原公式300–1740秒 | 到期按旧失败/重试策略；不能把requestId缺失当未发送 |
| 429→RETRY_WAIT | submitFailures增加，前29次等待60秒，同attempt；第30次走原不可重试失败 | 到期恢复CREATED后同attempt发送，仍先覆盖新deadline |
| 业务失败→RETRY_WAIT | 现有maxAttempts、attempt后缀、count和10秒加配置jitter；不将所有异常统一算业务失败 | 到期发送已经持久的新attempt；晚到旧回调继续忽略 |
| 成功回调→PROCESSING | 匹配既有result.json路径后CAS，保存manifestKey和5分钟登记租约 | 重放登记，不重转码；RegistrationException仍返回200并保留PROCESSING |
| PROCESSING到期 | 原恢复先改回SUBMITTED，再用保存的manifest重放成功回调 | 新的登记租约同样先登记可靠唤醒，不能只保护第一次 |
| CREATED/RETRY_WAIT到期 | 原恢复不增加attempt，缺snapshot按旧失败出口 | 先覆盖将写入的deadline，再CAS/重提；不编造缺失payload |
| 转入FALLBACK_PENDING | TRANSCODE且满足现有MPS资格/名额才交接；保留fallbackCount、currentProvider、jobId和30分钟deadline | 查询已有job、登记或原超时失败；名额占用/HTTP响应不确定时禁止重发MPS |
| MPS查询租约 | 原fallbackPollAfter认领5分钟，finally释放；不是FcTimeoutRecovery的PROCESSING | 活跃job的下一检查及租约崩溃恢复也要有托管唤醒，具体映射归M037/M021，不取消现有扫描冒充完成 |
| SUCCEEDED/FAILED | 既有终态和finishTime，重复回调不改变结果 | 旧唤醒只确认/清理，无新付费动作 |

回调继续在原HTTP边界执行既有登记/返回语义，不因有队列就改成“入队即登记完成”。需要异步化某个旧同步接口时必须另做契约/明确授权，不由本设计顺带改变。

## HTTP与未知结果的兼容边界

- 原片GET及OpenAPI仍保留原路径、参数、成功`Result`形状，不改成202、工单号或新的必填字段。持久受理替代原线程是已确认目标；前置检查和部分写入顺序由M024真实旧HTTP固定。
- 普通业务异常沿现有`GlobalExceptionHandler`/Result错误形状；它未设置异常HTTP状态，**HTTP200不能单独当业务成功**。不凭框架默认值换成422/503，不新增错误码掩盖未知受理；新适配必须验证实际错误正文/日志。
- 自建回调secret缺失/不符为403空体；业务handler返回200/400/404映射相应HTTP，其正文仍是`Result.ok()`。旧attempt/终态或FALLBACK_PENDING可忽略；当前attempt的非法status/manifest路径仍按原顺序报错。M033固定真实HTTP与完整BSON，不凭“更合理”改形状。
- 外部已受理但响应丢失和根本没发出在本地可能完全相同。fd/requestId日志只能辅助；不能据此证明未计费。业务期限后仍沿既有有限重试/兜底，不承诺付费动作的全局exactly-once。必要查询/manifest核对由M022实测，不擅自新增调用。
- 内部投递ACK、普通API成功、自建回调200、媒体作业成功是四种不同结果，不互相替代。生产切换前还须核实发送端重试/确认语义，不能靠一个HTTP200闭环。

## 崩溃矩阵与责任出口

| 崩溃/故障点 | 恢复依据与允许动作 | 禁止 |
|---|---|---|
| 托管受理前 | API无成功承诺；原键重试 | 先成功再后台投递 |
| 平台已受理但确认丢失 | 完整意图可能已运行；重复受理按原身份归并 | 当作一定未受理而换taskId |
| 已受理但任务尚不存在 | 消费完整意图重建、回读；重建失败不ACK | 缺任务就丢消息，或凭ID造默认载荷 |
| 任务已存但消费消息未确认 | 重投读取状态，补护栏/安全忽略 | 扩增业务attempt |
| 护栏已登记但CAS前崩溃 | 原状态或重复意图继续；陈旧护栏核对后安全清理 | 把护栏存在等同已付费 |
| CAS后、付费发送前崩溃 | 已有deadline护栏；保守按未知受理处理 | 因没requestId立即重发 |
| 平台接受后/响应持久化前崩溃 | 同上；快速回调/期限核对决定下一步 | 覆盖已PROCESSING/终态，隐式HTTP重放 |
| 登记写了一部分后崩溃 | PROCESSING/manifest/租约恢复登记；沿旧幂等和部分写入 | 再编码，或回滚已登记对象冒充全成功 |
| RETRY_WAIT持久后/新投递前崩溃 | 转换前已登记的对应attempt/deadline唤醒 | 只保护第一次提交 |
| MPS名额占用/提交结果未知 | 保留已耗名额和原期限，已有job仅查；无jobId按原超时失败 | 重复耗费兜底或无期限静默悬挂 |
| 完成已存、ACK前崩溃 | 重投识别终态并确认 | 重做收费动作/覆盖finishTime |
| 超过传输重试/队列保留 | 独立死信/告警及明确再投责任；完整意图仍须可找回 | ACK错误当成功，宣称有限保留等于永久不丢 |

已有本机实验覆盖若干模型间隙，**不等于上述目标协议或真实平台已经通过**。M023必须在最终选定产品上逐点杀进程/断响应，并验证数据库、托管活动、对象和付费调用次数；没有真实证据就不能停掉旧恢复。

## 保留期、成本与单所有者门槛

- M017须记录实际受理确认、重复/乱序、延迟粒度/提前投递、可见性/租约、保留/死信、重驱和服务端故障行为及来源，不预设某产品天然满足。请求ACK丢失和回调先于确认须单测。
- 平台保留期、活动最长时间和最大停机恢复窗口必须有实测值/操作责任；有限保留不是永久保证。现有PROCESSING登记可以反复续租，不能擅自加总时长/次数使旧任务提前FAILED。若候选服务需要续投或额外持久收件箱才能保持该语义，先提出明确方案，否则不通过门槛。
- 超过重试的死信须保留完整意图/错误并可安全再驱，告警不可依赖失效的同一消费者。未证明保留期内恢复和超期意图存续，不宣称“永不丢任务”。
- 业务无活跃任务时不启动自有轮询/保活；托管服务的轮询、消息、定时器、日志、保留和最低费用仍逐项计算。MPS的活跃检查有实际调用，不用“无空扫”包装成零费用，也不擅自降低旧检查频率。
- 真实产品/新增收费、RAM策略/资源、生产配置须另行授权。上线交接前以既有任务键划分唯一所有者；MPS、旧回调、在途Java任务也需M085/M086交接证据，不让旧扫描与新消费者并发收费，不直接关闭旧恢复。

## 技术模型与确认清单

| 项目 | 本设计建议/尚缺证据 | 是否本批执行 |
|---|---|---|
| 业务集合/字段/状态/索引 | 首选全部复用既有；不新增全局任务状态、attempt含义或金额/重试规则 | 否，无运行时/数据库改动 |
| 传输元数据 | 消息类型、投递ID、receipt/可见期限、目标status/deadline只属适配；平台值不成为业务状态 | 仅设计；具体wire编码在M017/M018证明 |
| CAS条件 | 现有键/attempt/status外核对观测deadline，防止旧唤醒认领后来同状态的租约 | 仅设计；M020并发证据待做 |
| 持久技术收件箱/outbox | 仅在托管完整意图存续能力不足且确有必要时提出独立集合/字段/唯一键/索引/清理方案 | 未选定、未授权、未创建 |
| 产品与保留/死信/费用 | M017在授权隔离环境提供可回读配置、真实事件和报价/账单边界 | 未实测，不用本机模型代替 |

M016的完成标准是以上源码身份、返回语义、所有deadline转换、故障点和待确认项可审阅；不是M017–M023通过。已有Java提交故障2项、本机进程模型7项仍是历史覆盖，本次文档批次未重新运行它们。文档链接/围栏、固定90项状态/依赖与空白门禁及本PR最终head CI另记交付证据；回退仅需撤回本批文档，不触碰业务数据。
