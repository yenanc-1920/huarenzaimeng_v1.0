export type CaseEventType = 'FOLLOW_UP' | 'CLAIM' | 'ASSIGN' | 'ESCALATE' | 'RESOLVE' | 'CLOSE'
export type ReconciliationAction = 'CLAIM' | 'QUERY_WECHAT' | 'QUERY_PROVIDER' | 'NOTE' | 'TRANSFER_CS' | 'TRANSFER_REVIEW' | 'RESOLVE' | 'CLOSE'
export type CaseState = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type ReconciliationState = 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'CLOSED'
export type ReconciliationDifferenceType = 'PAYMENT_TOPUP_PENDING' | 'WECHAT_PAYMENT_QUERY' | 'WECHAT_REFUND_QUERY' | 'WINLA_TOPUP_QUERY'
type CaseHistoryEventType = 'CREATE' | CaseEventType

export interface CustomerCaseDetail {
  caseRef: string; sourceType: string; issueType: string; relatedOrderRef: string
  priorityCode: string; ownerRef: string | null; state: CaseState; description: string
  dataOrigin: string; version: number; createdAt: string; updatedAt: string
}
export interface CustomerCaseEvent {
  eventRef: string; eventType: CaseHistoryEventType; note: string; evidenceRef: string | null
  fromState: 'NONE' | CaseState; toState: CaseState; actorRef: string; createdAt: string
}
export interface ReconciliationDetail {
  reconciliationRef: string; orderRef: string; differenceType: ReconciliationDifferenceType; amount: number
  currency: string; state: ReconciliationState; ownerRef: string | null; dataOrigin: string
  version: number; discoveredAt: string; updatedAt: string
}
export interface ReconciliationEvent {
  eventRef: string; actionType: ReconciliationAction; evidenceRef: string | null; note: string
  fromState: ReconciliationState; toState: ReconciliationState; actorRef: string; createdAt: string
}
export interface WorkflowDetail<I, H> { schemaVersion: '1.0'; projectionVersion: 'workflow-detail-v1'; item: I; history: H[] }
export interface WorkflowCommandResult { objectRef: string; state: CaseState | ReconciliationState; version: number | null; replayed: boolean }

type Transport = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const exactKeys = (value: Record<string, unknown>, keys: readonly string[]) => {
  const actual = Object.keys(value).sort(); const expected = [...keys].sort()
  return actual.length === expected.length && actual.every((key, index) => key === expected[index])
}
const text = (value: unknown): value is string => typeof value === 'string' && value.trim().length > 0
const nullableText = (value: unknown): value is string | null => value === null || text(value)
const instant = (value: unknown): value is string => text(value) && Number.isFinite(Date.parse(value))
const CASE_STATES = ['OPEN','IN_PROGRESS','RESOLVED','CLOSED'] as const
const RECON_STATES = ['OPEN','IN_REVIEW','RESOLVED','CLOSED'] as const
const CASE_EVENT_TYPES = ['CREATE','FOLLOW_UP','CLAIM','ASSIGN','ESCALATE','RESOLVE','CLOSE'] as const
const RECON_ACTIONS = ['CLAIM','QUERY_WECHAT','QUERY_PROVIDER','NOTE','TRANSFER_CS','TRANSFER_REVIEW','RESOLVE','CLOSE'] as const
const DIFFERENCE_TYPES = ['PAYMENT_TOPUP_PENDING','WECHAT_PAYMENT_QUERY','WECHAT_REFUND_QUERY','WINLA_TOPUP_QUERY'] as const
const oneOf = <T extends string>(value: unknown, values: readonly T[]): value is T => typeof value === 'string' && values.includes(value as T)

const CASE_KEYS = ['caseRef','sourceType','issueType','relatedOrderRef','priorityCode','ownerRef','state','description','dataOrigin','version','createdAt','updatedAt'] as const
const CASE_EVENT_KEYS = ['eventRef','eventType','note','evidenceRef','fromState','toState','actorRef','createdAt'] as const
const RECON_KEYS = ['reconciliationRef','orderRef','differenceType','amount','currency','state','ownerRef','dataOrigin','version','discoveredAt','updatedAt'] as const
const RECON_EVENT_KEYS = ['eventRef','actionType','evidenceRef','note','fromState','toState','actorRef','createdAt'] as const

function parseCase(value: unknown): CustomerCaseDetail | null {
  if (!isRecord(value) || !exactKeys(value, CASE_KEYS)
    || !['caseRef','sourceType','issueType','relatedOrderRef','priorityCode','description','dataOrigin'].every((key) => text(value[key]))
    || !oneOf(value.state, CASE_STATES)
    || !nullableText(value.ownerRef) || !Number.isSafeInteger(value.version) || Number(value.version) <= 0
    || !instant(value.createdAt) || !instant(value.updatedAt)) return null
  return value as unknown as CustomerCaseDetail
}
function parseCaseEvent(value: unknown): CustomerCaseEvent | null {
  if (!isRecord(value) || !exactKeys(value, CASE_EVENT_KEYS)
    || !['eventRef','note','actorRef'].every((key) => text(value[key]))
    || !oneOf(value.eventType, CASE_EVENT_TYPES) || !(value.fromState === 'NONE' || oneOf(value.fromState, CASE_STATES)) || !oneOf(value.toState, CASE_STATES)
    || !nullableText(value.evidenceRef) || !instant(value.createdAt)) return null
  return value as unknown as CustomerCaseEvent
}
function parseReconciliation(value: unknown): ReconciliationDetail | null {
  if (!isRecord(value) || !exactKeys(value, RECON_KEYS)
    || !['reconciliationRef','orderRef','currency','dataOrigin'].every((key) => text(value[key]))
    || !oneOf(value.differenceType, DIFFERENCE_TYPES) || !oneOf(value.state, RECON_STATES)
    || typeof value.amount !== 'number' || !Number.isFinite(value.amount) || !nullableText(value.ownerRef)
    || !Number.isSafeInteger(value.version) || Number(value.version) <= 0
    || !instant(value.discoveredAt) || !instant(value.updatedAt)) return null
  return value as unknown as ReconciliationDetail
}
function parseReconciliationEvent(value: unknown): ReconciliationEvent | null {
  if (!isRecord(value) || !exactKeys(value, RECON_EVENT_KEYS)
    || !['eventRef','note','actorRef'].every((key) => text(value[key]))
    || !oneOf(value.actionType, RECON_ACTIONS) || !oneOf(value.fromState, RECON_STATES) || !oneOf(value.toState, RECON_STATES)
    || !nullableText(value.evidenceRef) || !instant(value.createdAt)) return null
  return value as unknown as ReconciliationEvent
}
function parseDetail<I,H>(value: unknown, parseItem: (value: unknown) => I | null, parseHistory: (value: unknown) => H | null): WorkflowDetail<I,H> | null {
  if (!isRecord(value) || !exactKeys(value, ['schemaVersion','projectionVersion','item','history'])
    || value.schemaVersion !== '1.0' || value.projectionVersion !== 'workflow-detail-v1' || !Array.isArray(value.history)) return null
  const item = parseItem(value.item); const history = value.history.map(parseHistory)
  return item && history.every((entry) => entry !== null) ? { schemaVersion: '1.0', projectionVersion: 'workflow-detail-v1', item, history: history as H[] } : null
}

function commandError(status: number, body: unknown): Error {
  const code = isRecord(body) && text(body.projectCode) ? body.projectCode : ''
  if (status === 401) return new Error('登录已失效，请重新登录')
  if (status === 403) return new Error('当前角色不能执行此操作')
  if (status === 409 || code === 'VERSION_CONFLICT') return new Error('记录已被更新，请重新读取后再提交')
  if (code === 'EVIDENCE_REQUIRED') return new Error('完成或关闭前必须填写证据编号')
  if (code === 'OWNER_REQUIRED') return new Error('此操作必须填写负责人')
  return new Error('提交失败，请检查必填内容')
}
async function json(response: Response) { try { return await response.json() } catch { return null } }
async function read<I,H>(path: string, parseItem: (value: unknown) => I | null, parseHistory: (value: unknown) => H | null, transport: Transport) {
  const response = await transport(path, { method: 'GET', credentials: 'include', headers: { Accept: 'application/json' } })
  const body = await json(response)
  if (!response.ok) throw commandError(response.status, body)
  const parsed = parseDetail(body, parseItem, parseHistory)
  if (!parsed) throw new Error('详情数据不符合约定，请稍后重试')
  return parsed
}
async function command(path: string, body: Record<string, unknown>, refKey: 'caseRef' | 'reconciliationRef', states: readonly (CaseState | ReconciliationState)[], transport: Transport): Promise<WorkflowCommandResult> {
  const response = await transport(path, {
    method: 'POST', credentials: 'include',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify(body),
  })
  const payload = await json(response)
  if (!response.ok) throw commandError(response.status, payload)
  if (!isRecord(payload) || !text(payload[refKey]) || !oneOf(payload.state, states) || typeof payload.replayed !== 'boolean') throw new Error('提交结果不符合约定，请重新读取')
  const expectedKeys = payload.replayed ? [refKey,'state','replayed'] : [refKey,'state','version','replayed']
  if (!exactKeys(payload, expectedKeys) || (!payload.replayed && (!Number.isSafeInteger(payload.version) || Number(payload.version) <= 0))) throw new Error('提交结果不符合约定，请重新读取')
  return { objectRef: String(payload[refKey]), state: payload.state, version: payload.version === undefined ? null : Number(payload.version), replayed: payload.replayed }
}

export const loadCustomerCase = (caseRef: string, transport: Transport = fetch) => read(`/admin-workflow/v1/customer-cases/${encodeURIComponent(caseRef)}`, parseCase, parseCaseEvent, transport)
export const loadReconciliation = (ref: string, transport: Transport = fetch) => read(`/admin-workflow/v1/reconciliations/${encodeURIComponent(ref)}`, parseReconciliation, parseReconciliationEvent, transport)
export const createCustomerCase = (body: { sourceType: string; issueType: string; relatedOrderRef: string; priorityCode: string; ownerRef?: string; description: string }, transport: Transport = fetch) =>
  command('/admin-workflow/v1/customer-cases', { caseRef: `CASE-${crypto.randomUUID()}`, ...body, ownerRef: body.ownerRef || null }, 'caseRef', CASE_STATES, transport)
export const appendCustomerCaseEvent = (caseRef: string, body: { eventType: CaseEventType; note: string; expectedVersion: number; ownerRef?: string; evidenceRef?: string }, transport: Transport = fetch) =>
  command(`/admin-workflow/v1/customer-cases/${encodeURIComponent(caseRef)}/events`, { ...body, ownerRef: body.ownerRef || null, evidenceRef: body.evidenceRef || null }, 'caseRef', CASE_STATES, transport)
export const appendReconciliationEvent = (ref: string, body: { actionType: ReconciliationAction; note: string; expectedVersion: number; ownerRef?: string; evidenceRef?: string }, transport: Transport = fetch) =>
  command(`/admin-workflow/v1/reconciliations/${encodeURIComponent(ref)}/events`, { ...body, ownerRef: body.ownerRef || null, evidenceRef: body.evidenceRef || null }, 'reconciliationRef', RECON_STATES, transport)
