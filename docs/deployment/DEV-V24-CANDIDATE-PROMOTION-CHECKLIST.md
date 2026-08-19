# DEV V24 候选晋级清单

日期：2026-08-19
候选分支：`codex/v1-delivery-recovery`
清单编制时证据提交：`38955d5`（业务代码基线包含`f7f5915`；触发CI时必须重新冻结分支最新SHA）
当前状态：`LOCAL_GATE_GO / GITHUB_FULL_GATE_NOT_RUN / DEV_NOT_DEPLOYED`

本文只规定DEV晋级顺序，不授权数据库执行、真实支付、真实充值、合并、部署或重启。

## 1. 当前已完成

- 候选分支已推送；工作区干净。
- 后端完整门禁908项零失败、四环境Spring smoke通过。
- 后台与小程序contracts、typecheck、build及AppService load通过。
- Flyway V1-V24连续唯一；MySQL验证脚本AST、DryRun和合同通过，但真实MySQL未执行。
- DEV域名、小程序request合法域名、商户与AppID关联、JSAPI支付能力、隐私指引和订单中心path已准备。
- DEV环境变量已在平台编辑器填写但尚未保存；微信支付和WINLA双开关保持关闭。

## 2. 当前阻断

- 本机GitHub CLI令牌失效，尚未对候选提交触发`dev-predeploy-gate`的`workflow_dispatch`。
- 本地离线Maven仓缺`org.apache.commons:commons-parent:71`，普通JAR已形成但Spring Boot repackage未完成；由GitHub Docker构建补齐这一制品门禁。
- 真实MySQL、真机登录、微信支付/退款、WINLA充值和视觉截图均未执行，不得外推为通过。

## 3. 回来后的唯一安全顺序

1. 恢复GitHub CLI登录，或在GitHub网页手动选择`dev-predeploy-gate`，目标分支必须是`codex/v1-delivery-recovery`。
2. 手动运行只执行`full-release-gate`和DEV Docker镜像构建；`workflow_dispatch`不会运行`promote-deploy-branch`。
3. CI失败即停止，记录首个根因；不自动重试、不推进任何分支。
4. CI通过后，单独确认把候选合入`dev`。对`dev`的push会重新执行完整门禁。
5. 只有`dev`完整门禁通过，工作流才推进`deploy/dev`；CloudBase仅监听`deploy/dev`。
6. 等CloudBase使用旧环境变量完成候选镜像部署并确认8080探针、应用健康和数据库迁移状态。
7. 此时才保存已填写的DEV环境变量，允许CloudBase对候选版本触发一次配置重部署。
8. 配置重部署后先验证微信登录；支付、WINLA和恢复scheduler继续关闭。
9. 微信支付与WINLA只能进入另行批准的小额真实验收，所有真实金额操作由用户亲自执行。

## 4. DEV保存前复核

- `SPRING_PROFILES_ACTIVE=release-mysql,local-mysql`，`SERVER_PORT=8080`。
- JDBC库名与`HZ_DEV_DATABASE_NAME=huarenzaimeng_dev`完全一致。
- App、Flyway密码及三项pepper/HMAC已配置，值不回显且彼此不同。
- 微信登录三开关为`true / wechat-code2session / true`，AppID与预期AppID一致。
- 微信支付保持`disabled / false`；WINLA保持`disabled / false`；恢复scheduler保持`false`。
- 微信支付读取超时为`5000`毫秒；通知URL为DEV HTTPS域名。
- 不含`HZ_IT_*`、测试token、bootstrap token、Mock或synthetic开关。

## 5. 回滚边界

- CI阶段不部署，无运行态回滚。
- `dev`门禁失败不会推进`deploy/dev`。
- CloudBase部署失败时不重复发布；先保留首个失败日志和版本号，再决定回退`deploy/dev`或修复新提交。
- 环境变量保存失败时不打开支付/WINLA开关，不用Fake成功绕过。
- 禁止自动Flyway clean、repair、baseline、删除历史或重置业务库。

## 6. 用户回来后需要介入的事项

1. 恢复GitHub登录或在网页触发一次候选分支工作流。
2. CI通过后授权候选合入`dev`。
3. 候选部署健康后点击保存DEV环境变量。
4. 在微信开发者工具/真机执行登录与页面截图；真实金额步骤继续逐项人工确认。
