import process from 'node:process'

const environments = [
  ['DEV', process.env.HUAREN_DEV_BASE_URL || 'https://huaren-api-dev-284852-10-1456291159.sh.run.tcloudbase.com'],
  ['TEST', process.env.HUAREN_TEST_BASE_URL || 'https://huaren-api-test-284852-10-1456291159.sh.run.tcloudbase.com'],
  ['STAGE', process.env.HUAREN_STAGE_BASE_URL || 'https://huaren-api-stage-284852-10-1456291159.sh.run.tcloudbase.com'],
  ['PROD', process.env.HUAREN_PROD_BASE_URL || 'https://huaren-api-prod-284852-10-1456291159.sh.run.tcloudbase.com'],
]

const probes = [
  ['/actuator/health', body => body?.status === 'UP'],
  ['/api/v1/home/temporal-overview', body => body && typeof body === 'object' && !Array.isArray(body)],
  ['/api/v1/catalog?operatorCode=GRAMEENPHONE', body => body && typeof body === 'object' && !Array.isArray(body)],
  ['/api/v1/directory/cities', body => Array.isArray(body) || Array.isArray(body?.data)],
  ['/api/v1/content/life-items', body => Array.isArray(body) || Array.isArray(body?.data)],
]

function classifyBody(contentType, text) {
  if (!contentType.toLowerCase().includes('application/json')) return { kind: 'NON_JSON', body: null }
  try {
    return { kind: 'JSON', body: JSON.parse(text) }
  } catch {
    return { kind: 'INVALID_JSON', body: null }
  }
}

function describeBody(body) {
  if (Array.isArray(body)) return `array:${body.length}`
  if (!body || typeof body !== 'object') return typeof body
  return `object:${Object.keys(body).sort().join(',')}`
}

async function probe(environment, baseUrl, path, validator) {
  const started = Date.now()
  try {
    const response = await fetch(`${baseUrl}${path}`, {
      headers: { accept: 'application/json' },
      redirect: 'error',
      signal: AbortSignal.timeout(15_000),
    })
    const text = await response.text()
    const parsed = classifyBody(response.headers.get('content-type') || '', text)
    const valid = response.status >= 200 && response.status < 300 && parsed.kind === 'JSON' && validator(parsed.body)
    return {
      environment, path, status: response.status, bodyKind: parsed.kind,
      bodyShape: describeBody(parsed.body), elapsedMs: Date.now() - started,
      outcome: valid ? 'GO' : 'NO_GO',
    }
  } catch (error) {
    return {
      environment, path, status: null, bodyKind: 'NETWORK_ERROR',
      elapsedMs: Date.now() - started, outcome: 'NO_GO', error: error.name,
    }
  }
}

export async function verifyCloudBaseEnvironments() {
  const results = []
  for (const [environment, baseUrl] of environments) {
    for (const [path, validator] of probes) results.push(await probe(environment, baseUrl, path, validator))
  }
  return results
}

try {
  const results = await verifyCloudBaseEnvironments()
  console.table(results)
  if (results.some(result => result.outcome !== 'GO')) process.exitCode = 1
} catch (error) {
  process.stderr.write(`CLOUDBASE_ENVIRONMENT_VERIFICATION_FAILED ${error.message}\n`)
  process.exitCode = 1
}
