# S06.d1 旧HTTP身份上下文与请求日志契约

依据PutTokenInterceptor、CheckTokenInterceptor、RequestLogInterceptor、RequestUtil源码及RequestContextHttpContractTest的真实Spring管线/隔离Mongo验证；不修改运行代码，不装配Python路由，不访问生产。

## 身份选择与完成清理

- token先取大小写不敏感header的首值；只有null/空串才回退Servlet参数map中token首值。空格不trim，重复query首值无效时不试第二值；错误非空header覆盖正确query。不是Bearer解析。
- PutToken在前，对非null用户放入UserHolder；受保护路由CheckToken再独立查询用户。不能假称旧管线只查库一次；两次读取间凭据轮换/删除并发仍未验。
- 受保护路由无效身份返回HTTP401、code401/请先登录，没有data字段；随后RequestLog尚未执行，因此这类拒绝不产生requestLog。公开路由业务失败仍经过日志。
- 成功、已处理的Controller绑定异常、受保护拒绝之后，本夹具线程UserHolder与RequestLogContext均为null；同线程后续请求可以正常验证身份。没有把异步请求/跨线程/数据库保存日志失败等路径也写成已通过。

## 请求日志实测

物理集合`requestLog`，文档含id/requestPath/request/response/startTime/endTime/timeCost。start/end均Date，timeCost为毫秒差。当前同步afterCompletion执行Mongo保存，不是可丢弃的后台任务。

| 字段/情况 | 当前行为 |
|---|---|
| request.url | Servlet requestURL，不含query |
| request.path/requestPath | 原requestURI |
| request.method/queryString | 原方法和原始queryString；不是重新编码参数map |
| request.headerMap | 原始header名→首值，不保留重复值数组 |
| request.userAgent | User-Agent首值 |
| request.ip | Servlet remoteAddr；本夹具X-Forwarded-For不覆盖它 |
| request.body | 未捕获，为null，包括成功POST和畸形JSON |
| response.httpStatus | 最终Servlet HTTP状态；业务code1/处理过的JSON绑定错误仍可能200 |
| response.body/headerMap | 未捕获，均null |
| /file/access、/heartbeat/add | afterCompletion按路径跳过保存并清理日志上下文；夹具验证绑定错误请求，不重放真实访问/心跳 |

HTTP返回与日志不能混为一谈：业务错误code不决定HTTP状态，受保护401不一定有日志。另有OAuth/admin/rate-limit路径未纳入本阶段。

## 已发现的日志安全边界

旧日志直接保存raw headerMap和queryString，包括合成token。测试明确证实这一点，但不表示应新增复制凭据的日志方案。本次只使用固定合成身份、独立临时库，未读取或输出真实日志/凭据。后续日志脱敏须独立评审，不能把当前日志称为已脱敏或在生产抓取真实token作为对照样本。

IP验证仅针对此Spring/MockMvc路径；真实代理/FC envelope、转发头策略、请求URL编码/多值丢失、大小写与大小限制还需外壳级对照。Python通用Request当前仍为简单字典，不能直接声称覆盖Servlet多值输入或真实网络来源。

## Python装配前门槛

需要显式请求作用域身份/元数据、顺序一致的鉴权/用例/错误映射/日志落库与清理、同步持久化失败语义，再比较FC和ASGI外壳；不能用进程全局身份或异步后台日志替代旧ThreadLocal/同步保存。只读用例本身没有这些能力，不应提前暴露业务路由。

回退仅删除测试/文档，无在线行为或数据变化。实际命令/结果见verification.md；S06完整HTTP、生产FC/Mongo及日志故障路径仍未验。
