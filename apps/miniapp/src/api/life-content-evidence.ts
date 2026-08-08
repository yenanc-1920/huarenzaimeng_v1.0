export const LIFE_CONTENT_FIXED_INPUTS = Object.freeze({
  d1IndexSha:'F2CA50A6C8EAD6463753E33B50A819EE51DA69117D7EDDCE9A54EC4A8F785538',
  d1StaleRuleSha:'CD11326A05923C3F707A926B95716CED848529D667CEFB044F39E2134B441560',
  d2IndexSha:'2E9BC7D6F9075018CF5DB447571FD8FA9E23DA2E237EC0EEF13AA6D64628AA73',
  d3ContractSha:'2C3CE8BB4A872953CFAFAAEB8AC4983839BAD21A162FF6B40B09F6A20CCB6B5A',
  d3IndexSha:'358CBFCC3DEAEE618C8F478603367FA3001B84BE63AAF11ED9CCB9B3EA5BDB8C',
  frozenExecutionMatrixSha:'5059310751105E5C598BBB28C9FA69AF28188AAE58AD9D0C4343D5CE2566438B',
  currentLedgerSha:'78C6CF5308D2D300D0F96C6609973233A70D5B7511BF2B7A067D0AD9AA9A56B5',
  qualityMatrixSha:'5059310751105E5C598BBB28C9FA69AF28188AAE58AD9D0C4343D5CE2566438B',
})

export const LIFE_CONTENT_COUNTER_NAMES = [
  'QueryCall','ContentItem','ContentVersion','PublicationQualification','AuditEvent','ReportCandidate','SupportCase','Command',
  'SemanticAction','Order','PaymentIntent','PaymentAttempt','DispatchIntent','acceptedW','acceptedU','acceptedD','acceptedR','acceptedL',
  'LedgerEntry','A120PublishAction','ExternalCall','FileWrite','DatabaseWrite','QueueWrite','BackupWrite','PersistentLogWrite','ExternalAnalytics',
] as const

export type LifeContentCounterName = typeof LIFE_CONTENT_COUNTER_NAMES[number]
export type LifeContentCounterSnapshot = Record<LifeContentCounterName,number|'NOT_OBSERVED'|'NOT_OBSERVED_BACKEND_OWNED'>

export interface LifeContentEvidenceParameterPlan {
  evidencePackageId:string
  scenarioId:string
  subcaseId:string
  parameterId:string
  expected:string
  actual:'NOT_RUN_NO_EVIDENCE'
  before:LifeContentCounterSnapshot
  after:LifeContentCounterSnapshot
  delta:LifeContentCounterSnapshot
  executionStatus:'NOT_RUN'
}

const unobserved=():LifeContentCounterSnapshot => Object.fromEntries(
  LIFE_CONTENT_COUNTER_NAMES.map((name)=>[name,'NOT_OBSERVED']),
) as LifeContentCounterSnapshot

const scenarioDefinitions = [
  ['INF-EP-001','D5-INF-S01','INF-READ-001-LIFE-REMINDER-READY',['LIST_READY','DETAIL_READY'],'LIFE_REMINDER strict READY list/detail; CURRENT; read-only'],
  ['INF-EP-002','D5-INF-S02','INF-READ-002-HOLIDAY-EXPLANATION-READY',['LIST_READY','DETAIL_READY'],'HOLIDAY_EXPLANATION independent strict READY list/detail; CURRENT; read-only'],
  ['INF-EP-003','D5-INF-S03','INF-READ-003-CLIENT-LOADING',['FIRST_ENTRY','REENTRY','FOREGROUND_RESUME'],'client LOADING revokes prior item/body/READY before the read; no automatic retry'],
  ['INF-EP-004','D5-INF-S04','INF-READ-004-LIST-EMPTY',['COMPLETE_ZERO_CANDIDATE'],'strict EMPTY with zero items after a complete zero-candidate scan'],
  ['INF-EP-005','D5-INF-S05','INF-READ-005-READ-ERROR',['BACKEND_READ_ERROR','NETWORK_INTERRUPTED'],'read failure keeps body zero and allows only user-initiated read-only retry or safe return'],
  ['INF-EP-006','D5-INF-S06','INF-READ-006-STALE',['STALE_BEFORE_EFFECTIVE_TO'],'authoritative STALE before EffectiveTo converges to UNKNOWN with no item/body'],
  ['INF-EP-007','D5-INF-S07','INF-READ-007-UNDER-REVIEW',['DETAIL_UNDER_REVIEW','LIST_ONLY_CANDIDATE'],'UNDER_REVIEW has no item/body and no retry action'],
  ['INF-EP-008','D5-INF-S08','INF-READ-008-EXPIRED',['REFERENCE_AFTER_EFFECTIVE_TO'],'past EffectiveTo converges to EXPIRED before freshness and revokes body'],
  ['INF-EP-009','D5-INF-S09','INF-READ-009-REMOVED-OLD-LINK',['WITHDRAWN_CURRENT_REF','SUPERSEDED_VERSION_OLD_LINK'],'REMOVED echoes the requested opaque key, does not redirect, and returns no item/body'],
  ['INF-EP-010','D5-INF-S10','INF-READ-010-UNKNOWN',['MISSING_QUALIFICATION','RIGHTS_CONFLICT','INCOMPLETE_LIST_SCAN'],'UNKNOWN is fail-closed, all-or-empty, and only user-initiated read-only retryable'],
  ['INF-EP-011','D5-INF-S11','INF-READ-011-IMAGE-UNAVAILABLE',['CURRENT_BODY_COVER_UNAVAILABLE'],'IMAGE_UNAVAILABLE remains READY/CURRENT with body and null coverRef; no image external call'],
  ['INF-EP-012','D5-INF-S12','INF-READ-012-NOT-SELECTED-CATEGORY',['UNAPPROVED_CATEGORY_EXPLICIT'],'unapproved category fails closed without public item/body or A120 eligibility'],
  ['INF-EP-013','D5-INF-S13','INF-READ-013-ANONYMOUS-MINIMAL',['LIST_ANONYMOUS','DETAIL_ANONYMOUS','LOG_MINIMIZATION','EVIDENCE_MINIMIZATION','A120_ZERO'],'anonymous minimal read and redacted telemetry/evidence; no A120 action'],
] as const

export const LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN:readonly LifeContentEvidenceParameterPlan[] = Object.freeze(
  scenarioDefinitions.flatMap(([evidencePackageId,scenarioId,subcaseId,parameterIds,expected])=>parameterIds.map((parameterId)=>({
    evidencePackageId,scenarioId,subcaseId,parameterId,expected,actual:'NOT_RUN_NO_EVIDENCE' as const,
    before:unobserved(),after:unobserved(),delta:unobserved(),executionStatus:'NOT_RUN' as const,
  }))),
)

export const LIFE_CONTENT_EVIDENCE_DENOMINATOR = Object.freeze({
  total:13,pass:0,fail:0,blocked:0,skipped:0,notApplicable:0,notRun:13,
})
