import { parseA110ReadResponse } from '../src/api/admin-read.ts'
import { getSyntheticA110Response } from '../src/data/synthetic.ts'
import type { AdminReadState, AdminRole, A110ServerViewState, ReconciliationFinanceSummary } from '../src/domain/admin.ts'

type FixtureVariant = 'DEFAULT' | 'W_U_ASYMMETRIC' | 'U_D_BIDIRECTIONAL' | 'REFUND_LOCAL_OPEN'
type FixtureSpec = { role: AdminRole; viewState: A110ServerViewState | 'LOADING'; variant: FixtureVariant }

export const A110_VISUAL_SCENARIOS: Readonly<Record<string, FixtureSpec>> = Object.freeze({
  'ADM02-A110-V01': { role: 'FIN', viewState: 'READY', variant: 'DEFAULT' },
  'ADM02-A110-V02': { role: 'CS', viewState: 'READY', variant: 'DEFAULT' },
  'ADM02-A110-V03': { role: 'FIN', viewState: 'EMPTY', variant: 'DEFAULT' },
  'ADM02-A110-V04': { role: 'CS', viewState: 'EMPTY', variant: 'DEFAULT' },
  'ADM02-A110-V05': { role: 'FIN', viewState: 'LOADING', variant: 'DEFAULT' },
  'ADM02-A110-V06': { role: 'FIN', viewState: 'READ_ERROR', variant: 'DEFAULT' },
  'ADM02-A110-V07': { role: 'CONTENT', viewState: 'ACCESS_DENIED', variant: 'DEFAULT' },
  'ADM02-A110-V08': { role: 'FIN', viewState: 'AUTHORITY_UNKNOWN', variant: 'DEFAULT' },
  'ADM02-A110-V09': { role: 'FIN', viewState: 'LONG_RUNNING_UNKNOWN', variant: 'DEFAULT' },
  'ADM02-A110-V10': { role: 'FIN', viewState: 'ASYMMETRIC_FACTS', variant: 'W_U_ASYMMETRIC' },
  'ADM02-A110-V11': { role: 'FIN', viewState: 'ASYMMETRIC_FACTS', variant: 'U_D_BIDIRECTIONAL' },
  'ADM02-A110-V12': { role: 'FIN', viewState: 'ASYMMETRIC_FACTS', variant: 'REFUND_LOCAL_OPEN' },
  'ADM02-A110-V13': { role: 'FIN', viewState: 'REFUND_DELIVERY_CONFLICT', variant: 'DEFAULT' },
  'ADM02-A110-V14': { role: 'FIN', viewState: 'VERSION_CONFLICT', variant: 'DEFAULT' },
  'ADM02-A110-V01-Z200': { role: 'FIN', viewState: 'READY', variant: 'DEFAULT' },
  'ADM02-A110-V02-Z200': { role: 'CS', viewState: 'READY', variant: 'DEFAULT' },
  'ADM02-A110-V07-Z200': { role: 'CONTENT', viewState: 'ACCESS_DENIED', variant: 'DEFAULT' },
  'ADM02-A110-V01-W800': { role: 'FIN', viewState: 'READY', variant: 'DEFAULT' },
  'ADM02-A110-V02-W800': { role: 'CS', viewState: 'READY', variant: 'DEFAULT' },
  'ADM02-A110-V07-W800': { role: 'CONTENT', viewState: 'ACCESS_DENIED', variant: 'DEFAULT' },
})

export function buildA110VisualResponse(role: AdminRole, viewState: A110ServerViewState, variant: FixtureVariant) {
  const response = structuredClone(getSyntheticA110Response(role, viewState))
  if (variant === 'DEFAULT' || response.roleProjection !== 'FIN' || response.items.length === 0) return response
  const base = response.items[0] as ReconciliationFinanceSummary
  const unknownFact = { factState: 'UNKNOWN' as const, amountMinor: null, currency: null, occurredAt: null, observedAt: null }
  if (variant === 'W_U_ASYMMETRIC') return response
  if (variant === 'U_D_BIDIRECTIONAL') {
    const ready = getSyntheticA110Response('FIN', 'READY').items[0] as ReconciliationFinanceSummary
    response.items = [
      { ...base, reconciliationRef: '示例差异编号 U-D-1', orderRef: '示例订单编号 U-D-1', factSummaries: { ...base.factSummaries, U: ready.factSummaries.U, D: unknownFact } },
      { ...base, reconciliationRef: '示例差异编号 U-D-2', orderRef: '示例订单编号 U-D-2', factSummaries: { ...base.factSummaries, W: unknownFact, U: unknownFact } },
    ]
    return response
  }
  const ready = getSyntheticA110Response('FIN', 'READY').items[0] as ReconciliationFinanceSummary
  response.items = [{ ...base, factSummaries: {
    ...ready.factSummaries,
    R: { factState: 'CONFIRMED', amountMinor: 1280, currency: 'CNY', occurredAt: '2026-08-02T06:08:00Z', observedAt: '2026-08-02T06:08:02Z' },
    L: { factState: 'PENDING_OR_INFLIGHT', amountMinor: null, currency: null, occurredAt: null, observedAt: '2026-08-02T06:08:03Z' },
  }, differenceCategories: ['ACCOUNTING_INCOMPLETE'] }]
  return response
}

export function resolveA110VisualScenario(search: string): { role: AdminRole; state: AdminReadState | null } | null {
  const scenarioId = new URLSearchParams(search).get('a110Scenario')
  const spec = scenarioId ? A110_VISUAL_SCENARIOS[scenarioId] : undefined
  if (!spec) return null
  if (spec.viewState === 'LOADING') return { role: spec.role, state: null }
  return { role: spec.role, state: parseA110ReadResponse(buildA110VisualResponse(spec.role, spec.viewState, spec.variant), spec.role) }
}
