import { spawnSync } from 'node:child_process'
import { chmodSync, existsSync, lstatSync, readdirSync, rmSync } from 'node:fs'
import { basename, join, resolve, sep } from 'node:path'
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

function jarPath() {
  const target = join(root, 'apps', 'api', 'target')
  const jars = readdirSync(target)
    .filter(name => /^api-.*\.jar$/.test(name) && !name.endsWith('.original'))
    .sort()
  if (jars.length !== 1) throw new Error(`DEV_JAR_IDENTITY_INVALID count=${jars.length}`)
  return join(target, jars[0])
}

function jarEntries(jar) {
  const result = spawnSync('jar', ['tf', jar], { cwd: root, encoding: 'utf8', shell: false })
  if (result.error || result.status !== 0) throw new Error('JAR_ENTRY_READ_FAILED')
  return result.stdout.split(/\r?\n/)
}

function removeGeneratedTarget(target) {
  const absolute = resolve(target)
  if (!absolute.startsWith(`${root}${sep}`) || basename(absolute) !== 'target') {
    throw new Error(`UNSAFE_GENERATED_TARGET ${absolute}`)
  }
  if (!existsSync(absolute)) return
  const makeWritable = path => {
    const stat = lstatSync(path)
    if (stat.isDirectory()) {
      for (const name of readdirSync(path)) makeWritable(join(path, name))
    }
    chmodSync(path, stat.isDirectory() ? 0o777 : 0o666)
  }
  makeWritable(absolute)
  rmSync(absolute, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}

try {
  run('git diff check', 'git', ['diff', '--check'])
  run('DEV startup blockers', mvn, [
    ...mavenBase,
    '-pl', 'apps/api', '-am',
    '-Dtest=DevProfileApplicationSmokeTest,ReleaseSecretBoundaryValidatorTest,DevelopmentFlywayMigrationRunnerTest,BuildSelectionContractTest',
    '-Dsurefire.failIfNoSpecifiedTests=false',
    'test'
  ])
  run('admin contracts', npm, ['run', 'test:contracts'], join(root, 'apps', 'admin-web'))
  run('admin build', npm, ['run', 'build'], join(root, 'apps', 'admin-web'))
  run('miniapp contracts', npm, ['run', 'test:frontend-contracts'], join(root, 'apps', 'miniapp'))
  run('V2 H5 contracts and build', npm, ['run', 'build:h5:v2'], join(root, 'apps', 'miniapp'))
  removeGeneratedTarget(join(root, 'modules', 'core', 'target'))
  removeGeneratedTarget(join(root, 'apps', 'api', 'target'))
  run('backend full test', mvn, [...mavenBase, '-pl', 'apps/api', '-am', 'clean', 'test'])
  run('release package', mvn, [...mavenBase, '-pl', 'apps/api', '-am', 'package', '-DskipTests'])
  const releaseEntries = jarEntries(jarPath())
  if (releaseEntries.some(entry => entry.startsWith('BOOT-INF/classes/db/devdata/'))) {
    throw new Error('DEVDATA_PRESENT_IN_RELEASE_JAR')
  }
  run('DEV package', mvn, [...mavenBase, '-pl', 'apps/api', '-am', 'package', '-DskipTests', '-Plocal-devdata'])
  const devEntries = jarEntries(jarPath())
  if (!devEntries.some(entry => entry.startsWith('BOOT-INF/classes/db/devdata/'))) {
    throw new Error('DEVDATA_ABSENT_FROM_DEV_JAR')
  }
  if (!devEntries.includes('BOOT-INF/classes/db/migration/V14__add_v1_business_read_models.sql')) {
    throw new Error('V14_ABSENT_FROM_DEV_JAR')
  }

  process.stdout.write('\nDEV_DEPLOYMENT_GATE_GO\n')
} catch (error) {
  process.stderr.write(`\nDEV_DEPLOYMENT_GATE_NO_GO ${error.message}\n`)
  process.exitCode = 1
}
