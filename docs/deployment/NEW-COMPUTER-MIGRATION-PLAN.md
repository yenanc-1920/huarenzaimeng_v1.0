# 华人在孟新电脑迁移与角色恢复方案

> 任务ID：`PC-MIGRATION-01`  
> 适用范围：从当前Windows电脑迁移到另一台Windows电脑  
> 执行原则：Git恢复受版本控制内容，加密介质恢复本地专属内容，账号和凭据在新电脑重新认证，角色依靠`docs/baseline/`恢复当前记忆。  
> 本文是执行清单；当前权威仍以`docs/baseline/08-环境部署与运维.md`、`02-角色职责与通讯.md`和`10-版本计划与任务台账.md`为准。

## 1. 目标与完成条件

迁移完成必须同时满足：

- 新电脑能从正确远端取得正确分支和冻结提交；
- 当前未提交、未跟踪及被Git忽略但需保留的项目资料没有丢失；
- Secret、token、Cookie、私钥和登录缓存没有进入Git或普通压缩包；
- Java、Node、PowerShell及项目隔离运行时按项目要求重新建立；
- 同一OpenAI账号／工作区可见的旧Codex任务继续沿用；不可见时按角色恢复卡建立新执行窗口；
- 新空空和各角色只从当前基线恢复上下文，不以旧聊天记忆覆盖基线；
- 基线检查、定向构建和完整DEV门禁按顺序通过；
- 微信云托管、GitHub、微信开发者工具等外部账户仅完成重新登录和只读核验，不自动改配置或发布。

## 2. 迁移边界

### 2.1 通过Git恢复

- 已提交源码、测试、流水线和`docs/baseline/`；
- 当前远端分支和提交历史；
- 不含真实值的环境变量模板。

### 2.2 通过加密迁移包恢复

- 经逐项确认需要保留的未提交补丁和未跟踪文件；
- `项目管理/`、`项目调研/`、`团队治理/`、`角色定义/`等被`.gitignore`排除但仍属于项目来源的本地资料；
- 经人工确认属于用户自建的Codex技能、模板和记忆补充文件；
- 其他无法从官方来源重建且不含账户登录缓存的项目资料。

### 2.3 在新电脑重新登录或重新配置

- OpenAI/Codex账号与正确工作区；
- GitHub账号、Git凭据或SSH授权；
- 微信开发者工具；
- 微信云托管控制台；
- 其他外部平台账号。

禁止复制浏览器Cookie、Codex内部会话数据库、微信开发者工具登录缓存或整个用户配置目录来绕过重新认证。

### 2.4 不迁移，直接重建

- `node_modules/`、`target/`、`dist/`、`unpackage/`；
- `.m2-local/`、`.runtime/`、`.cache/`和历史隔离运行时；
- `.tmp/`、`.codex-tmp/`、日志、覆盖率和临时证据；
- 历史RunId、进程状态、端口状态和构建缓存。

## 3. 第一阶段：旧电脑冻结

### 3.1 建立迁移目录

在加密移动硬盘或已启用BitLocker的目录中建立：

```text
huarenzaimeng-migration-YYYYMMDD/
  00-manifest/
  01-git-dirty/
  02-local-project-docs/
  03-user-authored-codex-assets/
  04-config-key-inventory/
  05-verification/
```

不得把迁移包放入项目仓库。不得使用不加密的网盘、聊天工具或邮件传递敏感材料。

### 3.2 冻结Git身份

在项目根目录执行并保存输出到迁移包的`00-manifest`：

```powershell
git branch --show-current
git rev-parse HEAD
git remote -v
git status --short
git log -5 --oneline --decorate
git ls-remote origin refs/heads/dev refs/heads/deploy/dev refs/heads/codex/v2-h5-channel-transition
```

执行时必须重新记录实际结果，不得照抄本文中的历史值。冻结后暂停代码修改、合并、部署和环境变量调整，直到迁移包校验完成。

### 3.3 处理当前Git工作区

当前已知有5项来源未决内容，迁移时仍须以实时`git status`为准：

```text
docs/delivery/V1-DELIVERY-RECOVERY-PLAN.md
docs/deployment/DEV-V24-CANDIDATE-PROMOTION-CHECKLIST.md
docs/operations/CloudBase-DEV-environment-incident-log.md
docs/operations/development-incident-and-predeployment-baseline.md
docs/delivery/V1-PROJECT-HANDOVER-20260820.md
```

逐项选择且记录一种处理方式：

- `COMMIT`：已确认属于当前权威且适合进入Git，使用精确文件列表提交；
- `PATCH`：暂不应提交，导出二进制安全补丁；
- `COPY`：未跟踪或不适合补丁，原文件单独复制；
- `EXCLUDE`：明确为缓存或可重建内容，不迁移。

禁止执行`git add .`、清理、reset、checkout覆盖或批量删除。

未提交受控文件导出示例：

```powershell
git diff --binary -- docs/delivery/V1-DELIVERY-RECOVERY-PLAN.md docs/deployment/DEV-V24-CANDIDATE-PROMOTION-CHECKLIST.md docs/operations/CloudBase-DEV-environment-incident-log.md docs/operations/development-incident-and-predeployment-baseline.md > E:\SecureMigration\01-git-dirty\tracked.patch
```

未跟踪文件直接复制到`01-git-dirty/untracked/`并保持相对路径。生成补丁和复制前先检查内容是否含Secret、token、完整手机号或敏感响应。

### 3.4 盘点Git忽略的本地资料

不要把`.m2-local`、构建产物和临时目录产生的海量文件纳入迁移。只对下列项目来源目录做人工清单和复制：

```text
项目管理/
项目调研/
团队治理/
角色定义/
项目说明.md
华人在孟 V1.0 产品设计规范.md
sprint-plan-v3.md
reloadly_api.yaml
```

若文件包含未脱敏凭据，先移出普通资料包，登记到`04-config-key-inventory`，实际值只能进入单独加密的秘密迁移通道。

### 3.5 Secret与外部配置

只建立“键名、所在平台、用途、负责人、是否需在新电脑重新认证”的清单，不把真实值写入交接文档。优先策略：

- 微信云托管环境变量继续保留在平台，不导出真实值；
- GitHub、OpenAI、微信开发者工具均在新电脑重新登录；
- SSH私钥原则上重新签发；确需迁移时使用独立加密容器并限制权限；
- `.env`、证书或本地测试凭据如仍需使用，逐项加密迁移，恢复后检查权限；
- 旧电脑确认新电脑可用后再撤销不再需要的设备会话，不提前破坏回退能力。

### 3.6 Codex对话和角色材料

1. 确认当前使用的OpenAI账号、登录方式和工作区名称，不记录密码。
2. 保留`02-角色职责与通讯.md`中的角色与任务ID。
3. 对关键任务可创建只读分享链接或做账号数据导出，目的仅为历史追溯，不作为新任务长期上下文。
4. 不复制完整聊天日志进仓库，不把角色长回执写入基线。
5. 只迁移经人工确认的用户自建技能、模板和记忆补充；不复制Codex程序缓存、内部数据库、认证状态或系统自带资源。

官方边界：Chat/Work云端对话可跨端同步；Codex历史与普通ChatGPT历史分开，分享也不会转移本地文件、设备权限、凭据、保存记忆或工作区权限。因此项目恢复必须以仓库基线为主。

### 3.7 生成完整性校验

对迁移包中的每个文件生成SHA-256清单：

```powershell
Get-ChildItem E:\SecureMigration -Recurse -File | Get-FileHash -Algorithm SHA256 | Export-Csv E:\SecureMigration\05-verification\sha256.csv -NoTypeInformation -Encoding UTF8
```

完成后安全弹出介质；旧电脑保留不变，作为迁移失败时的回退源。

## 4. 第二阶段：新电脑基础安装

### 4.1 推荐目录

继续使用：

```text
E:\workspace\huarenzaimeng
```

若新电脑必须使用其他路径，先记录路径差异；不得批量改写项目文档中的固定路径，除非另立迁移后变更任务。

### 4.2 安装与身份

安装并核对：

- Git；
- Java 17；
- Node.js及npm；
- PowerShell 7；
- 微信开发者工具；
- Codex桌面端。

旧电脑2026-09-10只读参考版本为Git 2.55.0、Node 24.18.0、npm 11.16.0、Temurin Java 17.0.19、PowerShell 7.6.4；新电脑最终版本以项目清单和实际兼容验证为准，不要求盲目复制缓存。

### 4.3 重新认证

- 登录与旧电脑相同的OpenAI账号、相同工作区；
- 登录GitHub并验证仓库只读访问；
- 登录微信开发者工具和微信云托管控制台；
- 首轮只验证可访问性，不改Secret、环境变量、数据库或发布配置。

## 5. 第三阶段：恢复仓库与本地资料

### 5.1 克隆与固定分支

```powershell
git clone https://github.com/yenanc-1920/huarenzaimeng_v1.0.git E:\workspace\huarenzaimeng
Set-Location E:\workspace\huarenzaimeng
git switch codex/v2-h5-channel-transition
git branch --show-current
git rev-parse HEAD
git remote -v
```

实际HEAD必须与旧电脑冻结清单一致。若不一致，停止恢复补丁并先查明远端、分支或提交漂移。

### 5.2 恢复本地资料

1. 先校验迁移包SHA-256；
2. 恢复`02-local-project-docs`到原相对路径；
3. 使用`git apply --check`检查补丁，成功后才`git apply`；
4. 恢复未跟踪文件；
5. 执行`git status --short`并与旧电脑冻结清单逐项比较；
6. 恢复需要的本地Secret后立即检查其仍被`.gitignore`排除。

```powershell
git apply --check E:\SecureMigration\01-git-dirty\tracked.patch
git apply E:\SecureMigration\01-git-dirty\tracked.patch
git status --short
git check-ignore -v .env
```

任何补丁冲突都不得用`--reject`批量糊入；应返回旧电脑或按固定对象逐文件处理。

## 6. 第四阶段：恢复Codex与角色

### 6.1 先恢复空空

在Codex中添加新电脑上的项目目录。若旧空空任务可见，继续原任务；若不可见，只新建一个空空任务并发送：

```text
你接任“空空”，担任“华人在孟”项目PM／总调度。固定仓库为当前打开的huarenzaimeng目录。
开始前只读AGENTS.md、docs/baseline/README.md、00、01、02、08、09、10、15至18及CHANGELOG，核对当前分支、HEAD、远端和工作区。
聊天只作执行窗口，docs/baseline才是长期权威。不要根据旧聊天重建事实，不改变小前、小U、小D、小后、小测、小架、小产职责。
首次仅输出当前Git身份、当前阶段、问题台账、外部依赖、风险边界、角色通讯可达性和唯一下一动作；不得发布、改密钥、操作数据库或真实资金。
```

### 6.2 恢复专业角色

先在侧栏查找原任务ID。原任务可见则沿用；不可见才创建对应新任务。每个角色共同读取`README、00、02、09、10`，再追加：

| 角色 | 必读专题 |
|---|---|
| 小前 | 06、16、17、18 |
| 小U | 16、18 |
| 小D | 01、07、17、18 |
| 小后 | 05（V1归档）、06、17、18 |
| 小测 | 07、09、18 |
| 小架 | 01、06、17、18 |
| 小产 | 01、07、08、09、10、18 |

新角色统一启动语：

```text
你继续担任华人在孟项目的【角色名称】，职责和权限以docs/baseline/02为准。
先读README、00、02和指定专题基线；只接受当前问题ID的增量任务，不复述或重建全部历史。
旧聊天、历史文档和截图仅供追溯，与基线冲突时停止并报告。
终态按02号统一模板回传：状态、P0/P1、固定对象、验证计数、证据边界和唯一下一动作。
```

### 6.3 通讯验收

空空只向每个角色发送一次：

```text
【新电脑通讯恢复】继续沿用原角色和职责。请读取当前基线并仅回复：通讯可达／不可达、当前任务ID或无、是否存在固定对象漂移。无需复述历史。
```

通讯结果登记到`02-角色职责与通讯.md`，但不得把完整回复复制进去。

## 7. 第五阶段：项目验证

按从低到高顺序执行，前一步失败即停止：

1. `git status --short`与迁移清单一致；
2. `node tools/check-project-baseline.mjs`；
3. 重新下载环境治理清单固定的隔离PowerShell/Python运行时到`.cache/environment-governance/runtimes`，重新计算归档和可执行文件SHA；不得复制旧缓存或修改全局PATH／ExecutionPolicy；
4. 使用唯一入口`项目管理/正式交付/D4-开发计划与工程准备/环境治理/Invoke-ProjectValidation.cmd`运行无残留探针；
5. 执行当前任务所需的定向测试和构建；
6. 最后执行`node tools/run-dev-deployment-gate.mjs`。

上述均为本地或环境就绪证据，不等于微信云托管版本、真实数据库、真实业务或生产PASS。

## 8. 第六阶段：外部只读核验

- 核对GitHub当前分支、`dev`、`deploy/dev`；
- 核对微信云托管服务、版本、实例和环境，不改配置；
- 核对DEV H5和后台登录；
- 核对微信开发者工具项目可打开；
- 不进行生产发布、数据库写入、Secret变更或真实资金操作。

需要外部写入时另立任务并由南哥按权限边界授权。

## 9. 回退与旧电脑退役

新电脑满足全部完成条件前：

- 不删除旧电脑项目；
- 不撤销旧电脑必要访问；
- 不清空加密迁移包；
- 不把新电脑失败状态反写到生产或微信云托管。

新电脑连续完成Git、基线、构建、角色通讯和外部只读核验后，再执行：

1. 备份最终迁移清单；
2. 撤销旧电脑不再需要的GitHub、OpenAI和微信相关设备会话；
3. 安全删除旧电脑本地Secret；
4. 按设备处置计划擦除旧磁盘；
5. 在08、10和CHANGELOG登记迁移完成，不把设备迁移登记为业务验收PASS。

## 10. 最终验收表

| 检查项 | 状态 |
|---|---|
| Git分支、HEAD、远端与冻结清单一致 | NOT_RUN |
| 5项已知工作区内容逐项恢复 | NOT_RUN |
| 本地来源资料完整且SHA一致 | NOT_RUN |
| Secret未进入Git或普通压缩包 | NOT_RUN |
| 基线检查通过 | NOT_RUN |
| 隔离运行时重新下载、验哈希、无残留探针通过 | NOT_RUN |
| 定向构建与完整DEV门禁通过 | NOT_RUN |
| 空空恢复并能说明当前阶段和唯一下一动作 | NOT_RUN |
| 七个专业角色通讯可达或已按基线重建 | NOT_RUN |
| GitHub、微信云托管和微信开发者工具只读访问正常 | NOT_RUN |
| 旧电脑退役前回退窗口保留 | NOT_RUN |
