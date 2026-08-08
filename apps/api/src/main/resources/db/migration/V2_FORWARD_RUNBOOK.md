# V2前滚运行说明（NOT_RUN）

此迁移未在隔离MySQL 5.7执行，不得直接用于云环境。`release-mysql`发布前必须：

1. 在隔离、生产兼容MySQL 5.7恢复副本上执行V1→V2；普通/Mock profile不得启用Flyway。
2. 执行前确认`hz_quote`与`hz_order`均为空；如非空，先形成有批准证据的`quote_ref/order_ref → ProjectSubjectRef`映射迁移，禁止以默认主体回填。
3. 验证MySQL严格模式、InnoDB、JSON、复合唯一索引长度、DDL锁时长和回填金额差异均为0。
4. 执行迁移后验证主体隔离、同主体命令/幂等/语义唯一、跨主体同键可并存、aggregate CAS和V1事实/账务行数摘要。
5. 只有隔离验证EvidencePackage为PASS且Release授权有效时，才可显式启用`release-mysql`；失败只允许修订后前滚，不直接改已执行V2。

任务租约认领、续租和fencing执行逻辑当前为`NOT_IMPLEMENTED`；表字段不构成能力完成证据。
