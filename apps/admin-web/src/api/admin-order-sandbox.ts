export type AdminOrderSandboxState =
  | { status: 'LOADING'; data: null; message: string }
  | { status: 'READY'; data: AdminOrderSandboxProjection; message: string }
  | { status: 'NOT_FOUND' | 'UNAVAILABLE'; data: null; message: string }

export interface AdminSandboxTopup {
  availability: 'OBSERVED' | 'NOT_AVAILABLE'
  providerTransactionRef: string | null
  providerStatusCode: string | null
  observedAt: string | null
}

export interface AdminOrderSandboxProjection {
  schemaVersion: 'ADMIN_ORDER_SANDBOX_V1'
  orderRef: string
  stateCode: string
  paymentState: string
  deliveryState: string
  refundState: string
  maskedTarget: string
  totalMinor: number
  currency: string
  updatedAt: string
  sandboxTopup: AdminSandboxTopup
}

const ROOT_KEYS = ['schemaVersion', 'orderRef', 'stateCode', 'paymentState', 'deliveryState', 'refundState', 'maskedTarget', 'totalMinor', 'currency', 'updatedAt', 'sandboxTopup'] as const
const TOPUP_KEYS = ['availability', 'providerTransactionRef', 'providerStatusCode', 'observedAt'] as const
const record = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const exact = (value: Record<string, unknown>, keys: readonly string[]) => Object.keys(value).length === keys.length && Object.keys(value).every((key) => keys.includes(key))
const text = (value: unknown): value is string => typeof value === 'string' && value.length > 0
const nullableText = (value: unknown): value is string | null => value === null || text(value)
const instant = (value: unknown): value is string => text(value) && !Number.isNaN(Date.parse(value))
const nullableInstant = (value: unknown): value is string | null => value === null || instant(value)

export function parseAdminOrderSandbox(value: unknown): AdminOrderSandboxProjection | null {
  if (!record(value) || !exact(value, ROOT_KEYS) || value.schemaVersion !== 'ADMIN_ORDER_SANDBOX_V1'
    || !text(value.orderRef) || !text(value.stateCode) || !text(value.paymentState) || !text(value.deliveryState)
    || !text(value.refundState) || !text(value.maskedTarget) || !Number.isSafeInteger(value.totalMinor)
    || Number(value.totalMinor) < 0 || typeof value.currency !== 'string' || !/^[A-Z]{3}$/.test(value.currency)
    || !instant(value.updatedAt) || !record(value.sandboxTopup) || !exact(value.sandboxTopup, TOPUP_KEYS)) return null
  const topup = value.sandboxTopup
  if (topup.availability !== 'OBSERVED' && topup.availability !== 'NOT_AVAILABLE') return null
  if (!nullableText(topup.providerTransactionRef) || !nullableText(topup.providerStatusCode) || !nullableInstant(topup.observedAt)) return null
  if (topup.availability === 'NOT_AVAILABLE' && (topup.providerTransactionRef !== null || topup.providerStatusCode !== null || topup.observedAt !== null)) return null
  if (topup.availability === 'OBSERVED' && (!text(topup.providerTransactionRef) || !text(topup.providerStatusCode) || !instant(topup.observedAt))) return null
  return value as unknown as AdminOrderSandboxProjection
}

export async function loadAdminOrderSandbox(orderRef: string, transport: typeof fetch = fetch): Promise<AdminOrderSandboxState> {
  try {
    const response = await transport(`/admin-read/v1/orders/${encodeURIComponent(orderRef)}/sandbox-status`, {
      method: 'GET', credentials: 'include', headers: { Accept: 'application/json' },
    })
    if (response.status === 404) return { status: 'NOT_FOUND', data: null, message: '未找到该订单' }
    if (!response.ok) return { status: 'UNAVAILABLE', data: null, message: '订单详情暂不可用' }
    const data = parseAdminOrderSandbox(await response.json())
    return data ? { status: 'READY', data, message: '订单详情已读取' }
      : { status: 'UNAVAILABLE', data: null, message: '订单详情响应不符合约定' }
  } catch {
    return { status: 'UNAVAILABLE', data: null, message: '订单详情读取失败' }
  }
}
