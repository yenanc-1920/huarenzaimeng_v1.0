# V4 运营商支持批次与预设目录前滚边界

状态：`STATIC_CONTRACT_ONLY/NOT_RUN`。本文件不授权运行真实MySQL、创建真实运营商成员、连接Reloadly或写入生产资料。

1. V4必须在V1→V2→V3之后执行；V3黄页三表及其数据不得改写或回填。
2. 仅在独立、无真实数据的MySQL 5.7测试库验证四张新表及`hz_quote`三个可空扩展列。扩展阶段保持可空用于旧行兼容；应用新写入必须三字段齐备。旧行任一目录字段为`NULL`时，MyBatis读取须稳定失败关闭为`QUOTE_REQUOTE_REQUIRED_LEGACY_RECORD`，不得拆箱NPE、伪造版本、推进订单或产生支付/外部副作用；读切和非空收缩须另立迁移并取得证据。
3. 真实首批成员状态保持`PENDING_OPERATOR_BATCH_APPROVAL`。测试夹具只能使用`SYN-*`代码和`APPROVAL-E3-SYNTHETIC-ONLY`，不得写入或推断首批真实运营商成员。
4. 前滚失败立即停止，保留Flyway水位、schema diff、命令、stdout/stderr/exit code、时区/sql_mode/字符集、文件SHA-256和失败语句；DDL不可逆时销毁隔离测试库后从干净快照重建，不手工删表伪装回滚。
5. `ExecutionStatus`固定为`PASS/FAIL/STOPPED/BLOCKED/NOT_RUN`；静态解析、退出码0或内存测试不得证明MySQL事务、约束、并发和恢复已通过。
6. 后台目录写入在真实`AuthorizationRef`及最终A130角色映射缺失时资格为0；测试令牌不得充当真实授权。
