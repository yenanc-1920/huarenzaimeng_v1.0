export type PageId = 'A100' | 'A110' | 'A120' | 'A121' | 'A122' | 'A130' | 'A140'
export type AdminRole = 'SUPER_ADMIN' | 'CS' | 'CONTENT' | 'FIN'
export type AdminDataMode = 'PROJECT_API_PROXY'

export interface SupportCaseSummary {
  caseRef: string
  sourceType: string
  issueType: string
  relatedOrderRef: string | null
  priorityCode: string
  state: string
  ownerRef: string | null
  updatedAt: string
}

export interface ContentReviewSummary {
  contentRef: string
  title: string
  sourceLabel: string
  reviewLabel: string
  complaintLabel: string
  visibilityLabel: string
  ownerLabel: string
  historyLabel: string
  removalLabel: string
}

export interface ReconciliationCaseSummary {
  reconciliationRef: string
  orderRef: string
  differenceType: string
  amount: number
  currency: string
  state: string
  ownerRef: string | null
  discoveredAt: string
  updatedAt: string
}

export interface ManagedContentSummary {
  entryRef: string
  cityRef: string
  cityName: string
  category: string
  name: string
  summary: string
  localAddress: string
  phone: string
  sourceRef: string
  verifiedAt: string
  validUntil: string
  version: number
  state: string
  updatedAt: string
}

export interface ManagedRuleOrNewsSummary {
  objectType: 'HOLIDAY' | 'NEWS'
  objectRef: string
  countryCode: string | null
  category: string
  title: string
  summary: string | null
  bodyText: string | null
  sourceRef: string
  editor: string | null
  publishAt: string | null
  startDate: string | null
  endDate: string | null
  weekendDays: string | null
  effectiveFrom: string | null
  effectiveUntil: string | null
  validUntil: string
  version: number
  state: string
  updatedAt: string
}

export interface CatalogContentSummary {
  catalogRef: string
  displayName: string
  operatorLabel: string
  categoryLabel: string
  contentStatusLabel: string
  saleReviewLabel: string
}

export interface CatalogFinanceSummary {
  productRef: string
  operatorCode: string
  productType: string
  displayName: string
  benefitText: string
  denominationBdt: number | null
  validityText: string | null
  providerCode: string
  providerSku: string
  countryCode: string
  dataAllowanceMb: number | null
  voiceMinutes: number | null
  smsCount: number | null
  channelPriority: number
  phoneRule: string | null
  saleStartAt: string | null
  saleEndAt: string | null
  catalogBatchRef: string | null
  rawSkuName: string | null
  rawBenefitText: string | null
  supplierCost: number | null
  settlementCurrency: string | null
  supplierAvailability: string | null
  catalogSyncedAt: string | null
  normalizedType: string | null
  normalizedOperator: string | null
  mappingState: string | null
  mappingFailureReason: string | null
  state: string
  version: number
  priceVersionRef: string | null
  finalAmountCny: number | null
  fxSource: string | null
  fxSnapshotRef: string | null
  priceState: string | null
  effectiveFrom: string | null
  effectiveUntil: string | null
  priceVersion: number | null
  priceSupplierCost: number | null
  priceSettlementCurrency: string | null
  fxDirection: string | null
  fxRate: number | null
  fxUpdatedAt: string | null
  fxValidUntil: string | null
  bufferRate: number | null
  markupRate: number | null
  wechatFeeRate: number | null
  taxRate: number | null
  minimumMarginRate: number | null
  roundingRule: string | null
  promotionBearer: string | null
  pricingScope: string | null
}

export interface SupportOrderSummary {
  orderRef: string
  maskedPhone: string
  userStatusLabel: string
  totalLabel: string
  updatedLabel: string
}

export interface FinanceOrderSummary {
  orderRef: string
  refundLayerLabel: string
  factStatusLabel: string
  totalLabel: string
  agingLabel: string
}

export type ReconciliationFactCode = 'W' | 'U' | 'D' | 'R' | 'L'
export type ReconciliationFactState = 'ABSENT_CONFIRMED' | 'PENDING_OR_INFLIGHT' | 'UNKNOWN' | 'CONFIRMED' | 'CONFLICT'
export type ReconciliationDifferenceCategory = 'MISSING' | 'DUPLICATE' | 'AMOUNT_MISMATCH' | 'CURRENCY_MISMATCH' | 'FACT_CONFLICT' | 'ACCOUNTING_INCOMPLETE' | 'REFUND_DELIVERY_CONFLICT' | 'UNKNOWN'
export type ReconciliationAgeState = 'CURRENT' | 'LONG_RUNNING' | 'UNKNOWN'
export type A110ServerViewState = 'READY' | 'EMPTY' | 'READ_ERROR' | 'UNAVAILABLE' | 'ACCESS_DENIED' | 'AUTHORITY_UNKNOWN' | 'REVOKED' | 'VERSION_CONFLICT' | 'LONG_RUNNING_UNKNOWN' | 'ASYMMETRIC_FACTS' | 'REFUND_DELIVERY_CONFLICT'
export type A110AllowedAction = 'READ_REFRESH' | 'NAVIGATE_A100' | 'NAVIGATE_A140'

export interface ReconciliationFactSummary {
  factState: ReconciliationFactState
  amountMinor: number | null
  currency: string | null
  occurredAt: string | null
  observedAt: string | null
}

export interface ReconciliationTimelineEntry {
  factCode: ReconciliationFactCode
  factState: ReconciliationFactState
  occurredAt: string | null
  observedAt: string | null
}

export interface ReconciliationFinanceSummary {
  reconciliationRef: string
  orderRef: string
  supportRef: string | null
  factSummaries: Record<ReconciliationFactCode, ReconciliationFactSummary>
  differenceCategories: ReconciliationDifferenceCategory[]
  ageState: ReconciliationAgeState
  responsibilityCode: string
  timeline: ReconciliationTimelineEntry[]
  nextReviewPoint: string | null
  updatedAt: string
  projectionVersion: number
  displayVersion: string
}

export interface ReconciliationSupportSummary {
  reconciliationRef: string
  supportRef: string
  orderRef: string
  maskedSubjectSummary: string
  userFacingSummary: string
  confirmedItems: string[]
  unconfirmedItems: string[]
  responsibilityCode: string
  nextReviewPoint: string | null
  updatedAt: string
  projectionVersion: number
}

export interface A110TransportEnvelope {
  requestRef: string
  viewState: A110ServerViewState
  projectCode: string
  schemaVersion: 'A110_RECONCILIATION_READ_V1'
  roleProjection: 'FIN' | 'CS' | null
  roleBindingVersion: string | null
  authorizationDecisionVersion: string | null
  projectionVersion: number | null
  items: unknown[]
  allowedActions: A110AllowedAction[]
  retryClass: 'NONE' | 'USER_INITIATED_READ_ONLY'
}

export type A110PageProjection =
  | (Omit<A110TransportEnvelope, 'items' | 'roleProjection'> & { pageId: 'A110'; roleProjection: 'FIN'; items: ReconciliationFinanceSummary[] })
  | (Omit<A110TransportEnvelope, 'items' | 'roleProjection'> & { pageId: 'A110'; roleProjection: 'CS'; items: ReconciliationSupportSummary[] })

interface ProjectionBase {
  schemaVersion: 'ADMIN_READ_V1'
  projectionVersion: string
}

export type AdminPageProjection =
  | (ProjectionBase & { pageId: 'A100'; role: 'SUPER_ADMIN' | 'CS'; items: SupportCaseSummary[] })
  | (ProjectionBase & { pageId: 'A110'; role: 'SUPER_ADMIN' | 'FIN' | 'CS'; items: ReconciliationCaseSummary[] })
  | (ProjectionBase & { pageId: 'A120'; role: 'SUPER_ADMIN' | 'CONTENT'; items: ContentReviewSummary[] })
  | (ProjectionBase & { pageId: 'A121'; role: 'SUPER_ADMIN' | 'CONTENT'; items: ManagedContentSummary[] })
  | (ProjectionBase & { pageId: 'A122'; role: 'SUPER_ADMIN' | 'CONTENT'; items: ManagedRuleOrNewsSummary[] })
  | (ProjectionBase & { pageId: 'A130'; role: 'SUPER_ADMIN' | 'CONTENT'; items: CatalogFinanceSummary[] })
  | (ProjectionBase & { pageId: 'A140'; role: 'SUPER_ADMIN' | 'CS' | 'FIN'; items: SupportOrderSummary[] })

export type AdminReadState =
  | { status: 'LOADING'; data: null; message: string }
  | { status: 'READY'; data: AdminPageProjection; message: string }
  | { status: 'UNAUTHENTICATED'; data: null; message: string }
  | { status: 'ACCESS_DENIED'; data: null; message: string; denialKind: 'ROLE_DENIED' | 'AUTHORITY_UNAVAILABLE' }
  | { status: 'UNAVAILABLE'; data: null; message: string }

export const pageRoleAllowed = (pageId: PageId, role: AdminRole) =>
  role === 'SUPER_ADMIN' || (pageId === 'A100' && role === 'CS')
  || (pageId === 'A110' && (role === 'CS' || role === 'FIN'))
  || (pageId === 'A120' && role === 'CONTENT')
  || ((pageId === 'A121' || pageId === 'A122') && role === 'CONTENT')
  || (pageId === 'A130' && role === 'CONTENT')
  || (pageId === 'A140' && (role === 'CS' || role === 'FIN'))
