import { readFile, readdir } from 'node:fs/promises'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const requested = process.argv[2]
const config = JSON.parse(await readFile(path.join(root, 'config/release-environments.json'), 'utf8'))
const selected = config.environments?.[requested]
if (!selected || !['dev', 'test', 'stage', 'prod'].includes(requested)) throw new Error('RELEASE_ENVIRONMENT_REQUIRED')
const expectedService = `huaren-api-${requested}`
if (selected.service !== expectedService || !/^https:\/\/[a-z0-9.-]+\.guiye\.xyz$/.test(selected.apiOrigin)) {
  throw new Error('RELEASE_ENVIRONMENT_MAPPING_INVALID')
}

const run = (command, args) => {
  const result = spawnSync(command, args, { cwd: root, stdio: 'inherit', env: {
    ...process.env,
    VITE_AUTH_TRANSPORT_MODE: 'WECHAT_DEVELOPMENT',
    VITE_PROJECT_TRANSPORT_MODE: 'WECHAT_DEVELOPMENT',
    VITE_ORDER_RECOVERY_EXTERNAL_AUTH_READY: 'true',
    VITE_CLOUD_ENV_ID: config.cloudEnvironmentId,
    VITE_CLOUD_SERVICE_NAME: selected.service,
    VITE_RELEASE_ENV: selected.releaseEnv,
  } })
  if (result.error) throw result.error
  if (result.status !== 0) process.exit(result.status ?? 1)
}

run(process.execPath, ['scripts/clean-mp-weixin-output.mjs'])
run(process.execPath, ['node_modules/@dcloudio/vite-plugin-uni/bin/uni.js', 'build', '-p', 'mp-weixin'])
run(process.execPath, ['scripts/write-mp-weixin-compat-modules.mjs'])
run(process.execPath, ['scripts/verify-mp-weixin-output.mjs'])

const collect = async directory => {
  const output = []
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) output.push(...await collect(target))
    else if (entry.name.endsWith('.js')) output.push(await readFile(target, 'utf8'))
  }
  return output
}
const artifact = (await collect(path.join(root, 'dist/build/mp-weixin'))).join('\n')
for (const environment of Object.values(config.environments)) {
  const present = artifact.includes(environment.service)
  if (present !== (environment.service === selected.service)) throw new Error(`BUILT_SERVICE_IDENTITY_INVALID:${environment.service}`)
}
if (artifact.includes('huaren-api-it') || !artifact.includes(config.cloudEnvironmentId)) throw new Error('BUILT_CLOUD_IDENTITY_INVALID')
console.log(`MP_WEIXIN_RELEASE_ENVIRONMENT_OK:${requested}:${selected.service}`)
