# 四环境变量清单（CloudBase 可粘贴 JSON）

本目录以 `apps/api/src/main`、四个 profile YAML、五个 Dockerfile 和容器 entrypoint 的实际读取点为准。四份 JSON 是当前 V23 候选所需的**全量受控配置基线**，不是增量补丁；应用时应与云端现有键合并核对，不能直接删除模板未列出的平台保留键。模板不含真实秘密，可粘贴到 CloudBase 的 JSON 环境变量编辑器后逐项替换 `[REPLACE_...]`。

## 1. 模板

- `cloudbase-dev.env.json`
- `cloudbase-test.env.json`
- `cloudbase-stage.env.json`
- `cloudbase-prod.env.json`

四个文件均刻意不包含微信 AppID 和 AppSecret。四环境共用获批 AppID，但 AppID/AppSecret 由平台侧受控配置维护；AppSecret 不进 Git、不进截图、不进本次汇总。

四份 JSON 也不包含数据库真实密码或任何已配置秘密。`[REPLACE_...]` 是待配置标记，不是可部署默认值；看到任一该标记都必须停止发布。

### 已冻结的服务与域名映射

| 环境 | 云托管服务 | HTTPS域名 | 小程序构建命令 |
|---|---|---|---|
| DEV | `huaren-api-dev` | `https://dev.guiye.xyz` | `npm run build:mp-weixin:dev` |
| TEST | `huaren-api-test` | `https://test.guiye.xyz` | `npm run build:mp-weixin:test` |
| STAGE | `huaren-api-stage` | `https://stage.guiye.xyz` | `npm run build:mp-weixin:stage` |
| PROD | `huaren-api-prod` | `https://api.guiye.xyz` | `npm run build:mp-weixin:prod`（也是默认构建） |

四个服务位于同一已确认CloudBase环境`prod-d3g9ntdmsdf9d7877`，但服务、数据库、账号和秘密仍按四环境隔离。小程序目标环境在构建期固定，PROD包不能由终端用户运行时切换到非生产服务。构建映射的代码源为`apps/miniapp/config/release-environments.json`，不得只在微信开发者工具中手改产物。

### 兼容性规则

- 环境变量调整采用“保留现有键、只新增或修改已确认键”的方式；候选代码尚未晋级前，不提前删除旧分支仍可能读取的变量。
- TEST、STAGE 继续保留 `HZ_ENV_FUNCTION_RELEASE_ENABLED=true`。候选代码目前不读取该键，但当前云上旧分支的启动安全校验仍依赖它；多余键对候选版本无副作用。
- 删除环境变量前必须同时核对当前云上部署提交与候选提交的实际读取点，不能只按候选模板判断。

## 2. 正式运行必填

| 变量 | DEV | TEST/STAGE/PROD | 说明 |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `release-mysql,local-mysql` | 对应 `release-mysql,<env>-mysql` | entrypoint 只允许四种精确组合 |
| `SERVER_PORT` | `8080` | `8080` | 云探针端口 |
| `HZ_DATASOURCE_URL` | 必填 | 必填 | 必须精确指向本环境数据库 |
| `SPRING_DATASOURCE_USERNAME/PASSWORD` | 必填 | 必填 | 应用账号；每环境独立 |
| `SPRING_FLYWAY_USER/PASSWORD` | 必填 | 必填 | 迁移账号；每环境独立 |
| `HZ_DEV_DATABASE_NAME` | 必填 | 不使用 | DEV profile 的精确数据库身份 |
| `HZ_ENV_DATABASE_NAME` | 不使用 | 必填 | TEST/STAGE/PROD 精确数据库身份 |
| `HZ_ENV_MIGRATION_ENABLED` | `true` | `true` | 启动后单次受控迁移；失败不重试 |
| `HZ_PHONE_DIGEST_HMAC_SECRET` | 必填 | 必填 | 至少32字符、每环境独立；不得复用数据库密码 |
| `HZ_ADMIN_BOOTSTRAP_ENABLED` | `false` | `false` | release 校验强制关闭 |
| `HZ_ADMIN_SESSION_HOURS` | `8` | `8` | 管理员会话时长 |
| `HZ_ADMIN_SELF_APPROVAL_ENABLED` | `true` | `true` | 仅唯一有效SUPER_ADMIN同人提交/审核/发布窄例外 |
| `HZ_ADMIN_SELF_APPROVAL_POLICY_VERSION` | 固定版本 | 固定版本 | 当前为`V1_SINGLE_SUPER_ADMIN_20260818`，写入审计 |
| `HZ_BUYER_AUTH_ENABLED` | `false` | `false` | 真实微信身份验收前保持关闭 |
| `HZ_BUYER_AUTH_PROVIDER_MODE` | `disabled` | `disabled` | 启用真实登录时才切换为`wechat-code2session` |
| `HZ_WECHAT_IDENTITY_ENABLED` | `false` | `false` | 与provider mode双开关，真实验收前关闭 |
| `HZ_BUYER_AUTH_IDENTITY_PEPPER` | 秘密占位 | 秘密占位 | 至少32字符、每环境独立，不得与code pepper相同 |
| `HZ_BUYER_AUTH_CODE_PEPPER` | 秘密占位 | 秘密占位 | 至少32字符、每环境独立 |
| `HZ_BUYER_CONSENT_USER_AGREEMENT_VERSION` | `2026-08-28` | `2026-08-28` | 当前有效用户协议版本 |
| `HZ_BUYER_CONSENT_PRIVACY_POLICY_VERSION` | `2026-08-28` | `2026-08-28` | 当前有效隐私政策版本 |
| `HZ_RECIPIENT_RETENTION_SCHEDULER_ENABLED` | `true` | `true` | V23履约号码终态三年保留与到期匿名化 |
| `HZ_BUYER_CLOSURE_CLEANUP_SCHEDULER_ENABLED` | `true` | `true` | 注销请求的PII清理与CLOSED收敛 |
| `HZ_RECOVERY_SCHEDULER_ENABLED` | `false` | `false` | 真实provider查询未验收前关闭 |
| `HZ_P021_MODE` | `disabled` | `disabled` | 正式服务禁止测试只读旁路 |

JSON 中数据库 URL、账号和密码均为占位符；不替换就不应部署。用户名虽不一定是秘密，也使用占位符以防四环境误共用账号。

## 3. 代码读取但不进入安全模板的变量

### 3.1 真实外部能力（平台侧另行配置）

| Spring 属性 / canonical env | 当前规则 |
|---|---|
| `hz.buyer-auth.expected-app-id-ref` / `HZ_BUYER_AUTH_EXPECTED_APP_ID_REF` | AppID相关引用，按用户要求不进本次JSON |
| `hz.buyer-auth.provider-mode` / `HZ_BUYER_AUTH_PROVIDER_MODE` | JSON显式为`disabled`；真实adapter验收时才改`wechat-code2session` |
| `hz.buyer-auth.identity-pepper` / `HZ_BUYER_AUTH_IDENTITY_PEPPER` | 仅身份启用时必填；独立秘密 |
| `hz.buyer-auth.code-pepper` / `HZ_BUYER_AUTH_CODE_PEPPER` | 仅身份启用时必填；独立秘密 |
| `hz.wechat-pay.expected-app-id` / `HZ_WECHAT_PAY_EXPECTED_APP_ID` | AppID，按用户要求不进本次JSON |
| `hz.wechat-pay.merchant-id` / `HZ_WECHAT_PAY_MERCHANT_ID` | 支付启用时必填，按环境受控配置 |
| `hz.wechat-pay.allowed-certificate-serials` / `HZ_WECHAT_PAY_ALLOWED_CERTIFICATE_SERIALS` | 支付通知允许序列号集合；未配置时通知失败关闭 |

AppID/AppSecret已由平台侧单独维护，因此不重复出现在JSON；V23候选会通过`HZ_WECHAT_APP_ID/HZ_WECHAT_APP_SECRET`读取它们。WINLA 当前正式 Bean 固定为 Disabled，代码没有可安全启用的 WINLA 环境变量；不得自行添加猜测的 URL/token/signing key。

### 3.2 release 中禁止或只用于隔离测试

以下变量虽被代码/YAML读取，但 `ReleaseSecretBoundaryValidator` 要求正式 release 为空或固定关闭，故不进入四环境 JSON：

- `HZ_ADMIN_BOOTSTRAP_TOKEN`
- `HZ_TEST_ACCESS_TOKEN`
- `HZ_CONTENT_ADMIN_TOKEN`
- `HZ_PROJECT_AUTH_CONTENT_ACTOR`
- `HZ_PROJECT_AUTH_AUTHORIZATION_REF`
- `HZ_IT_BUYER_SESSION_TOKEN`
- `HZ_IT_ADMIN_CS_SESSION_TOKEN`
- `HZ_IT_ADMIN_FIN_SESSION_TOKEN`
- `HZ_IT_BUYER_SUBJECT_REF`
- `HZ_IT_BUYER_SESSION_REF`
- `HZ_IT_BUYER_SESSION_VERSION`（release 固定为 `0`）
- `HZ_IT_BUYER_AUTHORIZATION_SET_REF`
- `HZ_IT_BUYER_AUTHORIZATION_EVIDENCE_VERSION`
- `HZ_IT_BUYER_AUTHORIZED_ORDER_REFS`

### 3.3 默认值或遗留变量

| 变量 | 读取/默认 | 结论 |
|---|---|---|
| `HZ_DEV_FUNCTION_RELEASE_ENABLED` | release YAML 默认 `false`，DEV profile 自身设 `true` | 已退出统一迁移门禁，不进入模板 |
| `HZ_P021_MODE` | `disabled` | 模板显式锁定，防平台遗留覆盖 |
| `HZ_ENV_MIGRATION_ENABLED` | YAML默认 `true` | 模板显式锁定，避免含义漂移 |
| `HZ_ADMIN_SESSION_HOURS` | `8` | 可选经营配置，模板保留默认值 |
| `SERVER_PORT` | Spring/Docker 默认 `8080` | 模板显式锁定云探针端口 |
| `VITE_ADMIN_DATA_MODE` | Docker 构建期 `PROJECT_API_PROXY` | 不是服务运行环境变量 |
| `K_REVISION`、`TCB_CLOUD_RUN_VERSION`、`CLOUD_RUN_REVISION` | 平台自动注入，只读诊断 | 用户不得手工配置 |

### 3.4 Spring relaxed binding 读取的其他开关

这些键没有出现在安全模板中，但代码通过 `@ConditionalOnProperty`、`@Value` 或
`Environment.getProperty` 读取对应 Spring 属性；若由环境变量覆盖，canonical 名称如下。release
配置已经把其中多数固定在安全值，禁止为图省事打开：

| canonical env | 安全值/用途 |
|---|---|
| `HZ_P014_MODE` | `disabled`；旧本地合成充值 |
| `HZ_P014_FIXTURE_MODE` | `no-default` |
| `HZ_P021_FIXTURE_MODE` | `no-default` |
| `HZ_A110_MODE` | `disabled`；旧本地合成差异处置 |
| `HZ_LIFE_CONTENT_MODE` | `disabled`；旧本地合成资讯 |
| `HZ_TEMPORAL_OVERVIEW_MODE` | `disabled`；旧本地合成时钟/节假日 |
| `HZ_PERSISTENCE_MODE` | release 固定 `mysql` |
| `HZ_ADMIN_COMMAND_ENABLED` | 当前代码 `matchIfMissing=true`；后台命令仍受会话角色、版本、幂等和状态机约束 |
| `HZ_RECOVERY_SCHEDULER_ENABLED` | 缺省关闭；真实 provider 查询未验收前不得打开 |
| `HZ_RECOVERY_SCHEDULER_DELAY_MS` | 缺省 `30000`；仅 scheduler 获准后使用 |
| `HZ_RECIPIENT_RETENTION_SCHEDULER_ENABLED` | JSON显式开启；只处理V23专用表，终态三年后清除明文 |
| `HZ_RECIPIENT_RETENTION_SCHEDULER_DELAY_MS` | `86400000`，每日检查一次 |
| `HZ_BUYER_CLOSURE_CLEANUP_SCHEDULER_ENABLED` | JSON显式开启；注销任务失败可重试，完成后才CLOSED |
| `HZ_BUYER_CLOSURE_CLEANUP_SCHEDULER_DELAY_MS` | `30000` |
| `HZ_BUYER_CLOSURE_CLEANUP_LEASE_SECONDS` | `30` |
| `HZ_BUYER_CLOSURE_CLEANUP_RETRY_SECONDS` | `30` |
| `HZ_RELOADLY_SANDBOX_TOPUP_ENABLED` | 缺省关闭，四环境禁止启用 |
| `HZ_DATA_INTEGRATION_READINESS_ENABLED` | 缺省关闭；只读数据库就绪探针 |
| `HZ_DATA_INTEGRATION_EXPECTED_SERVER_UUID` | 只读就绪探针启用时必填；用于数据库身份绑定 |
| `HZ_DATA_INTEGRATION_BACKUP_REFERENCE_SHA256` | 就绪探针可选证据，不是业务启动变量 |
| `HZ_DATA_INTEGRATION_RESTORE_EVIDENCE_SHA256` | 就绪探针可选证据，不是业务启动变量 |
| `HZ_DATA_INTEGRATION_FLYWAY_V12_FUNCTION_VERIFICATION_ENABLED` | 历史隔离验证开关，正式四环境保持关闭 |
| `HZ_DEV_FUNCTION_RELEASE_ENABLED` | 遗留开关；不再控制统一迁移 Gate |

此外，`spring.flyway.locations/connect-retries/validate-on-migrate/baseline-on-migrate` 由 profile YAML
分别固定为 `classpath:db/migration`、`0`、`true`、`false`；不应在云平台额外覆盖。

## 4. 默认关闭矩阵

| 能力 | 安全默认 | 启用前置 |
|---|---|---|
| 微信登录 | `HZ_BUYER_AUTH_ENABLED=false`、provider disabled、identity disabled | 平台秘密、预期AppID绑定、真机验收；三开关按受控步骤切换 |
| 微信支付 | Disabled port | 商户/证书/回调配置、验签解密与小额支付退款授权 |
| WINLA充值 | Disabled port，且无启用变量 | 书面协议、真实adapter、余额/IP/签名/金额/回调验收 |
| P021测试只读 | `HZ_P021_MODE=disabled` | 只能在独立证据服务使用，不进入四环境正式服务 |
| 管理员bootstrap | `false` 且token必须为空 | 使用受控初始化流程，不在普通发布中打开 |
| 外部恢复scheduler | 默认关闭 | 频率、预算、租约和真实provider查询能力验收 |

## 5. 粘贴后人工核对

1. JSON解析成功，键无重复；不要把 `[REPLACE_...]` 当真实值发布。
2. JDBC URL中的库名与 `HZ_DEV_DATABASE_NAME` 或 `HZ_ENV_DATABASE_NAME` 完全一致。
3. 应用账号与Flyway账号、四环境之间的密码均不复用。
4. `SPRING_PROFILES_ACTIVE` 与所选 Dockerfile/服务名一致。
5. TEST/STAGE/PROD 不含任何 `HZ_IT_*`、测试token、bootstrap token或开发种子变量。
6. 真实微信/WINLA未单独验收前，看到503/UNKNOWN是预期失败关闭，不得改成Fake成功。
7. AppID/AppSecret虽不在JSON中，启用微信登录前必须确认平台侧两键已存在且AppID与预期值完全一致。
8. `HZ_BUYER_AUTH_IDENTITY_PEPPER`、`HZ_BUYER_AUTH_CODE_PEPPER`和`HZ_PHONE_DIGEST_HMAC_SECRET`必须三者不同且四环境不复用。
