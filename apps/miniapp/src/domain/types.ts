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
  operatorName: string
  productName: string
  priceVersion: string
  allowedActions: AllowedAction[]
  supportRef: string | null
  nextPollAt: string | null
}

export interface CatalogItem {
  productRef: string
  name: string
  faceValue: Money
  catalogVersion: string
  available: boolean
}

export interface QuoteSnapshot {
  orderRef: string
  priceSnapshotRef: string
  maskedPhone: string
  productName: string
  faceValue: Money
  total: Money
  priceVersion: string
  operatorName: string
  expiresAt: string
  validUntil: string
  valid: boolean
}

export type EligibilityResult =
  | { outcome: 'ELIGIBLE'; maskedPhone: string; operatorName: string; caseKey: string }
  | { outcome: 'UNKNOWN'; maskedPhone: string; caseKey: string; projectCode: 'PREPAY_MNP_UNKNOWN' }

export interface OrderSummary { orderRef: string; maskedPhone: string; total: Money; stateCode: string; projectionVersion: number; updatedAt: string }
export interface SupportCase { supportRef: string; confirmed: string; unknown: string; responsibility: string; nextReviewAt: string | null }
export type DirectoryPublicationState = 'ELIGIBLE' | 'EXPIRED' | 'COMPLAINT_PENDING' | 'REVIEW' | 'REMOVED' | 'UNKNOWN'
export interface DirectorySummary { contentRef: string; contentVersion: number; title: string; summary: string; category: string; validUntil: string; updatedAt: string; publicationState: 'ELIGIBLE' }
export interface DirectoryItem { contentRef: string; contentVersion: number; title: string; summary: string; category: string; sourceCategory: 'SELF_RESEARCH'; sourceType: '自营调研来源'; verificationScope: 'NAME_AND_PUBLIC_CONTACT_CHANNELS'; verifiedAt: string; validUntil: string; updatedAt: string; disclaimer: string; publicationState: DirectoryPublicationState }
export type DirectoryDetailResult =
  | { outcome: 'READY'; item: DirectoryItem & { publicationState: 'ELIGIBLE' } }
  | { outcome: 'EMPTY' | 'EXPIRED' | 'COMPLAINT_PENDING' | 'REVIEW' | 'REMOVED' | 'UNKNOWN' }
export interface ContentErrorReportCommand { commandId: string; idempotencyKey: string; contentVersion: number; expectedAggregateVersion: number; reason: string }
export interface ContentErrorReportResult { outcome: 'CONTENT_ERROR_REPORTED'; supportRef: string; reviewTarget: 'A120' }
