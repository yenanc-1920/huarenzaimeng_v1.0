import { parseP021Response, type P021Response } from '../api/order-detail-contract.ts'
import { p021SyntheticResponse } from '../api/order-detail-synthetic.ts'
import { executeP021Read, type P021PageState } from '../domain/order-detail-flow.ts'

export const P021_IT_DELAY_PLANS = [
  'PROJECTION_LOW_VERSION',
  'ORDER_VERSION_CONFLICT',
  'QUOTE_DIGEST_CONFLICT',
  'LATE_LOWER_VERSION_AFTER_READY',
] as const
export type P021ItDelayPlan = typeof P021_IT_DELAY_PLANS[number]

const ORDER_REF = 'IT-P021-AWAITING'
const session = {
  role: 'BUYER' as const,
  projectSubjectRef: 'IT-SUBJECT-P021',
  sessionVersion: 1,
  authorizationSetRef: 'IT-AUTHSET-P021',
  authorizationEvidenceVersion: 'IT-AUTH-EVIDENCE-P021-V1',
  authorizedOrderRefs: [ORDER_REF],
  issuedAt: '2026-08-03T00:00:00Z',
  expiresAt: '2099-08-03T00:00:00Z',
  semantics: 'SERVER_PROJECTION_CACHE_NOT_AUTHORITY' as const,
}
const snapshot = {
  priceSnapshotRef: 'PRICE-P021-SYN-1', version: 'DISPLAY-V1', totalAmount: 125000, currency: 'BDT', phone: '******1234',
  operator: 'SYN Operator', product: 'SYN Package', targetValueDisplay: '1000 BDT', targetCurrency: 'BDT', validUntil: '2099-08-03T01:00:00Z',
}

export function prepareP021ItRealSession(storage: { setStorageSync(key:string,value:unknown):void }, orderRef:string, authenticated=true) {
  storage.setStorageSync('projectSessionProjection', authenticated
    ? { ...structuredClone(session), authorizedOrderRefs:[orderRef] }
    : { role:'GUEST',projectSubjectRef:null,sessionVersion:0,authorizationSetRef:null,authorizationEvidenceVersion:null,
        authorizedOrderRefs:[],issuedAt:null,expiresAt:null,semantics:'SERVER_PROJECTION_CACHE_NOT_AUTHORITY' })
}

function runtime() {
  const memory = new Map<string, unknown>([['projectSessionProjection', structuredClone(session)], [`priceSnapshot:${ORDER_REF}`, structuredClone(snapshot)]])
  return { getStorageSync: (key: string) => memory.get(key), setStorageSync: (key: string, value: unknown) => memory.set(key, value) }
}
const clock = () => Date.parse('2026-08-03T01:00:00Z')
const clone = (value: P021Response) => structuredClone(value) as unknown as Record<string, any>

function conflictingResponse(plan: P021ItDelayPlan): P021Response {
  if (plan === 'PROJECTION_LOW_VERSION' || plan === 'LATE_LOWER_VERSION_AFTER_READY') return p021SyntheticResponse(ORDER_REF, 'TOPUP_PROCESSING', 2)
  const raw = clone(p021SyntheticResponse(ORDER_REF, 'DELIVERED', 3))
  if (plan === 'ORDER_VERSION_CONFLICT') raw.aggregateVersion = 4
  else raw.currentProjection.priceSnapshotSummary.priceSnapshotRef = 'PRICE-P021-CONFLICT'
  return parseP021Response(raw)
}

export async function runP021ItPageScenario(state: P021PageState, plan: P021ItDelayPlan) {
  const storage = runtime()
  await executeP021Read(state, storage, { getOrderDetail: async () => p021SyntheticResponse(ORDER_REF, 'DELIVERED', 3) }, ORDER_REF, clock)
  if (state.viewState !== 'READY' || !state.projection) throw new Error('P021_IT_OLD_READY_SEED_FAILED')
  const oldContent = `${state.projection.orderRef}|${state.projection.projectionVersion}|${state.projection.stateCode}`
  if (plan === 'LATE_LOWER_VERSION_AFTER_READY') {
    let release!: (value: P021Response) => void
    const late = executeP021Read(state, storage, { getOrderDetail: () => new Promise(resolve => { release = resolve }) }, ORDER_REF, clock)
    await executeP021Read(state, storage, { getOrderDetail: async () => p021SyntheticResponse(ORDER_REF, 'DELIVERED', 4) }, ORDER_REF, clock)
    release(conflictingResponse(plan)); await late
  } else {
    try { await executeP021Read(state, storage, { getOrderDetail: async () => conflictingResponse(plan) }, ORDER_REF, clock) }
    catch { state.projection = null; state.viewState = 'READ_ERROR' }
  }
  const current = state.projection ? `${state.projection.orderRef}|${state.projection.projectionVersion}|${state.projection.stateCode}` : null
  return { plan, oldContent, oldContentRestored: current === oldContent, finalViewState: state.viewState, detailCount: state.projection ? 1 : 0 }
}

export function isP021ItDelayPlan(value: unknown): value is P021ItDelayPlan {
  return typeof value === 'string' && P021_IT_DELAY_PLANS.includes(value as P021ItDelayPlan)
}
