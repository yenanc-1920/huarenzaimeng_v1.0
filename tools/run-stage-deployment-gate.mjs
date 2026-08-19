import { spawnSync } from 'node:child_process'
import { readdirSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import process from 'node:process'

const root = resolve(fileURLToPath(new URL('..', import.meta.url)))
const windows = process.platform === 'win32'
const node = process.execPath
const mvn = windows ? 'mvn.cmd' : 'mvn'
const mavenBase = ['-B', '-ntp', '-s', '.mvn/settings.xml']
if (process.env.MAVEN_OFFLINE === '1') mavenBase.push('-o')
if (process.env.MAVEN_REPO_LOCAL) mavenBase.push(`-Dmaven.repo.local=${process.env.MAVEN_REPO_LOCAL}`)

function run(label, command, args, cwd = root) {
  process.stdout.write(`\n=== ${label} ===\n`)
  const invocation = windows && command.endsWith('.cmd')
    ? { command: process.env.ComSpec || 'cmd.exe', args: ['/d', '/s', '/c', command, ...args] }
    : { command, args }
  const result = spawnSync(invocation.command, invocation.args, { cwd, stdio: 'inherit', shell: false })
  if (result.error) throw new Error(`${label}: ${result.error.message}`)
  if (result.status !== 0) throw new Error(`${label}: exit ${result.status}`)
}

function releaseJar() {
  const target = join(root, 'apps', 'api', 'target')
  const jars = readdirSync(target).filter(name => /^api-.*\.jar$/.test(name) && !name.endsWith('.original'))
  if (jars.length !== 1) throw new Error(`STAGE_JAR_IDENTITY_INVALID count=${jars.length}`)
  return join(target, jars[0])
}

function entries(jar) {
  const result = spawnSync('jar', ['tf', jar], { cwd: root, encoding: 'utf8', shell: false })
  if (result.error || result.status !== 0) throw new Error('STAGE_JAR_ENTRY_READ_FAILED')
  return result.stdout.split(/\r?\n/)
}

try {
  // Reuse the already proven deterministic whole-repository gate first.
  run('full deterministic repository gate', node, ['tools/run-test-deployment-gate.mjs'])
  run('STAGE startup contracts', mvn, [
    ...mavenBase, '-pl', 'apps/api', '-am',
    '-Dtest=StageProfileApplicationSmokeTest,StageFlywayMigrationRunnerTest,ReleaseSecretBoundaryValidatorTest,ReleaseMigrationGateFilterTest,BuildSelectionContractTest',
    '-Dsurefire.failIfNoSpecifiedTests=false', 'test'
  ])
  run('STAGE release package', mvn, [...mavenBase, '-pl', 'apps/api', '-am', 'package', '-DskipTests'])
  const jarEntries = entries(releaseJar())
  if (jarEntries.some(entry => entry.startsWith('BOOT-INF/classes/db/devdata/'))) {
    throw new Error('DEVDATA_PRESENT_IN_STAGE_JAR')
  }
  for (const required of [
    'BOOT-INF/classes/application-stage-mysql.yml',
    'BOOT-INF/classes/db/migration/V14__add_v1_business_read_models.sql'
  ]) {
    if (!jarEntries.includes(required)) throw new Error(`STAGE_JAR_ENTRY_MISSING ${required}`)
  }
  process.stdout.write('\nSTAGE_DEPLOYMENT_GATE_GO\n')
} catch (error) {
  process.stderr.write(`\nSTAGE_DEPLOYMENT_GATE_NO_GO ${error.message}\n`)
  process.exitCode = 1
}
