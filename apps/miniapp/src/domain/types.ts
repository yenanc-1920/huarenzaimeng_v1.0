export type FactState = 'CONFIRMED' | 'NOT_STARTED' | 'PENDING' | 'UNKNOWN' | 'CONFLICT'

export interface Money {
  minor: number
  currency: 'CNY' | 'BDT'
}

export interface AllowedAction {
  actionCode: 'REFRESH' | 'CONTACT_SUPPORT' | 'SAFE_EXIT'
  enabled: boolean
  expectedProjectionVersion: number
}

export interface OrderProjection {
  projectionId: string
  orderRef: string
  projectionVersion: number
  generatedAt: string
  terminal: boolean
  stateCode: string
  facts: {
    payment: FactState
    eligibility: FactState
    upstream: FactState
    delivery: FactState
    refund: FactState
    accounting: FactState
  }
  total: Money | null
  faceValue: Money | null
  priceSnapshotRef: string | null
  priceValidUntil: string | null
  maskedPhone: string
  operatorCode: string
  operatorName: string
  productRef: string
  productName: string
  denominationRef: string
  supportedOperatorSetVersion: number
  catalogVersion: number
  priceVersion: string
  allowedActions: AllowedAction[]
  supportRef: string | null
  nextPollAt: string | null
}

export interface CatalogItem {
  operatorCode: string
  productRef: string
  denominationRef: string
  itemKind: 'PRESET_DENOMINATION' | 'PRESET_PACKAGE'
  faceValue: Money
  displayName?: string
  productType?: 'BALANCE' | 'DATA' | 'BUNDLE'
  benefitText?: string
  validityText?: string | null
  finalAmountCny?: number
  priceVersionRef?: string
  available: true
}

export type OperatorQualification = 'SUPPORTED' | 'UNSUPPORTED' | 'UNKNOWN'

export type CatalogProjection =
  | { operatorQualification: 'SUPPORTED'; supportedOperatorSetVersion: number; catalogVersion: number; operatorCode: string; items: CatalogItem[]; evidenceSemantics: CatalogEvidenceSemantics }
  | { operatorQualification: 'UNSUPPORTED'; supportedOperatorSetVersion: number; catalogVersion: number; operatorCode: string; items: []; evidenceSemantics: CatalogEvidenceSemantics }
  | { operatorQualification: 'UNKNOWN'; supportedOperatorSetVersion: null; catalogVersion: null; operatorCode: string; items: []; evidenceSemantics: CatalogEvidenceSemantics }

export type CatalogEvidenceSemantics = 'LOCAL_MOCK_NO_REAL_OPERATOR_FACTS' | 'LOCAL_DATABASE_STATE_NOT_EXTERNAL_OPERATOR_FACT'

export interface RechargeSelection {
  maskedPhone: string
  operatorCode: string
  operatorName: string
  productRef: string
  denominationRef: string
  itemKind: CatalogItem['itemKind']
  faceValue: Money
  supportedOperatorSetVersion: number
  catalogVersion: number
}

export interface QuoteSnapshot {
  orderRef: string
  priceSnapshotRef: string
  maskedPhone: string
  operatorCode: string
  productRef: string
  denominationRef: string
  productName: string
  faceValue: Money
  total: Money
  priceVersion: string
  supportedOperatorSetVersion: number
  catalogVersion: number
  operatorName: string
  expiresAt: string
  validUntil: string
  valid: boolean
}

export type EligibilityResult =
  | { outcome: 'ELIGIBLE'; maskedPhone: string; operatorCode: string; operatorName: string; caseKey: string }
  | { outcome: 'UNKNOWN'; maskedPhone: string; caseKey: string; projectCode: 'PREPAY_MNP_UNKNOWN' }

export type OrderSummaryState =
  | 'AWAITING_PAYMENT'
  | 'PAYMENT_PROCESSING'
  | 'PAID_AWAITING_TOPUP'
  | 'TOPUP_PROCESSING'
  | 'TOPUP_RESULT_UNKNOWN'
  | 'DELIVERED'
  | 'CONFIRMED_NOT_DELIVERED'
  | 'REFUND_PROCESSING'
  | 'REFUNDED'
  | 'DELIVERY_REFUND_CONFLICT_REVIEW'
  | 'SUPPORT_REVIEW'
export interface OrderSummary { orderRef: string; stateCode: OrderSummaryState; projectionVersion: number; updatedAt: string }
export interface ProjectSessionProjection {
  role: 'GUEST' | 'BUYER'
  projectSubjectRef: string | null
  sessionVersion: number
  authorizationSetRef: string | null
  authorizationEvidenceVersion: string | null
  authorizedOrderRefs: string[]
  issuedAt: string | null
  expiresAt: string | null
  semantics: 'SERVER_PROJECTION_CACHE_NOT_AUTHORITY'
}
export type RecoveryResult =
  | { outcome: 'RECOVERED'; recoveryCaseRef: string; retryClass: 'NONE'; safeQueryPath: null; authorization: Omit<ProjectSessionProjection, 'role' | 'semantics'> & { sessionRole: 'BUYER' } }
  | { outcome: 'REJECTED'; recoveryCaseRef: string; retryClass: 'NONE'; safeQueryPath: null; authorization: null }
  | { outcome: 'UNKNOWN'; recoveryCaseRef: string; retryClass: 'READ_SAFE'; safeQueryPath: string; authorization: null }
export interface SupportCase { supportRef: string; confirmed: string; unknown: string; responsibility: string; nextReviewAt: string | null }
export type DirectoryPublicationState = 'ELIGIBLE' | 'EXPIRED' | 'COMPLAINT_PENDING' | 'REVIEW' | 'REMOVED' | 'UNKNOWN'
export interface DirectoryCity { cityCode:string; countryCode:string; displayName:string; localName:string; timezoneId:string }
export interface DirectorySummary { entryRef:string; cityCode:string; cityName:string; category:string; displayName:string; summary:string; localAddress:string; phone:string; verifiedAt:string; validUntil:string; updatedAt:string }
export interface DirectoryItem extends DirectorySummary { sourceLabel?:string }
export type DirectoryDetailResult = { outcome:'READY'; item:DirectoryItem }
export interface ContentErrorReportCommand { commandId: string; idempotencyKey: string; contentVersion: number; expectedAggregateVersion: number; reason: string }
export interface ContentErrorReportResult { outcome: 'CONTENT_ERROR_REPORTED'; supportRef: string; reviewTarget: 'A120' }

export type LifeContentReadState = 'READY' | 'EMPTY' | 'UNDER_REVIEW' | 'EXPIRED' | 'REMOVED' | 'UNKNOWN' | 'ERROR'
export type LifeContentDetailState = Exclude<LifeContentReadState, 'EMPTY'>
export type LifeContentCategory = 'LIFE_REMINDER' | 'HOLIDAY_EXPLANATION'
export type LifeContentCoverState = 'AVAILABLE' | 'NOT_CONFIGURED' | 'IMAGE_UNAVAILABLE'
export type LifeContentRetryClass = 'NONE' | 'USER_INITIATED_READ_ONLY'

export interface LifeContentSummary {
  contentRef: string
  contentVersion: string
  category: LifeContentCategory
  title: string
  summary: string
  sourceType: string
  jurisdiction: string
  applicableAudience: string
  publishedAt: string
  updatedAt: string
  effectiveFrom: string
  effectiveTo: string
  freshnessState: 'CURRENT'
  coverState: LifeContentCoverState
  coverRef: string | null
}

export interface LifeContentDetailItem extends LifeContentSummary { body: string }

export interface LifeContentListResult {
  requestRef: string
  viewState: LifeContentReadState
  projectCode: string
  schemaVersion: 'LIFE_CONTENT_READ_V1'
  visibilityRuleVersion: string
  items: LifeContentSummary[]
  retryClass: LifeContentRetryClass
  nextReadAt: null
}

export interface LifeContentDetailResult {
  requestRef: string
  viewState: LifeContentDetailState
  projectCode: string
  schemaVersion: 'LIFE_CONTENT_READ_V1'
  visibilityRuleVersion: string
  contentRef: string
  contentVersion: string
  item: LifeContentDetailItem | null
  retryClass: LifeContentRetryClass
  nextReadAt: null
}
