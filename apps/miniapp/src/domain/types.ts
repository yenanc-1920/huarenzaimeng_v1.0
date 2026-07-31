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
  | { outcome: 'ELIGIBLE'; maskedPhone: string; caseKey: string }
  | { outcome: 'UNKNOWN'; maskedPhone: string; caseKey: string; projectCode: 'PREPAY_MNP_UNKNOWN' }

export interface OrderSummary { orderRef: string; maskedPhone: string; total: Money; stateCode: string; projectionVersion: number; updatedAt: string }
export interface SupportCase { supportRef: string; confirmed: string; unknown: string; responsibility: string; nextReviewAt: string | null }
export interface DirectoryItem { contentRef: string; title: string; category: string; verificationScope: string; sourceType: string; verifiedAt: string; updatedAt: string; publicationState: 'ELIGIBLE' | 'REVIEW' | 'REMOVED' }
