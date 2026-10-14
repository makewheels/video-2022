# S05.a 公共视频列表契约台账

状态：源码及客户端核对完成；动态边界用例和 Python 业务实现未完成；离线对照工具见 [S05.b](response-comparison.md)（本阶段待 CI）。本页不表示 S05/S07 全部通过。源码基线 `53f275b8`，核对日期 2026-10-14。

## 请求与鉴权

`GET /video/getPublicVideoList`，query：`skip`、`limit` 均为必填 Java int，Controller 没有默认值、范围校验或上限；`keyword` 可省略。迁移不能擅自加分页上限、把缺失参数默认成 0/20，或把 limit=0 改成空列表。负数、0、溢出、重复参数及无法转换参数的实际 HTTP 行为仍须固定夹具实测，不能凭框架经验标通过。

该路由不在 CheckTokenInterceptor 的登录必需列表，也不属于 `/api/v1/**` OAuth 范围。PutTokenInterceptor 仍对全部请求调用 UserService：优先读取 `token` 头，空值才取 query token 的首值，再解析用户。匿名访问不要求登录；携带无效 token/数据库故障的动态行为仍待验，不为此路由新增 Bearer 要求。

依据：`server/video/src/main/java/com/github/makewheels/video2022/` 下 `video/VideoController.java`、`springboot/interceptor/InterceptorConfiguration.java`、`springboot/interceptor/token/PutTokenInterceptor.java`、`user/UserService.java`。

## 查询、排序与副作用

VideoRepository.getPublicVideoList/countPublicVideos 使用相同过滤：`visibility == PUBLIC`。没有额外 status、过期时间、上传者或 category 条件。keyword 非 null 且 trim 后非空时，对 trim 后字符串执行 Pattern.quote，匹配 title/description/tags，大小写忽略；是字面搜索，不能直接把用户字符串交给正则。空/全空白 keyword 不过滤。

列表按 createTime 降序，再 skip/limit。没有同时间第二排序键；对照夹具应避免同时间排序歧义，不能悄悄新增 id 排序。total 单独 count，非单一事务快照；并发写入时 list/total 可不同步，不自动把 total 改为页内条数。

列表用例未增加观看次数，但 HTTP 管线存在副作用：RequestLogInterceptor 对该路由在 afterCompletion 保存 Mongo 请求日志；GlobalExceptionHandler 保存异常日志。后续影子对照必须在受控夹具或禁用影子副作用的装配下执行，不能仅因 GET 就重放生产全链路并称零副作用。

## 响应与空值

成功为 Result.ok：code=0、message="success"、data={list,total}；total Java long，list 为 VideoVO 数组。字段声明及原单条脱敏观察见 [observed-shape](../../../serverless/contracts/public-video-list.observed-shape.json)。该观察只有一次成功响应的字段类型，不能证明所有字段永远非空。

| 字段组 | 源码处理和待保持行为 |
|---|---|
| id/userId/title/description/type/status/visibility/tags/category/uploaderId | BeanUtils 同名属性复制，不把名称相似字段自行改名映射；type/userId 等观察到 null 的字段仍需保留空值语义 |
| coverUrl | 收集非 null coverId，批量读取 Cover，生成 2 小时签名 URL，再按 coverId 查询 map；缺记录可为 null。签名字符串随时间变化，不能要求逐字相同或保存到 Git |
| uploaderName/uploaderAvatarUrl | 按去重 uploaderId 逐用户读取；昵称优先，空昵称则手机号长度至少 7 时前三位+****+后四位，否则“未知用户”；缺用户也为“未知用户”。头像沿用用户值 |
| createTime/createTimeString | Date 同名复制；字符串 DateUtil.formatDateTime。JSON 日期编码、时区、null 日期行为需固定环境实测，不用 Python 默认 ISO 格式替代 |
| watchCount/watchId/watchUrl/shortUrl | 从非 null watch 嵌套对象映射；不新造播放 ID/路径，不更改历史链接 |
| duration | 从非 null mediaInfo.duration 映射；不自行换单位或把 null 改为 0 |
| youtubePublishTime/youtubePublishTimeString | 非 null YouTube 视频嵌套 publishTime 时设置字符串；Date 字段不能只因名称就推定也被赋值，需样本确认 |

VideoVO 还声明 youtubePublishTime 等字段；完整 JSON 空字段包含策略、列表空值与日期序列化须动态确认。通用异常处理源码返回 code=1/message="未知错误"/data=null，并保存异常；无 @ResponseStatus 不能直接认定参数错误是 FastAPI 默认 422 或 Java 默认 400，须实际请求记录 HTTP status、Content-Type 与 body。副作用失败也可能影响错误返回，保持未验标记。

## 客户端调用覆盖

| 客户端 | 调用范围 |
|---|---|
| Web HomePage | limit=24，skip=(page-1)*24；非空 keyword 才发送；读取 data.list/data.total |
| Web RecommendedVideos | skip=0/limit=12 |
| Web ChannelPage | skip=0/limit=20 后在客户端筛 uploaderId；当前接口没有 uploader query 条件，不顺手改频道行为 |
| CLI video public | 默认 skip=0/limit=20，非空 keyword 发送；JSON 或表格读取 list，表格使用 id/title/uploaderName/watchCount |
| Android VideoApi/HomeViewModel | Retrofit skip/limit/可空 keyword；首页 pageSize=20，以当前 videos.size 作为下一页 skip |
| Android ChannelViewModel | skip=0/limit=50，客户端按 uploaderId 筛选 |
| iOS HomeScreen/ChannelScreen | 首页 pageSize 和 videos.count 分页；频道 skip=0/limit=50，客户端按 uploaderId 筛选 |
| Console/ai-agent | 本次按该路径全仓搜索未发现直接调用；不推断未来无需验其他接口 |

## 下一小阶段：固定夹具与脱敏对照

S05.b 在既有 serverless 测试体系增加离线响应对照工具及意义用例，不主动请求生产、不存完整签名 URL/个人信息；输入来自受控测试响应。工具保留 key 集、null/非 null、类型、数组顺序、code/HTTP status；可变化字段必须显式限定，不能忽略整个视频对象或把所有字符串都视作一致。

需要覆盖：匿名/旧 token、必填参数和异常 HTTP、空结果、正常分页、literal `.*`/中英文/空白关键词、大小写/tags、PRIVATE/UNLISTED 排除、PUBLIC 非 READY 保留、缺封面/缺用户/缺 watch/mediaInfo、固定时区日期、历史链接、签名 URL 可变及不泄密。签名正确性和实际播放留 S07/S08，字段形状比较不能替代权限签名校验。

本阶段仅文档，未新增生产规则、未修改 API/客户端/数据库。回退本 PR 只移除台账，运行时不变。
