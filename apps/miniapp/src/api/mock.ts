import type { CatalogItem, DirectoryItem, EligibilityResult, OrderProjection, OrderSummary, QuoteSnapshot, SupportCase } from '../domain/types'

const wait = (ms = 180) => new Promise((resolve) => setTimeout(resolve, ms))

const total = { minor: 1280, currency: 'CNY' as const }
const faceValue = { minor: 20000, currency: 'BDT' as const }

export async function mockEligibility(phone: string): Promise<EligibilityResult> {
  await wait()
  const maskedPhone = phone.length >= 4 ? `01•• •••• ${phone.slice(-2)}` : '01•• •••• 78'
  if (phone.endsWith('00')) {
    return { outcome: 'UNKNOWN', maskedPhone, caseKey: 'case_mock_a1', projectCode: 'PREPAY_MNP_UNKNOWN' }
  }
  return { outcome: 'ELIGIBLE', maskedPhone, caseKey: 'case_mock_ok' }
}

export async function mockCatalog(): Promise<CatalogItem[]> {
  await wait()
  return [{ productRef: 'product_mock_200', name: '测试充值套餐', faceValue, catalogVersion: 'catalog-mock-v1', available: true }]
}

export async function mockQuote(maskedPhone: string): Promise<QuoteSnapshot> {
  await wait()
  return {
    orderRef: 'order_mock_001', priceSnapshotRef: 'quote_mock_001', maskedPhone, productName: '测试充值套餐', faceValue, total,
    priceVersion: 'price-mock-v1', operatorName: '合成运营商', expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(), validUntil: new Date(Date.now() + 10 * 60 * 1000).toISOString(), valid: true,
  }
}

export async function mockProjection(orderRef: string): Promise<OrderProjection> {
  await wait()
  return {
    projectionId: `projection_${orderRef}_012`, orderRef, projectionVersion: orderRef === 'order_mock_002' ? 7 : 12,
    generatedAt: new Date().toISOString(), terminal: false, stateCode: 'TOPUP_RESULT_PENDING',
    facts: orderRef === 'order_mock_002'
      ? { payment: 'CONFIRMED', eligibility: 'CONFIRMED', upstream: 'CONFIRMED', delivery: 'CONFIRMED', refund: 'CONFIRMED', accounting: 'PENDING' }
      : { payment: 'CONFIRMED', eligibility: 'UNKNOWN', upstream: 'UNKNOWN', delivery: 'UNKNOWN', refund: 'NOT_STARTED', accounting: 'PENDING' },
    total, faceValue, priceSnapshotRef: 'quote_mock_001', priceValidUntil: new Date(Date.now() + 10 * 60 * 1000).toISOString(), maskedPhone: orderRef === 'order_mock_002' ? '01•• •••• 26' : '01•• •••• 78', operatorName:'合成运营商', productName: '测试充值套餐', priceVersion: 'price-mock-v1',
    allowedActions: [
      { actionCode: 'REFRESH', enabled: true, expectedProjectionVersion: 12 },
      { actionCode: 'SAFE_EXIT', enabled: true, expectedProjectionVersion: 12 },
    ],
    supportRef: 'SUP-MOCK-001', nextPollAt: new Date(Date.now() + 30000).toISOString(),
  }
}

export async function mockOrders(): Promise<OrderSummary[]> { await wait(); return [
  { orderRef:'order_mock_001', maskedPhone:'01•• •••• 78', total, stateCode:'TOPUP_RESULT_PENDING', projectionVersion:12, updatedAt:new Date().toISOString() },
  { orderRef:'order_mock_002', maskedPhone:'01•• •••• 26', total, stateCode:'REVIEW', projectionVersion:7, updatedAt:new Date().toISOString() },
] }
export async function mockRecovery(): Promise<{ outcome:'GENERIC_ACCEPTED'; supportRef:string }> { await wait(); return { outcome:'GENERIC_ACCEPTED', supportRef:'SUP-MOCK-RECOVERY' } }
export async function mockSupport(): Promise<SupportCase> { await wait(); return { supportRef:'SUP-MOCK-001', confirmed:'微信收款已确认', unknown:'用户到账待确认', responsibility:'公司继续查询', nextReviewAt:null } }
export async function mockDirectory(): Promise<DirectoryItem[]> { await wait(); return [
  { contentRef:'content_mock_001', title:'在孟实用信息（合成条目）', category:'生活服务', verificationScope:'名称与公开联系方式', sourceType:'登记公开来源', verifiedAt:'2026-07-31', updatedAt:'2026-08-01', publicationState:'ELIGIBLE' },
] }
