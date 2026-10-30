# S07.b 搜索与分类

统一后端新增GET /search、GET /category/list。搜索保留q/category、page默认0、
pageSize默认20和SearchResultVO的content/total/totalPages/currentPage/pageSize；
分类固定沿用原15项中文及顺序。无分页上限、正数验证或分类白名单校验。

关键词沿用字面匹配；category仅用trim判断是否为空，实际相等比较仍使用原值，
因此“ 音乐 ”不当成“音乐”。查询仅PUBLIC，原排序及封面/上传者/日期字段复用，
没有复制第二份VideoVO编码。公共列表仍先装配再count；搜索保持先page→count→装配。

本次JDK21独立临时探针实测旧Math.ceil(double)再转int：0/0得到0，3/0得到
2147483647，3/-2得到-1，3/2得到2；MAX_INT*2的skip为-2。Python保留这些
旧算术边界，不把不常见参数变成新业务校验。此为算术探针，不等于全Java HTTP矩阵。

2026-10-15实际临时Mongo8.3.7/HTTP验证新增8项，连公开列表、真实TCP与架构
focused共30passed，Ruff通过，1既有Starlette warning。包含查询/category原值、
空默认、0/负size、乘法溢出、分类顺序、count顺序；完整Java搜索HTTP及云网络未验。
本阶段没有重跑整套本机测试，以受影响模块回归及后续精确head CI验收。

原fc_handler/web生产入口仍为原型；新组合工厂显式调用才生效。回退本阶段搜索模块
及组合注册，恢复公共列表编码即可，没有线上数据/配置/资源变更。

## M010 剩余契约矩阵（2026-10-30，本PR隔离交付）

15:41从已按最终head CI合并M033的master `4ac86586`建立
`fix-m010-catalog-contract-20261030`。先固定真实旧MVC的公开列表、搜索和分类HTTP，
再对显式Python组合工厂重放，不重复实现已有能力或增加路由、分页上限/分类白名单。

真实旧MVC产生[144组固定语料](../../../../serverless/contracts/catalog-matrix/catalog.json)：
公开列表94、搜索49、分类1，其中123个原成功/21个原错误响应。覆盖字面正则、
Java trim/Unicode/嵌入NUL、重复参数、可见性/分类原值、排序分页/溢出、
存量BSON类型/缺失/null及嵌套默认值。每组保存完整video/user/cover前后BSON、
HTTP正文/状态/content-type及日志数量，未知字段和只读不变性不丢。

生成后撤属性，按固定文件重放。源快照按canonical Extended JSON保留类型，
显式null和原BSON字段顺序都保留；Double文本的跨驱动拼写只按同一IEEE754位型
比较，不合并Integer/Long、不抹平负零。日期显示核验原本地时区格式后变成epoch标签，
只有构造器日期落在实际请求窗口内才归一。

修复实际旧证据证明的差异：重复String参数逗号拼接（数值仍取首值）；Spring
字符串的bool/Double/集合/Document转换、CSV tags及Long日期；watch明确null不当0，
空数组/0/空字符串等坏嵌套不再因Python真值判断冒充默认对象。Document字符串使用
驱动relaxed JSON、原字段顺序及UTF16代理转义，不错用Fastjson；JDK科学形式最少两位
有效数字，极小Double同样保留。仅本域私有适配器使用，未改共享user mapper或数值格式器。

**本次实际**Python三个相关文件一次165passed/0skipped（144新增+21既有），一个既有
Starlette warning；另46组真实JDK Double文本对照全一致。新进程只在内存加载基线
`4ac86586`的四个原Python文件，用同一144组和完整BSON重放，实际31failed/113passed，
无error/skip，未改工作区；证明新用例会击中修复而非重述实现。
Java固定重放/清理保护及原公开列表/搜索一次16passed/0skipped，Checkstyle零违规；
命令及最终head CI详见[验证记录](../verification.md)及本PR。

本批不含M011真实封面签名/云网络或M009各端实测。所有读写只在独享回环测试Mongo；
新Java清理入口连AfterEach一同保护，拒绝非test库先于枚举/删除集合。
没有真实FC部署/切流、新业务字段/集合/索引/状态、依赖或收费资源。回退本PR四个
Python文件、测试/语料及文档即可，无业务库回写或在途任务影响。
