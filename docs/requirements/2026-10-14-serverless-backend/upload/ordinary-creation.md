# S10.a.2 / S10.b.1 普通创建

`VideoCreation`、`MongoCreationWrites`与`UploadApplication`提供普通视频创建。隔离后端必须显式传入`creation_base_url`才安装`POST /video/create`，共用已有身份/请求日志/异常日志周期；生产原型入口未切换。YouTube依赖搬运任务，当前普通创建不支持该分支，不提交海外/封面任务。

## 保留的行为

- CheckToken先于正文绑定，未登录401不写业务或请求日志。成功响应保持`video created`及五个原字段。
- USER_UPLOAD只拒绝空类型/空文件名/缺失或0的size；不新增正数、类型枚举或TTL语义。精确LOCAL保留，其他模式归AUTO。
- 使用现有`id_counters`原子分配longId/shortId；视频/文件继续v_/f_，watch短ID保持本地日期counter、2022起算日数、序号/随机数十进制拼接转大写36进制。跨实例共用序号，不回退到进程内计数。
- 完整默认BSON含PUBLIC、owner/uploader、CREATED、空youTube/mediaInfo、storage/watch/link；空字段不写入，不产生_class。请求size不替代实际对象元数据。
- 文件扩展名保留旧FilenameUtils规则，key年月来自视频创建时刻在旧本地时区的月份。调用方负责显式提供旧JVM本地时钟。
- 保持初存file→存video→回写file.videoId/key顺序。任一写入失败立即传播，不自动事务化、补写、重试或删除；中间残留属于既有恢复需求，后续S12处理。

## 可复验依据

`UploadHttpContractTest`在真实旧MVC/隔离Mongo采集`serverless/contracts/upload-create/cases.json`，86组固定输入对照完整响应与video/file BSON；只归一服务端日期和本地月份，固定合成ID，未调用云服务。覆盖空/畸形正文、重复字段、数字/布尔/容器转换、Long/Integer边界、内部DTO对象绑定、未知字段、文件名后缀、负size、LOCAL及默认文档。

Python同一份样本经ASGI和真实独享Mongo逐字段比较，另验证三处写失败、默认不开路由、401先于绑定、真实共享ID创建两条记录。短ID并发/跨日期/序号扩宽另用真实Mongo测试。已有较广编码矩阵不等于本批上传各种Content-Type、表单及全端体验都已实际验收。

本批不包含上传凭证、OSS直传/完成确认、原片可靠受理、MD5去重、转码或完整上传客户端验收。S10.a.2的MD5/AUTO/LOCAL故障样本仍需后续补齐；S10.b本批只交付普通创建子项。回退本批提交即可撤销隔离能力，无生产配置或数据变更。

本地实际验证：Java18passed/0skipped及Checkstyle；Python1030passed/2项Windows符号链接权限用例未跑/0skipped/1既有Starlette warning，Ruff/架构/500行/空白通过。Linux必要CI按精确提交另外记录。
