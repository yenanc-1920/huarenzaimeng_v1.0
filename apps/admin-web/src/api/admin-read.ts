import type { A110AllowedAction, A110PageProjection, A110ServerViewState, AdminDataMode, AdminPageProjection, AdminReadState, AdminRole, PageId, ReconciliationFactCode, ReconciliationFactState, ReconciliationFinanceSummary, ReconciliationSupportSummary } from '../domain/admin.ts'

export const ADMIN_READ_PROXY_PATH = '/admin-read/v1/pages'
export const A110_READ_PROXY_PATH = '/admin-read/v1/reconciliations'

type AdminTransport = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

const exactKeys = (value: object, expected: readonly string[]) => {
  const actual = Object.keys(value).sort()
  const sortedExpected = [...expected].sort()
  return actual.length === sortedExpected.length && actual.every((key, index) => key === sortedExpected[index])
}

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const isNonEmptyString = (value: unknown): value is string => typeof value === 'string' && value.trim().length > 0
const isRfc3339 = (value: unknown): value is string => isNonEmptyString(value) && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(value) && Number.isFinite(Date.parse(value))
const isNullableRfc3339 = (value: unknown) => value === null || isRfc3339(value)

const itemKeyMap = {
  'A120:SUPER_ADMIN': ['contentRef', 'title', 'sourceLabel', 'reviewLabel', 'complaintLabel', 'visibilityLabel', 'ownerLabel', 'historyLabel', 'removalLabel'],
  'A120:CONTENT': ['contentRef', 'title', 'sourceLabel', 'reviewLabel', 'complaintLabel', 'visibilityLabel', 'ownerLabel', 'historyLabel', 'removalLabel'],
  'A140:SUPER_ADMIN': ['orderRef', 'maskedPhone', 'userStatusLabel', 'totalLabel', 'updatedLabel'],
  'A140:CS': ['orderRef', 'maskedPhone', 'userStatusLabel', 'totalLabel', 'updatedLabel'],
  'A140:FIN': ['orderRef', 'maskedPhone', 'userStatusLabel', 'totalLabel', 'updatedLabel'],
} as const

type ProjectionVariant = keyof typeof itemKeyMap

const A110_ROOT_KEYS = ['requestRef', 'viewState', 'projectCode', 'schemaVersion', 'roleProjection', 'roleBindingVersion', 'authorizationDecisionVersion', 'projectionVersion', 'items', 'allowedActions', 'retryClass'] as const
const A110_FIN_ITEM_KEYS = ['reconciliationRef', 'orderRef', 'supportRef', 'factSummaries', 'differenceCategories', 'ageState', 'responsibilityCode', 'timeline', 'nextReviewPoint', 'updatedAt', 'projectionVersion', 'displayVersion'] as const
const A110_CS_ITEM_KEYS = ['reconciliationRef', 'supportRef', 'orderRef', 'maskedSubjectSummary', 'userFacingSummary', 'confirmedItems', 'unconfirmedItems', 'responsibilityCode', 'nextReviewPoint', 'updatedAt', 'projectionVersion'] as const
const A110_FACT_KEYS = ['factState', 'amountMinor', 'currency', 'occurredAt', 'observedAt'] as const
const A110_TIMELINE_KEYS = ['factCode', 'factState', 'occurredAt', 'observedAt'] as const
const A110_VIEW_STATES: A110ServerViewState[] = ['READY', 'EMPTY', 'READ_ERROR', 'UNAVAILABLE', 'ACCESS_DENIED', 'AUTHORITY_UNKNOWN', 'REVOKED', 'VERSION_CONFLICT', 'LONG_RUNNING_UNKNOWN', 'ASYMMETRIC_FACTS', 'REFUND_DELIVERY_CONFLICT']
const A110_FACT_CODES: ReconciliationFactCode[] = ['W', 'U', 'D', 'R', 'L']
const A110_FACT_STATES: ReconciliationFactState[] = ['ABSENT_CONFIRMED', 'PENDING_OR_INFLIGHT', 'UNKNOWN', 'CONFIRMED', 'CONFLICT']
const A110_DIFFERENCE_CATEGORIES = ['MISSING', 'DUPLICATE', 'AMOUNT_MISMATCH', 'CURRENCY_MISMATCH', 'FACT_CONFLICT', 'ACCOUNTING_INCOMPLETE', 'REFUND_DELIVERY_CONFLICT', 'UNKNOWN'] as const
const A110_AGE_STATES = ['CURRENT', 'LONG_RUNNING', 'UNKNOWN'] as const
const A110_ACTIONS: A110AllowedAction[] = ['READ_REFRESH', 'NAVIGATE_A100', 'NAVIGATE_A140']
const A110_EXPLANATION_CODES = ['PAYMENT_RECEIVED', 'DELIVERY_RESULT_PENDING', 'LOCAL_RECORD_PENDING', 'REFUND_CONFIRMED', 'DELIVERY_CONFIRMED', 'FINAL_CONCLUSION_PENDING', 'RESULT_DIFFERENCE_PENDING'] as const
const A110_DATA_STATES: A110ServerViewState[] = ['READY', 'LONG_RUNNING_UNKNOWN', 'ASYMMETRIC_FACTS', 'REFUND_DELIVERY_CONFLICT']
const A110_DENIED_STATES: A110ServerViewState[] = ['ACCESS_DENIED', 'AUTHORITY_UNKNOWN', 'REVOKED']

const uniqueAllowedStrings = (value: unknown, allowed?: readonly string[]) => Array.isArray(value)
  && value.every((item) => isNonEmptyString(item) && (!allowed || allowed.includes(item)))
  && new Set(value).size === value.length

function parseA110Fact(value: unknown) {
  if (!isRecord(value) || !exactKeys(value, A110_FACT_KEYS) || !A110_FACT_STATES.includes(value.factState as ReconciliationFactState)) return null
  if (!(value.amountMinor === null || (Number.isSafeInteger(value.amountMinor) && Number(value.amountMinor) >= 0))) return null
  if (!(value.currency === null || (typeof value.currency === 'string' && /^[A-Z]{3}$/.test(value.currency)))) return null
  if ((value.amountMinor === null) !== (value.currency === null) || !isNullableRfc3339(value.occurredAt) || !isNullableRfc3339(value.observedAt)) return null
  if (!['CONFIRMED', 'CONFLICT'].includes(String(value.factState)) && value.amountMinor !== null) return null
  return value
}

function parseA110Timeline(value: unknown) {
  if (!isRecord(value) || !exactKeys(value, A110_TIMELINE_KEYS) || !A110_FACT_CODES.includes(value.factCode as ReconciliationFactCode)
    || !A110_FACT_STATES.includes(value.factState as ReconciliationFactState) || !isNullableRfc3339(value.occurredAt) || !isNullableRfc3339(value.observedAt)) return null
  return value
}

function parseA110FinanceItem(value: unknown): ReconciliationFinanceSummary | null {
  if (!isRecord(value) || !exactKeys(value, A110_FIN_ITEM_KEYS)) return null
  for (const key of ['reconciliationRef', 'orderRef', 'responsibilityCode', 'displayVersion']) if (!isNonEmptyString(value[key])) return null
  if (!(value.supportRef === null || isNonEmptyString(value.supportRef)) || !isNullableRfc3339(value.nextReviewPoint)
    || !Number.isSafeInteger(value.projectionVersion) || Number(value.projectionVersion) <= 0
    || !isRfc3339(value.updatedAt) || !isRecord(value.factSummaries) || !exactKeys(value.factSummaries, A110_FACT_CODES)) return null
  const factSummaries = value.factSummaries
  if (!A110_FACT_CODES.every((code) => parseA110Fact(factSummaries[code]))) return null
  if (!uniqueAllowedStrings(value.differenceCategories, A110_DIFFERENCE_CATEGORIES)
    || !A110_AGE_STATES.includes(value.ageState as typeof A110_AGE_STATES[number]) || !Array.isArray(value.timeline) || !value.timeline.every(parseA110Timeline)) return null
  return value as unknown as ReconciliationFinanceSummary
}

function parseA110SupportItem(value: unknown): ReconciliationSupportSummary | null {
  if (!isRecord(value) || !exactKeys(value, A110_CS_ITEM_KEYS)) return null
  for (const key of ['reconciliationRef', 'supportRef', 'orderRef', 'maskedSubjectSummary', 'userFacingSummary', 'responsibilityCode']) if (!isNonEmptyString(value[key])) return null
  if (!isNullableRfc3339(value.nextReviewPoint) || !Number.isSafeInteger(value.projectionVersion) || Number(value.projectionVersion) <= 0
    || !isRfc3339(value.updatedAt) || !uniqueAllowedStrings(value.confirmedItems, A110_EXPLANATION_CODES) || !uniqueAllowedStrings(value.unconfirmedItems, A110_EXPLANATION_CODES)) return null
  return value as unknown as ReconciliationSupportSummary
}

function finViewStateMatches(viewState: A110ServerViewState, items: ReconciliationFinanceSummary[]) {
  const confirmed = (item: ReconciliationFinanceSummary, code: ReconciliationFactCode) => item.factSummaries[code].factState === 'CONFIRMED'
  const isRefundDeliveryConflict = (item: ReconciliationFinanceSummary) => {
    const refund = item.factSummaries.R
    const delivery = item.factSummaries.D
    return refund.factState === 'CONFIRMED' && delivery.factState === 'CONFIRMED'
      && refund.occurredAt !== null && delivery.occurredAt !== null && Date.parse(delivery.occurredAt) > Date.parse(refund.occurredAt)
  }
  if (items.some((item) => isRefundDeliveryConflict(item) !== item.differenceCategories.includes('REFUND_DELIVERY_CONFLICT'))) return false
  const refundDeliveryConflict = items.some(isRefundDeliveryConflict)
  const asymmetric = items.some((item) => item.differenceCategories.length > 0
    || confirmed(item, 'W') !== confirmed(item, 'U') || confirmed(item, 'U') !== confirmed(item, 'D'))
  const longRunningUnknown = items.some((item) => item.ageState === 'LONG_RUNNING'
    && Object.values(item.factSummaries).some(({ factState }) => ['UNKNOWN', 'PENDING_OR_INFLIGHT'].includes(factState)))
  const derived = refundDeliveryConflict ? 'REFUND_DELIVERY_CONFLICT' : asymmetric ? 'ASYMMETRIC_FACTS' : longRunningUnknown ? 'LONG_RUNNING_UNKNOWN' : 'READY'
  return viewState === derived
}

const a110Unavailable = (message: string) => ({ status: 'UNAVAILABLE' as const, data: null, message })
const a110Denied = (message: string, denialKind: 'ROLE_DENIED' | 'AUTHORITY_UNAVAILABLE') => ({ status: 'ACCESS_DENIED' as const, data: null, message, denialKind })

type LegacyA110ReadState = Exclude<AdminReadState, { status: 'READY' }> | { status: 'READY'; data: A110PageProjection; message: string }
export function parseA110ReadResponse(value: unknown): LegacyA110ReadState {
  if (!isRecord(value) || !exactKeys(value, A110_ROOT_KEYS) || !isNonEmptyString(value.requestRef)
    || value.schemaVersion !== 'A110_RECONCILIATION_READ_V1' || !A110_VIEW_STATES.includes(value.viewState as A110ServerViewState)
    || value.projectCode !== `A110_${String(value.viewState)}` || !['FIN', 'CS', null].includes(value.roleProjection as 'FIN' | 'CS' | null)
    || !['NONE', 'USER_INITIATED_READ_ONLY'].includes(String(value.retryClass)) || !Array.isArray(value.items)
    || !uniqueAllowedStrings(value.allowedActions, A110_ACTIONS)) return a110Unavailable('交易差异只读响应不符合约定')

  const viewState = value.viewState as A110ServerViewState
  const allowedActions = value.allowedActions as A110AllowedAction[]
  const denied = A110_DENIED_STATES.includes(viewState)
  if (denied) {
    if (value.roleProjection !== null || value.roleBindingVersion !== null || value.authorizationDecisionVersion !== null || value.projectionVersion !== null
      || value.items.length !== 0 || allowedActions.length !== 0) return a110Unavailable('交易差异拒绝响应不符合约定')
    return viewState === 'ACCESS_DENIED'
      ? a110Denied('当前无法访问此页面', 'ROLE_DENIED')
      : a110Denied('当前无法安全显示，请重新进入', 'AUTHORITY_UNAVAILABLE')
  }

  if (['READ_ERROR', 'UNAVAILABLE', 'VERSION_CONFLICT'].includes(viewState)) {
    if (value.roleProjection !== null || value.roleBindingVersion !== null || value.authorizationDecisionVersion !== null || value.projectionVersion !== null
      || value.items.length !== 0 || allowedActions.length !== 1 || allowedActions[0] !== 'READ_REFRESH') return a110Unavailable('交易差异失败响应不符合约定')
    const message = viewState === 'VERSION_CONFLICT' ? '当前信息已变化，请重新读取' : '暂时无法读取交易差异数据'
    return a110Unavailable(message)
  }

  if (!['FIN', 'CS'].includes(String(value.roleProjection)) || !isNonEmptyString(value.roleBindingVersion)
    || !isNonEmptyString(value.authorizationDecisionVersion) || !Number.isSafeInteger(value.projectionVersion) || Number(value.projectionVersion) <= 0
    ) return a110Unavailable('交易差异授权投影不符合约定')

  if ((viewState === 'EMPTY' && value.items.length !== 0) || (A110_DATA_STATES.includes(viewState) && value.items.length === 0)) return a110Unavailable('交易差异状态和记录数量不一致')
  const parsedItems = value.roleProjection === 'FIN' ? value.items.map(parseA110FinanceItem) : value.items.map(parseA110SupportItem)
  if (parsedItems.some((item) => !item)) return a110Unavailable('交易差异职责字段不符合约定')
  if (parsedItems.some((item) => item!.projectionVersion !== value.projectionVersion)) return a110Unavailable('交易差异版本不一致')
  if (value.roleProjection === 'FIN' && A110_DATA_STATES.includes(viewState)
    && !finViewStateMatches(viewState, parsedItems as ReconciliationFinanceSummary[])) return a110Unavailable('交易差异状态和事实不一致')

  const projection = { ...value, pageId: 'A110', items: parsedItems } as unknown as A110PageProjection
  return { status: 'READY', data: projection, message: viewState === 'EMPTY' ? '当前可查看范围没有待显示差异' : '已读取当前只读差异' }
}

export const parseAdminPageProjection = (value: unknown, requestedPage: PageId): AdminPageProjection | null => {
  if (!isRecord(value) || !exactKeys(value, ['schemaVersion', 'projectionVersion', 'pageId', 'role', 'items'])) return null
  if (value.schemaVersion !== 'ADMIN_READ_V1' || value.pageId !== requestedPage || !isNonEmptyString(value.projectionVersion)) return null
  if (!Array.isArray(value.items)) return null

  if (['SUPER_ADMIN', 'FIN', 'CS'].includes(String(value.role)) && value.pageId === 'A110') {
    const keys = ['reconciliationRef', 'orderRef', 'differenceType', 'amount', 'currency', 'state', 'ownerRef', 'discoveredAt', 'updatedAt'] as const
    const valid = value.items.every((item) => isRecord(item) && exactKeys(item, keys)
      && ['reconciliationRef', 'orderRef', 'differenceType', 'currency', 'state', 'discoveredAt', 'updatedAt'].every(key => isNonEmptyString(item[key]))
      && typeof item.amount === 'number' && (item.ownerRef === null || isNonEmptyString(item.ownerRef)))
    return valid ? value as unknown as AdminPageProjection : null
  }

  const variant = `${String(value.pageId)}:${String(value.role)}` as ProjectionVariant
  if (['SUPER_ADMIN', 'CS'].includes(String(value.role)) && value.pageId === 'A100') {
    const keys = ['caseRef', 'sourceType', 'issueType', 'relatedOrderRef', 'priorityCode', 'ownerRef', 'state', 'updatedAt'] as const
    const valid = value.items.every((item) => isRecord(item) && exactKeys(item, keys)
      && ['caseRef', 'sourceType', 'issueType', 'priorityCode', 'state', 'updatedAt'].every(key => isNonEmptyString(item[key]))
      && (item.relatedOrderRef === null || isNonEmptyString(item.relatedOrderRef)) && (item.ownerRef === null || isNonEmptyString(item.ownerRef)))
    return valid ? value as unknown as AdminPageProjection : null
  }
  if (['SUPER_ADMIN', 'CONTENT'].includes(String(value.role)) && value.pageId === 'A121') {
    const keys = ['entryRef', 'cityRef', 'cityName', 'category', 'name', 'summary', 'localAddress', 'phone', 'sourceRef', 'verifiedAt', 'validUntil', 'version', 'state', 'updatedAt'] as const
    const valid = value.items.every((item) => isRecord(item) && exactKeys(item, keys)
      && keys.filter(key => key !== 'version').every(key => isNonEmptyString(item[key])) && Number.isSafeInteger(item.version) && Number(item.version) > 0)
    return valid ? value as unknown as AdminPageProjection : null
  }
  if (['SUPER_ADMIN', 'CONTENT'].includes(String(value.role)) && value.pageId === 'A122') {
    const keys = ['objectType', 'objectRef', 'countryCode', 'category', 'title', 'summary', 'bodyText', 'sourceRef', 'editor', 'publishAt', 'startDate', 'endDate', 'weekendDays', 'effectiveFrom', 'effectiveUntil', 'validUntil', 'version', 'state', 'updatedAt'] as const
    const nullable = ['countryCode', 'summary', 'bodyText', 'editor', 'publishAt', 'startDate', 'endDate', 'weekendDays', 'effectiveFrom', 'effectiveUntil'] as const
    const valid = value.items.every((item) => isRecord(item) && exactKeys(item, keys) && ['HOLIDAY', 'NEWS'].includes(String(item.objectType))
      && ['objectRef', 'category', 'title', 'sourceRef', 'validUntil', 'state', 'updatedAt'].every(key => isNonEmptyString(item[key]))
      && nullable.every(key => item[key] === null || isNonEmptyString(item[key])) && Number.isSafeInteger(item.version) && Number(item.version) > 0)
    return valid ? value as unknown as AdminPageProjection : null
  }
  if (['SUPER_ADMIN', 'CONTENT'].includes(String(value.role)) && value.pageId === 'A130') {
    const keys = ['productRef', 'operatorCode', 'productType', 'displayName', 'benefitText', 'denominationBdt', 'validityText', 'providerCode', 'providerSku', 'countryCode', 'dataAllowanceMb', 'voiceMinutes', 'smsCount', 'channelPriority', 'phoneRule', 'saleStartAt', 'saleEndAt', 'catalogBatchRef', 'rawSkuName', 'rawBenefitText', 'supplierCost', 'settlementCurrency', 'supplierAvailability', 'catalogSyncedAt', 'normalizedType', 'normalizedOperator', 'mappingState', 'mappingFailureReason', 'state', 'version', 'priceVersionRef', 'finalAmountCny', 'fxSource', 'fxSnapshotRef', 'priceState', 'effectiveFrom', 'effectiveUntil', 'priceVersion', 'priceSupplierCost', 'priceSettlementCurrency', 'fxDirection', 'fxRate', 'fxUpdatedAt', 'fxValidUntil', 'bufferRate', 'markupRate', 'wechatFeeRate', 'taxRate', 'minimumMarginRate', 'roundingRule', 'promotionBearer', 'pricingScope'] as const
    const nullableStrings = ['validityText', 'phoneRule', 'saleStartAt', 'saleEndAt', 'catalogBatchRef', 'rawSkuName', 'rawBenefitText', 'settlementCurrency', 'supplierAvailability', 'catalogSyncedAt', 'normalizedType', 'normalizedOperator', 'mappingState', 'mappingFailureReason', 'priceVersionRef', 'fxSource', 'fxSnapshotRef', 'priceState', 'effectiveFrom', 'effectiveUntil', 'priceSettlementCurrency', 'fxDirection', 'fxUpdatedAt', 'fxValidUntil', 'roundingRule', 'promotionBearer', 'pricingScope'] as const
    const nullableNumbers = ['denominationBdt', 'dataAllowanceMb', 'voiceMinutes', 'smsCount', 'supplierCost', 'finalAmountCny', 'priceSupplierCost', 'fxRate', 'bufferRate', 'markupRate', 'wechatFeeRate', 'taxRate', 'minimumMarginRate'] as const
    const valid = value.items.every((item) => isRecord(item) && exactKeys(item, keys)
      && ['productRef', 'operatorCode', 'productType', 'displayName', 'benefitText', 'providerCode', 'providerSku', 'countryCode', 'state'].every(key => isNonEmptyString(item[key]))
      && nullableStrings.every(key => item[key] === null || isNonEmptyString(item[key]))
      && nullableNumbers.every(key => item[key] === null || typeof item[key] === 'number')
      && Number.isSafeInteger(item.channelPriority) && Number(item.channelPriority) >= 0 && Number.isSafeInteger(item.version) && Number(item.version) > 0 && (item.priceVersion === null || (Number.isSafeInteger(item.priceVersion) && Number(item.priceVersion) > 0)))
    return valid ? value as unknown as AdminPageProjection : null
  }
  const itemKeys = itemKeyMap[variant]
  if (!itemKeys) return null
  if (!value.items.every((item) => isRecord(item) && exactKeys(item, itemKeys) && itemKeys.every((key) => isNonEmptyString(item[key])))) return null

  return value as unknown as AdminPageProjection
}

export const resolveAdminDataMode = (value: unknown): AdminDataMode | null => {
  if (value === undefined || value === '' || value === 'PROJECT_API_PROXY') return 'PROJECT_API_PROXY'
  return null
}

export async function loadAdminPage(
  mode: AdminDataMode,
  pageId: PageId,
  transport: AdminTransport = fetch,
): Promise<AdminReadState> {
  try {
    const response = await transport(`${ADMIN_READ_PROXY_PATH}/${pageId}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (response.status === 401) {
      return { status: 'UNAUTHENTICATED', data: null, message: '登录已失效，请重新登录' }
    }
    if (response.status === 403) {
      return { status: 'ACCESS_DENIED', data: null, message: '当前无法访问此页面', denialKind: 'ROLE_DENIED' }
    }
    if (response.status === 404) return { status: 'UNAVAILABLE', data: null, message: '后台只读代理尚未配置' }
    if (!response.ok) return { status: 'UNAVAILABLE', data: null, message: '后台只读服务暂不可用' }

    const body = await response.json()
    const parsed = parseAdminPageProjection(body, pageId)
    if (!parsed) return { status: 'UNAVAILABLE', data: null, message: '后台只读响应不符合约定' }
    return { status: 'READY', data: parsed, message: '已读取受信后台代理数据' }
  } catch {
    return { status: 'UNAVAILABLE', data: null, message: '后台只读服务连接失败' }
  }
}
