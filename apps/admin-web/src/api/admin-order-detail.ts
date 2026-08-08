export type P021AdminRole = 'CS' | 'FIN'
export type P021AdminRequestedRole = P021AdminRole | 'CONTENT'
export type P021AdminViewState = 'LOADING' | 'READY' | 'ACCESS_DENIED' | 'UNAVAILABLE'

export interface P021AdminTimelineItem {
  timelineItemRef: string
  sequence: number
  projectionVersion: number
  stateCode: string
  occurredAt: string | null
  userMessageCode: string
}

export interface P021AdminCsProjection {
  role: 'CS'
  orderRef: string
  stateCode: string
  maskedTarget: string
  confirmedItems: string[]
  unknownItems: string[]
  responsibilityCode: string
  updatedAt: string
  nextReviewPoint: string | null
  timeline: P021AdminTimelineItem[]
}

export interface P021AdminFinProjection {
  role: 'FIN'
  orderRef: string
  stateCode: string
  totalMinor: number
  currency: string
  targetValueDisplay: string
  targetCurrency: string
  confirmedItems: string[]
  unknownItems: string[]
  responsibilityCode: string
  updatedAt: string
  nextReviewPoint: string | null
  timeline: P021AdminTimelineItem[]
}

export type P021AdminProjection = P021AdminCsProjection | P021AdminFinProjection
export type P021AdminReadState =
  | { status: 'LOADING'; data: null; message: string }
  | { status: 'READY'; data: P021AdminProjection; message: string }
  | { status: 'ACCESS_DENIED'; data: null; message: string }
  | { status: 'UNAVAILABLE'; data: null; message: string }

type AdminTransport = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
const CS_KEYS = ['orderRef', 'stateCode', 'maskedTarget', 'confirmedItems', 'unknownItems', 'responsibilityCode', 'updatedAt', 'nextReviewPoint', 'timeline'] as const
const FIN_KEYS = ['orderRef', 'stateCode', 'totalMinor', 'currency', 'targetValueDisplay', 'targetCurrency', 'confirmedItems', 'unknownItems', 'responsibilityCode', 'updatedAt', 'nextReviewPoint', 'timeline'] as const
const TIMELINE_KEYS = ['timelineItemRef', 'sequence', 'projectionVersion', 'stateCode', 'occurredAt', 'userMessageCode'] as const
const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const exactKeys = (value: Record<string, unknown>, keys: readonly string[]) => Object.keys(value).length === keys.length && Object.keys(value).every((key) => keys.includes(key))
const text = (value: unknown): value is string => typeof value === 'string' && value.length > 0
const instant = (value: unknown): value is string => text(value) && !Number.isNaN(Date.parse(value))
const nullableInstant = (value: unknown): value is string | null => value === null || instant(value)
const stringList = (value: unknown): value is string[] => Array.isArray(value) && value.every(text) && new Set(value).size === value.length

function parseTimeline(value: unknown): P021AdminTimelineItem[] | null {
  if (!Array.isArray(value) || value.length === 0) return null
  let previousVersion = 0
  const parsed: P021AdminTimelineItem[] = []
  for (let index = 0; index < value.length; index += 1) {
    const item = value[index]
    if (!isRecord(item) || !exactKeys(item, TIMELINE_KEYS) || !text(item.timelineItemRef) || item.sequence !== index + 1
      || !Number.isSafeInteger(item.projectionVersion) || Number(item.projectionVersion) <= 0 || Number(item.projectionVersion) < previousVersion
      || !text(item.stateCode) || !nullableInstant(item.occurredAt) || !text(item.userMessageCode)) return null
    previousVersion = Number(item.projectionVersion)
    parsed.push(item as unknown as P021AdminTimelineItem)
  }
  return parsed
}

export function parseP021AdminResponse(value: unknown, role: P021AdminRole): P021AdminReadState {
  if (!isRecord(value) || !exactKeys(value, ['outcome', 'projectCode', 'currentProjection'])) return { status: 'UNAVAILABLE', data: null, message: '订单详情响应不符合约定' }
  if (value.outcome === 'REJECTED' && value.projectCode === 'ADMIN_ORDER_DETAIL_NOT_AVAILABLE' && value.currentProjection === null) {
    return { status: 'ACCESS_DENIED', data: null, message: '当前无法查看该订单' }
  }
  if (value.outcome !== 'ACCEPTED' || value.projectCode !== 'ADMIN_ORDER_DETAIL_READ' || !isRecord(value.currentProjection)) {
    return { status: 'UNAVAILABLE', data: null, message: '订单详情响应不符合约定' }
  }
  const item = value.currentProjection
  const keys = role === 'CS' ? CS_KEYS : FIN_KEYS
  const confirmedItems = item.confirmedItems
  const unknownItems = item.unknownItems
  if (!exactKeys(item, keys) || !text(item.orderRef) || !text(item.stateCode) || !stringList(confirmedItems)
    || !stringList(unknownItems) || confirmedItems.some((entry) => unknownItems.includes(entry))
    || !text(item.responsibilityCode) || !instant(item.updatedAt) || !nullableInstant(item.nextReviewPoint)) {
    return { status: 'UNAVAILABLE', data: null, message: '订单详情职责字段不符合约定' }
  }
  const timeline = parseTimeline(item.timeline)
  if (!timeline) return { status: 'UNAVAILABLE', data: null, message: '订单时间线不符合约定' }
  if (role === 'CS') {
    if (!text(item.maskedTarget)) return { status: 'UNAVAILABLE', data: null, message: '客服订单详情不符合约定' }
    return { status: 'READY', data: { ...item, role, timeline } as unknown as P021AdminCsProjection, message: '已读取客服订单详情' }
  }
  if (!Number.isSafeInteger(item.totalMinor) || Number(item.totalMinor) < 0 || typeof item.currency !== 'string' || !/^[A-Z]{3}$/.test(item.currency)
    || !text(item.targetValueDisplay) || typeof item.targetCurrency !== 'string' || !/^[A-Z]{3}$/.test(item.targetCurrency)) {
    return { status: 'UNAVAILABLE', data: null, message: '财务订单详情不符合约定' }
  }
  return { status: 'READY', data: { ...item, role, timeline } as unknown as P021AdminFinProjection, message: '已读取财务订单详情' }
}

export async function loadP021AdminOrderDetail(orderRef: string, role: P021AdminRequestedRole, transport: AdminTransport = fetch): Promise<P021AdminReadState> {
  if (!orderRef) return { status: 'UNAVAILABLE', data: null, message: '未选择订单' }
  try {
    const response = await transport(`/admin-read/v1/orders/${encodeURIComponent(orderRef)}`, {
      method: 'GET', credentials: 'include', headers: { Accept: 'application/json' },
    })
    if (response.status === 401 || response.status === 403) return { status: 'ACCESS_DENIED', data: null, message: '当前无法查看该订单' }
    if (!response.ok) return { status: 'UNAVAILABLE', data: null, message: '订单详情服务暂不可用' }
    const body = await response.json()
    if (role === 'CONTENT') {
      return isRecord(body) && exactKeys(body, ['outcome', 'projectCode', 'currentProjection'])
        && body.outcome === 'REJECTED' && body.projectCode === 'ADMIN_ORDER_DETAIL_NOT_AVAILABLE' && body.currentProjection === null
        ? { status: 'ACCESS_DENIED', data: null, message: '当前无法查看该订单' }
        : { status: 'UNAVAILABLE', data: null, message: '服务端未按职责拒绝该读取' }
    }
    return parseP021AdminResponse(body, role)
  } catch {
    return { status: 'UNAVAILABLE', data: null, message: '订单详情读取失败' }
  }
}
