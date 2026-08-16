import assert from 'node:assert/strict'
import { createAdminReadController } from '../src/api/admin-read-controller.ts'
import { parseA110ReadResponse, parseAdminPageProjection } from '../src/api/admin-read.ts'

const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const a100 = { schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'CASE-V1', pageId: 'A100', role: 'SUPER_ADMIN', items: [{ caseRef: 'CASE-1', sourceType: 'ADMIN_MANUAL', issueType: 'TOPUP_STATUS_QUERY', relatedOrderRef: null, priorityCode: 'NORMAL', state: 'OPEN', ownerRef: null, updatedAt: '2026-08-16T00:00:00Z' }] }
const a120 = { schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'REVIEW-V1', pageId: 'A120', role: 'SUPER_ADMIN', items: [] }
assert.ok(parseAdminPageProjection(a100, 'A100'))
assert.ok(parseAdminPageProjection(a120, 'A120'))

const deniedA110 = { requestRef: 'REQ-1', viewState: 'ACCESS_DENIED', projectCode: 'A110_ACCESS_DENIED', schemaVersion: 'A110_RECONCILIATION_READ_V1', roleProjection: null, roleBindingVersion: null, authorizationDecisionVersion: null, projectionVersion: null, items: [], allowedActions: [], retryClass: 'NONE' }
const errorA110 = { ...deniedA110, requestRef: 'REQ-2', viewState: 'READ_ERROR', projectCode: 'A110_READ_ERROR', allowedActions: ['READ_REFRESH'] }
assert.equal(parseA110ReadResponse(deniedA110).status, 'ACCESS_DENIED')
assert.equal(parseA110ReadResponse(errorA110).status, 'UNAVAILABLE')

const readyState = { status: 'READY', data: a100, message: '真实DTO已读取' }
const failure = deferred(), states = []
let calls = 0
const controller = createAdminReadController((state) => states.push(state), async () => ++calls === 1 ? readyState : failure.promise)
await controller.refresh({ mode: 'PROJECT_API_PROXY', pageId: 'A100' })
const failing = controller.refresh({ mode: 'PROJECT_API_PROXY', pageId: 'A100' })
assert.equal(states.at(-1).status, 'LOADING')
assert.equal(states.at(-1).data, null)
failure.reject(new Error('read failed'))
await failing
assert.equal(states.at(-1).status, 'UNAVAILABLE')
assert.equal(states.at(-1).data, null)

const oldRequest = deferred(), currentRequest = deferred(), overlapStates = []
let overlaps = 0
const overlapController = createAdminReadController((state) => overlapStates.push(state), async () => ++overlaps === 1 ? oldRequest.promise : currentRequest.promise)
const oldRefresh = overlapController.refresh({ mode: 'PROJECT_API_PROXY', pageId: 'A100' })
const currentRefresh = overlapController.refresh({ mode: 'PROJECT_API_PROXY', pageId: 'A120' })
currentRequest.resolve({ status: 'READY', data: a120, message: '真实DTO已读取' })
assert.equal(await currentRefresh, true)
const count = overlapStates.length
oldRequest.resolve(readyState)
assert.equal(await oldRefresh, false)
assert.equal(overlapStates.length, count)
assert.equal(overlapStates.at(-1).data.pageId, 'A120')
console.log('admin pure DTO/state contracts: PASS (DENIED; READ_ERROR; READY_WITHDRAWAL; LATE_RESPONSE_DROPPED)')
