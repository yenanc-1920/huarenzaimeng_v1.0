import assert from 'node:assert/strict'
import { createAdminReadController } from '../src/api/admin-read-controller.ts'
import { loadAdminPage, parseA110ReadResponse, parseAdminPageProjection } from '../src/api/admin-read.ts'
import { getSyntheticA110Response } from '../src/data/synthetic.ts'
import { A110_VISUAL_SCENARIOS, buildA110VisualResponse } from './a110-visual-fixture-entry.ts'

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

const allowedRole = { A100: 'CS', A110: 'FIN', A120: 'CONTENT', A130: 'FIN', A140: 'FIN' }
const deniedRole = { A100: 'CONTENT', A110: 'CONTENT', A120: 'CS', A130: 'CS', A140: 'CONTENT' }
const pageFixtures = []

for (const pageId of ['A100', 'A110', 'A120', 'A130', 'A140']) {
  const ready = await loadAdminPage('BUILTIN_SYNTHETIC', pageId, allowedRole[pageId])
  assert.equal(ready.status, 'READY')

  if (pageId === 'A110') assert.equal(parseA110ReadResponse(getSyntheticA110Response(allowedRole[pageId], 'EMPTY'), allowedRole[pageId]).status, 'READY')
  else {
    const emptyProjection = { ...ready.data, items: [] }
    assert.ok(parseAdminPageProjection(emptyProjection, pageId))
  }

  const denied = await loadAdminPage('BUILTIN_SYNTHETIC', pageId, deniedRole[pageId])
  assert.equal(denied.status, 'ACCESS_DENIED')
  assert.equal(denied.data, null)

  const unavailable = await loadAdminPage('PROJECT_API_PROXY', pageId, allowedRole[pageId], async () => new Response('', { status: 500 }))
  const unauthenticated = await loadAdminPage('PROJECT_API_PROXY', pageId, allowedRole[pageId], async () => new Response('', { status: 401 }))
  assert.equal(unavailable.status, 'UNAVAILABLE')
  assert.equal(unavailable.data, null)

  assert.equal(unauthenticated.status, 'UNAUTHENTICATED')
  pageFixtures.push({ pageId, states: ['READY', 'READY_EMPTY', 'UNAUTHENTICATED', 'ACCESS_DENIED', 'UNAVAILABLE'] })
}

const a110VisualSlots = [
  ['ADM02-A110-V01', 'FIN', 'READY', 'DEFAULT'], ['ADM02-A110-V02', 'CS', 'READY', 'DEFAULT'], ['ADM02-A110-V03', 'FIN', 'EMPTY', 'DEFAULT'], ['ADM02-A110-V04', 'CS', 'EMPTY', 'DEFAULT'],
  ['ADM02-A110-V05', 'FIN', 'LOADING', 'DEFAULT'], ['ADM02-A110-V06', 'FIN', 'READ_ERROR', 'DEFAULT'], ['ADM02-A110-V07', 'CONTENT', 'ACCESS_DENIED', 'DEFAULT'], ['ADM02-A110-V08', 'FIN', 'AUTHORITY_UNKNOWN', 'DEFAULT'],
  ['ADM02-A110-V09', 'FIN', 'LONG_RUNNING_UNKNOWN', 'DEFAULT'], ['ADM02-A110-V10', 'FIN', 'ASYMMETRIC_FACTS', 'W_U_ASYMMETRIC'], ['ADM02-A110-V11', 'FIN', 'ASYMMETRIC_FACTS', 'U_D_BIDIRECTIONAL'],
  ['ADM02-A110-V12', 'FIN', 'ASYMMETRIC_FACTS', 'REFUND_LOCAL_OPEN'], ['ADM02-A110-V13', 'FIN', 'REFUND_DELIVERY_CONFLICT', 'DEFAULT'], ['ADM02-A110-V14', 'FIN', 'VERSION_CONFLICT', 'DEFAULT'],
  ['ADM02-A110-V01-Z200', 'FIN', 'READY', 'DEFAULT'], ['ADM02-A110-V02-Z200', 'CS', 'READY', 'DEFAULT'], ['ADM02-A110-V07-Z200', 'CONTENT', 'ACCESS_DENIED', 'DEFAULT'],
  ['ADM02-A110-V01-W800', 'FIN', 'READY', 'DEFAULT'], ['ADM02-A110-V02-W800', 'CS', 'READY', 'DEFAULT'], ['ADM02-A110-V07-W800', 'CONTENT', 'ACCESS_DENIED', 'DEFAULT'],
]
assert.equal(a110VisualSlots.length, 20)
assert.equal(new Set(a110VisualSlots.map(([scenarioId]) => scenarioId)).size, 20)
assert.deepEqual(Object.keys(A110_VISUAL_SCENARIOS).sort(), a110VisualSlots.map(([scenarioId]) => scenarioId).sort())
for (const [scenarioId, role, state, variant] of a110VisualSlots) {
  if (state === 'LOADING') continue
  const parsed = parseA110ReadResponse(buildA110VisualResponse(role, state, variant), role)
  assert.notEqual(parsed.status, 'LOADING', `${scenarioId} must have a deterministic post-read fixture`)
}

const wu = parseA110ReadResponse(buildA110VisualResponse('FIN', 'ASYMMETRIC_FACTS', 'W_U_ASYMMETRIC'), 'FIN').data.items[0]
assert.equal(wu.factSummaries.W.factState, 'CONFIRMED')
assert.equal(wu.factSummaries.U.factState, 'UNKNOWN')
const ud = parseA110ReadResponse(buildA110VisualResponse('FIN', 'ASYMMETRIC_FACTS', 'U_D_BIDIRECTIONAL'), 'FIN').data.items
assert.equal(ud.length, 2)
assert.deepEqual(ud.map((item) => [item.factSummaries.U.factState, item.factSummaries.D.factState]), [['CONFIRMED', 'UNKNOWN'], ['UNKNOWN', 'CONFIRMED']])
const rl = parseA110ReadResponse(buildA110VisualResponse('FIN', 'ASYMMETRIC_FACTS', 'REFUND_LOCAL_OPEN'), 'FIN').data.items[0]
assert.equal(rl.factSummaries.R.factState, 'CONFIRMED')
assert.equal(rl.factSummaries.L.factState, 'PENDING_OR_INFLIGHT')

const a110Loading = deferred()
const a110LoadingStates = []
const a110LoadingController = createAdminReadController((state) => a110LoadingStates.push(state), async () => a110Loading.promise)
const a110LoadingRefresh = a110LoadingController.refresh({ mode: 'BUILTIN_SYNTHETIC', pageId: 'A110', syntheticRole: 'FIN' })
assert.equal(a110LoadingStates.at(-1).status, 'LOADING')
assert.equal(a110LoadingStates.at(-1).data, null)
a110Loading.resolve(await loadAdminPage('BUILTIN_SYNTHETIC', 'A110', 'FIN'))
await a110LoadingRefresh

const a110ReadyState = await loadAdminPage('BUILTIN_SYNTHETIC', 'A110', 'FIN')
const a110Failure = deferred()
const a110WithdrawalStates = []
let a110ReadCount = 0
const a110Controller = createAdminReadController(
  (state) => a110WithdrawalStates.push(state),
  async () => (++a110ReadCount === 1 ? a110ReadyState : a110Failure.promise),
)
await a110Controller.refresh({ mode: 'BUILTIN_SYNTHETIC', pageId: 'A110', syntheticRole: 'FIN' })
const a110FailingRefresh = a110Controller.refresh({ mode: 'PROJECT_API_PROXY', pageId: 'A110', syntheticRole: 'FIN' })
assert.equal(a110WithdrawalStates.at(-1).status, 'LOADING')
assert.equal(a110WithdrawalStates.at(-1).data, null)
a110Failure.reject(new Error('deterministic A110 read failure'))
await a110FailingRefresh
assert.equal(a110WithdrawalStates.at(-1).status, 'UNAVAILABLE')
assert.equal(a110WithdrawalStates.at(-1).data, null)

const firstReady = await loadAdminPage('BUILTIN_SYNTHETIC', 'A100', 'CS')
const errorAfterReady = deferred()
const readyThenErrorStates = []
let readyThenErrorCalls = 0
const readyThenErrorController = createAdminReadController(
  (state) => readyThenErrorStates.push(state),
  async () => (++readyThenErrorCalls === 1 ? firstReady : errorAfterReady.promise),
)
await readyThenErrorController.refresh({ mode: 'BUILTIN_SYNTHETIC', pageId: 'A100', syntheticRole: 'CS' })
const failingRefresh = readyThenErrorController.refresh({ mode: 'PROJECT_API_PROXY', pageId: 'A100', syntheticRole: 'CS' })
assert.equal(readyThenErrorStates.at(-1).status, 'LOADING')
assert.equal(readyThenErrorStates.at(-1).data, null)
errorAfterReady.reject(new Error('deterministic network failure'))
await failingRefresh
assert.equal(readyThenErrorStates.at(-1).status, 'UNAVAILABLE')
assert.equal(readyThenErrorStates.at(-1).data, null)

const oldRequest = deferred()
const currentRequest = deferred()
const lateResponseStates = []
let overlapCalls = 0
const overlapController = createAdminReadController(
  (state) => lateResponseStates.push(state),
  async () => (++overlapCalls === 1 ? oldRequest.promise : currentRequest.promise),
)
const oldRefresh = overlapController.refresh({ mode: 'PROJECT_API_PROXY', pageId: 'A100', syntheticRole: 'CS' })
const currentRefresh = overlapController.refresh({ mode: 'PROJECT_API_PROXY', pageId: 'A120', syntheticRole: 'CONTENT' })
const a120Ready = await loadAdminPage('BUILTIN_SYNTHETIC', 'A120', 'CONTENT')
currentRequest.resolve(a120Ready)
assert.equal(await currentRefresh, true)
assert.equal(lateResponseStates.at(-1).data.pageId, 'A120')
const stateCountBeforeLateResponse = lateResponseStates.length
oldRequest.resolve(firstReady)
assert.equal(await oldRefresh, false)
assert.equal(lateResponseStates.length, stateCountBeforeLateResponse)
assert.equal(lateResponseStates.at(-1).data.pageId, 'A120')

console.log(`admin local state fixtures: PASS (${pageFixtures.length} pages x ${pageFixtures[0]?.states.length ?? 0} states; A110_VISUAL_PRECONDITIONS=20; READY_TO_ERROR; LATE_RESPONSE_DROPPED; formal visual/technical matrices remain NOT_RUN)`)
