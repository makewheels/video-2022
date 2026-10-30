# M027 LOCAL产物扫描登记

[固定台账](../overview/remaining-work.md) · [档位创建](local-transcodes.md) · [产物校验](output-validation.md) · [全档就绪](video-readiness.md)

状态：隔离实现及本机验收已交付，未合并。2026-10-30用户授权合并#231后继续；基线master `dec7a776`，功能分支`feat-m027-local-output-20261030`。本轮只交M027，不扩大到M028完成回调、Webhook可靠派发或视频READY副作用；新PR及终版CI见接续记录/PR正文。

## 真实边界与设计

扫描登记不是独立旧HTTP。旧`TranscodeCallbackService.onLocalTranscodeFinish`依次读取可选result.json、核对旧元数据、调用现有OutputValidator、读取视频、保存播放列表、扫描/保存TsFile、保存登记后的Transcode，之后才调用finishTranscode。M027止于登记保存，不新增只做半条链路却返回成功的HTTP；原`/transcode/local/finishTranscode`仍404，生产入口不变。

实现普通`LocalOutputRegistration`、端口、现有Mongo及显式OSS适配和独立惰性工厂；复用既有OutputValidation、ID能力、File/Transcode映射和Fastjson输入方言。对象key是协议路径，不是本机文件路径。没有新业务字段、集合、索引、队列或后台线程。

## 必须保留

- 可选侧车读取在校验之前；已知对象读取失败沿旧语义记录warning后按缺侧车继续，坏JSON或元数据/codec不匹配不吞。Java getter的缺失/null及转换顺序按旧样本固定；容器转字符串保留HashMap桶序、扩容/树桶链和UTF16代理对，不当作DTO绑定或改成字典序，不新增媒体规则。
- 校验后再次读签名播放列表（10分钟），不能把前一次校验读取当同一份快照；先查播放列表File，未登记才生成ID/读取元数据/保存。保留File构造默认值和BSON Long大小。
- 列举整个输出前缀的全部分页对象，包含非媒体文件；按旧basename建表，重复basename不能悄悄覆盖。按旧原始EXTINF行解析时长，不能“修好”CRLF/带空格行后改变原码率。
- fMP4 init使用tsIndex=-1且不算媒体码率；分片按原播放顺序、transcodeId+index查重，已有记录不重写。先构造全部新分片，再一次有序insert；不增加旧代码未写的timeLength/provider/审计字段。
- 插入后查询全部历史及新分片，保存全量tsFileIds；平均值仅在有媒体大小及视频时长时计算，最大值忽略null。保留Java long溢出、HALF_UP与int结果范围。缺时长按旧warning，不伪造0。
- 保存/列表/下载/解析/计算任何阶段失败均保留已完成的前序写入，不重试、不回滚为成功。不改变完成状态、finishTime、视频或Webhook。

## 实际验证与限制

真实Java方法与独享回环Mongo生成**69组**完整BSON/对象调用/ID前缀样本；测试在真实AfterSave事件边界停止LOCAL方法，截取登记已持久化、但尚未进入finishTranscode的状态，而不是伪造HTTP成功。异常场景截取真实部分写入；重复basename错误只去掉含随机对象地址的JDK诊断后缀。撤掉生成参数重放固定文件，连同LOCAL创建/校验共**Java8passed/0skipped**，Checkstyle通过。

Python新增**85项**。首轮191+123项不重叠回归后，终审修正getter，并在增强固定语料上十文件一次复跑，最终**314passed/0skipped/3warnings**（既有Starlette/httpx弃用、OSS SDK两处非法转义）。同一合成对象/独享Mongo逐项对照，含已登记历史、真实Mongo拒绝及有序插入中途失败；另验第二次登记、1000条分页续令牌、资源关闭、惰性工厂和不重放。默认完成HTTP仍404；Ruff/架构/500行/空白门禁通过，固定台账/链接与完整CI另记。

终审确实发现原单键样本漏掉的Fastjson1.2.83嵌套对象顺序和UTF16差异，修正前探针失败；私有侧车适配补兼容后，249组Java/Python实际getter对照通过，不新增运行依赖。固定69例内强化普通乱序键、32个同哈希键和代理对，重新生成并完成上述Java重放；独立进程停用顺序修正时，固定完整BSON契约1failed（预期），正常实现的314项已实际跑通。临时对照不是新增249项持久测试，也不把首轮通过冒充终版覆盖。

旧实现对任何读取异常都warn并当缺侧车；本实现只对明确的OSError/OSS SDK错误保留该回退，JSON/业务错误与未知编程异常上抛，避免把坏代码伪装成兼容成功。元数据读取主动关闭SDK对象且不缓冲媒体。没有新增业务规则；同源字段按Java Integer/Long映射（含BSON小数截断）固定，而不是新增正数校验。

无生产/收费云调用，真实OSS/FC/网络/设备未验，不把固定样本或现有Java部署当上线验证。M027普通能力不自行认领并发完成或派发恢复任务，这些仍归M028/M019。

回退本PR代码即可；M027没有新任务所有者或调度，不恢复旧库备份覆盖业务数据。M028需补完整完成/重复/可靠副作用后才允许安装原完成HTTP。
