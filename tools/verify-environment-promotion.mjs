import { spawnSync } from 'node:child_process'
import process from 'node:process'

function readArgument(name) {
  const index = process.argv.indexOf(name)
  return index === -1 ? null : process.argv[index + 1]
}

function resolveCommit(ref) {
  const result = spawnSync('git', ['rev-parse', '--verify', `${ref}^{commit}`], {
    encoding: 'utf8',
    shell: false,
  })
  if (result.status !== 0) throw new Error(`PROMOTION_REF_UNAVAILABLE ref=${ref}`)
  return result.stdout.trim().toLowerCase()
}

export function verifyPromotionIdentity({ environment, currentCommit, upstreamCommit }) {
  const shaPattern = /^[0-9a-f]{40}$/
  if (!environment || !shaPattern.test(currentCommit) || !shaPattern.test(upstreamCommit)) {
    throw new Error('PROMOTION_IDENTITY_INPUT_INVALID')
  }
  if (currentCommit !== upstreamCommit) {
    throw new Error(`PROMOTION_COMMIT_MISMATCH environment=${environment} current=${currentCommit} upstream=${upstreamCommit}`)
  }
  return { status: 'GO', environment, currentCommit, upstreamCommit }
}

function main() {
  const environment = readArgument('--environment')
  const currentRef = readArgument('--current-ref') || 'HEAD'
  const upstreamRef = readArgument('--upstream-ref')
  if (!upstreamRef) throw new Error('PROMOTION_UPSTREAM_REF_REQUIRED')
  const result = verifyPromotionIdentity({
    environment,
    currentCommit: resolveCommit(currentRef),
    upstreamCommit: resolveCommit(upstreamRef),
  })
  process.stdout.write(`${JSON.stringify(result)}\n`)
}

if (process.argv[1]?.endsWith('verify-environment-promotion.mjs')) {
  try {
    main()
  } catch (error) {
    process.stderr.write(`${error.message}\n`)
    process.exitCode = 1
  }
}
