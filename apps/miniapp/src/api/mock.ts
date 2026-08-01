import type { CatalogItem, ContentErrorReportCommand, ContentErrorReportResult, DirectoryDetailResult, DirectoryItem, DirectorySummary, EligibilityResult, OrderProjection, OrderSummary, QuoteSnapshot, SupportCase } from '../domain/types'

const wait = (ms = 180) => new Promise((resolve) => setTimeout(resolve, ms))

const total = { minor: 1280, currency: 'CNY' as const }
const faceValue = { minor: 20000, currency: 'BDT' as const }

export async function mockEligibility(phone: string): Promise<EligibilityResult> {
  await wait()
  const maskedPhone = phone.length >= 4 ? `01•• •••• ${phone.slice(-2)}` : '01•• •••• 78'
  if (phone.endsWith('00')) {
    return { outcome: 'UNKNOWN', maskedPhone, caseKey: 'case_mock_a1', projectCode: 'PREPAY_MNP_UNKNOWN' }
  }
  return { outcome: 'ELIGIBLE', maskedPhone, operatorName: 'Grameenphone', caseKey: 'case_mock_ok' }
}

export async function mockCatalog(): Promise<CatalogItem[]> {
  await wait()
  return [100, 200, 300, 500].map((amount) => ({
    productRef: `product_mock_${amount}`,
    name: `合成测试充值 ${amount} BDT`,
    faceValue: { minor: amount * 100, currency: 'BDT' as const },
    catalogVersion: 'catalog-mock-v1',
    available: true,
  }))
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
    generatedAt: new Date().toISOString(), terminal: orderRef === 'order_mock_002', stateCode: orderRef === 'order_mock_002' ? 'COMPLETED' : 'TOPUP_RESULT_PENDING',
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
  { orderRef:'order_mock_002', maskedPhone:'01•• •••• 26', total, stateCode:'COMPLETED', projectionVersion:7, updatedAt:new Date().toISOString() },
  { orderRef:'order_mock_003', maskedPhone:'01•• •••• 52', total, stateCode:'REVIEW', projectionVersion:5, updatedAt:new Date().toISOString() },
] }
export async function mockRecovery(): Promise<{ outcome:'GENERIC_ACCEPTED'; supportRef:string }> { await wait(); return { outcome:'GENERIC_ACCEPTED', supportRef:'SUP-MOCK-RECOVERY' } }
export async function mockSupport(): Promise<SupportCase> { await wait(); return { supportRef:'SUP-MOCK-001', confirmed:'微信收款已确认', unknown:'用户到账待确认', responsibility:'公司继续查询', nextReviewAt:null } }
const directoryRecords: DirectoryItem[] = [
  { contentRef:'content_mock_001', contentVersion:3, title:'在孟实用信息（合成条目）', summary:'合成静态生活服务信息', category:'生活服务', sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2026-07-31T00:00:00Z', validUntil:'2099-08-01T00:00:00Z', updatedAt:'2026-08-01T00:00:00Z', disclaimer:'仅说明所列范围已核验，不构成官方认证、推荐或持续有效担保', publicationState:'ELIGIBLE' },
  { contentRef:'content_mock_expired', contentVersion:1, title:'过期合成条目（不可见）', summary:'过期状态样本', category:'生活服务', sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2025-01-01T00:00:00Z', validUntil:'2025-01-02T00:00:00Z', updatedAt:'2025-01-02T00:00:00Z', disclaimer:'不构成官方认证、推荐或持续有效担保', publicationState:'EXPIRED' },
  { contentRef:'content_mock_complaint', contentVersion:2, title:'投诉待查合成条目（不可见）', summary:'投诉待查状态样本', category:'生活服务', sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2026-07-01T00:00:00Z', validUntil:'2099-07-01T00:00:00Z', updatedAt:'2026-07-02T00:00:00Z', disclaimer:'不构成官方认证、推荐或持续有效担保', publicationState:'COMPLAINT_PENDING' },
  { contentRef:'content_mock_review', contentVersion:4, title:'复核中合成条目（不可见）', summary:'复核状态样本', category:'生活服务', sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2026-07-05T00:00:00Z', validUntil:'2099-07-05T00:00:00Z', updatedAt:'2026-07-06T00:00:00Z', disclaimer:'不构成官方认证、推荐或持续有效担保', publicationState:'REVIEW' },
  { contentRef:'content_mock_removed', contentVersion:5, title:'下架合成条目（不可见）', summary:'下架状态样本', category:'生活服务', sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2026-06-01T00:00:00Z', validUntil:'2099-06-01T00:00:00Z', updatedAt:'2026-06-02T00:00:00Z', disclaimer:'不构成官方认证、推荐或持续有效担保', publicationState:'REMOVED' },
  { contentRef:'content_mock_unknown', contentVersion:6, title:'状态未知合成条目（不可见）', summary:'未知状态样本', category:'生活服务', sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2026-06-10T00:00:00Z', validUntil:'2099-06-10T00:00:00Z', updatedAt:'2026-06-11T00:00:00Z', disclaimer:'不构成官方认证、推荐或持续有效担保', publicationState:'UNKNOWN' },
]

export async function mockDirectory(): Promise<DirectorySummary[]> {
  await wait()
  return directoryRecords.filter((item) => item.publicationState === 'ELIGIBLE').map((item) => ({ contentRef:item.contentRef, contentVersion:item.contentVersion, title:item.title, summary:item.summary, category:item.category, validUntil:item.validUntil, updatedAt:item.updatedAt, publicationState:'ELIGIBLE' }))
}

export async function mockDirectoryDetail(contentRef: string, contentVersion: number): Promise<DirectoryDetailResult> {
  await wait()
  const item = directoryRecords.find((candidate) => candidate.contentRef === contentRef)
  if (!item) return { outcome: 'EMPTY' }
  if (item.contentVersion !== contentVersion) return { outcome: 'UNKNOWN' }
  if (item.publicationState === 'ELIGIBLE') return { outcome: 'READY', item: { ...item, publicationState: 'ELIGIBLE' } }
  return { outcome: item.publicationState }
}

const contentReportReplays = new Map<string, { identity:string; fingerprint:string; receipt:ContentErrorReportResult }>()
export async function mockReportDirectoryError(contentRef: string, command: ContentErrorReportCommand): Promise<ContentErrorReportResult> {
  await wait()
  const identity = `${command.commandId}|${command.idempotencyKey}`
  const fingerprint = JSON.stringify({ contentRef, ...command })
  const byCommand = contentReportReplays.get(`command:${command.commandId}`)
  const byIdempotency = contentReportReplays.get(`idempotency:${command.idempotencyKey}`)
  if (byCommand || byIdempotency) {
    const replay = byCommand || byIdempotency!
    if (replay.identity !== identity || replay.fingerprint !== fingerprint) throw new Error('IDEMPOTENCY_CONFLICT')
    return replay.receipt
  }
  if (command.contentVersion !== command.expectedAggregateVersion) throw new Error('CONTENT_VERSION_STALE')
  const detail = await mockDirectoryDetail(contentRef, command.contentVersion)
  if (detail.outcome !== 'READY') throw new Error('CONTENT_NOT_REPORTABLE')
  const receipt:ContentErrorReportResult = { outcome:'CONTENT_ERROR_REPORTED', supportRef:`SUPPORT-${contentRef}-V${command.contentVersion + 1}`, reviewTarget:'A120' }
  contentReportReplays.set(`command:${command.commandId}`, { identity, fingerprint, receipt })
  contentReportReplays.set(`idempotency:${command.idempotencyKey}`, { identity, fingerprint, receipt })
  const current = directoryRecords.find((item) => item.contentRef === contentRef)!
  current.contentVersion += 1
  current.publicationState = 'REVIEW'
  return receipt
}
