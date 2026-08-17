import { spawnSync } from 'node:child_process'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import process from 'node:process'

const root = resolve(fileURLToPath(new URL('..', import.meta.url)))
const windows = process.platform === 'win32'
const npm = windows ? 'npm.cmd' : 'npm'
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

try {
  run('git diff check', 'git', ['diff', '--check'])
  run('DEV startup and build contracts', mvn, [
    ...mavenBase,
    '-pl', 'apps/api', '-am',
    '-Dtest=DevProfileApplicationSmokeTest,ReleaseSecretBoundaryValidatorTest,DevelopmentFlywayMigrationRunnerTest,BuildSelectionContractTest',
    '-Dsurefire.failIfNoSpecifiedTests=false',
    'test'
  ])
  run('admin contracts', npm, ['run', 'test:contracts'], resolve(root, 'apps/admin-web'))
  run('admin build', npm, ['run', 'build'], resolve(root, 'apps/admin-web'))
  run('miniapp contracts', npm, ['run', 'test:frontend-contracts'], resolve(root, 'apps/miniapp'))
  run('miniapp build', npm, ['run', 'build:mp-weixin:dev'], resolve(root, 'apps/miniapp'))
  process.stdout.write('\nDEV_FAST_GATE_PASS\n')
} catch (error) {
  process.stderr.write(`\nDEV_FAST_GATE_FAIL ${error.message}\n`)
  process.exitCode = 1
}
