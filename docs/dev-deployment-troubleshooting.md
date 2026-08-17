# DEV 环境部署问题与处理基线

本文只记录已实际出现、可复现的开发环境部署问题。每次部署前先执行本地发布门禁；云端只构建已经通过门禁的固定提交，不再承担首次发现问题的职责。

## 部署前固定顺序

1. Pull Request 或人工检查执行快速门禁：启动阻断测试、后台契约与构建、小程序契约与构建。
2. 合入 `dev` 前执行本地完整门禁：后端完整测试、release/dev 制品打包、本地 MySQL 5.7 一次性数据库迁移、JAR 启动和健康检查。
3. 完整门禁必须满足：V1-V14 全部成功、失败迁移为 0、最高版本为 14、`/actuator/health` 为 `UP`。
4. GitHub Actions 对固定提交重复完整代码门禁并构建 `Dockerfile.dev`。
5. 只有 GitHub 门禁成功后才推进 `deploy/dev`；微信云托管开发服务只监听 `deploy/dev`。
6. 云端失败不自动反复重发。记录首个根因，回到本地修复并重新完整验证。

本地入口：

```powershell
powershell -ExecutionPolicy Bypass -File tools\Invoke-DevReleaseGate.ps1
```

已经在同一轮执行并取得 `DEV_DEPLOYMENT_GATE_GO` 时，可仅继续一次性数据库与启动验证：

```powershell
powershell -ExecutionPolicy Bypass -File tools\Invoke-DevReleaseGate.ps1 -SkipCodeGate
```

`-SkipCodeGate` 不能跨提交、跨工作树或跨任务复用。

## 已知问题记录

| 编号 | 现象 | 根因 | 固定处理 | 防复发门禁 |
|---|---|---|---|---|
| DEV-ENV-001 | Docker 构建在容器内重复执行完整 Maven 测试，耗时长且偶发找不到 Surefire 临时摘要 | 测试与镜像打包耦合，同一提交重复执行重测试 | 完整测试移到 GitHub 门禁；Dockerfile 只消费通过门禁的源码并打包 | `BuildSelectionContractTest` 锁定 Dockerfile 不再执行 `clean test` |
| DEV-ENV-002 | `Cannot subclass final class ...Controller`，服务不断重启 | Spring AOP 需要 CGLIB 代理被标记事务的 final Controller | 事务边界放在可代理类/方法，启动 smoke test 使用真实 profile 组合装配 | `DevProfileApplicationSmokeTest` |
| DEV-ENV-003 | `RELEASE_PROFILE_MUST_NOT_MIX_WITH_TEST_PROFILES` | 开发服务使用 `release-mysql,local-mysql`，旧校验把所有多 profile 视为测试混用 | 只允许固定开发组合；mock/test 等 profile 仍失败关闭 | `ReleaseSecretBoundaryValidatorTest` |
| DEV-ENV-004 | `No qualifying bean of type Flyway ... found 2` | 隔离迁移 Flyway 与 release Flyway 同时进入开发上下文 | 开发 Runner 显式注入 release Flyway；隔离 Flyway 不参与普通 API 链 | `DevelopmentFlywayMigrationRunnerTest`、启动 smoke test |
| DEV-ENV-005 | Maven 依赖解析慢或网络失败 | 首次运行无稳定本地缓存，云构建边下载边测试 | 使用项目专用 Maven 缓存；本地完整门禁先完成依赖解析 | 本地门禁失败即禁止推送 |
| DEV-ENV-006 | JUnit 完成业务测试后清理系统 Temp 报 `AccessDeniedException` | Windows 临时目录遗留权限/进程上下文不一致 | 同一提交完整测试只跑一次；项目缓存与运行输出放入工作区 `.runtime`；不重复执行相同门禁 | 快速/完整门禁分层，禁止 Docker 再跑测试 |
| DEV-ENV-007 | 本地 JDBC 报参数串是“过长标识符” | PowerShell 将 `$database?` 误解析为变量名的一部分，库名被吃掉 | JDBC 字符串使用 `${database}?` 显式变量边界 | 一次性真实 MySQL 启动门禁 |
| DEV-ENV-008 | 健康接口先 UP，但 Flyway 历史仍为 0 | Tomcat 已监听时异步迁移尚未完成，过早读取历史表 | 健康 UP 后继续轮询 Flyway：版本迁移 14、失败 0、最高版本 14 | `Invoke-DevReleaseGate.ps1` 双阶段等待 |
| DEV-ENV-009 | 历史表尚未创建时轮询直接终止 | 用“查询不存在表”的异常作为等待条件 | 先查 `information_schema.tables`，存在后再读历史 | 同上 |
| DEV-ENV-010 | 云托管显示镜像发布成功，但容器持续 502/503；日志为 `Access denied for user 'huaren_app'` | 服务环境变量中的应用账号密码与云 MySQL 当前密码不一致；镜像发布成功不等于应用启动成功 | 在云 MySQL 重置 `huaren_app` 密码，并将同一个值更新到对应服务的 `SPRING_DATASOURCE_PASSWORD`；不得把密码写入仓库或日志 | 发布后必须同时通过容器健康检查和真实数据库读接口；四环境密码分别维护，不复制旧环境密文 |
| TEST-ENV-001 | 本地真实 MySQL 门禁先看到健康 UP，但迁移历史尚未到 V14 | Web 端口就绪早于一次性迁移完成 | TEST 门禁分别等待迁移历史达到 14/0/14，再检查健康；不得把首次健康响应当成迁移完成 | `Invoke-TestReleaseGate.ps1` 固定双阶段等待，且断言开发预置数据为 0 |
| TEST-ENV-002 | 完整测试出现大量 JUnit/Mockito `AccessDeniedException`，或仓库根目录负例在项目内临时目录误判 | Windows 系统 Temp 权限污染；临时目录放在仓库内部又会改变“无仓库祖先”负例的前提 | Surefire 子 JVM 通过 `argLine` 使用仓库同级的专用临时目录；本地沙箱验证可用 `TEST_JUNIT_TMP` 指向仓库外可写目录 | TEST 工作流与本地门禁统一隔离临时目录；业务失败与执行环境失败分开统计 |

## 四环境边界

- `dev`：允许确定性本地预置数据；外部微信支付和真实充值关闭。
- `test`：独立数据库，不装载开发预置数据；只使用受控测试数据。
- `stage`：配置形态接近生产，独立数据库；外部能力仍需逐项授权。
- `prod`：不得启用 `local-mysql`、开发预置数据或测试 profile；真实支付/充值上线门禁另行执行。

任何环境都不得共享数据库名、业务账号密码、Flyway 密码或第三方 Secret。模板文件只保留键名和占位符，不提交真实凭据。
