# PROD 部署就绪记录（2026-08-17）

## 固定范围

- Git 分支：`prod`，门禁通过后自动提升到 `deploy/prod`
- 云托管服务：`huaren-api-prod`
- 数据库：`huarenzaimeng_prod`
- Spring profiles：`release-mysql,prod-mysql`
- 数据库终态：Flyway V1-V14 全部成功，开发种子记录为 0

## 已通过

- PROD 启动与边界定向测试：22/22
- 仓库完整后端测试：738 项，失败 0、错误 0、跳过 65
- 后台契约、类型检查与构建
- 小程序契约、构建和 appservice 加载
- PROD 制品检查：包含 PROD 配置与 V14，不包含开发种子
- 总门禁：`PROD_DEPLOYMENT_GATE_GO`

## 未执行项

本机 MySQL 临时库实跑未进入数据库。原因是本机测试实例未运行，且旧
`credentials.clixml` 无法由当前 Windows 加密上下文解密。该项记录为
`NOT_RUN_LOCAL_ENVIRONMENT`，不是代码或云数据库失败。DEV、TEST、STAGE 已完成
云端逐级迁移和启动验证。

## 首次 PROD 启动规则

1. 首次部署仅在确认目标库为空时设置 `HZ_ENV_INITIALIZE_EMPTY_DATABASE=true`。
2. 应用只允许对精确数据库 `huarenzaimeng_prod` 执行一次 V1-V14 初始化。
   若云数据库连接在首次初始化期间中断，只允许从经过脚本 checksum 校验、
   20 张表且 V1-V10 连续成功的固定 `PRE_V10` 状态恢复；其他非空状态全部拒绝。
3. 验证 V14、14 条成功迁移且开发种子为 0 后，将该变量改为 `false` 并重新部署。
4. 后续新增迁移版本必须新增受控升级路径，不允许沿用首次空库初始化开关。
