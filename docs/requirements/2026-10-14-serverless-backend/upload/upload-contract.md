# S10.a.1 上传HTTP/持久化基线

状态：本地真实旧HTTP/隔离Mongo7passed、Checkstyle成功，必要CI另查，不修改业务运行代码。ID生成和后台RawFileService受理使用合成替代；OSS/MD5/转码/封面等外部服务沿基础测试替代，不连接生产或调用收费服务。

## 入口和调用顺序

| 入口 | 输入/成功输出 | 鉴权与副作用 |
|---|---|---|
| POST `/video/create` | JSON videoType/rawFilename/size，另有youtubeUrl/ttl/transcodeMode；成功message为`video created`，data含fileId/videoId/watchId/watchUrl/shortUrl | CheckToken先于正文绑定；USER_UPLOAD要求类型/文件名非空且size非null/非0，旧负size不拒绝。创建file→video→回写file的videoId/key，非事务 |
| GET `/file/getUploadCredentials` | fileId；原bucket/key/endpoint/accessKeyId/secretKey/sessionToken/expiration，并追加provider | CheckToken→文件存在→所属用户→STS；null凭证为code34，不改变文件；供应商异常按原通用错误处理 |
| GET `/file/uploadFinish` | fileId；成功code0/data null | CheckToken→文件存在/所属用户→OSS getObject→从远端metadata保存size/etag/uploadTime/fileStatus READY。声明size不取代真实对象元数据 |
| GET `/video/rawFileUploadFinish` | videoId；成功code0/data null | CheckToken→视频存在/非READY→原片存在/READY→启动后台处理线程后先返回。旧Controller没有追加视频owner检查；兼容迁移不暗增规则 |

Web UploadPage、Android UploadRepository/UploadWorker与CLI video upload均按这四步调用，原片由客户端直传OSS。CLI LOCAL也走这四步，再登记转码/封面。YouTube创建会启动海外搬运/封面线程，应归S22/S13，不能在普通创建API中偷偷执行跨云长任务。

`size`的现有校验是“缺失或0”，不是正数校验；创建时没有把它写成file.size。`ttl`没有在当前创建路径应用到过期时间（旧源码相关逻辑注释），不得迁移时新增期限语义。只有精确`LOCAL`映射LOCAL，其他值归AUTO。原片扩展名取FilenameUtils并转小写，key按旧系统本地年月和ID组成；PUBLIC、owner/uploader、状态和默认文档都要对照，不能只比较HTTP成功码。

## 两类完成通知的边界

file/uploadFinish只确认远端对象并写元数据；OSS读取失败时文件保持原状态。rawFileUploadFinish是另一个入口，READY视频返回25，原片未READY返回32；Controller异步线程异常只写日志，HTTP成功不能当作MD5/转码成功。

RawFileService的旧主路径（本批源码调查，未把它宣称真实HTTP执行通过）：

1. READY/TRANSCODING/TRANSCODING_PARTLY_COMPLETE提前返回；其他状态先写PREPARE_TRANSCODING。
2. LOCAL直接等待客户端登记，不调用云MD5/探测/转码/封面。
3. AUTO同步MD5→查询旧md5原片→校验OSS原片、旧视频READY、已有转码finish状态与m3u8对象。
4. 可复用时写file/video链接、共享转码/封面/owner、删除新上传对象、标READY并执行就绪回调；否则标TRANSCODING并提交转码/封面。

这涉及多次写入、外部调用及删除，不能把FC返回前启动线程视作可靠受理。S10完成/MD5与AUTO迁移依赖S12写库/投递间隙、唤醒/恢复和单写入所有者实验；先保留接口/响应语义，再实现可靠执行，不直接在生产重放，也不双提交收费任务。

## 本批验收范围

`UploadHttpContractTest`使用真实MVC/隔离Mongo固定四条入口401先于绑定、创建完整响应/原片字段和key形状、非法/负size及LOCAL大小写、上传凭证格式/失败、文件存在/所属权限、远端元数据写入/失败不写、原片状态与异步受理边界。

ID生成器在本批固定为合成ID（其普通算法已有S06独立证据）；后台worker受理替代只证明Controller调度，未执行MD5/去重/云转码。没有真实STS/直传、完整客户端上传或FC网络验收，Python能力仍未实现。回退本批测试/文档即可，无生产数据或配置变化。

实际Java7passed/0skipped，Checkstyle/空白/500行门禁通过；Python运行源码未改，未重复其最近869passed/2符号链接项未跑的全量。首轮合成OSS异常后的重置使用when触发旧异常，改用doReturn设置下一合成结果后全部7项复验成功；未修改运行代码或放宽业务断言。

下一批S10.a.2补创建完整BSON/输入绑定和MD5/AUTO/LOCAL的可恢复边界样本；S10.b随后实现普通创建/旧状态能力，S12.b同步调查可靠受理，不以现有服务级UploadFlowTest（绕过部分Controller校验）代替真实HTTP契约。
