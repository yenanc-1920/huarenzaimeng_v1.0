# V1 本地候选验收报告

日期：2026-08-19
分支：`codex/v1-delivery-recovery`
可执行代码候选：`f7f5915b300de11d11b2f66ee767598e38be5601`
结论：`LOCAL_RELEASE_CANDIDATE_GO / REAL_MYSQL_NO_GO / REAL_WECHAT_PAYMENT_WINLA_NO_GO / VISUAL_NOT_EVIDENCED`

## 1. 当前本地候选

- 后台已接正式持久化边界：A100客服案件、A110交易差异、A120审核、A121黄页、A122节假日/资讯、A130目录/渠道/价格/试算、A140订单/支付/充值/退款只读详情。
- 小程序既定16个页面、协议同意、微信官方隐私授权顺序、登录会话、退出与注销入口、报价/订单/支付/充值/退款只读状态已接严格正式DTO。
- Flyway V15–V24覆盖后台工作流、支付充值协调、不可变报价/订单快照、事务Outbox、UNKNOWN恢复、跨实例限额、供应商原始金额、买家同意/注销、唯一SUPER_ADMIN例外、受控履约号码保留及微信支付身份/预支付事实。
- 外部适配器未配置时保持失败关闭，不伪造支付、退款或充值成功。

## 2. 当前提交的自动化证据

### 后端

- 微信身份、支付API v3、履约号码及关联聚焦门禁通过；所有真实适配器仍默认关闭。
- 四环境Spring smoke：DEV/TEST/STAGE/PROD 4/4通过。
- 完整后端：908 tests，0 failures，0 errors，65 skipped。
- Flyway V1–V24：24个迁移文件连续、唯一、无缺号；V24只追加新迁移，未修改V1–V23。
- 当前候选离线package已生成普通API JAR（1,023,537 bytes，SHA256 `C09EF7EB6469A05229EB5437BF4DF74BE65E45AEA90BB9C6C74157EC5C972C62`），但Spring Boot repackage因本地离线仓缺`org.apache.commons:commons-parent:71`停止。该普通JAR不含`BOOT-INF`，不是可部署制品；未联网、未复制依赖、未重试。

### 后台与小程序

- 后台 contracts、state contracts、TypeScript typecheck、Vite build全部通过。
- 小程序 development、legacy、formal contracts全部通过。
- 小程序 TypeScript 历史错误已从约58项收口为0；`tsc --noEmit`通过。
- `mp-weixin` build与AppService load通过，核验106个相对静态依赖。
- 正式产物 Mock、synthetic、sandbox及退役动作扫描0命中。
- `git diff --check`通过。

## 3. 本地仍未完成

- MySQL验证脚本已冻结到V24；AST、DryRun（24个迁移、零连接）及合同7/7通过。尚需在授权的真实MySQL 5.7临时库执行空库V1→V24、V14→V24、V21→V24、重复执行、中断诊断和真实锁语义验证。
- 微信登录、微信支付、退款、WINLA充值/回调/查单及ECB汇率尚未真实外连。
- V23已完成专用履约手机号存储、对外掩码/HMAC、供应商读取边界及从实际终态起三个日历年后匿名化的本地实现；真实MySQL执行、数据库账号最小权限和真实供应商出口仍待验收。
- 21张后台与16张小程序页面尚未取得同版本实际截图和人工高保真签核；当前统一为 `NOT_EVIDENCED`。
- 当前候选分支已推送，但未合并、重启或部署；GitHub全量CI尚未触发，本机`gh`令牌失效。此前环境可用结果不证明本候选已运行。

## 4. 证据边界

- `LOCAL_RELEASE_CANDIDATE_GO`不等于可上线。
- H2、迁移合同和DryRun不等于真实MySQL 5.7通过。
- Fake、fixture、Disabled adapter和官方样例不等于真实微信、支付或WINLA通过。
- typecheck/build/AppService成功不等于视觉像素验收通过。
- 四环境Spring context成功不等于云环境部署、探针或长期稳定性通过。
- 本报告不授权数据库执行、外部调用、推送、重启或部署。
