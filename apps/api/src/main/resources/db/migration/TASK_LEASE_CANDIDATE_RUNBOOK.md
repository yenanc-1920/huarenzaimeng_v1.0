# 任务租约候选说明（NOT_RUN）

- `TaskLeaseStore` 定义 `register/claim/renew/release`；认领成功递增 `fencingToken`。
- InMemory 实现只用于确定性交错测试，不代表真实并发或持久化恢复能力。
- MyBatis 实现使用显式 `SELECT ... FOR UPDATE`、带旧 token 条件的 claim，以及 owner+token+未过期条件的 renew/release。
- MyBatis 路径忽略 worker 传入时间的业务语义，只在事务内读取 `CURRENT_TIMESTAMP(3)`；接口 `now` 仅保留给 InMemory 确定性测试并做非空校验。
- 崩溃后的重认领只允许在旧租约到期后发生；新 owner 获得更大的 token，旧 token 不得续租或释放。
- V2 与这些 SQL 尚未在 MySQL 5.7 执行；MyBatis 事务、锁等待、时钟精度、死锁和崩溃恢复均为 `NOT_RUN`。
- `FencedResultStore` 已增加领域结果、账务占位与 outbox 的同事务候选；只有当前 owner+token+未过期租约可写。
- 该候选仍未接真实资金端点，未选择队列，也未在 MySQL 执行；因此不代表 worker 或领域资金闭环。
- 不得连接云数据库或启用 `release-mysql` 来关闭本地候选验证。
