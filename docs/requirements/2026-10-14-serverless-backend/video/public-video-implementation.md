# S07.a 公共视频列表与后端组合

新增PublicVideos公共用例、只读Mongo视频/封面适配、PublicVideoApplication及显式
BackendApplication/build_backend_application组合。独立应用现在可连续执行登录、
资料、观看身份、头像和公开列表；现有生产Handler/web仍未替换。

## 保留的行为

PUBLIC是唯一可见性条件，不按READY筛选。关键词使用Java trim字符范围与字面
正则，忽略大小写，匹配title/description/tags。createTime降序，无附加排序键。
负skip忽略，limit<=0不限条数；count在列表及封面/上传者装配后单独查询，不假装
同一快照。没有分页上限、默认skip/limit或新增字段。

封面批量读后签名2小时，上传者去重查；昵称优先、手机号脱敏、缺用户未知用户。
保留历史watch链接和媒体时长。BeanUtils没有对应来源的type/userId和未被赋值的
youtubePublishTime保持null；YouTube只填publishTimeString。显示时区由调用者
显式提供，JSON日期固定UTC毫秒。字段集与原VideoVO相同，不改名称或日期单位。

## 独立依据与验证范围

复用已有PublicVideoHttpContractTest五项旧HTTP证据，Python隔离HTTP验证对应
分页、非READY PUBLIC、PRIVATE/UNLISTED排除、字面搜索/大小写/标签、0limit、
负skip、总数、旧链接/null、北京时间、缺参/非整数/溢出及请求/异常日志。
另验证封面2小时签名，真实TCP通过统一BackendApplication访问空公共列表。

项目Spring Data Mongo5.0.3字节码实际确认Query.limit(int)把<=0转为unlimited。
Spring Core7.0.5/JDK21临时探针实际确认0x10/#10/010/-0X10/内部ASCII空白/
U+2003的数值；+0x10及NBSP输入被拒。Python转换按该证据实现，不套框架默认422。
这些直接探针不是本次完整旧Java HTTP重跑；Unicode十六进制数字、重复参数、极端
输入和日志失败等仍需完整HTTP对照，不能据普通整数用例宣称全部绑定兼容。

原始历史BSON的null/缺失构造默认、非标准类型/嵌套、同时间排序、空封面key、真实
OSS权限/签名播放尚未全验；适配器只覆盖已核对的正常模型和已记录默认，未开放生产。
没有生产影子请求或线上写入，普通请求的日志只写随机回环隔离库。

回退本阶段视频模块/组合工厂/测试即可，用户阶段独立保留，无线上资源变化。

2026-10-15本次实际：Python3.12.12/临时Mongo8.3.7完整361passed、2deselected
（Windows符号链接权限）、1既有warning，Ruff通过；含13项本阶段用例及统一后端
真实TCP。尝试本机离线重跑旧Java14项夹具时，Maven Checkstyle3.6.0插件无法从
现有缓存解析，未进入编译/测试，不能报告本机Java通过；此前PR CI证据单独核对。
