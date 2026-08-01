# V3 自营静态黄页迁移边界

`V3__add_self_operated_directory_content.sql`当前只经过静态契约检查，尚未在隔离MySQL 5.7执行，状态为`NOT_RUN`。不得把Maven测试或内存Store结果写成MySQL迁移、事务、唯一约束或崩溃恢复已验证。

后续必须在获批的生产兼容MySQL 5.7隔离副本中：

1. 记录精确内核、`sql_mode`、字符集、排序规则、时区、Flyway版本和迁移前schema摘要；
2. 从V1→V2→V3执行，核对`hz_content_item/hz_content_command/hz_content_audit`结构、索引和Flyway水位；
3. 验证同CommandId/IdempotencyKey重放、异参冲突、ExpectedAggregateVersion CAS及审计版本唯一；
4. 在内容更新后、审计插入前、命令记录前设置具名中断点，确认事务回滚或按原键安全前滚；
5. 验证投诉待查后公开查询为0、后台内容与只追加审计仍可追溯；
6. 保存脱敏原始输出和SHA。任一失败时停止，不降级发布资格、版本守卫或审计不变量。

MySQL 5.7对`CHECK`约束不作为可靠执行门禁；`SELF_OPERATED_CHINA_COMPANY`仍由应用写入守卫、列值审计和测试共同约束，后续升级到支持强制CHECK的内核时再验证数据库级增强。
# V3 前滚与失败门禁（补充）

- 只允许在一次性、独立、无真实数据的 MySQL 5.7 测试库按 V1→V2→V3 前滚；执行前保存 server/Flyway 版本、schema SHA-256、字符集、时区、sql_mode、操作者授权引用与开始时间。
- V3 未设计降级脚本；DDL 一旦提交，禁止用手工删表伪装回滚。失败时立即停止后续迁移，保留 Flyway history、DDL/stdout/stderr/exit code、失败语句、库实例引用和执行时间；销毁独立测试库后只能从干净快照重新前滚。
- 共享库、生产库、含真实内容的库、授权不完整或证据捕获不可用一律 `STOPPED`。静态检查、内存测试和退出码 0 均不得替代独立测试库的结构、约束及事务验证。
- 证据包至少包含：`ExecutionStatus`、授权引用、环境/实例引用、V1/V2/V3 SHA-256、迁移前后 Flyway 水位、schema diff、索引和唯一约束、时钟/时区、命令、完整输出路径及 SHA-256、失败处置、操作者与复核者。
