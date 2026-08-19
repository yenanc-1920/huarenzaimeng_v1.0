# MySQL 5.7 V1→V24 临时库验证方案

## 1. 证据边界

本方案只验证候选迁移在独立 MySQL 5.7 临时库中的结构演进，不证明开发、测试、预发或生产业务库可迁移，也不构成生产发布证据。当前批次只完成脚本和静态合同测试，不连接数据库。

脚本：`apps/api/scripts/mysql57-validation/Invoke-MySql57V1ToV24Validation.ps1`

迁移基线必须是连续且唯一的 V1–V24。脚本运行时记录 Git commit、脚本 SHA-256、V1–V24 清单 SHA-256、RunId、临时库名、`@@server_uuid`、`VERSION()`、Flyway 终态计数、表计数和 UTC 时间。

## 2. 强制安全边界

- 操作者必须显式提供 8–32 位小写字母/数字 RunId；库名只能由脚本生成：`hz_verify_<runid>`，禁止自定义覆盖。
- 库名包含 `huarenzaimeng/prod/stage/test/dev/it_vnext` 时拒绝。
- 默认 `DryRun`：零连接、零写入，只输出计划。
- `Preflight` 和 `Diagnose` 只执行 `SELECT`；`Execute` 必须给出精确确认令牌。
- 凭据仅来自 MySQL defaults 文件及 Flyway config 文件；脚本不内置、不回显密码。占位值、重复或缺少 user/password 键、空账号、ACL 允许 Everyone/Authenticated Users/普通 Users 读取时，在首次连接前拒绝。两个文件中的账号经去除首尾空白及成对引号规范化后必须完全相同，保证 `SHOW GRANTS` 检查者就是 Flyway 实际迁移者。JDBC base URL 不得包含库名、查询串或凭据，且必须与 MySQL host/port 完全一致。
- 脚本不创建数据库。目标临时库须由受控 DBA 步骤预创建，再交给只拥有该临时库精确迁移权限的独立账号；脚本不接受全局高权限来换取自动建库。
- 脚本不删除库，不自动清理，不修改 checksum，不执行 Flyway clean/baseline/repair。
- Execute 首次写入前执行 `SHOW GRANTS FOR CURRENT_USER()`：除全局 `USAGE` 外，只允许本次 `hz_verify_<runid>` 库上的 `ALTER, CREATE, DELETE, INDEX, INSERT, REFERENCES, SELECT, UPDATE`；任何其他库权限、`*.*` 非 USAGE、ALL PRIVILEGES、SUPER 或 GRANT OPTION 均拒绝。
- 任一步失败立即停止，不自动重试；失败临时库原样保留，供只读诊断。

## 3. 场景矩阵

| 场景 | 第一步 | 升级 | 幂等检查 | 终态 |
|---|---|---|---|---|
| EMPTY | 使用预创建空临时库 | V1→V24 | 同一授权运行再次执行 target=24 | 24/24 成功，0 失败 |
| V14 | 预创建空库后 V1→V14 并核验 current=14 | V15→V24 | 同上 | 24/24 成功，0 失败 |
| V21 | 预创建空库后 V1→V21 并核验 current=21 | V22→V24 | 同上 | 24/24 成功，0 失败 |

再次执行 target=24 是同一验证运行中的预定幂等检查，不是失败重试。任何失败后都不得再次执行 `Execute`。

## 4. 使用流程

### 4.1 Dry-run（零连接）

```powershell
& .\apps\api\scripts\mysql57-validation\Invoke-MySql57V1ToV24Validation.ps1 `
  -RunId a1b2c3d4 `
  -Scenario EMPTY
```

### 4.2 只读预检

```powershell
& .\apps\api\scripts\mysql57-validation\Invoke-MySql57V1ToV24Validation.ps1 `
  -RunId a1b2c3d4 `
  -Scenario EMPTY `
  -Action Preflight `
  -MysqlDefaultsFile C:\secure\mysql57-client.cnf
```

预检必须确认：目标临时库不存在、服务器 UUID 唯一可读、版本严格为 5.7.x。

### 4.3 受控预创建数据库和迁移账号

`Preflight` 成功后，由独立 DBA/平台操作在相同 server UUID 上创建脚本派生的空库，并创建专用迁移账号。该步骤不属于脚本，也不得把 DBA/root 凭据交给脚本。

迁移账号的授权集合必须精确等价于：

```sql
GRANT ALTER, CREATE, DELETE, INDEX, INSERT, REFERENCES, SELECT, UPDATE
ON `hz_verify_a1b2c3d4`.* TO '<dedicated-migration-user>'@'<restricted-host>';
```

此 SQL 仅说明授权合同，不包含密码，也不授权任何其他库。MySQL 的 `CREATE DATABASE` 是全局能力，不能在 grant 层可靠表达“只创建某一动态命名临时库”，因此脚本明确不持有该能力。

### 4.4 真执行（本批次不执行）

除上述参数外，还需：

- `-Action Execute`
- `-FlywayConfigFile C:\secure\flyway.conf`
- `-JdbcBaseUrl jdbc:mysql://host:3306`
- `-ConfirmationToken EXECUTE_MYSQL57_VERIFY:hz_verify_a1b2c3d4:EMPTY`

Execute 会先验证两个凭据文件、账号一致性、ACL、命令、JDBC/MySQL 端点、确认令牌，再发生首次连接；任一不完整条件均保证零连接。连接后再用 `CURRENT_USER()` 核对 MySQL 实际认证账号与配置迁移账号完全相同，并由该账号只读核对库存在、数据库/server UUID/5.7 身份和 SHOW GRANTS，全部通过后才允许 Flyway 首次写入。

三个场景必须使用不同 RunId 和不同预创建临时库，不得复用失败 RunId/库。

## 5. 非事务 DDL 中断诊断与受控恢复

MySQL 5.7 DDL 可能非事务化。若进程中断、连接重置或 Flyway 失败：

1. 立即停止，保留临时库；禁止继续 migrate、手工补列/建表或修改历史表。
2. 使用同 RunId 和场景执行 `-Action Diagnose`，只读取 `DATABASE()`、`@@server_uuid`、版本和 Flyway 历史摘要。
3. 冻结 Git commit、脚本 SHA、迁移文件 SHA、数据库身份、错误日志、已存在表/列与 Flyway failed row，形成新的缺陷记录。
4. 在代码层修复候选并重新完成静态门禁；不得修改已发布旧迁移。
5. 获得新的明确授权和新 RunId 后，在全新临时库从场景起点重新验证。失败库继续保留，是否人工删除由操作者另行决定，脚本永不删除。

禁止把“失败库经手工修补后可继续”作为恢复方案，也禁止把临时库 PASS 外推为生产 GO。

## 6. 终态判定

PASS 必须同时满足：

- `DATABASE()` 等于脚本生成的临时库名；server UUID、MySQL 5.7.x 身份在整个运行中一致。
- V1–V24 共 24 个版本，版本唯一，current=24，success=24，failed=0。
- V22 的同意/注销/自审对象、V23 的履约号码对象、V24 的支付身份与五项预支付字段均存在。
- Flyway validate 成功；第二次 target=24 不新增历史记录。
- 证据 JSON 不含密码、AppSecret、连接凭据或业务数据。

任一条件缺失均为 FAIL/NOT_RUN，不允许解释为“基本成功”。
