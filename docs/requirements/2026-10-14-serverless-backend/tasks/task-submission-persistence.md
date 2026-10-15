# S12.a 提交持久化故障基线

目标：先固化现有FcTask在写库/认领/派发之间的恢复能力，再选择可靠唤醒机制。使用手工装配仓储/提交器、真实随机回环Mongo、mock外部客户端及回调，无Spring上下文/定时扫描。

故障点一：createIfAbsent真实落库后、updateCurrent认领前，合成异常终止；回读CREATED完整payload/attempt/deadline，重建对象可同attempt安全提交一次。
故障点二：已写SUBMITTED、客户端收到提交后抛合成异常；回读完整持久化状态，重建后立即submit被阻止，不二次派发、不改变deadline或attempt。

只是对象重建与方法故障注入，不是操作系统崩溃、真实云受理或自动扫描恢复验收。不增加生产字段/索引/队列，不声称解决严格零空扫：当前恢复依赖定时扫描，outbox无可靠唤醒也不完整。生产Mongo拓扑仍需单独核实。

状态：JDK21/真实自建回环随机端口Mongo，FcSubmissionPersistenceTest 2passed/0failed/0error/0skipped，Maven validate/Checkstyle通过。源码/target/db/log均系统临时目录，finally停止Mongo并清理；测试随机库finally删除。无Spring定时任务或真实云调用。回退仅撤新增测试/文档，无任务所有者或调度改变。
