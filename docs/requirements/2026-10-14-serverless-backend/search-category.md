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
