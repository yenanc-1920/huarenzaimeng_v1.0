import type { A110AllowedAction, A110ServerViewState, A110TransportEnvelope, AdminPageProjection, AdminRole, PageId, ReconciliationFactCode, ReconciliationFactState, ReconciliationFinanceSummary, ReconciliationSupportSummary } from '../domain/admin.ts'

const base = {
  schemaVersion: 'ADMIN_READ_V1' as const,
  projectionVersion: 'BUILTIN_SYNTHETIC_V1',
}

const projections: AdminPageProjection[] = [
  {
    ...base,
    pageId: 'A100',
    role: 'CS',
    items: [
      { supportRef: '客服示例引用 01', subjectLabel: '充值结果待确认', statusLabel: '等待核对', maskedPhone: '01•• •••• 78', nextReviewLabel: '下一复核点尚未确定', noteStatusLabel: '暂无新增备注', escalationStatusLabel: '尚未升级' },
      { supportRef: '客服示例引用 02', subjectLabel: '用户补充材料待查看', statusLabel: '处理中', maskedPhone: '01•• •••• 26', nextReviewLabel: '等待用户材料', noteStatusLabel: '已有只读备注记录', escalationStatusLabel: '尚未升级' },
    ],
  },
  {
    ...base,
    pageId: 'A120',
    role: 'CONTENT',
    items: [
      { contentRef: '内容示例引用 A', title: '信息条目占位 A', sourceLabel: '自主整理的公开信息', reviewLabel: '等待复核', complaintLabel: '无待查投诉', visibilityLabel: '暂不可展示', ownerLabel: '内容职责待复核', historyLabel: '保留一个示例历史版本', removalLabel: '尚无下架原因' },
      { contentRef: '内容示例引用 B', title: '信息条目占位 B', sourceLabel: '自主整理的公开信息', reviewLabel: '证据缺失', complaintLabel: '投诉待查', visibilityLabel: '已下架', ownerLabel: '内容职责继续核验', historyLabel: '保留两个示例历史版本', removalLabel: '投诉待查期间停止展示' },
      { contentRef: '内容示例引用 C', title: '信息条目占位 C', sourceLabel: '自主整理的公开信息', reviewLabel: '已过期', complaintLabel: '无待查投诉', visibilityLabel: '已下架', ownerLabel: '内容职责更新时效', historyLabel: '保留一个示例历史版本', removalLabel: '核验信息已过期' },
    ],
  },
  {
    ...base,
    pageId: 'A130',
    role: 'CONTENT',
    items: [
      { catalogRef: '目录示例引用 A', displayName: '商品占位 A', operatorLabel: '运营商占位 A', categoryLabel: '预设充值项目', contentStatusLabel: '草稿待补充', saleReviewLabel: '等待另一职责' },
      { catalogRef: '目录示例引用 B', displayName: '商品占位 B', operatorLabel: '运营商占位 B', categoryLabel: '预设充值项目', contentStatusLabel: '内容已核对', saleReviewLabel: '已停用' },
    ],
  },
  {
    ...base,
    pageId: 'A130',
    role: 'FIN',
    items: [
      { catalogRef: '目录示例引用 A', displayName: '商品占位 A', denominationLabel: 'BDT —', currencyLabel: '待正式目录确认', priceCostCandidateLabel: '价格与成本候选未提供', validityLabel: '尚未确定', supportBatchLabel: '支持批次尚未确定', financeReviewLabel: '等待另一职责' },
      { catalogRef: '目录示例引用 B', displayName: '商品占位 B', denominationLabel: 'BDT —', currencyLabel: '待正式目录确认', priceCostCandidateLabel: '价格与成本候选未提供', validityLabel: '尚未确定', supportBatchLabel: '支持批次尚未确定', financeReviewLabel: '已停用' },
    ],
  },
  {
    ...base,
    pageId: 'A140',
    role: 'CS',
    items: [
      { orderRef: '订单示例 01', maskedPhone: '01•• •••• 78', userStatusLabel: '充值结果待确认', totalLabel: 'CNY —', updatedLabel: '待确认' },
      { orderRef: '订单示例 02', maskedPhone: '01•• •••• 26', userStatusLabel: '客服处理中', totalLabel: 'CNY —', updatedLabel: '待确认' },
    ],
  },
  {
    ...base,
    pageId: 'A140',
    role: 'FIN',
    items: [
      { orderRef: '订单示例 01', refundLayerLabel: '退款候选待确认', factStatusLabel: '资金事实未知', totalLabel: 'CNY —', agingLabel: '账龄待确认' },
      { orderRef: '订单示例 02', refundLayerLabel: '尚无可信退款事实', factStatusLabel: '差异待核对', totalLabel: 'CNY —', agingLabel: '账龄待确认' },
    ],
  },
]

export function getSyntheticProjection(pageId: PageId, role: AdminRole): AdminPageProjection | null {
  return projections.find((projection) => projection.pageId !== 'A110' && projection.pageId === pageId && projection.role === role) ?? null
}

const fact = (factState: ReconciliationFactState, amountMinor: number | null, currency: string | null, occurredAt: string | null, observedAt: string | null) =>
  ({ factState, amountMinor, currency, occurredAt, observedAt })

const financeReady: ReconciliationFinanceSummary = {
  reconciliationRef: '示例差异编号 001', orderRef: '示例订单编号 001', supportRef: '示例客服编号 001',
  factSummaries: {
    W: fact('CONFIRMED', 1280, 'CNY', '2026-08-02T06:00:00Z', '2026-08-02T06:00:02Z'),
    U: fact('CONFIRMED', 20000, 'BDT', '2026-08-02T06:00:03Z', '2026-08-02T06:00:04Z'),
    D: fact('CONFIRMED', 20000, 'BDT', '2026-08-02T06:00:05Z', '2026-08-02T06:00:06Z'), R: fact('ABSENT_CONFIRMED', null, null, null, '2026-08-02T06:00:07Z'),
    L: fact('CONFIRMED', 1280, 'CNY', '2026-08-02T06:00:08Z', '2026-08-02T06:00:09Z'),
  },
  differenceCategories: [], ageState: 'CURRENT', responsibilityCode: 'FIN_REVIEW_QUEUE',
  timeline: [
    { factCode: 'W', factState: 'CONFIRMED', occurredAt: '2026-08-02T06:00:00Z', observedAt: '2026-08-02T06:00:02Z' },
    { factCode: 'U', factState: 'CONFIRMED', occurredAt: '2026-08-02T06:00:03Z', observedAt: '2026-08-02T06:00:04Z' },
    { factCode: 'D', factState: 'CONFIRMED', occurredAt: '2026-08-02T06:00:05Z', observedAt: '2026-08-02T06:00:06Z' },
  ],
  nextReviewPoint: '2026-08-02T07:00:00Z', updatedAt: '2026-08-02T06:00:10Z', projectionVersion: 1, displayVersion: '界面预览 1',
}

const supportReady: ReconciliationSupportSummary = {
  reconciliationRef: '示例差异编号 001', supportRef: '示例客服编号 001', orderRef: '示例订单编号 001',
  maskedSubjectSummary: '01•• •••• 78', userFacingSummary: '充值链路仍有一项结果待确认',
  confirmedItems: ['PAYMENT_RECEIVED'], unconfirmedItems: ['DELIVERY_RESULT_PENDING', 'LOCAL_RECORD_PENDING'],
  responsibilityCode: 'CS_FOLLOW_UP', nextReviewPoint: '2026-08-02T07:00:00Z', updatedAt: '2026-08-02T06:00:10Z', projectionVersion: 1,
}

const dataStates = new Set<A110ServerViewState>(['READY', 'LONG_RUNNING_UNKNOWN', 'ASYMMETRIC_FACTS', 'REFUND_DELIVERY_CONFLICT'])
const deniedStates = new Set<A110ServerViewState>(['ACCESS_DENIED', 'AUTHORITY_UNKNOWN', 'REVOKED'])

const actionsFor = (viewState: A110ServerViewState): A110AllowedAction[] => {
  if (deniedStates.has(viewState)) return []
  if (['READ_ERROR', 'UNAVAILABLE', 'VERSION_CONFLICT'].includes(viewState)) return ['READ_REFRESH']
  return ['READ_REFRESH', 'NAVIGATE_A100', 'NAVIGATE_A140']
}

function financeItemFor(viewState: A110ServerViewState): ReconciliationFinanceSummary {
  if (viewState === 'LONG_RUNNING_UNKNOWN') return {
    ...financeReady,
    factSummaries: {
      ...financeReady.factSummaries,
      W: fact('UNKNOWN', null, null, null, null), U: fact('UNKNOWN', null, null, null, null), D: fact('UNKNOWN', null, null, null, null),
    },
    ageState: 'LONG_RUNNING', differenceCategories: [], nextReviewPoint: null,
  }
  if (viewState === 'ASYMMETRIC_FACTS') {
    return {
      ...financeReady,
      factSummaries: { ...financeReady.factSummaries, U: fact('UNKNOWN', null, null, null, null) },
      differenceCategories: [], nextReviewPoint: null,
    }
  }
  if (viewState === 'REFUND_DELIVERY_CONFLICT') {
    const facts = { ...financeReady.factSummaries, D: fact('CONFIRMED', 20000, 'BDT', '2026-08-02T06:10:00Z', '2026-08-02T06:10:02Z'), R: fact('CONFIRMED', 1280, 'CNY', '2026-08-02T06:08:00Z', '2026-08-02T06:08:02Z') }
    return { ...financeReady, factSummaries: facts, differenceCategories: ['REFUND_DELIVERY_CONFLICT'], responsibilityCode: 'CROSS_TEAM_REVIEW', nextReviewPoint: null }
  }
  return financeReady
}

function supportItemFor(viewState: A110ServerViewState): ReconciliationSupportSummary {
  if (viewState === 'LONG_RUNNING_UNKNOWN') return { ...supportReady, userFacingSummary: '部分结果较长时间仍未确认', unconfirmedItems: ['DELIVERY_RESULT_PENDING'], nextReviewPoint: '等待权威结果更新' }
  if (viewState === 'ASYMMETRIC_FACTS') return { ...supportReady, userFacingSummary: '已知结果之间存在不一致', unconfirmedItems: ['RESULT_DIFFERENCE_PENDING'], nextReviewPoint: '等待跨职责只读核对' }
  if (viewState === 'REFUND_DELIVERY_CONFLICT') return { ...supportReady, userFacingSummary: '退款后出现新的到账结果，需要复核', confirmedItems: ['REFUND_CONFIRMED', 'DELIVERY_CONFIRMED'], unconfirmedItems: ['FINAL_CONCLUSION_PENDING'], nextReviewPoint: '等待跨职责复核结论' }
  return supportReady
}

export function getSyntheticA110Response(role: AdminRole, viewState: A110ServerViewState): A110TransportEnvelope {
  const failureClosed = ['READ_ERROR', 'UNAVAILABLE', 'VERSION_CONFLICT'].includes(viewState)
  const denied = role === 'CONTENT' || deniedStates.has(viewState)
  const closed = denied || failureClosed
  const effectiveState: A110ServerViewState = role === 'CONTENT' ? 'ACCESS_DENIED' : viewState
  const roleProjection = closed ? null : role as 'FIN' | 'CS'
  const items = dataStates.has(effectiveState) && roleProjection
    ? [roleProjection === 'FIN' ? financeItemFor(effectiveState) : supportItemFor(effectiveState)]
    : []
  return {
    requestRef: `A110-SYN-${effectiveState}`, viewState: effectiveState, projectCode: `A110_${effectiveState}`,
    schemaVersion: 'A110_RECONCILIATION_READ_V1', roleProjection,
    roleBindingVersion: closed ? null : 'A110-SYN-RBV-1', authorizationDecisionVersion: closed ? null : 'A110-SYN-ADV-1',
    projectionVersion: closed ? null : 1, items, allowedActions: actionsFor(effectiveState),
    retryClass: ['READ_ERROR', 'UNAVAILABLE', 'VERSION_CONFLICT', 'LONG_RUNNING_UNKNOWN', 'ASYMMETRIC_FACTS', 'REFUND_DELIVERY_CONFLICT'].includes(effectiveState) ? 'USER_INITIATED_READ_ONLY' : 'NONE',
  }
}
