import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const BASE_URL = 'https://huaren-api-it-284852-10-1456291159.sh.run.tcloudbase.com'
const TOKEN_FILE = resolve('token.txt')
const EXPECTED_HOST = 'huaren-api-it-284852-10-1456291159.sh.run.tcloudbase.com'

function readToken(text, key) {
  const match = text.match(new RegExp(`^\\s*${key}\\s*[:=]\\s*(\\S+)\\s*$`, 'm'))
  if (!match) throw new Error(`TOKEN_MISSING_${key}`)
  return match[1]
}

async function main() {
  const base = new URL(BASE_URL)
  if (base.protocol !== 'https:' || base.hostname !== EXPECTED_HOST) throw new Error('HOST_BOUNDARY_INVALID')

  const tokenText = await readFile(TOKEN_FILE, 'utf8')
  let buyer = readToken(tokenText, 'HZ_IT_BUYER_SESSION_TOKEN')
  const nonce = randomUUID().replaceAll('-', '').toUpperCase()
  const url = new URL(`/internal/test-readonly/p021/challenge?nonce=${nonce}`, base)
  if (url.origin !== base.origin) throw new Error('HOST_BOUNDARY_VIOLATION')

  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: { accept: 'application/json', cookie: `HZM_IT_SESSION=${buyer}` },
      redirect: 'error',
      signal: AbortSignal.timeout(60_000),
    })
    const raw = await response.text()
    let body
    try {
      body = JSON.parse(raw)
    } catch {
      throw new Error(`HTTP_JSON_INVALID_${response.status}`)
    }
    if (response.status !== 200) throw new Error(`HTTP_STATUS_${response.status}`)
    const keys = ['databaseIdentity', 'mode', 'nonce', 'serviceVersion']
    if (JSON.stringify(Object.keys(body).sort()) !== JSON.stringify(keys)) throw new Error('CHALLENGE_KEYS_MISMATCH')
    if (body.nonce !== nonce) throw new Error('NONCE_MISMATCH')
    process.stdout.write(`${JSON.stringify({
      ExecutionStatus: 'PASS',
      RequestCount: 1,
      ReadOnly: true,
      HttpStatus: response.status,
      CacheControl: response.headers.get('cache-control'),
      NonceMatched: true,
      ServiceVersion: body.serviceVersion,
      Mode: body.mode,
      DatabaseInstanceIdentity: body.databaseIdentity,
    })}\n`)
  } finally {
    buyer = null
  }
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`)
  process.exitCode = 1
})
