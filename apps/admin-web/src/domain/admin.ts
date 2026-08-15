export type PageId = 'A100' | 'A110' | 'A120' | 'A121' | 'A122' | 'A130' | 'A140'
export type AdminRole = 'CS' | 'CONTENT' | 'FIN'
export type AdminDataMode = 'BUILTIN_SYNTHETIC' | 'PROJECT_API_PROXY'

export interface SupportCaseSummary {
  supportRef: string
  subjectLabel: string
  statusLabel: string
  maskedPhone: string
  nextReviewLabel: string
  noteStatusLabel: string
  escalationStatusLabel: string
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

export interface ManagedContentSummary {
  contentRef: string; title: string; summary: string; category: string; sourceLabel: string
  statusLabel: string; versionLabel: string; verifiedAtLabel: string; validUntilLabel: string; updatedAtLabel: string
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
  catalogRef: string
  displayName: string
  denominationLabel: string
  currencyLabel: string
  priceCostCandidateLabel: string
  validityLabel: string
  supportBatchLabel: string
  financeReviewLabel: string
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
  | (ProjectionBase & { pageId: 'A100'; role: 'CS'; items: SupportCaseSummary[] })
  | (ProjectionBase & { pageId: 'A120'; role: 'CONTENT'; items: ContentReviewSummary[] })
  | (ProjectionBase & { pageId: 'A121'; role: 'CONTENT'; items: ManagedContentSummary[] })
  | (ProjectionBase & { pageId: 'A122'; role: 'CONTENT'; items: ManagedContentSummary[] })
  | (ProjectionBase & { pageId: 'A130'; role: 'CONTENT'; items: CatalogContentSummary[] })
  | (ProjectionBase & { pageId: 'A130'; role: 'FIN'; items: CatalogFinanceSummary[] })
  | (ProjectionBase & { pageId: 'A140'; role: 'CS'; items: SupportOrderSummary[] })
  | (ProjectionBase & { pageId: 'A140'; role: 'FIN'; items: FinanceOrderSummary[] })
  | A110PageProjection

export type AdminReadState =
  | { status: 'LOADING'; data: null; message: string }
  | { status: 'READY'; data: AdminPageProjection; message: string }
  | { status: 'UNAUTHENTICATED'; data: null; message: string }
  | { status: 'ACCESS_DENIED'; data: null; message: string; denialKind: 'ROLE_DENIED' | 'AUTHORITY_UNAVAILABLE' }
  | { status: 'UNAVAILABLE'; data: null; message: string }

export const pageRoleAllowed = (pageId: PageId, role: AdminRole) =>
  (pageId === 'A100' && role === 'CS')
  || (pageId === 'A110' && (role === 'CS' || role === 'FIN'))
  || (pageId === 'A120' && role === 'CONTENT')
  || ((pageId === 'A121' || pageId === 'A122') && role === 'CONTENT')
  || (pageId === 'A130' && (role === 'CONTENT' || role === 'FIN'))
  || (pageId === 'A140' && (role === 'CS' || role === 'FIN'))
