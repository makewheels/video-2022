# S11.a.1 / S11.c.1 LOCAL封面登记

显式隔离后端可选择装配`POST /cover/local/createCover`与`POST /cover/local/finishCover?coverId=...`，共用既有身份和同步日志周期。必须同时提供对象凭证能力与明确的访问前缀；默认不安装，不切生产入口。

## 旧行为与实现

- create先检查视频存在，再执行视频存在/用户存在/uploader归属检查，然后重新读取视频。ownerId不作为权限依据；旧服务不检查视频LOCAL模式、READY状态或扩展名白名单，不能新增限制。
- 缺失/空/Java空白扩展名默认jpg；其他原样保留，包含大小写。按原视频创建日期的系统时区生成年月路径，沿用现有file/cover ID和对象目录规则。
- 保持file首次保存→cover保存→file回写key→请求STS的顺序。STS失败/返回null时记录已存在，不能自动回滚或重放；null凭证仍为code34。
- finish先读取封面，再检查视频/uploader；不校验提供方或对象存在，也不读取远端元数据。顺序是封面READY/finishTime保存→文件存在则保存READY→视频coverId保存。文件记录缺失仍挂接封面；重复完成会更新时间，不新增幂等规则。
- 完成不改变视频状态，不触发就绪回调，不提交云MD5、媒体探测、转码或截帧。重复创建使用新ID生成两份登记，不能偷偷改成复用旧封面。
- 普通层只依赖值/端口；Mongo读取保留构造默认、映射字段和ObjectId转换。保存沿旧覆盖写入及空字段省略；Video内的动态JSON与Cover.result保留自身null，不能按普通对象空字段一起删掉。

## 验收与范围

旧`LocalCoverHttpContractTest`在真实MVC/独享Mongo生成25组HTTP/完整前后BSON/请求与异常日志/凭证调用计数；含创建/完成、AUTO、缺失文件、非LOCAL提供方、空扩展名/大写、错误输入、鉴权和STS失败。rich-video样本覆盖帧率、BSON Long时长、动态JSON null与未知映射字段省略。另固定不支持方法在身份拦截前通用错误、不记请求日志，OPTIONS仍受保护。

Python经真实ASGI/独享Mongo逐项对照，另验证共享生命周期、显式开关、三处创建写失败、完成部分写失败、重复完成时间刷新和真实ID重复创建。不将合成STS称为实际云凭证或完整客户端验收。

本批仅封面登记；S11仍缺转码档位/媒体信息登记、OSS目录与播放文件读取、完成后的就绪路径、CLI侧车/端到端及真实FC。完整历史BSON类型矩阵和并发覆盖写行为仍需后续调查，不宣称已验证任意历史记录或新增事务保证。

本机最终验收：Python3.12.13完整1123passed/0skipped/1既有Starlette弃用warning，Ruff/架构/500行/空白通过；JDK21/独享回环Mongo旧Java HTTP2项及既有封面服务1项全部通过，Maven validate通过。系统临时目录保存依赖、构建、合成样本生成和Mongo数据，结束后关闭独享进程并清理。真实STS、FC和旧CLI尚未验。

回退本批即可撤销显式封面能力；未修改Java业务实现、生产配置、数据库或云资源，不需要恢复旧备份。

接续补漏：存在性查询允许null键并按旧Mongo查询返回存在/不存在，与findById/save的非null ID校验分开。省略videoId的旧HTTP封面/档位两个新增样本均code22；保留实体读取null ID的原错误。补真实Mongo null键查询测试后，Python完整1288passed/0skipped，Java两类HTTP共3项及validate通过；无生产变化。
