# V1 本地候选验收报告

日期：2026-08-18
分支：`codex/v1-delivery-recovery`
可执行代码候选：`a702f86dcf2a9dda5392724dc1cae52b6ffe2fdf`
结论：`LOCAL_RELEASE_CANDIDATE_GO / REAL_MYSQL_NO_GO / REAL_WECHAT_PAYMENT_WINLA_NO_GO / VISUAL_NOT_EVIDENCED`

## 1. 当前本地候选

- 后台已接正式持久化边界：A100客服案件、A110交易差异、A120审核、A121黄页、A122节假日/资讯、A130目录/渠道/价格/试算、A140订单/支付/充值/退款只读详情。
- 小程序既定16个页面、协议同意、微信官方隐私授权顺序、登录会话、退出与注销入口、报价/订单/支付/充值/退款只读状态已接严格正式DTO。
- Flyway V15–V23覆盖后台工作流、支付充值协调、不可变报价/订单快照、事务Outbox、UNKNOWN恢复、跨实例限额、供应商原始金额、买家同意/注销、唯一SUPER_ADMIN例外及受控履约号码保留。
- 外部适配器未配置时保持失败关闭，不伪造支付、退款或充值成功。

## 2. 当前提交的自动化证据

### 后端

- V23聚焦测试：11 tests，0 failures，0 errors；保留期日历年补充测试2/2通过。
- 四环境Spring smoke：DEV/TEST/STAGE/PROD 4/4通过。
- 完整后端：889 tests，0 failures，0 errors，65 skipped。
- Flyway V1–V23：23个迁移文件连续、唯一、无缺号；V23只追加新迁移，未修改V1–V22。
- 当前V23候选未重新执行package。以下制品仅是较早`a8877b4`候选的历史证据，不代表当前提交：
  - `core-0.1.0-SNAPSHOT.jar`：28,350 bytes，SHA256 `EDFA5A2DA0DE170ADDB40A2D7094482474986B881D827DC70E7993F26C39BC7D`
  - `api-0.1.0-SNAPSHOT.jar`：32,648,669 bytes，SHA256 `13BC949A2B35E1C189EE4F8D20650DAD6031B85A98A47B090D4E33EE707CE64E`

### 后台与小程序

- 后台 contracts、state contracts、TypeScript typecheck、Vite build全部通过。
- 小程序 development、legacy、formal contracts全部通过。
- 小程序 TypeScript 历史错误已从约58项收口为0；`tsc --noEmit`通过。
- `mp-weixin` build与AppService load通过，核验106个相对静态依赖。
- 正式产物 Mock、synthetic、sandbox及退役动作扫描0命中。
- `git diff --check`通过。

## 3. 本地仍未完成

- MySQL验证脚本只有静态合同、H2与DryRun证据；脚本当前仍冻结到V22，尚需先升级为V23，再执行真实MySQL 5.7临时库的空库V1→V23、V14→V23、V22→V23、重复执行、中断诊断和真实锁语义验证。
- 微信登录、微信支付、退款、WINLA充值/回调/查单及ECB汇率尚未真实外连。
- V23已完成专用履约手机号存储、对外掩码/HMAC、供应商读取边界及从实际终态起三个日历年后匿名化的本地实现；真实MySQL执行、数据库账号最小权限和真实供应商出口仍待验收。
- 21张后台与16张小程序页面尚未取得同版本实际截图和人工高保真签核；当前统一为 `NOT_EVIDENCED`。
- 当前候选尚未推送、重启或部署；此前环境可用结果不证明本候选已运行。

## 4. 证据边界

- `LOCAL_RELEASE_CANDIDATE_GO`不等于可上线。
- H2、迁移合同和DryRun不等于真实MySQL 5.7通过。
- Fake、fixture、Disabled adapter和官方样例不等于真实微信、支付或WINLA通过。
- typecheck/build/AppService成功不等于视觉像素验收通过。
- 四环境Spring context成功不等于云环境部署、探针或长期稳定性通过。
- 本报告不授权数据库执行、外部调用、推送、重启或部署。
