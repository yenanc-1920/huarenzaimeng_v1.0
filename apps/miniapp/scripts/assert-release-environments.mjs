import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const config = JSON.parse(await readFile(path.join(root, 'config/release-environments.json'), 'utf8'))
const expected = {
  dev: ['DEV', 'huaren-api-dev', 'https://dev.guiye.xyz'],
  test: ['TEST', 'huaren-api-test', 'https://test.guiye.xyz'],
  stage: ['STAGE', 'huaren-api-stage', 'https://stage.guiye.xyz'],
  prod: ['PROD', 'huaren-api-prod', 'https://api.guiye.xyz'],
}
if (config.cloudEnvironmentId !== 'prod-d3g9ntdmsdf9d7877') throw new Error('CLOUD_ENVIRONMENT_ID_DRIFT')
if (JSON.stringify(Object.keys(config.environments).sort()) !== JSON.stringify(Object.keys(expected).sort())) throw new Error('ENVIRONMENT_SET_DRIFT')
for (const [key, values] of Object.entries(expected)) {
  const actual = config.environments[key]
  if (!actual || actual.releaseEnv !== values[0] || actual.service !== values[1] || actual.apiOrigin !== values[2]) throw new Error(`ENVIRONMENT_MAPPING_DRIFT:${key}`)
}
const transport = await readFile(path.join(root, 'src/api/wechat-development-transport.ts'), 'utf8')
if (transport.includes("'huaren-api-it'")) throw new Error('LEGACY_IT_SERVICE_FORBIDDEN')
if (!transport.includes("build.VITE_RELEASE_ENV || 'PROD'") || !transport.includes('WECHAT_CLOUD_BUILD_IDENTITY_INVALID')) throw new Error('PRODUCTION_BUILD_FAIL_CLOSED_MISSING')
console.log('RELEASE_ENVIRONMENT_CONTRACTS_OK')
