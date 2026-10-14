# S06.d3 ASGI多值元数据转换（未装配）

新增独立from_asgi_metadata能力，使用ASGI原始scope采集普通RequestMetadata。现有adapters/asgi.receive、http.Request和FC入口均未改变，不通过这一模块启用业务路由。

## 官方依据与实现

[ASGI HTTP规范](https://asgi.readthedocs.io/en/latest/specs/www.html)（2026-10-15查阅）要求保留有序重复header值，并分别提供已解码path、可选raw_path、原query_string及可选client。项目读取原header字节对（Latin-1映射）而非dict，参数使用锁定Starlette QueryParams.multi_items，raw query另外保留。

- 日志URI使用ASCII raw_path，不把已解码path再次编码/解码；URL以框架解析的origin加同一raw_path、去除query，避免把编码斜杠重复解释。
- peer只取ASGI client，不自行信任X-Forwarded-For；真实代理配置仍另验。
- raw_path或client缺失、raw URI非ASCII时固定MetadataMappingError，消息不含请求值，不编造原始URI或IP。
- POST表单/MIME multipart参数与Servlet的合并顺序尚未证明，固定错误拒绝该未支持转换，不能偷偷当成仅query参数；JSON POST只采集query且不消费body。

上述错误仅属于未装配能力的兼容门槛，不是新增生产请求校验。Starlette官方Requests页面本次取回502，未把该页面记为已读；ASGI规范与锁定本地Starlette接口/实际TestClient结果共同作为依据。

## 验证与限制

完整Python225passed/0skipped/1既有warning，Ruff/500行/空白通过。新增8项：真实Starlette TestClient的多值header/query→IdentityResolver组合、plus/百分号/空值与原query保留、编码URI不再解码、peer不被XFF覆盖、缺失或未知wire字段固定失败、表单未支持显式失败、JSON metadata不读取body。原型/黄金响应/Mongo回归同时通过。

没有实际FC或网络服务器验收，TestClient不是Uvicorn/真实代理证明。空query bytes不能区分没有问号与裸问号，原header大小写可能已被服务器规范化；根路径/挂载、IPv6/Host/代理、非ASCII原header/URI及非法编码尚未穷尽。不能称与Servlet所有输入完全一致，也不能从已折叠的FC header字典恢复原重复值。

后续继续FC能力调查、表单参数/外壳对照、完整鉴权/同步日志/异常装配；现有旧空token/匿名头像/raw日志问题保持单独记录，不顺带改规则。回退删除未装配模块/测试/文档，无在线入口或数据变化。
