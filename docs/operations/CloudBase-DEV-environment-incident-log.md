# CloudBase 环境问题台账与部署前门禁

## 目的

本文件是 DEV、TEST、STAGE、PROD 环境问题的唯一追加台账。云托管部署只用于验证已通过门禁的固定提交，不再用于发现本地可发现的问题。

每次问题必须记录：编号、时间、固定提交、现象、根因、处理、预防门禁、验证结果和云上结果。没有完成预防门禁的修复不得标记关闭。

## 环境固定关系

| 项目 | DEV 固定值 |
|---|---|
| Git 仓库 | `yenanc-1920/huarenzaimeng_v1.0` |
| 分支 | `dev` |
| CloudBase 服务 | `huaren-api-dev` |
| 数据库 | `huarenzaimeng_dev` |
| 容器端口 | `8080` |
| Dockerfile | `Dockerfile.dev` |
| Spring Profiles | `release-mysql,local-mysql` |

禁止在 DEV 配置中复用其他环境的数据库名。密码只存放在云托管环境变量中，不写入本文、源码、日志或截图。

## 事件记录

### ENV-DEV-001：云构建测试读取不到根 Dockerfile

- 时间：2026-08-17（Asia/Dhaka）
- 首次失败提交：`9e313ff`
- 现象：云端构建执行 706 项测试时，`BuildSelectionContractTest` 报 `NoSuchFileException`，路径为仓库根 `Dockerfile`。
- 根因：`Dockerfile.dev` 只复制了自身，没有把契约测试读取的根 `Dockerfile` 复制到 Maven build stage。
- 处理：`Dockerfile.dev` 增加 `COPY Dockerfile Dockerfile`。
- 修复提交：`0f4d7d9`
- 预防门禁：部署前必须执行完整 `Dockerfile.dev` 构建；本地无 Docker 时只能得到 `NO-GO/NEEDS_DOCKER`，不得推送触发部署。
- 本地验证：`BuildSelectionContractTest` 2/2 通过。
- 云上结果：构建通过，进入容器启动阶段。
- 状态：已关闭。

### ENV-DEV-002：事务 Controller 为 final，Spring 启动失败

- 时间：2026-08-17（Asia/Dhaka）
- 首次失败提交：`0f4d7d9`
- 现象：容器启动时 `V1AdminCommandController` 创建失败，根因文本为 `Cannot subclass final class`；端口 8080 未持续监听，健康检查连接被拒绝。
- 根因：Controller 方法使用 `@Transactional`，Spring 需要创建 CGLIB 类代理，但 Controller 被声明为 `final`。同类风险也存在于 `V1DirectoryController`。
- 处理：移除两个事务 Controller 的 `final`，新增 `TransactionalControllerProxyContractTest`，真实创建 class-based Spring proxy。
- 修复提交：`ddf9a23`
- 预防门禁：全量测试必须包含所有 Spring 代理契约；新增 `@Transactional`、`@Async`、`@Cacheable` 等 AOP 注解时，必须验证目标类可代理。
- 本地验证：代理与相关契约 5/5 通过，`BUILD SUCCESS`。
- 云上结果：待当前自动部署回填。
- 状态：代码已修复，云上待确认。

### ENV-LOCAL-001：离线 Maven 参数与仓库标识不匹配

- 时间：2026-08-17（Asia/Dhaka）
- 现象：PowerShell 未加引号的 `-Dmaven.repo.local=C:\...` 被 Maven 误解析；同时指定项目 settings 后，离线缓存来源仓库 ID 与 settings 不一致。
- 根因：Windows 参数引用与 Maven 本地缓存来源标识不一致。
- 处理：使用独立参数 `"-Dmaven.repo.local=..."`，本地离线验证不强制切换远程仓库 settings；云 Docker 构建继续使用项目固定 settings。
- 预防门禁：统一通过 `node tools/run-dev-deployment-gate.mjs` 执行，不再手写 Maven 长命令。
- 验证结果：修正后定向测试通过。
- 状态：已关闭。

### ENV-LOCAL-002：Windows Maven target 内只读文件阻断 clean

- 时间：2026-08-17（Asia/Dhaka）
- 现象：统一门禁的后台和小程序步骤通过后，Maven `clean` 无法删除 `apps/api/target/classes/db/migration/V11__add_state_advance_authority_columns.sql`；后端测试尚未执行。
- 根因：先前构建产物中的 SQL 文件带有 Windows 只读属性；Maven clean 在测试发现前失败。
- 处理：统一门禁只对经过绝对路径校验且目录名精确为 `target` 的 `modules/core/target` 与 `apps/api/target` 解除只读并删除，然后执行 Maven clean test。
- 预防门禁：禁止手工删除或扩大清理范围；清理函数拒绝工作区外路径和非 `target` 目录。
- 验证结果：修正后全量门禁成功进入并完成后端测试，715 项、失败 0、错误 0、跳过 65。
- 状态：已关闭。

### ENV-DEV-003：普通 DEV 服务误装配一次性 Flyway Bean

- 时间：2026-08-17（Asia/Dhaka）
- 首次失败提交：`ddf9a23`
- 现象：镜像构建成功，但容器启动时 `DevelopmentFlywayMigrationRunner` 发现两个 `Flyway` Bean：`isolatedFlyway` 与 `releaseFlyway`；Spring 无法选择依赖，8080 未监听，健康检查连接被拒绝。
- 根因：一次性 V12 功能验证器的嵌套 `IsolatedFlywayConfiguration` 只有 `release-mysql` Profile，没有绑定自身启用开关，因而被普通 API 组件扫描；同时 DEV 迁移器未显式限定 `releaseFlyway`。
- 处理：为隔离配置增加 `hz.data-integration.flyway-v12-function-verification-enabled=true` 条件；为 DEV 迁移器增加 `@Qualifier("releaseFlyway")`。
- 预防门禁：必须验证隔离配置的显式开关契约、DEV 迁移器的 Bean 限定契约，并在完整容器构建后验证普通 API 启动链。
- 定向验证：19 项通过，失败 0、错误 0；完整后端 717 项通过，失败 0、错误 0、跳过 65。
- 云上结果：待完整门禁通过并发布固定提交后确认。
- 状态：代码与本地全量验证已完成；精确 Docker 构建及云上验证未完成。

### ENV-LOCAL-003：PowerShell 拆分 Maven 测试与系统属性参数

- 时间：2026-08-17（Asia/Dhaka）
- 现象：未加引号的逗号分隔测试名触发 PowerShell `MissingArgument`；未独立加引号的 `-Dsurefire.failIfNoSpecifiedTests=false` 被 Maven 解析为生命周期阶段。
- 根因：PowerShell 对逗号与 `-D` 参数的命令行解析先于 Maven。
- 处理：将 `-Dtest=...,...`、`-Dsurefire...` 和 `-Dmaven.repo.local=...` 分别作为完整的加引号参数传递。
- 预防门禁：人工定向测试只用于首因定位；最终结论统一使用 Node 门禁脚本的参数数组调用，不拼接 Maven 长命令。
- 验证结果：修正参数后定向 19/19 与完整门禁后端 717/717 均通过。
- 状态：已关闭。

### ENV-DEV-004：DEV 双 Profile 被正式环境校验器拒绝

- 时间：2026-08-17（Asia/Dhaka）
- 首次失败版本：`huaren-api-dev-004`
- 现象：镜像构建成功，但容器启动时 `ReleaseSecretBoundaryValidator` 抛出 `RELEASE_PROFILE_MUST_NOT_MIX_WITH_TEST_PROFILES`；实际激活的是固定 DEV 组合 `release-mysql,local-mysql`，8080 未持续监听。
- 根因：正式环境边界校验器只允许单独 `release-mysql`，没有把受控 `local-mysql` 识别为 DEV 数据扩展；原门禁运行了全量测试和镜像构建，却没有把云上真实 Profile 组合作为第一道启动阻断契约。
- 处理：正式环境继续只允许 `release-mysql`；DEV 仅允许精确的 `release-mysql + local-mysql`，并要求 `hz.dev-function-release.enabled=true` 与 `hz.v1-dev-data.enabled=true`。任何第三个 Profile 或开发开关不完整仍失败关闭。
- 预防门禁：本地脚本与 GitHub PR 在耗时构建前先运行 `ReleaseSecretBoundaryValidatorTest`、`DevelopmentFlywayMigrationRunnerTest` 与 `BuildSelectionContractTest`；快速阻断通过后才进入后台、小程序、后端全量及 Docker 构建。
- 定向验证：快速启动阻断组 16/16 通过（Profile 边界 11、Flyway 装配 3、Docker 构建选择 2），失败 0、错误 0。
- 云上结果：未验证；修复未推送、未部署。
- 状态：代码已修复，门禁验证中。

### ENV-PROD-001：单行聚合结果读取游标越界，业务 Gate 永久停在启动中

- 时间：2026-08-18（Asia/Dhaka）
- 首次确认版本：`huaren-api-prod-009`，基线提交 `2f147f4`
- 现象：容器与 `/actuator/health` 均为 UP，但业务接口持续返回 `503 SERVICE_STARTING`；日志显示 `ENVIRONMENT_FLYWAY_BOOTSTRAP_FAILED` 和 `java.sql.SQLException: After end of result set`。
- 数据库事实：数据库身份为 `huarenzaimeng_prod`；Flyway V1-V14 共 14/14 成功、失败 0；`hz_v1_dev_seed_registry` 存在且行数为 0，符合 PROD 边界。
- 根因：启动 Runner 对单行 `COUNT(*)` 结果先调用第二次 `ResultSet.next()` 验证“没有第二行”，随后才调用 `getLong(1)` 读取第一行；真实 MySQL 驱动已把游标移动到结果集末尾并抛出异常。旧 Mockito 测试桩允许越界读取，导致本地门禁假通过。
- 处理：所有单行结果必须按“首行存在 -> 在当前行读取全部字段 -> 再检查不存在第二行”的顺序读取；测试桩加入真实游标位置约束，越界读取必须抛出 `After end of result set`。
- 预防门禁：数据库 Runner 的单行查询测试必须使用严格游标语义；禁止用 `when(next()).thenReturn(true, false)` 搭配无状态 `getLong()` 冒充 JDBC 行为。
- 定向验证：严格 Runner、PROD 双 Profile 启动、发布边界与构建选择共 27/27 通过，失败 0、错误 0；日志无 `ENVIRONMENT_FLYWAY_BOOTSTRAP_FAILED`；离线 API 打包 `BUILD SUCCESS`。
- 云上结果：待固定修复提交部署后回填。
- 状态：代码与定向测试已关闭；提交、推送、部署未执行。

### ENV-REL-006：四环境使用不同提交，后级环境未复用前级已验证对象

- 时间：2026-08-18（Asia/Dhaka）
- 现象：DEV、TEST、STAGE 的部署顺利不能证明 PROD 使用的代码可运行；四个环境分支及 deploy 分支实际指向不同提交。
- 根因：四套工作流分别从各自分支构建并部署，只验证当前分支，没有校验目标提交等于上一环境已经验收的 deploy 提交。
- 处理：TEST 必须精确等于 `deploy/dev`，STAGE 必须精确等于 `deploy/test`，PROD 必须精确等于 `deploy/stage`；不接受 cherry-pick 后的新 SHA 冒充同一制品。
- 预防门禁：`tools/verify-environment-promotion.mjs` 在耗时全量门禁之前校验提交身份；三套后级 workflow 已接入。
- 制品边界：TEST/STAGE/PROD 使用同一 Dockerfile 与相同源码提交，但 CloudBase 分环境重建镜像，当前只能证明“同提交、同构建输入”，不能证明镜像摘要字节相同；DEV 因包含本地预置数据使用 `Dockerfile.dev`，明确不属于同镜像。
- 状态：本地实现完成，单元测试 3/3 通过；尚未提交、推送或触发云构建。

### ENV-CLOUD-005：网关短时 503 与应用业务 503 混淆

- 时间：2026-08-18（Asia/Dhaka）
- 现象：DEV 曾短时返回 nginx HTML 503，随后在无代码和配置修改的情况下恢复；应用自身的启动 Gate 503 则返回 JSON。
- 处理：验收器同时记录 HTTP 状态、响应类型和结构；非 JSON 归为网关/平台层，JSON 业务信封归为应用层，不再根据单个 503 直接改代码。
- 预防门禁：`tools/verify-cloudbase-environments.mjs` 对四环境依次执行健康、时间、目录、城市、资讯共 20 个只读检查点，默认不重试。
- 验证结果：2026-08-18 本轮 20/20 均为 HTTP 200、JSON 且契约通过。
- 状态：已关闭；精确平台触发原因未证明，不冒充已定位。

## 部署前全量门禁

必须按相同固定提交依次通过：

1. `git diff --check`。
2. 后台契约、状态测试、TypeScript 类型检查和生产构建。
3. 小程序正式 API 契约和微信开发模式构建，产物中不得含运行时 mock/synthetic 模块。
4. 后端 `clean test` 全量测试，不得只跑失败类。
5. 使用 `local-devdata` 打包 DEV JAR，确认迁移与 `db/devdata` 都进入 DEV JAR。
6. 确认普通 release 构建不包含 `db/devdata`。
7. `Dockerfile.dev` 完整容器构建成功；构建内部再次执行后台构建和后端全量测试。
8. 生成门禁摘要，固定 commit SHA、测试数量和制品 SHA。
9. 只有门禁 `GO` 才允许合并到 `dev`；`dev` 的推送才触发 CloudBase。
10. 云上只执行健康、数据库身份、迁移版本和预设数据的只读验收；失败即停止，不以连续推送诊断。

## 门禁命令

本地：

```powershell
node tools/run-dev-deployment-gate.mjs
```

如本机没有 Docker，该命令必须返回非零并报告 `NEEDS_DOCKER`。完整 GO 应由具备 Docker 的 GitHub PR 门禁获得。

## 2026-08-17 当时结论（历史记录）

- 当前候选：在 `dev` 本地分支上完成 ENV-DEV-003 修复，尚未推送触发 CloudBase。
- 后台契约、状态、类型检查和构建：通过。
- 小程序正式 API 契约、微信开发构建与 appservice 加载：通过。
- 后端全量：717 项，失败 0、错误 0、跳过 65，`BUILD SUCCESS`。
- release JAR：确认不含 `db/devdata`。
- DEV JAR：确认包含 `db/devdata` 与 V14；本次候选制品 SHA-256 以提交后固定门禁摘要为准。
- Docker 门禁：本机 Docker 不可用，尚未执行；GitHub PR 工作流已准备使用真实 Docker 构建 `Dockerfile.dev`。
- 当前门禁裁定：`NEEDS_DOCKER`，P0=1（精确 DEV Docker 镜像尚未构建），P1=0。
- 当前部署裁定：`NO-GO`，不得继续通过微修复直接触发部署。

## 2026-08-18 四环境只读验收补充

- DEV/TEST/STAGE/PROD 的 `/actuator/health` 均为 `UP`。
- 四环境的时间概览、指定运营商目录、城市、资讯接口共 16 个业务检查点均返回 HTTP 200、JSON 业务信封并通过结构校验。
- 本轮只读验收合计 20/20 GO；未写数据库、未触发部署、未自动重试。
- 下一次晋级必须先消除分支 SHA 漂移，再按 DEV -> TEST -> STAGE -> PROD 单向推进。
