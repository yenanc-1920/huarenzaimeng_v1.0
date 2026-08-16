export type AdminResource = 'cities' | 'directory-entries' | 'holidays' | 'news' | 'products' | 'price-versions' | 'product-mappings' | 'channels'

export interface AdminCommandResult { objectRef: string; version: number; auditRef?: string; replayed: boolean }

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)

const parseResult = (value: unknown): AdminCommandResult | null => {
  if (!isRecord(value) || typeof value.objectRef !== 'string' || !Number.isSafeInteger(value.version) || typeof value.replayed !== 'boolean') return null
  if (!(value.auditRef === undefined || typeof value.auditRef === 'string')) return null
  return value as unknown as AdminCommandResult
}

const request = async (path: string, method: 'POST' | 'PUT', body: Record<string, unknown>, idempotent = true): Promise<AdminCommandResult> => {
  const response = await fetch(path, {
    method,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...(idempotent ? { 'Idempotency-Key': crypto.randomUUID() } : {}),
    },
    body: JSON.stringify(body),
  })
  if (response.status === 401) throw new Error('登录已失效，请重新登录')
  if (response.status === 403) throw new Error('只有超级管理员可以执行此操作')
  if (response.status === 409) throw new Error('记录版本已变化，请刷新后重试')
  if (!response.ok) throw new Error('保存失败，请检查必填字段')
  const parsed = parseResult(await response.json())
  if (!parsed) throw new Error('服务端返回格式不符合约定')
  return parsed
}

export const createAdminObject = (resource: AdminResource, body: Record<string, unknown>) =>
  request(`/admin-command/v1/${resource}`, 'POST', body)

export const updateAdminObject = (resource: AdminResource, ref: string, body: Record<string, unknown>) =>
  request(`/admin-command/v1/${resource}/${encodeURIComponent(ref)}`, 'PUT', body)

export const transitionAdminObject = (resource: AdminResource, ref: string, action: 'submit' | 'publish' | 'unpublish' | 'enable' | 'disable', expectedVersion: number, reason: string) =>
  request(`/admin-command/v1/${resource}/${encodeURIComponent(ref)}/${action}`, 'POST', { expectedVersion, reason })

export interface PriceTrialResult { costCny: number; bufferRate: number; markupRate: number; wechatFeeRate: number; taxRate: number; minimumMarginRate: number; roundingRule: string; finalAmountCny: number; marginCny: number; marginRate: number; minimumMarginSatisfied: boolean; formula: string; persistent: false }
export async function trialPrice(body: Record<string, number | string>) {
  const response = await fetch('/admin-command/v1/price-versions/trial', {
    method: 'POST', credentials: 'include', headers: { Accept: 'application/json', 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error(response.status === 403 ? '只有超级管理员可以试算' : '价格试算失败')
  const value = await response.json()
  const numeric = ['costCny','bufferRate','markupRate','wechatFeeRate','taxRate','minimumMarginRate','finalAmountCny','marginCny','marginRate']
  if (!isRecord(value) || !numeric.every(key => typeof value[key] === 'number') || typeof value.roundingRule !== 'string' || typeof value.minimumMarginSatisfied !== 'boolean' || typeof value.formula !== 'string' || value.persistent !== false) throw new Error('试算返回格式不符合约定')
  return value as unknown as PriceTrialResult
}
