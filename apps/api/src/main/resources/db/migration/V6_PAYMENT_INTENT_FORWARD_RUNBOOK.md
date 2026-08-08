# V6 LOCAL_SYNTHETIC PaymentIntent前滚候选

状态：静态候选；真实MySQL执行`NOT_RUN`。

## 前置门禁

- 只允许在独立测试库验证；生产、共享库和真实资金数据资格为0。
- 固定D3-03、D3-05、D3版本清单摘要，并保存迁移文件SHA-256、Flyway版本、MySQL版本和执行主体授权引用。
- 迁移前记录`hz_order`、`hz_semantic_action`、`hz_command`、`hz_outbox`对象计数与schema摘要；任一缺失或版本漂移即停止。

## 前滚与失败处置

1. 由Flyway按V6执行；不得手工跳号或修改已执行迁移。
2. 验证`hz_payment_intent`主键、三组业务/语义唯一约束及两个外键均存在。
3. 只使用合成Order执行首次创建、精确重放、单键冲突和父Order CAS静态契约；不连接微信，不形成PaymentAttempt或资金事实。
4. DDL失败、锁等待超出获批窗口、约束不完整或对象计数异常时停止应用启动并保全错误、schema及版本证据；不得标记迁移成功。

## 不可逆边界

MySQL 5.7 DDL可能发生隐式提交。本候选不提供自动回滚或删除表脚本；失败后只能在获批的隔离测试库按备份/重建流程处置。未经单独授权不得对真实库执行。

## Evidence字段

`MigrationVersion/ArtifactDigest/SchemaBeforeDigest/SchemaAfterDigest/ObjectCountsBefore/ObjectCountsAfter/StartedAt/FinishedAt/ExecutorRef/AuthorizationRef/Result/ErrorRef`。

## 本地证据包与聚合规范

- 证据根：`apps/api/target/m2-payment-intent-evidence/`；清单：`evidence-package-index.json`。
- 实现清单以仓库相对路径逐行写为`path|UPPERCASE_SHA256`，使用`.NET StringComparer.Ordinal`或等价UTF-16逐码元升序；UTF-8无BOM编码，行间LF，末行无LF。不得把人工列出顺序称为Ordinal。
- 证据清单使用同一Ordinal规则；每次执行必须记录新的`ExecutionRunId/ExecutedAt/FixtureDigest/EvidencePackageId/EvidencePackageSha256`，不得继承上轮动态UUID或时间证据摘要。
- 可复制复算入口由交付消息给出；任何路径、文件SHA、排序、换行或末行规则不一致即停止复核。

## 唯一约束竞争分类候选

已知约束：`PRIMARY`、`uk_hz_command_subject_idempotency`、`uk_hz_command_subject_semantic`、`uk_hz_command_subject_endpoint_idempotency`、`uk_hz_payment_intent_business`、`uk_hz_payment_intent_business_key`、`uk_hz_payment_intent_semantic`。唯一约束竞争后必须回读规范PaymentIntent和接收键：仅同双键、同指纹、同业务/语义键可精确重放；单键、换键或异指纹稳定为`IDEMPOTENCY_CONFLICT`；未知约束或无法回读同样失败关闭为受控冲突，不得生成别名、推进父版本或产生资金/外呼。该文本及纯内存模拟不证明真实MySQL锁、死锁、回滚、跨进程或崩溃恢复行为，上述能力继续`NOT_RUN`。
