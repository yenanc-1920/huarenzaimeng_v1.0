import assert from 'node:assert/strict'
import test from 'node:test'
import { verifyPromotionIdentity } from './verify-environment-promotion.mjs'

const sha = '1234567890abcdef1234567890abcdef12345678'

test('accepts the exact upstream commit', () => {
  assert.deepEqual(verifyPromotionIdentity({ environment: 'TEST', currentCommit: sha, upstreamCommit: sha }), {
    status: 'GO', environment: 'TEST', currentCommit: sha, upstreamCommit: sha,
  })
})

test('rejects a different commit', () => {
  assert.throws(() => verifyPromotionIdentity({
    environment: 'PROD', currentCommit: sha, upstreamCommit: `a${sha.slice(1)}`,
  }), /PROMOTION_COMMIT_MISMATCH/)
})

test('rejects malformed identities', () => {
  assert.throws(() => verifyPromotionIdentity({ environment: 'STAGE', currentCommit: 'HEAD', upstreamCommit: sha }), /INPUT_INVALID/)
})
