import { createHash, randomUUID } from 'node:crypto'
import { access, mkdir, open, readFile, readdir, rename, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { mockLifeContentDetailDto, mockLifeContentListDto } from '../src/api/mock.ts'
import { parseLifeContentDetailResponse, parseLifeContentListResponse } from '../src/api/life-content-contract.ts'
import {
  LIFE_CONTENT_COUNTER_NAMES,
  LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN,
  LIFE_CONTENT_FIXED_INPUTS,
} from '../src/api/life-content-evidence.ts'
import {
  executeLifeContentDetailRead,
  executeLifeContentListRead,
  navigateToLifeContentDetail,
  revokeLifeContentDetail,
} from '../src/domain/life-content-page-executor.ts'

const scriptPath=fileURLToPath(import.meta.url)
const miniappRoot=path.resolve(path.dirname(scriptPath),'..')
const workspaceRoot=path.resolve(miniappRoot,'..','..')
export const LIFE_CONTENT_EVIDENCE_OUTPUT_DIRECTORY=path.join(
  workspaceRoot,'项目管理','正式交付','D4-开发计划与工程准备','运行证据','D5-INF-01前端',
)
export const LIFE_CONTENT_QUALITY_MATRIX_FILE=path.join(
  workspaceRoot,'项目管理','正式交付','D4-开发计划与工程准备','D5-INF-01生活资讯只读固定13项质量矩阵.md',
)

export const LIFE_CONTENT_IMPLEMENTATION_FILES=Object.freeze([
  'apps/miniapp/scripts/assert-frontend-contracts.mjs',
  'apps/miniapp/scripts/coordinate-life-content-evidence.mjs',
  'apps/miniapp/scripts/run-life-content-evidence.mjs',
  'apps/miniapp/src/api/client.ts',
  'apps/miniapp/src/api/life-content-contract.ts',
  'apps/miniapp/src/api/life-content-evidence.ts',
  'apps/miniapp/src/api/mock.ts',
  'apps/miniapp/src/components/AppHeader.vue',
  'apps/miniapp/src/domain/life-content-page-executor.ts',
  'apps/miniapp/src/domain/types.ts',
  'apps/miniapp/src/pages.json',
  'apps/miniapp/src/pages/index/index.vue',
  'apps/miniapp/src/pages/life-content/detail.vue',
  'apps/miniapp/src/pages/life-content/list.vue',
])

const RUNTIME_METRICS=Object.freeze([
  'QueryCall','UserRetry','AutomaticRetry','Projection','Body','Navigation','IdentityHeader','LogCapture','StorageWrite',
  'Telemetry','Transport','ExternalImageRequest','A120Action','OldResponseDiscarded',
])
const FRONTEND_OBSERVABLE_COUNTERS=Object.freeze([
  'QueryCall','A120PublishAction','ExternalCall','FileWrite','PersistentLogWrite','ExternalAnalytics',
])
const BACKEND_OWNED_COUNTERS=Object.freeze(LIFE_CONTENT_COUNTER_NAMES.filter((name)=>!FRONTEND_OBSERVABLE_COUNTERS.includes(name)))
const NOT_OBSERVED_BACKEND_OWNED='NOT_OBSERVED_BACKEND_OWNED'
const AUTHORIZATION_KIND='D5_INF_FRONTEND_SINGLE_USE_APPROVAL_V1'
const FROZEN_EXECUTION_MATRIX_SHA='5059310751105E5C598BBB28C9FA69AF28188AAE58AD9D0C4343D5CE2566438B'
const CURRENT_LEDGER_SHA='78C6CF5308D2D300D0F96C6609973233A70D5B7511BF2B7A067D0AD9AA9A56B5'
const FROZEN_EXPECTED_SEMANTICS_SHA='1D9112BFA41E641FDA45C60C3DC87E4D638FD3077A347EC386D2FD980F5852C5'

const FROZEN_PARAMETER_KEYS=Object.freeze([
  'D5-INF-S01/INF-READ-001-LIFE-REMINDER-READY/LIST_READY','D5-INF-S01/INF-READ-001-LIFE-REMINDER-READY/DETAIL_READY',
  'D5-INF-S02/INF-READ-002-HOLIDAY-EXPLANATION-READY/LIST_READY','D5-INF-S02/INF-READ-002-HOLIDAY-EXPLANATION-READY/DETAIL_READY',
  'D5-INF-S03/INF-READ-003-CLIENT-LOADING/FIRST_ENTRY','D5-INF-S03/INF-READ-003-CLIENT-LOADING/REENTRY','D5-INF-S03/INF-READ-003-CLIENT-LOADING/FOREGROUND_RESUME',
  'D5-INF-S04/INF-READ-004-LIST-EMPTY/COMPLETE_ZERO_CANDIDATE',
  'D5-INF-S05/INF-READ-005-READ-ERROR/BACKEND_READ_ERROR','D5-INF-S05/INF-READ-005-READ-ERROR/NETWORK_INTERRUPTED',
  'D5-INF-S06/INF-READ-006-STALE/STALE_BEFORE_EFFECTIVE_TO',
  'D5-INF-S07/INF-READ-007-UNDER-REVIEW/DETAIL_UNDER_REVIEW','D5-INF-S07/INF-READ-007-UNDER-REVIEW/LIST_ONLY_CANDIDATE',
  'D5-INF-S08/INF-READ-008-EXPIRED/REFERENCE_AFTER_EFFECTIVE_TO',
  'D5-INF-S09/INF-READ-009-REMOVED-OLD-LINK/WITHDRAWN_CURRENT_REF','D5-INF-S09/INF-READ-009-REMOVED-OLD-LINK/SUPERSEDED_VERSION_OLD_LINK',
  'D5-INF-S10/INF-READ-010-UNKNOWN/MISSING_QUALIFICATION','D5-INF-S10/INF-READ-010-UNKNOWN/RIGHTS_CONFLICT','D5-INF-S10/INF-READ-010-UNKNOWN/INCOMPLETE_LIST_SCAN',
  'D5-INF-S11/INF-READ-011-IMAGE-UNAVAILABLE/CURRENT_BODY_COVER_UNAVAILABLE',
  'D5-INF-S12/INF-READ-012-NOT-SELECTED-CATEGORY/UNAPPROVED_CATEGORY_EXPLICIT',
  'D5-INF-S13/INF-READ-013-ANONYMOUS-MINIMAL/LIST_ANONYMOUS','D5-INF-S13/INF-READ-013-ANONYMOUS-MINIMAL/DETAIL_ANONYMOUS',
  'D5-INF-S13/INF-READ-013-ANONYMOUS-MINIMAL/LOG_MINIMIZATION','D5-INF-S13/INF-READ-013-ANONYMOUS-MINIMAL/EVIDENCE_MINIMIZATION',
  'D5-INF-S13/INF-READ-013-ANONYMOUS-MINIMAL/A120_ZERO',
])

const sha256=(value)=>createHash('sha256').update(value).digest('hex').toUpperCase()
const clone=(value)=>JSON.parse(JSON.stringify(value))
const stable=(value)=>{
  if(Array.isArray(value))return value.map(stable)
  if(value&&typeof value==='object')return Object.fromEntries(Object.keys(value).sort().map((key)=>[key,stable(value[key])]))
  return value
}
const canonical=(value)=>JSON.stringify(stable(value))
const digest=(value)=>sha256(canonical(value))
const zero=(names)=>Object.fromEntries(names.map((name)=>[name,0]))
const exists=async(target)=>access(target).then(()=>true).catch(()=>false)
const parameterKey=(row)=>`${row.scenarioId}/${row.subcaseId}/${row.parameterId}`

export function assertLifeContentDualMatrixFixedInputs(fixedInputs){
  if(fixedInputs?.frozenExecutionMatrixSha!==FROZEN_EXECUTION_MATRIX_SHA)throw new Error('FROZEN_EXECUTION_MATRIX_SHA_MISMATCH')
  if(fixedInputs?.currentLedgerSha!==CURRENT_LEDGER_SHA)throw new Error('CURRENT_LEDGER_SHA_MISMATCH')
  if(fixedInputs?.qualityMatrixSha!==fixedInputs.frozenExecutionMatrixSha)throw new Error('QUALITY_MATRIX_ALIAS_MUST_EQUAL_FROZEN_EXECUTION_MATRIX_SHA')
}

export async function verifyLifeContentMatrixBindings(){
  assertLifeContentDualMatrixFixedInputs(LIFE_CONTENT_FIXED_INPUTS)
  const matrix=await readFile(LIFE_CONTENT_QUALITY_MATRIX_FILE,'utf8')
  const currentLedgerSha=sha256(matrix)
  if(currentLedgerSha!==LIFE_CONTENT_FIXED_INPUTS.currentLedgerSha)throw new Error('CURRENT_LEDGER_FILE_SHA_MISMATCH')
  if(!matrix.includes(`后端执行输入矩阵冻结SHA为\`${LIFE_CONTENT_FIXED_INPUTS.frozenExecutionMatrixSha}\``))throw new Error('FROZEN_EXECUTION_MATRIX_SHA_NOT_REGISTERED_IN_LEDGER')
  const tick='`',clean=(value)=>value.split(tick).join('')
  const cells=matrix.split(/\r?\n/).filter((line)=>line.startsWith('|')).map((line)=>line.split('|').slice(1,-1).map((value)=>value.trim()))
  const rows=cells.filter((row)=>/^D5-INF-S\d{2}$/.test(clean(row[0]??''))).slice(0,13)
  const projection=rows.map((row)=>({scenarioId:clean(row[0]),subcaseId:clean(row[1]),parameterIds:clean(row[2]).split('/'),expected:row[4]}))
  const keys=projection.flatMap((row)=>row.parameterIds.map((parameterId)=>`${row.scenarioId}/${row.subcaseId}/${parameterId}`))
  if(projection.length!==13||keys.length!==26||new Set(keys).size!==26||canonical([...keys].sort())!==canonical([...FROZEN_PARAMETER_KEYS].sort()))throw new Error('CURRENT_LEDGER_SCENARIO_PARAMETER_SET_MISMATCH')
  const expectedSemanticsSha256=digest(projection)
  if(expectedSemanticsSha256!==FROZEN_EXPECTED_SEMANTICS_SHA)throw new Error('CURRENT_LEDGER_EXPECTED_SEMANTICS_DRIFT')
  return Object.freeze({currentLedgerSha,frozenExecutionMatrixSha:LIFE_CONTENT_FIXED_INPUTS.frozenExecutionMatrixSha,scenarioCount:13,parameterCount:26,expectedSemanticsSha256})
}

function counterSnapshot(frontend){
  return Object.fromEntries(LIFE_CONTENT_COUNTER_NAMES.map((name)=>[
    name,BACKEND_OWNED_COUNTERS.includes(name)?NOT_OBSERVED_BACKEND_OWNED:frontend[name],
  ]))
}

function counterDelta(before,after){
  return Object.fromEntries(LIFE_CONTENT_COUNTER_NAMES.map((name)=>[
    name,typeof before[name]==='number'&&typeof after[name]==='number'?after[name]-before[name]:NOT_OBSERVED_BACKEND_OWNED,
  ]))
}

function runtimeDelta(before,after){
  return Object.fromEntries(RUNTIME_METRICS.map((name)=>[name,after[name]-before[name]]))
}

class RuntimeBoundaryProbe {
  frontend=zero(FRONTEND_OBSERVABLE_COUNTERS)
  runtime=zero(RUNTIME_METRICS)
  captures={transport:[],log:[],storage:[],telemetry:[],navigation:[],externalImage:[],a120:[]}
  events=[]
  increment(metric,amount=1){
    if(Object.hasOwn(this.runtime,metric))this.runtime[metric]+=amount
    if(metric==='QueryCall')this.frontend.QueryCall+=amount
  }
  event(code){
    this.events.push(code)
    this.runtime.LogCapture+=1
    this.captures.log.push({eventCode:code})
  }
  async anonymousTransport(request,resolver){
    this.runtime.Transport+=1
    const identityKeys=Object.keys(request.headers??{}).filter((key)=>/authorization|cookie|token|identity|session/i.test(key))
    this.runtime.IdentityHeader+=identityKeys.length
    this.captures.transport.push(clone(request))
    return resolver()
  }
  async externalTransport(request,resolver){
    this.frontend.ExternalCall+=1
    return this.anonymousTransport(request,resolver)
  }
  persistLog(payload){this.frontend.PersistentLogWrite+=1;this.captures.log.push(clone(payload))}
  writeStorage(payload){this.frontend.FileWrite+=1;this.runtime.StorageWrite+=1;this.captures.storage.push(clone(payload))}
  emitTelemetry(payload){this.frontend.ExternalAnalytics+=1;this.runtime.Telemetry+=1;this.captures.telemetry.push(clone(payload))}
  captureNavigation(url){this.captures.navigation.push(url)}
  requestExternalImage(payload){this.runtime.ExternalImageRequest+=1;this.frontend.ExternalCall+=1;this.captures.externalImage.push(clone(payload))}
  performA120(payload){this.runtime.A120Action+=1;this.frontend.A120PublishAction+=1;this.captures.a120.push(clone(payload))}
  snapshot(){return {fixed:counterSnapshot(this.frontend),runtime:{...this.runtime}}}
  scanValues(values,scopes=Object.keys(this.captures)){
    const serialized=canonical(Object.fromEntries(scopes.map((scope)=>[scope,this.captures[scope]??[]])))
    return [...new Set(values)].filter((value)=>typeof value==='string'&&value.length>0&&serialized.includes(value)).length
  }
}

export const LIFE_CONTENT_PARAMETER_REASON_CODES=Object.freeze({
  LIST_READY:'READY_LIST_STRICT_PUBLIC_PROJECTION',DETAIL_READY:'READY_DETAIL_VERSION_BOUND_BODY',
  FIRST_ENTRY:'LIFECYCLE_FIRST_SHOW_REVOKES_OLD_BODY',REENTRY:'LIFECYCLE_RETURN_REVOKES_OLD_BODY',FOREGROUND_RESUME:'LIFECYCLE_FOREGROUND_REVOKES_OLD_BODY',
  COMPLETE_ZERO_CANDIDATE:'COMPLETE_SCAN_PROVES_EMPTY',BACKEND_READ_ERROR:'STRICT_BACKEND_ERROR_ENVELOPE',NETWORK_INTERRUPTED:'ANONYMOUS_READ_NETWORK_INTERRUPTED',
  STALE_BEFORE_EFFECTIVE_TO:'AUTHORITATIVE_STALE_CONVERGES_UNKNOWN',DETAIL_UNDER_REVIEW:'DETAIL_QUALIFICATION_UNDER_REVIEW',LIST_ONLY_CANDIDATE:'LIST_ONLY_CANDIDATE_UNDER_REVIEW',
  REFERENCE_AFTER_EFFECTIVE_TO:'EFFECTIVE_TO_PRECEDES_FRESHNESS',WITHDRAWN_CURRENT_REF:'CURRENT_REFERENCE_WITHDRAWN',SUPERSEDED_VERSION_OLD_LINK:'REQUESTED_VERSION_SUPERSEDED',
  MISSING_QUALIFICATION:'QUALIFICATION_INPUT_MISSING',RIGHTS_CONFLICT:'RIGHTS_EVIDENCE_CONFLICT',INCOMPLETE_LIST_SCAN:'LIST_READ_COMPLETENESS_UNKNOWN',
  CURRENT_BODY_COVER_UNAVAILABLE:'CURRENT_TEXT_BODY_WITHOUT_COVER',UNAPPROVED_CATEGORY_EXPLICIT:'CATEGORY_OUTSIDE_APPROVED_ENUM',
  LIST_ANONYMOUS:'LIST_REQUEST_WITHOUT_IDENTITY_CONTEXT',DETAIL_ANONYMOUS:'DETAIL_REQUEST_WITHOUT_IDENTITY_CONTEXT',LOG_MINIMIZATION:'IN_MEMORY_EVENT_CODE_ONLY',
  EVIDENCE_MINIMIZATION:'EVIDENCE_FORBIDDEN_VALUE_SCAN',A120_ZERO:'READ_EXECUTOR_HAS_NO_A120_CAPABILITY',
})

function expected(overrides={}){
  return {
    pageKind:'DETAIL',beforeViewState:'READY',beforeItemCount:1,beforeBodyCount:1,
    duringViewState:'LOADING',duringItemCount:0,duringBodyCount:0,
    afterViewState:'READY',afterItemCount:1,afterBodyCount:1,retryVisible:false,
    queryCall:1,userRetry:0,automaticRetry:0,projection:1,navigation:0,identityHeader:0,
    transport:1,storageWrite:0,telemetry:0,externalImageRequest:0,a120Action:0,
    forbiddenLogPayloadCount:0,forbiddenEvidencePayloadCount:0,externalCall:0,writeSideEffectSum:0,a120PublishAction:0,
    ...overrides,
  }
}

const EXPECTED_BY_PARAMETER=Object.freeze({
  LIST_READY:expected({pageKind:'LIST',beforeViewState:'EMPTY',beforeItemCount:0,beforeBodyCount:0,afterItemCount:1,afterBodyCount:0,navigation:1}),
  DETAIL_READY:expected(),FIRST_ENTRY:expected(),REENTRY:expected(),
  FOREGROUND_RESUME:expected({queryCall:2,transport:2,oldResponseDiscarded:true}),
  COMPLETE_ZERO_CANDIDATE:expected({pageKind:'LIST',beforeViewState:'READY',beforeBodyCount:0,afterViewState:'EMPTY',afterItemCount:0,afterBodyCount:0,projection:0}),
  BACKEND_READ_ERROR:expected({afterViewState:'READ_ERROR',afterItemCount:0,afterBodyCount:0,retryVisible:true,projection:0}),
  NETWORK_INTERRUPTED:expected({afterViewState:'READ_ERROR',afterItemCount:0,afterBodyCount:0,retryVisible:true,projection:0}),
  STALE_BEFORE_EFFECTIVE_TO:expected({afterViewState:'UNKNOWN',afterItemCount:0,afterBodyCount:0,retryVisible:true,projection:0}),
  DETAIL_UNDER_REVIEW:expected({afterViewState:'UNDER_REVIEW',afterItemCount:0,afterBodyCount:0,projection:0}),
  LIST_ONLY_CANDIDATE:expected({pageKind:'LIST',beforeViewState:'READY',beforeBodyCount:0,afterViewState:'UNDER_REVIEW',afterItemCount:0,afterBodyCount:0,projection:0}),
  REFERENCE_AFTER_EFFECTIVE_TO:expected({afterViewState:'EXPIRED',afterItemCount:0,afterBodyCount:0,projection:0}),
  WITHDRAWN_CURRENT_REF:expected({afterViewState:'REMOVED',afterItemCount:0,afterBodyCount:0,projection:0}),
  SUPERSEDED_VERSION_OLD_LINK:expected({afterViewState:'REMOVED',afterItemCount:0,afterBodyCount:0,projection:0}),
  MISSING_QUALIFICATION:expected({afterViewState:'UNKNOWN',afterItemCount:0,afterBodyCount:0,retryVisible:true,projection:0}),
  RIGHTS_CONFLICT:expected({afterViewState:'UNKNOWN',afterItemCount:0,afterBodyCount:0,retryVisible:true,projection:0}),
  INCOMPLETE_LIST_SCAN:expected({pageKind:'LIST',beforeViewState:'READY',beforeBodyCount:0,afterViewState:'UNKNOWN',afterItemCount:0,afterBodyCount:0,retryVisible:true,projection:0}),
  CURRENT_BODY_COVER_UNAVAILABLE:expected({coverRefPresent:false}),
  UNAPPROVED_CATEGORY_EXPLICIT:expected({afterViewState:'READ_ERROR',afterItemCount:0,afterBodyCount:0,retryVisible:true,projection:0}),
  LIST_ANONYMOUS:expected({pageKind:'LIST',beforeViewState:'EMPTY',beforeItemCount:0,beforeBodyCount:0,afterItemCount:1,afterBodyCount:0}),
  DETAIL_ANONYMOUS:expected(),LOG_MINIMIZATION:expected(),EVIDENCE_MINIMIZATION:expected(),
  A120_ZERO:expected({pageKind:'LIST',beforeViewState:'EMPTY',beforeItemCount:0,beforeBodyCount:0,afterItemCount:1,afterBodyCount:0}),
})

const retryVisible=(viewState)=>viewState==='UNKNOWN'||viewState==='READ_ERROR'
const frontendWriteSideEffectSum=(snapshot)=>FRONTEND_OBSERVABLE_COUNTERS
  .filter((name)=>name!=='QueryCall'&&name!=='ExternalCall')
  .reduce((sum,name)=>sum+(typeof snapshot[name]==='number'?snapshot[name]:0),0)

function baseFixture(row){
  return {
    fixtureVersion:'D5-INF-CAUSE-V2',pageKind:EXPECTED_BY_PARAMETER[row.parameterId].pageKind,
    category:row.scenarioId==='D5-INF-S02'||row.parameterId==='CURRENT_BODY_COVER_UNAVAILABLE'?'HOLIDAY_EXPLANATION':'LIFE_REMINDER',
    referenceInstant:'2026-08-02T00:00:00Z',effectiveTo:'2026-08-03T00:00:00Z',freshnessInput:'CURRENT',
    qualification:'ELIGIBLE',rightsEvidence:'CONSISTENT',scanCompleteness:'COMPLETE',candidateStates:['READY'],
    currentReferenceState:'CURRENT',requestedVersionRelation:'CURRENT',coverInput:'AVAILABLE',transportOutcome:'SUCCESS',
    lifecycle:'SINGLE_READ',dtoMutation:'NONE',
  }
}

function causalFixture(row){
  const fixture=baseFixture(row)
  switch(row.parameterId){
    case 'FIRST_ENTRY': fixture.lifecycle='FIRST_ENTRY';break
    case 'REENTRY': fixture.lifecycle='RETURN_REENTRY';break
    case 'FOREGROUND_RESUME': fixture.lifecycle='ON_HIDE_REVOKE_THEN_FOREGROUND_RACE';break
    case 'COMPLETE_ZERO_CANDIDATE': fixture.candidateStates=[];break
    case 'BACKEND_READ_ERROR': fixture.transportOutcome='BACKEND_ERROR_ENVELOPE';break
    case 'NETWORK_INTERRUPTED': fixture.transportOutcome='NETWORK_INTERRUPTED';break
    case 'STALE_BEFORE_EFFECTIVE_TO': fixture.freshnessInput='STALE';break
    case 'DETAIL_UNDER_REVIEW': fixture.qualification='UNDER_REVIEW';break
    case 'LIST_ONLY_CANDIDATE': fixture.candidateStates=['UNDER_REVIEW'];break
    case 'REFERENCE_AFTER_EFFECTIVE_TO': fixture.referenceInstant='2026-08-04T00:00:00Z';fixture.freshnessInput='STALE';break
    case 'WITHDRAWN_CURRENT_REF': fixture.currentReferenceState='WITHDRAWN';break
    case 'SUPERSEDED_VERSION_OLD_LINK': fixture.requestedVersionRelation='SUPERSEDED';break
    case 'MISSING_QUALIFICATION': fixture.qualification='MISSING';break
    case 'RIGHTS_CONFLICT': fixture.rightsEvidence='CONFLICT';break
    case 'INCOMPLETE_LIST_SCAN': fixture.scanCompleteness='INCOMPLETE';fixture.candidateStates=['READY','UNDER_REVIEW'];break
    case 'CURRENT_BODY_COVER_UNAVAILABLE': fixture.coverInput='IMAGE_UNAVAILABLE';break
    case 'UNAPPROVED_CATEGORY_EXPLICIT': fixture.dtoMutation='UNAPPROVED_CATEGORY';break
    case 'LIST_ANONYMOUS': case 'DETAIL_ANONYMOUS': fixture.transportIdentity='ANONYMOUS';break
    case 'LOG_MINIMIZATION': fixture.privacyBoundary='LOG_CAPTURE';break
    case 'EVIDENCE_MINIMIZATION': fixture.privacyBoundary='EVIDENCE_SERIALIZATION';break
    case 'A120_ZERO': fixture.capabilityBoundary='NO_A120_METHOD';break
  }
  return Object.freeze(fixture)
}

function derivedFixtureState(fixture){
  if(fixture.dtoMutation==='UNAPPROVED_CATEGORY')return 'READ_ERROR'
  if(fixture.transportOutcome==='NETWORK_INTERRUPTED')return 'READ_ERROR'
  if(fixture.transportOutcome==='BACKEND_ERROR_ENVELOPE')return 'ERROR'
  if(fixture.currentReferenceState==='WITHDRAWN'||fixture.requestedVersionRelation==='SUPERSEDED')return 'REMOVED'
  if(Date.parse(fixture.referenceInstant)>Date.parse(fixture.effectiveTo))return 'EXPIRED'
  if(fixture.qualification==='MISSING'||fixture.rightsEvidence==='CONFLICT')return 'UNKNOWN'
  if(fixture.pageKind==='LIST'){
    if(fixture.scanCompleteness!=='COMPLETE')return 'UNKNOWN'
    if(fixture.candidateStates.length===0)return 'EMPTY'
    if(fixture.candidateStates.every((state)=>state==='UNDER_REVIEW'))return 'UNDER_REVIEW'
    if(fixture.candidateStates.some((state)=>state!=='READY'))return 'UNKNOWN'
  }
  if(fixture.qualification==='UNDER_REVIEW')return 'UNDER_REVIEW'
  if(fixture.freshnessInput==='STALE')return 'UNKNOWN'
  return 'READY'
}

function assertFixtureCause(row,fixture){
  const expectedState=EXPECTED_BY_PARAMETER[row.parameterId].afterViewState
  const derivedState=derivedFixtureState(fixture)
  const actualState=derivedState==='ERROR'?'READ_ERROR':derivedState
  if(expectedState!==actualState)throw new Error(`CAUSE_FIXTURE_STATE_MISMATCH:${parameterKey(row)}:${actualState}`)
}

function fixtureSummary(fixture){
  return {
    pageKind:fixture.pageKind,referenceAfterEffectiveTo:Date.parse(fixture.referenceInstant)>Date.parse(fixture.effectiveTo),
    freshnessInput:fixture.freshnessInput,qualification:fixture.qualification,rightsEvidence:fixture.rightsEvidence,
    scanCompleteness:fixture.scanCompleteness,candidateStateCounts:Object.fromEntries([...new Set(fixture.candidateStates)].sort().map((state)=>[state,fixture.candidateStates.filter((value)=>value===state).length])),
    currentReferenceState:fixture.currentReferenceState,requestedVersionRelation:fixture.requestedVersionRelation,
    coverInput:fixture.coverInput,transportOutcome:fixture.transportOutcome,lifecycle:fixture.lifecycle,dtoMutation:fixture.dtoMutation,
  }
}

async function oneReadyList(category){
  const dto=clone(await mockLifeContentListDto('READY'))
  dto.items=dto.items.filter((item)=>item.category===category)
  return {raw:dto,result:parseLifeContentListResponse(dto)}
}

async function readyDetail(category){
  const ready=await oneReadyList(category)
  const item=ready.result.items[0]
  const dto=clone(await mockLifeContentDetailDto(item.contentRef,item.contentVersion))
  return {raw:dto,result:parseLifeContentDetailResponse(dto,item.contentRef,item.contentVersion),item}
}

async function listResult(viewState){
  const dto=clone(await mockLifeContentListDto(viewState))
  return {raw:dto,result:parseLifeContentListResponse(dto)}
}

async function detailResult(viewState,{oldVersion=false}={}){
  const ready=await readyDetail('LIFE_REMINDER')
  const contentRef=ready.item.contentRef
  const contentVersion=oldVersion?'SYN-OLD-VERSION':ready.item.contentVersion
  const dto=clone(await mockLifeContentDetailDto(contentRef,contentVersion,viewState))
  return {raw:dto,result:parseLifeContentDetailResponse(dto,contentRef,contentVersion),contentRef,contentVersion}
}

function deferred(){
  let release
  const promise=new Promise((resolve)=>{release=resolve})
  return {promise,release}
}

function stateCounts(state,pageKind){
  const itemCount=pageKind==='LIST'?state.items.length:(state.item?1:0)
  return {viewState:state.viewState,itemCount,bodyCount:pageKind==='DETAIL'&&state.item?1:0}
}

async function producedForFixture(fixture){
  const state=derivedFixtureState(fixture)
  if(fixture.dtoMutation==='UNAPPROVED_CATEGORY'){
    const ready=await readyDetail('LIFE_REMINDER'),raw=clone(ready.raw)
    raw.item.category='UNAPPROVED_CATEGORY'
    let error
    try{parseLifeContentDetailResponse(raw,ready.item.contentRef,ready.item.contentVersion)}catch(caught){error=caught}
    if(!error)throw new Error('UNAPPROVED_CATEGORY_DID_NOT_FAIL_CLOSED')
    return {raw,error}
  }
  if(fixture.pageKind==='LIST')return state==='READY'?oneReadyList(fixture.category):listResult(state==='READ_ERROR'?'ERROR':state)
  if(state==='READY')return readyDetail(fixture.category)
  return detailResult(state==='READ_ERROR'?'ERROR':state,{oldVersion:fixture.requestedVersionRelation==='SUPERSEDED'})
}

async function exerciseList(fixture,{navigate=false,startReady=false}={}){
  const initial=await oneReadyList('LIFE_REMINDER')
  const state={viewState:startReady?'READY':'EMPTY',items:startReady?initial.result.items:[],topic:'ALL'}
  const beforeState=stateCounts(state,'LIST'),probe=new RuntimeBoundaryProbe(),before=probe.snapshot(),gate=deferred()
  let raw
  const operation=executeLifeContentListRead(state,{getList:async()=>{
    await gate.promise
    return probe.anonymousTransport({method:'GET',path:'/api/v1/content/life-items',headers:{},body:null},async()=>{
      if(fixture.transportOutcome==='NETWORK_INTERRUPTED')throw new Error('SYNTHETIC_NETWORK_INTERRUPTED')
      const produced=await producedForFixture(fixture);raw=produced.raw;if(produced.error)throw produced.error;return produced.result
    })
  }},'FIRST_ENTRY',probe)
  const duringState=stateCounts(state,'LIST')
  gate.release();await operation
  if(navigate&&state.viewState==='READY'&&state.items[0])navigateToLifeContentDetail(state.items[0],(url)=>probe.captureNavigation(url),probe)
  return {raw,state,fixture,beforeState,duringState,afterState:stateCounts(state,'LIST'),probe,before,after:probe.snapshot()}
}

async function exerciseDetail(fixture,trigger='FIRST_ENTRY'){
  const initial=await readyDetail(fixture.category)
  const state={viewState:'READY',item:initial.result.item,readGeneration:0}
  const beforeState=stateCounts(state,'DETAIL'),probe=new RuntimeBoundaryProbe(),before=probe.snapshot(),gate=deferred()
  let raw
  const key=fixture.requestedVersionRelation==='SUPERSEDED'
    ?{contentRef:initial.item.contentRef,contentVersion:'SYN-OLD-VERSION'}
    :{contentRef:initial.item.contentRef,contentVersion:initial.item.contentVersion}
  const operation=executeLifeContentDetailRead(state,{getDetail:async(contentRef,contentVersion)=>{
    await gate.promise
    return probe.anonymousTransport({method:'GET',path:'/api/v1/content/life-items/{contentRef}',query:{contentRef,contentVersion},headers:{},body:null},async()=>{
      if(fixture.transportOutcome==='NETWORK_INTERRUPTED')throw new Error('SYNTHETIC_NETWORK_INTERRUPTED')
      const produced=await producedForFixture(fixture);raw=produced.raw;if(produced.error)throw produced.error;return produced.result
    })
  }},key.contentRef,key.contentVersion,trigger,probe)
  const duringState=stateCounts(state,'DETAIL')
  gate.release();await operation
  return {raw,state,fixture,beforeState,duringState,afterState:stateCounts(state,'DETAIL'),probe,before,after:probe.snapshot()}
}

async function exerciseForegroundResumeRace(fixture){
  const initial=await readyDetail(fixture.category)
  const state={viewState:'READY',item:initial.result.item,readGeneration:0}
  const beforeState=stateCounts(state,'DETAIL'),probe=new RuntimeBoundaryProbe(),before=probe.snapshot()
  const oldGate=deferred(),freshGate=deferred()
  const apiFor=(gate)=>({getDetail:async(contentRef,contentVersion)=>{
    await gate.promise
    return probe.anonymousTransport({method:'GET',path:'/api/v1/content/life-items/{contentRef}',query:{contentRef,contentVersion},headers:{},body:null},async()=>(await readyDetail(fixture.category)).result)
  }})
  const oldOperation=executeLifeContentDetailRead(state,apiFor(oldGate),initial.item.contentRef,initial.item.contentVersion,'REENTRY',probe)
  const duringState=stateCounts(state,'DETAIL')
  revokeLifeContentDetail(state,'STALE')
  const freshOperation=executeLifeContentDetailRead(state,apiFor(freshGate),initial.item.contentRef,initial.item.contentVersion,'FOREGROUND_RESUME',probe)
  freshGate.release();await freshOperation
  const acceptedGeneration=state.readGeneration,acceptedItem=state.item
  oldGate.release();await oldOperation
  const oldResponseDiscarded=state.readGeneration===acceptedGeneration&&state.item===acceptedItem&&state.viewState==='READY'
  if(oldResponseDiscarded)probe.increment('OldResponseDiscarded')
  return {raw:null,state,fixture,beforeState,duringState,afterState:stateCounts(state,'DETAIL'),probe,before,after:probe.snapshot(),oldResponseDiscarded}
}

async function exerciseParameter(row){
  const fixture=causalFixture(row)
  assertFixtureCause(row,fixture)
  if(row.parameterId==='FOREGROUND_RESUME')return exerciseForegroundResumeRace(fixture)
  if(fixture.pageKind==='LIST')return exerciseList(fixture,{navigate:row.parameterId==='LIST_READY',startReady:['COMPLETE_ZERO_CANDIDATE','LIST_ONLY_CANDIDATE','INCOMPLETE_LIST_SCAN'].includes(row.parameterId)})
  return exerciseDetail(fixture,row.parameterId==='REENTRY'?'REENTRY':row.parameterId==='FIRST_ENTRY'?'FIRST_ENTRY':'FIRST_ENTRY')
}

function forbiddenRawValues(raw){
  const rawItem=raw?.item??raw?.items?.[0]??{}
  return ['title','summary','body','coverRef','sourceRef','rightsEvidenceRef','verifiedBy','verifiedAt','contentRef']
    .map((key)=>rawItem?.[key]).filter((value)=>typeof value==='string'&&value.length>0)
}

function sanitizeActual(execution,row){
  const afterFixed=execution.after.fixed,afterRuntime=execution.after.runtime
  const forbiddenValues=forbiddenRawValues(execution.raw)
  const actual={
    pageKind:execution.fixture.pageKind,beforeViewState:execution.beforeState.viewState,beforeItemCount:execution.beforeState.itemCount,beforeBodyCount:execution.beforeState.bodyCount,
    duringViewState:execution.duringState.viewState,duringItemCount:execution.duringState.itemCount,duringBodyCount:execution.duringState.bodyCount,
    afterViewState:execution.afterState.viewState,afterItemCount:execution.afterState.itemCount,afterBodyCount:execution.afterState.bodyCount,
    category:execution.state.item?.category??execution.state.items?.[0]?.category??null,retryVisible:retryVisible(execution.afterState.viewState),
    queryCall:afterRuntime.QueryCall,userRetry:afterRuntime.UserRetry,automaticRetry:afterRuntime.AutomaticRetry,
    projection:afterRuntime.Projection,navigation:afterRuntime.Navigation,identityHeader:afterRuntime.IdentityHeader,
    transport:afterRuntime.Transport,storageWrite:afterRuntime.StorageWrite,telemetry:afterRuntime.Telemetry,
    externalImageRequest:afterRuntime.ExternalImageRequest,a120Action:afterRuntime.A120Action,
    forbiddenLogPayloadCount:execution.probe.scanValues(forbiddenValues,['log','storage','telemetry']),externalCall:afterFixed.ExternalCall,
    writeSideEffectSum:frontendWriteSideEffectSum(afterFixed),a120PublishAction:afterFixed.A120PublishAction,
    causeSummary:fixtureSummary(execution.fixture),oldResponseDiscarded:execution.oldResponseDiscarded??false,
  }
  if(row.parameterId==='CURRENT_BODY_COVER_UNAVAILABLE')actual.coverRefPresent=execution.state.item?.coverRef!=null
  actual.forbiddenEvidencePayloadCount=forbiddenValues.filter((value)=>canonical(actual).includes(value)).length
  return actual
}

function expectedFor(row){
  const value=clone(EXPECTED_BY_PARAMETER[row.parameterId])
  value.category=value.afterItemCount===0?null:(row.scenarioId==='D5-INF-S02'||row.parameterId==='CURRENT_BODY_COVER_UNAVAILABLE'?'HOLIDAY_EXPLANATION':'LIFE_REMINDER')
  return value
}

function matchesExpected(expectedValue,actualValue){
  if(expectedValue===null||typeof expectedValue!=='object')return Object.is(expectedValue,actualValue)
  if(Array.isArray(expectedValue))return Array.isArray(actualValue)&&expectedValue.length===actualValue.length&&expectedValue.every((value,index)=>matchesExpected(value,actualValue[index]))
  return actualValue&&typeof actualValue==='object'&&Object.entries(expectedValue).every(([key,value])=>matchesExpected(value,actualValue[key]))
}

export async function computeLifeContentImplementationManifest(){
  const files={}
  for(const relative of LIFE_CONTENT_IMPLEMENTATION_FILES){files[relative]=sha256(await readFile(path.join(workspaceRoot,...relative.split('/'))))}
  const aggregatePayload=Object.entries(files).map(([name,hash])=>`${name}|${hash}`).join('\n')+'\n'
  return {files,aggregateSha256:sha256(aggregatePayload),aggregateRule:'path|UPPERCASE_SHA256; lexicographic path order; UTF-8 no BOM; LF with final LF'}
}

function packageManifest(){
  const packages=new Map()
  for(const row of LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN){
    if(!packages.has(row.evidencePackageId))packages.set(row.evidencePackageId,{evidencePackageId:row.evidencePackageId,scenarioId:row.scenarioId,subcaseId:row.subcaseId,parameterIds:[]})
    packages.get(row.evidencePackageId).parameterIds.push(row.parameterId)
  }
  return [...packages.values()]
}

export function buildLifeContentEvidenceRunnerManifest(){
  const packages=packageManifest()
  return Object.freeze({
    mode:'RUNNER_IMPLEMENTED_FINAL_RUN_NOT_EXECUTED',packageCount:packages.length,parameterCount:LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN.length,
    singleRunIdRequired:true,oneTimeAuthorizationRequired:true,atomicChildStagingRequired:true,parentCoordinatorRequired:true,
    fixedInputs:{...LIFE_CONTENT_FIXED_INPUTS},outputDirectory:LIFE_CONTENT_EVIDENCE_OUTPUT_DIRECTORY,
    implementationFiles:[...LIFE_CONTENT_IMPLEMENTATION_FILES],packages,
  })
}

export async function runLifeContentBoundarySensitivityCheck(){
  const canary=`D5_INF_SECRET_CANARY_${randomUUID()}`
  const probe=new RuntimeBoundaryProbe()
  const before=probe.snapshot()
  probe.increment('QueryCall');probe.increment('UserRetry');probe.increment('AutomaticRetry');probe.increment('Projection');probe.increment('Body')
  await probe.anonymousTransport({method:'GET',path:'/sensitivity',headers:{authorization:canary},body:{canary}},async()=>({ok:true}))
  await probe.externalTransport({method:'GET',path:'https://invalid.example/sensitivity',headers:{},body:{canary}},async()=>({ok:true}))
  probe.persistLog({canary});probe.writeStorage({canary});probe.emitTelemetry({canary})
  navigateToLifeContentDetail({contentRef:'CANARY-REF',contentVersion:'CANARY-VERSION'},(url)=>probe.captureNavigation(url),probe)
  probe.requestExternalImage({canary});probe.performA120({canary})
  const after=probe.snapshot(),runtimeSensitivity=runtimeDelta(before.runtime,after.runtime),counterSensitivity=counterDelta(before.fixed,after.fixed)
  const scopes=Object.fromEntries(Object.entries(probe.captures).filter(([name])=>['transport','log','storage','telemetry'].includes(name)).map(([name,values])=>[name,canonical(values).includes(canary)?1:0]))
  if(Object.values(scopes).some((value)=>value!==1))throw new Error('SENSITIVE_CANARY_DETECTOR_INSENSITIVE')
  for(const metric of ['QueryCall','UserRetry','AutomaticRetry','Projection','Body','Navigation','IdentityHeader','StorageWrite','Telemetry','ExternalImageRequest','A120Action']){
    if(runtimeSensitivity[metric]<1)throw new Error(`RUNTIME_SPY_INSENSITIVE:${metric}`)
  }
  for(const name of ['QueryCall','A120PublishAction','ExternalCall','FileWrite','PersistentLogWrite','ExternalAnalytics']){
    if(counterSensitivity[name]<1)throw new Error(`FRONTEND_COUNTER_SPY_INSENSITIVE:${name}`)
  }
  return Object.freeze({scopes,runtimeSensitivity,counterSensitivity,canarySha256:sha256(canary),rawCanaryExposed:false})
}

export async function runLifeContentRunnerStaticPrecheck(){
  const rows=[...LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN]
  const keys=rows.map(parameterKey)
  if(rows.length!==26||new Set(keys).size!==26||canonical([...keys].sort())!==canonical([...FROZEN_PARAMETER_KEYS].sort()))throw new Error('FROZEN_PARAMETER_SET_MISMATCH')
  if(packageManifest().length!==13)throw new Error('FROZEN_PACKAGE_COUNT_MISMATCH')
  let dryParameterCount=0
  for(const row of rows){
    const fixture=causalFixture(row);assertFixtureCause(row,fixture)
    const execution=await exerciseParameter(row),actual=sanitizeActual(execution,row)
    if(!matchesExpected(expectedFor(row),actual))throw new Error(`STATIC_DRY_PARAMETER_MISMATCH:${parameterKey(row)}:${canonical({expected:expectedFor(row),actual})}`)
    if(BACKEND_OWNED_COUNTERS.some((name)=>execution.after.fixed[name]!==NOT_OBSERVED_BACKEND_OWNED))throw new Error(`BACKEND_OWNED_COUNTER_FABRICATED:${parameterKey(row)}`)
    dryParameterCount+=1
  }
  const distinctCauseIds=['STALE_BEFORE_EFFECTIVE_TO','REFERENCE_AFTER_EFFECTIVE_TO','MISSING_QUALIFICATION','RIGHTS_CONFLICT','INCOMPLETE_LIST_SCAN']
  const distinctRows=distinctCauseIds.map((parameterId)=>rows.find((row)=>row.parameterId===parameterId))
  const causeDigests=distinctRows.map((row)=>digest(causalFixture(row)))
  if(new Set(causeDigests).size!==causeDigests.length)throw new Error('CAUSE_FIXTURE_DIGEST_NOT_DISTINCT')
  const raceRow=rows.find((row)=>row.parameterId==='FOREGROUND_RESUME')
  const race=await exerciseParameter(raceRow)
  if(!race.oldResponseDiscarded||race.after.runtime.OldResponseDiscarded!==1||race.after.runtime.QueryCall!==2)throw new Error('OLD_RESPONSE_GENERATION_GUARD_NOT_OBSERVED')
  const sensitivity=await runLifeContentBoundarySensitivityCheck()
  return Object.freeze({packageCount:13,parameterCount:26,uniqueParameterCount:new Set(keys).size,dryParameterCount,distinctCausalFixtureCount:new Set(causeDigests).size,oldResponseDiscarded:1,sensitivity})
}

const issuedAuthorizations=new WeakMap()
const consumedAuthorizationIds=new Set()

function validateAuthorizationPayload(value,authorizationFile){
  if(!value||typeof value!=='object'||Array.isArray(value))throw new Error('INVALID_FINAL_RUN_AUTHORIZATION')
  if(value.authorizationKind!==AUTHORIZATION_KIND||typeof value.authorizationId!=='string'||!/^AUTH-D5-INF-[A-Za-z0-9-]{8,}$/.test(value.authorizationId))throw new Error('INVALID_FINAL_RUN_AUTHORIZATION_ID')
  if(typeof value.executionRunId!=='string'||!/^D5-INF-01-FE-[A-Za-z0-9-]{8,}$/.test(value.executionRunId))throw new Error('INVALID_SINGLE_EXECUTION_RUN_ID')
  if(!/^[A-F0-9]{64}$/.test(value.approvedImplementationSha256??''))throw new Error('INVALID_APPROVED_IMPLEMENTATION_SHA')
  assertLifeContentDualMatrixFixedInputs(value.approvedFixedInputs)
  if(canonical(value.approvedFixedInputs)!==canonical(LIFE_CONTENT_FIXED_INPUTS))throw new Error('AUTHORIZATION_FIXED_INPUTS_MISMATCH')
  if(typeof value.executor!=='string'||!value.executor.trim()||typeof value.reviewer!=='string'||!value.reviewer.trim()||value.executor===value.reviewer)throw new Error('INVALID_EXECUTOR_REVIEWER')
  if(!Number.isFinite(Date.parse(value.issuedAt))||!Number.isFinite(Date.parse(value.expiresAt))||Date.parse(value.expiresAt)<=Date.now())throw new Error('EXPIRED_OR_INVALID_AUTHORIZATION_TIMEBOX')
  if(!/^[A-F0-9]{64}$/.test(value.backendImplementationSha256??'')||!/^[A-F0-9]{64}$/.test(value.backendEvidenceIndexSha256??''))throw new Error('MISSING_BACKEND_IMPLEMENTATION_EVIDENCE')
  if(!value.backendEvidenceRefs||typeof value.backendEvidenceRefs!=='object')throw new Error('MISSING_BACKEND_EVIDENCE_REFS')
  for(const item of packageManifest())if(typeof value.backendEvidenceRefs[item.evidencePackageId]!=='string'||!value.backendEvidenceRefs[item.evidencePackageId].trim())throw new Error(`MISSING_BACKEND_EVIDENCE_REF:${item.evidencePackageId}`)
  return Object.freeze({...clone(value),authorizationFile:path.resolve(authorizationFile),consumptionMarker:`${path.resolve(authorizationFile)}.consumed`})
}

async function issueAuthorizationFromFile(authorizationFile){
  const payload=JSON.parse(await readFile(path.resolve(authorizationFile),'utf8'))
  const metadata=validateAuthorizationPayload(payload,authorizationFile)
  const token=Object.freeze({authorizationId:metadata.authorizationId})
  issuedAuthorizations.set(token,{...metadata,consumed:false})
  return token
}

async function assertPreExecutionGate(metadata){
  if(metadata.consumed||consumedAuthorizationIds.has(metadata.authorizationId)||await exists(metadata.consumptionMarker))throw new Error('FINAL_RUN_AUTHORIZATION_ALREADY_CONSUMED')
  const implementation=await computeLifeContentImplementationManifest()
  if(implementation.aggregateSha256!==metadata.approvedImplementationSha256)throw new Error('APPROVED_IMPLEMENTATION_SHA_MISMATCH')
  await verifyLifeContentMatrixBindings()
  assertLifeContentDualMatrixFixedInputs(metadata.approvedFixedInputs)
  if(canonical(metadata.approvedFixedInputs)!==canonical(LIFE_CONTENT_FIXED_INPUTS))throw new Error('FIXED_INPUT_VERSION_DRIFT')
  const manifest=buildLifeContentEvidenceRunnerManifest(),keys=LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN.map(parameterKey)
  if(manifest.packageCount!==13||manifest.parameterCount!==26||new Set(keys).size!==26||canonical([...keys].sort())!==canonical([...FROZEN_PARAMETER_KEYS].sort()))throw new Error('FINAL_RUN_DENOMINATOR_OR_PARAMETER_SET_MISMATCH')
  const outputDirectory=path.resolve(LIFE_CONTENT_EVIDENCE_OUTPUT_DIRECTORY),outputParent=path.dirname(outputDirectory)
  if(path.basename(outputDirectory)!=='D5-INF-01前端'||!outputDirectory.startsWith(path.resolve(workspaceRoot)+path.sep))throw new Error('UNSAFE_EVIDENCE_OUTPUT_DIRECTORY')
  if(await exists(outputDirectory))throw new Error('FORMAL_EVIDENCE_DIRECTORY_ALREADY_EXISTS')
  const siblings=await readdir(outputParent).catch((error)=>error?.code==='ENOENT'?[]:Promise.reject(error))
  if(siblings.some((name)=>name.includes(metadata.executionRunId)))throw new Error('EXECUTION_RUN_ID_ALREADY_USED')
  return {implementation,outputDirectory,outputParent}
}

async function reserveAuthorization(metadata){
  const handle=await open(metadata.consumptionMarker,'wx')
  try{await handle.writeFile(JSON.stringify({authorizationId:metadata.authorizationId,executionRunId:metadata.executionRunId,consumedAt:new Date().toISOString()})+'\n','utf8')}
  finally{await handle.close()}
  metadata.consumed=true
  consumedAuthorizationIds.add(metadata.authorizationId)
}

async function executeAllParameters(metadata,implementation){
  const packageMap=new Map(),allForbiddenValues=[]
  for(const row of LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN){
    const execution=await exerciseParameter(row),actual=sanitizeActual(execution,row),expectedValue=expectedFor(row)
    if(!matchesExpected(expectedValue,actual))throw new Error(`PARAMETER_EXPECTED_ACTUAL_MISMATCH:${parameterKey(row)}`)
    const forbiddenValues=forbiddenRawValues(execution.raw);allForbiddenValues.push(...forbiddenValues)
    if(execution.probe.scanValues(forbiddenValues,['log','storage','telemetry'])!==0)throw new Error(`RUNTIME_PRIVACY_BOUNDARY_LEAK:${parameterKey(row)}`)
    const parameter={
      scenarioId:row.scenarioId,subcaseId:row.subcaseId,parameterId:row.parameterId,parameterReasonCode:LIFE_CONTENT_PARAMETER_REASON_CODES[row.parameterId],
      fixedInputs:{...LIFE_CONTENT_FIXED_INPUTS},frontendImplementationSha256:implementation.aggregateSha256,
      backendImplementationSha256:metadata.backendImplementationSha256,backendEvidenceRef:metadata.backendEvidenceRefs[row.evidencePackageId],
      fixtureDigest:digest(execution.fixture),causeSummary:fixtureSummary(execution.fixture),referenceInstant:execution.fixture.referenceInstant,clock:'CONTROLLED_LOCAL_SYNTHETIC',
      expected:expectedValue,actual,counters:{before:execution.before.fixed,after:execution.after.fixed,delta:counterDelta(execution.before.fixed,execution.after.fixed)},
      runtime:{before:execution.before.runtime,after:execution.after.runtime,delta:runtimeDelta(execution.before.runtime,execution.after.runtime)},
      executionStatus:'PASS',defectRef:'NONE_REGISTERED',
    }
    parameter.parameterEvidenceDigest=digest(parameter)
    if(!packageMap.has(row.evidencePackageId))packageMap.set(row.evidencePackageId,{evidencePackageId:row.evidencePackageId,executionRunId:metadata.executionRunId,scenarioId:row.scenarioId,subcaseId:row.subcaseId,parameters:[]})
    packageMap.get(row.evidencePackageId).parameters.push(parameter)
  }
  return {packageMap,allForbiddenValues:[...new Set(allForbiddenValues)]}
}

function materializeEvidence(metadata,implementation,execution,sensitivity){
  const packages=[...execution.packageMap.values()].map((entry)=>{
    const base={...entry,fixedInputs:{...LIFE_CONTENT_FIXED_INPUTS},frontendImplementation:implementation,
      backendImplementationSha256:metadata.backendImplementationSha256,backendEvidenceIndexSha256:metadata.backendEvidenceIndexSha256,
      backendEvidenceRef:metadata.backendEvidenceRefs[entry.evidencePackageId],executor:metadata.executor,reviewer:metadata.reviewer,
      authorizationRef:metadata.authorizationId,sensitivityProbe:sensitivity,
      forbiddenPayloadScan:{ruleVersion:'D5-INF-01-FE-FORBIDDEN-V2',scope:'PACKAGE_JSON_IN_MEMORY',hitCount:0,evidenceRef:`SCAN-${entry.evidencePackageId}`},executionStatus:'PASS'}
    base.forbiddenPayloadScan.hitCount=execution.allForbiddenValues.filter((value)=>canonical(base).includes(value)).length
    if(base.forbiddenPayloadScan.hitCount!==0)throw new Error(`PACKAGE_FORBIDDEN_PAYLOAD:${entry.evidencePackageId}`)
    base.evidencePackageDigest=digest(base);return base
  })
  if(packages.length!==13||packages.reduce((sum,item)=>sum+item.parameters.length,0)!==26)throw new Error('MATERIALIZED_DENOMINATOR_MISMATCH')
  const indexBase={executionRunId:metadata.executionRunId,fixedInputs:{...LIFE_CONTENT_FIXED_INPUTS},frontendImplementationSha256:implementation.aggregateSha256,
    backendImplementationSha256:metadata.backendImplementationSha256,backendEvidenceIndexSha256:metadata.backendEvidenceIndexSha256,
    executor:metadata.executor,reviewer:metadata.reviewer,authorizationRef:metadata.authorizationId,
    packageCount:packages.length,parameterCount:packages.reduce((sum,item)=>sum+item.parameters.length,0),
    packages:packages.map((item)=>({evidencePackageId:item.evidencePackageId,scenarioId:item.scenarioId,subcaseId:item.subcaseId,parameterIds:item.parameters.map((row)=>row.parameterId),backendEvidenceRef:item.backendEvidenceRef,executionStatus:item.executionStatus,evidencePackageDigest:item.evidencePackageDigest,file:`${item.evidencePackageId}.json`})),
    executionStatus:'PASS'}
  const index={...indexBase,evidencePackageIndexDigest:digest(indexBase)}
  const bundleBase={executionRunId:metadata.executionRunId,fixedInputs:{...LIFE_CONTENT_FIXED_INPUTS},frontendImplementation:implementation,
    backendImplementationSha256:metadata.backendImplementationSha256,backendEvidenceIndexSha256:metadata.backendEvidenceIndexSha256,
    executor:metadata.executor,reviewer:metadata.reviewer,authorizationRef:metadata.authorizationId,indexDigest:index.evidencePackageIndexDigest,
    packageDigests:packages.map((item)=>item.evidencePackageDigest),packageCount:packages.length,parameterCount:index.parameterCount,executionStatus:'PASS'}
  const bundle={...bundleBase,evidencePackageDigest:digest(bundleBase)}
  const serialized=canonical({packages,index,bundle})
  if(execution.allForbiddenValues.some((value)=>serialized.includes(value)))throw new Error('FINAL_EVIDENCE_FORBIDDEN_PAYLOAD')
  return {packages,index,bundle}
}

function assertSafeChildStagingDirectory(stagingDirectory,gate){
  const target=path.resolve(stagingDirectory),coordinatorDirectory=path.dirname(target)
  if(path.basename(target)!=='child-result'||!path.basename(coordinatorDirectory).startsWith('.D5-INF-01前端.coordinator-'))throw new Error('UNSAFE_CHILD_STAGING_DIRECTORY')
  if(path.dirname(coordinatorDirectory)!==gate.outputParent||target===gate.outputDirectory)throw new Error('CHILD_STAGING_OUTSIDE_COORDINATOR_DIRECTORY')
  return target
}

async function writeUnpublishedChildResult(metadata,gate,evidence,forbiddenValues,stagingDirectory){
  const targetDirectory=assertSafeChildStagingDirectory(stagingDirectory,gate)
  const temporaryDirectory=`${targetDirectory}.tmp-${randomUUID()}`
  if(await exists(temporaryDirectory)||await exists(targetDirectory)||await exists(gate.outputDirectory))throw new Error('NON_UNIQUE_OR_EXISTING_CHILD_STAGING_DIRECTORY')
  await mkdir(temporaryDirectory,{recursive:false})
  try{
    for(const item of evidence.packages)await writeFile(path.join(temporaryDirectory,`${item.evidencePackageId}.json`),JSON.stringify(item,null,2)+'\n','utf8')
    await writeFile(path.join(temporaryDirectory,'EvidencePackageIndex.json'),JSON.stringify(evidence.index,null,2)+'\n','utf8')
    await writeFile(path.join(temporaryDirectory,'EvidencePackage.json'),JSON.stringify(evidence.bundle,null,2)+'\n','utf8')
    const files=(await readdir(temporaryDirectory)).sort()
    if(files.length!==15||!files.includes('EvidencePackageIndex.json')||!files.includes('EvidencePackage.json'))throw new Error('TEMPORARY_EVIDENCE_FILE_SET_INCOMPLETE')
    const serializedFiles=await Promise.all(files.map((file)=>readFile(path.join(temporaryDirectory,file),'utf8')))
    if(forbiddenValues.some((value)=>serializedFiles.some((serialized)=>serialized.includes(value))))throw new Error('TEMPORARY_EVIDENCE_FORBIDDEN_PAYLOAD')
    const parsed=serializedFiles.map((serialized)=>JSON.parse(serialized))
    const packageParameters=parsed.filter((value)=>value.evidencePackageId?.startsWith('INF-EP-')).flatMap((value)=>value.parameters??[])
    if(packageParameters.length!==26||new Set(packageParameters.map((row)=>`${row.scenarioId}/${row.subcaseId}/${row.parameterId}`)).size!==26)throw new Error('TEMPORARY_EVIDENCE_PARAMETER_SET_INVALID')
    if(parsed.some((value)=>value.executionStatus!=='PASS'))throw new Error('TEMPORARY_EVIDENCE_CONTAINS_NON_PASS')
    await rename(temporaryDirectory,targetDirectory)
  }catch(error){await rm(temporaryDirectory,{recursive:true,force:true});throw error}
  return targetDirectory
}

export async function runLifeContentEvidence({authorization,stagingDirectory}={}){
  const metadata=issuedAuthorizations.get(authorization)
  if(!metadata)throw new Error('UNISSUED_OR_FORGED_FINAL_RUN_AUTHORIZATION')
  const gate=await assertPreExecutionGate(metadata)
  await reserveAuthorization(metadata)
  const sensitivity=await runLifeContentBoundarySensitivityCheck()
  const execution=await executeAllParameters(metadata,gate.implementation)
  const evidence=materializeEvidence(metadata,gate.implementation,execution,sensitivity)
  const unpublishedDirectory=await writeUnpublishedChildResult(metadata,gate,evidence,execution.allForbiddenValues,stagingDirectory)
  return {
    executionRunId:metadata.executionRunId,authorizationId:metadata.authorizationId,
    frontendImplementationSha256:gate.implementation.aggregateSha256,packageCount:evidence.packages.length,parameterCount:evidence.index.parameterCount,
    childToolVersions:{node:process.version,platform:process.platform,architecture:process.arch},
    indexDigest:evidence.index.evidencePackageIndexDigest,bundleDigest:evidence.bundle.evidencePackageDigest,
    packageDigests:Object.fromEntries(evidence.packages.map((item)=>[item.evidencePackageId,item.evidencePackageDigest])),
    unpublishedResultReady:await exists(unpublishedDirectory),
  }
}

function argumentValue(flag){const index=process.argv.indexOf(flag);return index>=0?process.argv[index+1]:null}

async function main(){
  const args=new Set(process.argv.slice(2))
  if(args.has('--preflight')){console.log(JSON.stringify(buildLifeContentEvidenceRunnerManifest()));return}
  if(args.has('--final-run'))throw new Error('FINAL_PUBLICATION_REQUIRES_PARENT_COORDINATOR')
  if(!args.has('--child-run'))throw new Error('USE_PREFLIGHT_OR_PARENT_COORDINATED_CHILD_RUN')
  const authorizationFile=argumentValue('--authorization-file')
  const stagingDirectory=argumentValue('--staging-directory')
  if(!authorizationFile||!stagingDirectory)throw new Error('CHILD_RUN_REQUIRES_AUTHORIZATION_AND_STAGING_DIRECTORY')
  const authorization=await issueAuthorizationFromFile(authorizationFile)
  const result=await runLifeContentEvidence({authorization,stagingDirectory})
  console.log(JSON.stringify(result))
}

if(process.argv[1]&&pathToFileURL(path.resolve(process.argv[1])).href===import.meta.url){
  main().catch((error)=>{console.error(error instanceof Error?error.message:String(error));process.exitCode=1})
}
