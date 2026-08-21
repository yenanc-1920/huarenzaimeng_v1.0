import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = process.cwd();
const baselineDir = path.join(root, "docs", "baseline");
const required = [
  "README.md",
  "00-项目总览与当前状态.md",
  "01-治理权限与变更规则.md",
  "02-角色职责与通讯.md",
  "03-产品需求与业务规则.md",
  "04-UI与交互基线.md",
  "05-技术架构与外部集成.md",
  "06-开发工程与代码约束.md",
  "07-质量门禁与证据边界.md",
  "08-环境部署与运维.md",
  "09-问题台账与经验.md",
  "10-版本计划与任务台账.md",
  "11-来源与历史映射.md",
  "12-V1全功能验收矩阵.md",
  "13-V1上线试运行执行规划.md",
  "CHANGELOG.md",
];

const failures = [];
const read = (file) => fs.readFileSync(file, "utf8");

for (const name of required) {
  const file = path.join(baselineDir, name);
  if (!fs.existsSync(file)) failures.push(`missing:${name}`);
}

for (const name of required.filter((name) => name !== "CHANGELOG.md")) {
  const file = path.join(baselineDir, name);
  if (fs.existsSync(file) && !read(file).includes("是否为执行基线: 是")) {
    failures.push(`not-execution-baseline:${name}`);
  }
}

const agentsFile = path.join(root, "AGENTS.md");
if (!fs.existsSync(agentsFile)) {
  failures.push("missing:AGENTS.md");
} else {
  const agents = read(agentsFile);
  if (!agents.includes("docs/baseline/README.md") || !agents.includes("唯一当前权威来源")) {
    failures.push("invalid:AGENTS.md-entry");
  }
}

const ledgerFile = path.join(baselineDir, "09-问题台账与经验.md");
if (fs.existsSync(ledgerFile)) {
  const ledger = read(ledgerFile);
  for (const marker of ["OPEN-01", "OPEN-09", "责任角色", "唯一下一动作", "关闭条件"]) {
    if (!ledger.includes(marker)) failures.push(`ledger-missing:${marker}`);
  }
}

const acceptanceFile = path.join(baselineDir, "12-V1全功能验收矩阵.md");
if (fs.existsSync(acceptanceFile)) {
  const acceptance = read(acceptanceFile);
  for (const marker of ["FN-MP-01", "FN-AD-01", "FN-PL-01", "当前状态", "目标证据", "唯一下一动作"]) {
    if (!acceptance.includes(marker)) failures.push(`acceptance-missing:${marker}`);
  }
}

const secretShape = /(appsecret|token|private.?key|session_key)\s*[:=]\s*[A-Za-z0-9+/=_-]{12,}/iu;
for (const name of required) {
  const file = path.join(baselineDir, name);
  if (fs.existsSync(file) && secretShape.test(read(file))) failures.push(`secret-shaped-value:${name}`);
}

if (failures.length > 0) {
  console.error(`PROJECT_BASELINE_INVALID\n${failures.join("\n")}`);
  process.exit(1);
}

console.log(`PROJECT_BASELINE_OK files=${required.length} authority=${required.length - 1}`);
