# S06.j 用户HTTP应用与真实TCP

新增UserApplication和显式build_user_application装配，以及create_user_asgi工厂。
复用已有用例，不再只在各测试函数中临时复制业务路由。已装配能力覆盖验证码申请/
提交、token和公开用户读取、资料/频道读取、资料更新、client/session九条原路径。
使用真实时钟、secrets随机数字和持久化ID/token，profile、当地时钟、node及
数据库/头像签名适配器须显式提供，没有默认dev环境或生产连接。

required query保留首值，空字符串不当缺参；鉴权在绑定/业务之前。业务错误11/12
按旧VideoException路径保存exceptionLog，再返回原code/message/data；普通异常
保存异常日志并返回固定未知错误。未知token的普通查询仍直接返回13，不伪造
异常日志。旧枚举异常exceptionMessage为null，Mongo按原映射不写该字段；堆栈
是Python运行时堆栈，不冒充Java栈。数据库日志保留旧字段，本阶段只用合成身份。

## 实际验证范围

隔离ASGI验证完整申请→登录→资料→更新→再次登录及旧token失效，钱包不重置；
缺码/错码/生产111的异常及请求日志；缺参、重复参数首值、鉴权优先；观看身份；
异常日志写失败显式暴露，业务不重放。

另以独立Uvicorn子进程、真实回环TCP和http.client执行申请/登录/资料/client流程，
确认对端IP为127.0.0.1，不信任任意X-Forwarded-For；这比TestClient多覆盖真实
TCP/服务进程，但不代表FC/线上代理、CORS、冷启动、跨云Mongo已验。

## 尚未开放的边界

现有fc_handler.handler和web:app仍为只读原型；新工厂没有注册到生产入口。
头像创建/STS/完成、实际OSS签名、客户端CrossOrigin、表单参数合并、非UTF8
JSON及完整FC元数据仍待分阶段验收。未授权开放Mongo公网、迁库、扩RAM权限或
生产切流。未知业务路径返回独立应用404，不代理Java，也不声称完整后端迁移。

本阶段回退新增应用、装配、ASGI/异常日志模块及测试；没有线上资源或业务数据变化。

2026-10-15本次实际：Python3.12.12/临时Mongo8.3.7，完整回归328passed、
2deselected（既有Windows符号链接权限）、1warning，Ruff通过；随后新增4份旧Java
读取黄金样本的应用级对照，最后focused19passed（含真实TCP）、Ruff通过。
不同轮次不相加为一次全套结果；最新全部用例仍须Linux CI确认。
