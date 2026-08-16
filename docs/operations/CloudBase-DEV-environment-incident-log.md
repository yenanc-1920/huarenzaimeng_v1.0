# CloudBase DEV 环境问题台账与部署前门禁

## 目的

本文件是 DEV 环境问题的唯一追加台账。云托管部署只用于验证已通过门禁的固定提交，不再用于发现本地可发现的问题。

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

## 当前结论

- 固定源码提交：`ddf9a23c566220e8318309b110a201ca6f468316`
- 后台契约、状态、类型检查和构建：通过。
- 小程序正式 API 契约、微信开发构建与 appservice 加载：通过。
- 后端全量：715 项，失败 0、错误 0、跳过 65，`BUILD SUCCESS`。
- release JAR：确认不含 `db/devdata`。
- DEV JAR：确认包含 `db/devdata` 与 V14；SHA-256=`AA0D5C120AED3E04653E8F3D73D9CEE14BB5BA578534010A48EC625C0658F300`。
- Docker 门禁：本机 Docker 不可用，尚未执行；GitHub PR 工作流已准备使用真实 Docker 构建 `Dockerfile.dev`。
- 当前门禁裁定：`NEEDS_DOCKER`，P0=1（精确 DEV Docker 镜像尚未构建），P1=0。
- 当前部署裁定：`NO-GO`，不得继续通过微修复直接触发部署。
