# S11.b.1 只读产物校验（供S13后续复用）

新增普通`OutputValidation`、普通manifest声明值与显式`OssOutputObjects`读取适配器，供后续LOCAL完成和云回调复用；尚未安装完成HTTP、登记写入或生产任务。

## 规则与证据

- 保持旧顺序：playlistKey匹配→清单存在/非空→包含ENDLIST→至少一段→逐段安全文件名/实物非空→init→可选manifest对象集合/重复/大小。
- 复用S08已有`hls`行拆分、Java trim与init解析，不重新发明一套MAP解析。原解析对URI之后带属性的行为也按真实旧结果保留，不在迁移中修改。
- 返回旧错误文本和相同对象调用顺序；对象读取失败的业务位置不同会分别表现为清单不存在、分片缺失或manifest读取失败。
- 普通层不依赖SDK、HTTP、Mongo或配置。适配器只对清单/侧车读取文本，UTF-8非法字节按旧Java替换；分片只取响应元数据后关闭流，不缓冲视频内容。读取/元数据异常也finally关闭结果。
- 对象键按迁移协议的POSIX路径处理；任意历史Windows路径、任意未解码JSON/BSON类型以及超大清单资源界限仍需单独调查，不把普通类型端口当完整输入绑定。

真实旧Java `OutputValidationContractTest`使用手工装配的真实validator和mock FileService，无Spring上下文/Mongo/云调用，生成26组合成样本；覆盖TS、fMP4/init、CRLF、路径越界、缺失/空对象、manifest缺项/重复/越界/错误大小和读取失败，并记录调用顺序。Python通过显式模拟SDK bucket逐项对照，另测异常关闭流、UTF-8和元数据不读取正文。

本机Python3.12.13完整1186passed/0skipped/1既有Starlette warning，Ruff/架构/500行/空白通过；Java契约1项及M3u8工具6项全部通过，Maven validate通过。Java源码/构建/样本生成、Python依赖与完整套件Mongo均任务独享系统临时目录并清理。这里没有真实OSS/FC或旧HTTP完成验收。

下一批继续只读目录/清单/侧车与登记记录构建，再实现完成写入、全档就绪与副作用恢复。就绪会触发存储类型和Webhook，依赖对应可靠执行/单写入所有者；不能先接空回调绕过。
