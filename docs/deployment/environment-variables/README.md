# 四环境变量清单（CloudBase 可粘贴 JSON）

本目录以 `apps/api/src/main`、四个 profile YAML、五个 Dockerfile 和容器 entrypoint 的实际读取点为准。模板不含真实秘密，可直接粘贴到 CloudBase 的 JSON 环境变量编辑器后逐项替换 `[REPLACE_...]`。

## 1. 模板

- `cloudbase-dev.env.json`
- `cloudbase-test.env.json`
- `cloudbase-stage.env.json`
- `cloudbase-prod.env.json`

四个文件均刻意不包含微信 AppID 和 AppSecret。四环境共用获批 AppID，但 AppID/AppSecret 由平台侧受控配置维护；AppSecret 不进 Git、不进截图、不进本次汇总。

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
| `HZ_BUYER_AUTH_ENABLED` | `false` | `false` | 真实微信身份验收前保持关闭 |
| `HZ_P021_MODE` | `disabled` | `disabled` | 正式服务禁止测试只读旁路 |

JSON 中数据库 URL、账号和密码均为占位符；不替换就不应部署。用户名虽不一定是秘密，也使用占位符以防四环境误共用账号。

## 3. 代码读取但不进入安全模板的变量

### 3.1 真实外部能力（平台侧另行配置）

| Spring 属性 / canonical env | 当前规则 |
|---|---|
| `hz.buyer-auth.expected-app-id-ref` / `HZ_BUYER_AUTH_EXPECTED_APP_ID_REF` | AppID相关引用，按用户要求不进本次JSON |
| `hz.buyer-auth.provider-mode` / `HZ_BUYER_AUTH_PROVIDER_MODE` | YAML 当前固定 `disabled`；真实 adapter 验收前不得改 |
| `hz.buyer-auth.identity-pepper` / `HZ_BUYER_AUTH_IDENTITY_PEPPER` | 仅身份启用时必填；独立秘密 |
| `hz.buyer-auth.code-pepper` / `HZ_BUYER_AUTH_CODE_PEPPER` | 仅身份启用时必填；独立秘密 |
| `hz.wechat-pay.expected-app-id` / `HZ_WECHAT_PAY_EXPECTED_APP_ID` | AppID，按用户要求不进本次JSON |
| `hz.wechat-pay.merchant-id` / `HZ_WECHAT_PAY_MERCHANT_ID` | 支付启用时必填，按环境受控配置 |
| `hz.wechat-pay.allowed-certificate-serials` / `HZ_WECHAT_PAY_ALLOWED_CERTIFICATE_SERIALS` | 支付通知允许序列号集合；未配置时通知失败关闭 |

AppSecret 当前没有被候选代码直接读取；后续真实 WeChat adapter 应通过秘密装载边界读取，不能新增明文 YAML。WINLA 当前正式 Bean 固定为 Disabled，代码没有可安全启用的 WINLA 环境变量；不得自行添加猜测的 URL/token/signing key。

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
| 微信登录 | `HZ_BUYER_AUTH_ENABLED=false`、provider disabled | 平台秘密、预期AppID绑定、正式adapter、真机验收 |
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
