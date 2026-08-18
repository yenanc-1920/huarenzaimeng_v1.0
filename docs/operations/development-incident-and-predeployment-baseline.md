# 开发环境故障与部署前验证基线

本文件持续记录已经发生过的环境故障，并把根因转换为可重复的部署前检查。它不是生产运行证据，也不替代正式部署授权。

## 执行原则

- 云环境只做最终验证，不承担首轮诊断。
- 同一根因不得通过反复发布确认；应先在本地修复并加入自动门禁。
- 开发验证不得连接真实支付、真实充值或生产数据。
- 编译、单测、H2、Mock、静态合同和构建成功不得外推为真实 MySQL、容器、微信或供应商联调通过。
- DEV、TEST、STAGE、PROD 应晋升同一提交或镜像摘要；环境配置和数据库相互隔离。

## 已发生问题与防复发检查

### ENV-DEV-001：容器构建缺少测试资源

Dockerfile 未复制测试引用的固定文件，导致构建阶段出现 `NoSuchFileException`。容器构建前必须运行构建选择合同，并核对 Dockerfile 引用路径。

### ENV-DEV-002：Spring AOP 无法代理 final Bean

需要事务或切面增强的 Spring Bean 被声明为 `final`，应用在探针就绪前退出。相关 Bean 不得为 `final`，四环境组件图冒烟测试必须加载真实 AOP 配置。

### ENV-DEV-003：Flyway Bean 候选不唯一

组合 profile 后按类型注入出现多个 Flyway Bean。必须使用明确限定符，并让隔离验证 Bean 只在专用链路装配。

### ENV-DEV-004：release 与环境 profile 组合被误拒绝

四环境只允许精确组合：`release-mysql,local-mysql`、`release-mysql,test-mysql`、`release-mysql,stage-mysql`、`release-mysql,prod-mysql`。其他混用继续失败关闭。

### ENV-CLOUD-005：云托管入口短时 503

nginx 503 与应用业务 JSON 503 必须分别记录。首次入口 503 先观察健康端点和实例事件，不自动修改业务代码或重复构建。

### ENV-REL-006：环境分支制品身份不一致

环境通过只能证明实际运行的提交或镜像。每次发布记录 `sourceCommit`、镜像摘要、目标环境和数据库名，后一环境只接受前一环境通过的同一制品。

### ENV-LOCAL-007：离线 Maven 仓库元数据上下文不一致

依赖文件存在不代表离线仓库元数据可解析。门禁必须使用已验证的项目 settings 与固定本地仓路径；解析失败只记为环境闭包不足，不复制依赖后循环重试。

### ENV-LOCAL-008：共享临时目录污染治理证据

测试临时文件必须写入任务独占目录，不得复用 `.gate-temp`。发生误写后只读登记，不擅自删除或改写其他任务的临时材料。

## 部署前分层验证

### 快速层

1. 后台合同、状态合同、类型检查和构建。
2. 小程序 development、legacy、formal 合同与 `mp-weixin` 构建。
3. DEV、TEST、STAGE、PROD Spring 组件图冒烟测试。
4. `git diff --check`，并扫描正式产物中的 Mock、synthetic、sandbox 和秘密形态。
5. 校验 Flyway V1–V22 连续且历史迁移未漂移。

### 数据库层

1. 默认只运行 MySQL 5.7 验证脚本的 DryRun。
2. 真执行仅限预创建的 `hz_verify_<runid>` 临时库和独立最小权限迁移账号。
3. 禁止自动 `drop`、`clean`、`repair`、`baseline` 或修改历史 checksum。
4. 未执行真实 MySQL 时必须标记 `DB_NOT_RUN`，不得用 H2 结果替代。

### 容器层

1. 使用目标环境对应 Dockerfile 构建镜像。
2. 容器必须显式收到受支持的 release profile 组合，缺失或 Mock profile 应非零退出。
3. 验证 `/actuator/health`、liveness、readiness、后台静态资源和公开读 API。
4. 没有真实 Docker 运行证据时标记 `CONTAINER_NOT_RUN`。

### 云端层

云端仅在本地门禁完成后执行一次。失败应登记唯一根因并退回本地，不在云端循环试错。发布后至少验证健康端点及一个公开业务端点，并记录实际提交或镜像身份。

## 当前证据边界（2026-08-18）

- 本地候选包含 Flyway V1–V22、四环境冒烟合同和 MySQL 5.7 临时库验证脚本。
- MySQL 验证脚本目前只有静态合同与 DryRun 证据，尚未执行真实临时库迁移。
- 微信登录、微信支付和赢啦充值仍需真实凭据、协议及人工操作证据；本地 Fake 或 Disabled 适配器不能证明真实集成。
- 高保真截图矩阵已准备，但微信开发者工具、真机和后台逐帧截图仍为 `NOT_EVIDENCED`。
