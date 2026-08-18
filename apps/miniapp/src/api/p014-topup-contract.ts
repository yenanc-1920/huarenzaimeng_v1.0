export const P014_BACKEND_IMPLEMENTATION_SHA = '46849B2856431B21FAE002B988822803606C4C60545434BD9ECE608CADEACF89'

export type P014Outcome = 'ACCEPTED' | 'REJECTED' | 'UNKNOWN'
export type P014ProjectCode =
  | 'TOPUP_NOT_AVAILABLE' | 'IDEMPOTENCY_CONFLICT' | 'VERSION_CONFLICT' | 'TOPUP_QUALIFICATION_UNKNOWN'
  | 'TOPUP_INTENT_CREATE_UNKNOWN' | 'TOPUP_INTENT_CREATED' | 'TOPUP_INTENT_REPLAYED'
  | 'TOPUP_INTENT_RESULT_FOUND' | 'TOPUP_INTENT_RESULT_REJECTED' | 'TOPUP_INTENT_RESULT_UNKNOWN'
  | 'TOPUP_INTENT_QUERY_NOT_AVAILABLE' | 'TOPUP_PROGRESS_NOT_AVAILABLE' | 'TOPUP_PROGRESS_READ'
export type P014StateCode = 'PAID_AWAITING_TOPUP' | 'TOPUP_PROCESSING' | 'TOPUP_RESULT_UNKNOWN'
  | 'CONFIRMED_NOT_DELIVERED' | 'DELIVERED' | 'SUPPORT_REVIEW'
export type P014FactCode = 'PAYMENT' | 'UPSTREAM_DEBIT' | 'DELIVERY' | 'ACCOUNTING_CLOSURE'
export type P014FactState = 'NOT_OBSERVED' | 'UNKNOWN' | 'CONFIRMED' | 'CONFLICT' | 'ABSENT_CONFIRMED'
export type P014ActionCode = 'QUERY_ORIGINAL_TOPUP' | 'REFRESH_ORDER_PROJECTION' | 'OPEN_SUPPORT' | 'SAFE_LEAVE'

export interface P014PriceSnapshotSummary {
  priceSnapshotRef:string; totalMinor:number; currency:string; displayVersion:string; maskedRecipientNumber:string
  operatorDisplayName:string; productDisplayName:string; targetFaceValueMinor:number; targetCurrency:string; expiresAt:string
}
export interface P014ProgressSummary {
  userMessageCode:string; confirmedItems:P014FactCode[]; unknownItems:P014FactCode[]
  responsibilityCode:'SYSTEM_RECHECK'|'SUPPORT_REVIEW'|'ACCOUNTING_REVIEW'|'NONE'
  supportRef:string|null; updatedAt:string; nextReviewPoint:string|null
}
export interface P014Fact { factCode:P014FactCode; state:P014FactState; occurredAt:string|null; observedAt:string|null }
export interface P014AllowedAction {
  actionCode:P014ActionCode; enabled:true; expectedProjectionVersion:number; actionBindingVersion:string
}
export interface P014Projection {
  orderRef:string; stateCode:P014StateCode; schemaVersion:'P014_TOPUP_PROGRESS_V1'; projectionVersion:number
  aggregateVersion:number; priceSnapshotSummary:P014PriceSnapshotSummary; topupIntentRef:string|null
  dispatchIntentRef:string|null; paymentConfirmationState:Exclude<P014FactState,'ABSENT_CONFIRMED'>
  upstreamDebitState:Exclude<P014FactState,'ABSENT_CONFIRMED'>; deliveryState:P014FactState
  accountingClosureState:Exclude<P014FactState,'ABSENT_CONFIRMED'>; progressSummary:P014ProgressSummary
  factTimeline:P014Fact[]; allowedActions:P014AllowedAction[]
}
export interface P014Response {
  requestRef:string|null; outcome:P014Outcome; projectCode:P014ProjectCode; resourceRef:string|null
  aggregateVersion:number|null; currentProjection:P014Projection|null
  retryClass:'NONE'|'READ_SAFE'|'SAME_ACTION_QUERY_ONLY'; nextPollAt:string|null
}
export interface P014OriginalResultQuery {
  commandId:string; idempotencyKey:string; sessionVersion:number; authorizationSetRef:string
}
export interface P014IdentityStorage { getStorageSync(key:string):unknown; setStorageSync(key:string,value:unknown):void }
export interface P014QuerySession {
  role:'GUEST'|'BUYER'; sessionVersion:number; authorizationSetRef:string|null; authorizedOrderRefs:string[]
  issuedAt:string|null; expiresAt:string|null
}

const object=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null&&!Array.isArray(value)
const exactKeys=(value:Record<string,unknown>,keys:readonly string[])=>Object.keys(value).length===keys.length&&Object.keys(value).every(key=>keys.includes(key))
const text=(value:unknown):value is string=>typeof value==='string'&&value.length>0
const integer=(value:unknown):value is number=>typeof value==='number'&&Number.isInteger(value)&&value>0
const nonNegativeInteger=(value:unknown):value is number=>typeof value==='number'&&Number.isInteger(value)&&value>=0
const RFC3339=/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})$/
const instant=(value:unknown):value is string=>{
  if(!text(value))return false
  const match=RFC3339.exec(value)
  if(!match)return false
  const year=Number(match[1]),month=Number(match[2]),day=Number(match[3]),hour=Number(match[4]),minute=Number(match[5]),second=Number(match[6])
  const offset=match[7]
  const maxDay=month>=1&&month<=12?new Date(Date.UTC(year,month,0)).getUTCDate():0
  if(day<1||day>maxDay||hour>23||minute>59||second>59)return false
  if(offset!=='Z'){
    const offsetHour=Number(offset.slice(1,3)),offsetMinute=Number(offset.slice(4,6))
    if(offsetHour>23||offsetMinute>59)return false
  }
  return true
}
const nullableInstant=(value:unknown):value is string|null=>value===null||instant(value)
const nullableText=(value:unknown):value is string|null=>value===null||text(value)
const unique=<T>(values:T[])=>new Set(values).size===values.length

const FACT_CODES:readonly P014FactCode[]=['PAYMENT','UPSTREAM_DEBIT','DELIVERY','ACCOUNTING_CLOSURE']
const ACTION_CODES:readonly P014ActionCode[]=['QUERY_ORIGINAL_TOPUP','REFRESH_ORDER_PROJECTION','OPEN_SUPPORT','SAFE_LEAVE']
const NON_DELIVERY_STATES=['NOT_OBSERVED','UNKNOWN','CONFIRMED','CONFLICT'] as const
const DELIVERY_STATES=[...NON_DELIVERY_STATES,'ABSENT_CONFIRMED'] as const
const factCode=(value:unknown):value is P014FactCode=>typeof value==='string'&&FACT_CODES.includes(value as P014FactCode)
const actionCode=(value:unknown):value is P014ActionCode=>typeof value==='string'&&ACTION_CODES.includes(value as P014ActionCode)
const nonDeliveryState=(value:unknown):value is Exclude<P014FactState,'ABSENT_CONFIRMED'>=>typeof value==='string'&&NON_DELIVERY_STATES.includes(value as never)
const deliveryState=(value:unknown):value is P014FactState=>typeof value==='string'&&DELIVERY_STATES.includes(value as never)

const originalWriteKey=(orderRef:string)=>`p014OriginalWrite:${orderRef}`
export function readP014OriginalWriteIdentity(storage:Pick<P014IdentityStorage,'getStorageSync'>,orderRef:string,session:P014QuerySession,now=Date.now()):P014OriginalResultQuery|null{
  if(!text(orderRef)||session.role!=='BUYER'||!integer(session.sessionVersion)||!text(session.authorizationSetRef)
    ||!session.authorizedOrderRefs.includes(orderRef)||!instant(session.issuedAt)||!instant(session.expiresAt)
    ||Date.parse(session.issuedAt)>=Date.parse(session.expiresAt)||Date.parse(session.expiresAt)<=now)return null
  const value=storage.getStorageSync(originalWriteKey(orderRef))
  if(!object(value)||!exactKeys(value,['orderRef','commandId','idempotencyKey','sessionVersion','authorizationSetRef'])
    ||value.orderRef!==orderRef||!text(value.commandId)||!text(value.idempotencyKey)
    ||value.sessionVersion!==session.sessionVersion||value.authorizationSetRef!==session.authorizationSetRef)return null
  return{commandId:value.commandId,idempotencyKey:value.idempotencyKey,sessionVersion:value.sessionVersion as number,authorizationSetRef:value.authorizationSetRef as string}
}

function parsePrice(value:unknown):P014PriceSnapshotSummary {
  const keys=['priceSnapshotRef','totalMinor','currency','displayVersion','maskedRecipientNumber','operatorDisplayName','productDisplayName','targetFaceValueMinor','targetCurrency','expiresAt']
  if(!object(value)||!exactKeys(value,keys)||!text(value.priceSnapshotRef)||!nonNegativeInteger(value.totalMinor)
    ||typeof value.currency!=='string'||!/^[A-Z]{3}$/.test(value.currency)||!text(value.displayVersion)
    ||!text(value.maskedRecipientNumber)||!value.maskedRecipientNumber.includes('*')||!text(value.operatorDisplayName)
    ||!text(value.productDisplayName)||!nonNegativeInteger(value.targetFaceValueMinor)
    ||typeof value.targetCurrency!=='string'||!/^[A-Z]{3}$/.test(value.targetCurrency)||!instant(value.expiresAt)) throw new Error('INVALID_P014_PRICE_SNAPSHOT')
  return value as unknown as P014PriceSnapshotSummary
}
function parseFact(value:unknown):P014Fact {
  if(!object(value)||!exactKeys(value,['factCode','state','occurredAt','observedAt'])||!factCode(value.factCode)
    ||!(value.factCode==='DELIVERY'?deliveryState(value.state):nonDeliveryState(value.state))
    ||!nullableInstant(value.occurredAt)||!nullableInstant(value.observedAt)) throw new Error('INVALID_P014_FACT')
  return value as unknown as P014Fact
}
function parseAction(value:unknown,projectionVersion:number):P014AllowedAction|null {
  if(!object(value)||!exactKeys(value,['actionCode','enabled','expectedProjectionVersion','actionBindingVersion'])
    ||!actionCode(value.actionCode)||value.enabled!==true||value.expectedProjectionVersion!==projectionVersion
    ||!text(value.actionBindingVersion)) return null
  return value as unknown as P014AllowedAction
}
function parseProgress(value:unknown,facts:P014Fact[]):P014ProgressSummary {
  const keys=['userMessageCode','confirmedItems','unknownItems','responsibilityCode','supportRef','updatedAt','nextReviewPoint']
  if(!object(value)||!exactKeys(value,keys)||!text(value.userMessageCode)||!Array.isArray(value.confirmedItems)
    ||!Array.isArray(value.unknownItems)||!value.confirmedItems.every(factCode)||!value.unknownItems.every(factCode)
    ||!unique(value.confirmedItems)||!unique(value.unknownItems)||value.confirmedItems.some(item=>value.unknownItems.includes(item))
    ||!['SYSTEM_RECHECK','SUPPORT_REVIEW','ACCOUNTING_REVIEW','NONE'].includes(String(value.responsibilityCode))
    ||!nullableText(value.supportRef)||!instant(value.updatedAt)||!nullableInstant(value.nextReviewPoint)) throw new Error('INVALID_P014_PROGRESS_SUMMARY')
  const expectedConfirmed=facts.filter(f=>f.state==='CONFIRMED'||f.state==='ABSENT_CONFIRMED').map(f=>f.factCode)
  const expectedUnknown=facts.filter(f=>f.state==='UNKNOWN'||f.state==='NOT_OBSERVED').map(f=>f.factCode)
  if(value.confirmedItems.join('|')!==expectedConfirmed.join('|')||value.unknownItems.join('|')!==expectedUnknown.join('|')) throw new Error('P014_PROGRESS_FACT_MISMATCH')
  return value as unknown as P014ProgressSummary
}

const DECISIONS:Record<string,{state:P014StateCode;responsibility:P014ProgressSummary['responsibilityCode'];actions:P014ActionCode[];support:'required'|'nullable';nextReview:'required'|'nullable'|'null'}>={
  PAYMENT_CONFIRMED_READY_FOR_TOPUP:{state:'PAID_AWAITING_TOPUP',responsibility:'NONE',actions:['REFRESH_ORDER_PROJECTION','OPEN_SUPPORT','SAFE_LEAVE'],support:'nullable',nextReview:'null'},
  PAYMENT_CONFIRMED_TOPUP_QUALIFICATION_CHECKING:{state:'PAID_AWAITING_TOPUP',responsibility:'SYSTEM_RECHECK',actions:['REFRESH_ORDER_PROJECTION','OPEN_SUPPORT','SAFE_LEAVE'],support:'nullable',nextReview:'null'},
  PAYMENT_CONFIRMATION_CHECKING_NO_AUTO_TOPUP:{state:'PAID_AWAITING_TOPUP',responsibility:'SYSTEM_RECHECK',actions:['REFRESH_ORDER_PROJECTION','OPEN_SUPPORT','SAFE_LEAVE'],support:'nullable',nextReview:'null'},
  TOPUP_FACT_CONFLICT_UNDER_REVIEW:{state:'SUPPORT_REVIEW',responsibility:'SUPPORT_REVIEW',actions:['QUERY_ORIGINAL_TOPUP','OPEN_SUPPORT','SAFE_LEAVE'],support:'nullable',nextReview:'nullable'},
  DELIVERY_EVIDENCE_UNDER_REVIEW:{state:'SUPPORT_REVIEW',responsibility:'ACCOUNTING_REVIEW',actions:['QUERY_ORIGINAL_TOPUP','OPEN_SUPPORT','SAFE_LEAVE'],support:'nullable',nextReview:'nullable'},
  TOPUP_DELIVERED:{state:'DELIVERED',responsibility:'NONE',actions:['REFRESH_ORDER_PROJECTION','OPEN_SUPPORT','SAFE_LEAVE'],support:'nullable',nextReview:'null'},
  TOPUP_RESULT_PENDING_CONFIRMATION:{state:'TOPUP_RESULT_UNKNOWN',responsibility:'SUPPORT_REVIEW',actions:['QUERY_ORIGINAL_TOPUP','OPEN_SUPPORT','SAFE_LEAVE'],support:'required',nextReview:'nullable'},
  UPSTREAM_CONFIRMED_DELIVERY_ABSENT:{state:'CONFIRMED_NOT_DELIVERED',responsibility:'SUPPORT_REVIEW',actions:['QUERY_ORIGINAL_TOPUP','REFRESH_ORDER_PROJECTION','OPEN_SUPPORT','SAFE_LEAVE'],support:'nullable',nextReview:'nullable'},
  TOPUP_PROCESSING_DELIVERY_UNCONFIRMED:{state:'TOPUP_PROCESSING',responsibility:'SYSTEM_RECHECK',actions:['QUERY_ORIGINAL_TOPUP','REFRESH_ORDER_PROJECTION','OPEN_SUPPORT','SAFE_LEAVE'],support:'nullable',nextReview:'nullable'},
}

function decisionFactsMatch(messageCode:string,hasTopup:boolean,byCode:Map<P014FactCode,P014FactState>,actions:P014AllowedAction[],supportRef:string|null):boolean{
  const w=byCode.get('PAYMENT'),u=byCode.get('UPSTREAM_DEBIT'),d=byCode.get('DELIVERY'),l=byCode.get('ACCOUNTING_CLOSURE')
  const actionCodes=actions.map(action=>action.actionCode)
  const hasConflict=[u,d,l].includes('CONFLICT')
  const remainingCreated=hasTopup&&!hasConflict&&!(d==='CONFIRMED'&&(u!=='CONFIRMED'||l!=='CONFIRMED'))
    &&!(u==='CONFIRMED'&&d==='CONFIRMED'&&l==='CONFIRMED')&&!(u==='CONFIRMED'&&d==='ABSENT_CONFIRMED')
  switch(messageCode){
    case 'PAYMENT_CONFIRMED_READY_FOR_TOPUP': return !hasTopup&&w==='CONFIRMED'
    case 'PAYMENT_CONFIRMED_TOPUP_QUALIFICATION_CHECKING': return !hasTopup&&w==='CONFIRMED'
    case 'PAYMENT_CONFIRMATION_CHECKING_NO_AUTO_TOPUP': return !hasTopup&&(w==='UNKNOWN'||w==='CONFLICT')
    case 'TOPUP_FACT_CONFLICT_UNDER_REVIEW': return hasTopup&&hasConflict
    case 'DELIVERY_EVIDENCE_UNDER_REVIEW': return hasTopup&&!hasConflict&&d==='CONFIRMED'&&(u!=='CONFIRMED'||l!=='CONFIRMED')
    case 'TOPUP_DELIVERED': return hasTopup&&!hasConflict&&u==='CONFIRMED'&&d==='CONFIRMED'&&l==='CONFIRMED'
    case 'TOPUP_RESULT_PENDING_CONFIRMATION': return remainingCreated&&supportRef!==null
    case 'UPSTREAM_CONFIRMED_DELIVERY_ABSENT': return hasTopup&&!hasConflict&&u==='CONFIRMED'&&d==='ABSENT_CONFIRMED'
    case 'TOPUP_PROCESSING_DELIVERY_UNCONFIRMED': return remainingCreated
    default:return false
  }
}

function parseProjection(value:unknown):P014Projection {
  const keys=['orderRef','stateCode','schemaVersion','projectionVersion','aggregateVersion','priceSnapshotSummary','topupIntentRef','dispatchIntentRef','paymentConfirmationState','upstreamDebitState','deliveryState','accountingClosureState','progressSummary','factTimeline','allowedActions']
  if(!object(value)||!exactKeys(value,keys)||!text(value.orderRef)||value.schemaVersion!=='P014_TOPUP_PROGRESS_V1'
    ||!integer(value.projectionVersion)||!integer(value.aggregateVersion)||!nullableText(value.topupIntentRef)||!nullableText(value.dispatchIntentRef)
    ||(value.topupIntentRef===null)!==(value.dispatchIntentRef===null)||!nonDeliveryState(value.paymentConfirmationState)
    ||!nonDeliveryState(value.upstreamDebitState)||!deliveryState(value.deliveryState)||!nonDeliveryState(value.accountingClosureState)
    ||!Array.isArray(value.factTimeline)||value.factTimeline.length!==4||!Array.isArray(value.allowedActions)) throw new Error('INVALID_P014_PROJECTION')
  const priceSnapshotSummary=parsePrice(value.priceSnapshotSummary)
  const facts=value.factTimeline.map(parseFact)
  if(!unique(facts.map(f=>f.factCode))||facts.map(f=>f.factCode).join('|')!==FACT_CODES.join('|')) throw new Error('INVALID_P014_FACT_TIMELINE')
  const progressSummary=parseProgress(value.progressSummary,facts)
  const allowedActions=value.allowedActions.map(action=>parseAction(action,value.projectionVersion as number)).filter((action):action is P014AllowedAction=>action!==null)
  if(!unique(allowedActions.map(action=>action.actionCode))||!unique(allowedActions.map(action=>action.actionBindingVersion))) throw new Error('INVALID_P014_ACTION_SET')
  const byCode=new Map(facts.map(f=>[f.factCode,f.state]))
  const decision=DECISIONS[progressSummary.userMessageCode]
  if(!decision||value.stateCode!==decision.state||progressSummary.responsibilityCode!==decision.responsibility
    ||allowedActions.map(a=>a.actionCode).join('|')!==decision.actions.join('|')
    ||(decision.support==='required'&&!progressSummary.supportRef)
    ||(decision.nextReview==='null'&&progressSummary.nextReviewPoint!==null)
    ||(decision.nextReview==='required'&&progressSummary.nextReviewPoint===null)
    ||!decisionFactsMatch(progressSummary.userMessageCode,value.topupIntentRef!==null,byCode,allowedActions,progressSummary.supportRef)) throw new Error('P014_DECISION_MATRIX_MISMATCH')
  if(byCode.get('PAYMENT')!==value.paymentConfirmationState||byCode.get('UPSTREAM_DEBIT')!==value.upstreamDebitState
    ||byCode.get('DELIVERY')!==value.deliveryState||byCode.get('ACCOUNTING_CLOSURE')!==value.accountingClosureState) throw new Error('P014_FACT_STATE_MISMATCH')
  return {...value,priceSnapshotSummary,progressSummary,factTimeline:facts,allowedActions} as unknown as P014Projection
}

type Rule={outcome:P014Outcome;retry:P014Response['retryClass'];payload:'none'|'safe'|'resource'|'projection';requestRef:'any'|'required'|'null';nextPoll:boolean}
const RULES:Record<P014ProjectCode,Rule>={
  TOPUP_NOT_AVAILABLE:{outcome:'REJECTED',retry:'NONE',payload:'none',requestRef:'any',nextPoll:false},
  IDEMPOTENCY_CONFLICT:{outcome:'REJECTED',retry:'NONE',payload:'none',requestRef:'required',nextPoll:false},
  VERSION_CONFLICT:{outcome:'REJECTED',retry:'READ_SAFE',payload:'none',requestRef:'required',nextPoll:false},
  TOPUP_QUALIFICATION_UNKNOWN:{outcome:'REJECTED',retry:'READ_SAFE',payload:'safe',requestRef:'required',nextPoll:false},
  TOPUP_INTENT_CREATE_UNKNOWN:{outcome:'UNKNOWN',retry:'SAME_ACTION_QUERY_ONLY',payload:'none',requestRef:'required',nextPoll:true},
  TOPUP_INTENT_CREATED:{outcome:'ACCEPTED',retry:'NONE',payload:'resource',requestRef:'required',nextPoll:false},
  TOPUP_INTENT_REPLAYED:{outcome:'ACCEPTED',retry:'NONE',payload:'resource',requestRef:'required',nextPoll:false},
  TOPUP_INTENT_RESULT_FOUND:{outcome:'ACCEPTED',retry:'NONE',payload:'resource',requestRef:'required',nextPoll:false},
  TOPUP_INTENT_RESULT_REJECTED:{outcome:'REJECTED',retry:'NONE',payload:'none',requestRef:'required',nextPoll:false},
  TOPUP_INTENT_RESULT_UNKNOWN:{outcome:'UNKNOWN',retry:'SAME_ACTION_QUERY_ONLY',payload:'none',requestRef:'required',nextPoll:true},
  TOPUP_INTENT_QUERY_NOT_AVAILABLE:{outcome:'REJECTED',retry:'NONE',payload:'none',requestRef:'any',nextPoll:false},
  TOPUP_PROGRESS_NOT_AVAILABLE:{outcome:'REJECTED',retry:'NONE',payload:'none',requestRef:'null',nextPoll:false},
  TOPUP_PROGRESS_READ:{outcome:'ACCEPTED',retry:'NONE',payload:'projection',requestRef:'null',nextPoll:false},
}

export function parseP014Response(value:unknown):P014Response {
  const keys=['requestRef','outcome','projectCode','resourceRef','aggregateVersion','currentProjection','retryClass','nextPollAt']
  if(!object(value)||!exactKeys(value,keys)||!nullableText(value.requestRef)||!text(value.projectCode)
    ||!(value.projectCode in RULES)||!nullableText(value.resourceRef)||(value.aggregateVersion!==null&&!integer(value.aggregateVersion))
    ||!nullableInstant(value.nextPollAt)) throw new Error('INVALID_P014_RESPONSE')
  const code=value.projectCode as P014ProjectCode,rule=RULES[code]
  if(value.outcome!==rule.outcome||value.retryClass!==rule.retry||(rule.requestRef==='required'&&!text(value.requestRef))
    ||(rule.requestRef==='null'&&value.requestRef!==null)||(!rule.nextPoll&&value.nextPollAt!==null)) throw new Error('P014_RESPONSE_RULE_MISMATCH')
  const projection=value.currentProjection===null?null:parseProjection(value.currentProjection)
  if(rule.payload==='none'&&(value.resourceRef!==null||value.aggregateVersion!==null||projection!==null)) throw new Error('P014_NONE_PAYLOAD_MISMATCH')
  if(rule.payload==='safe'&&(value.resourceRef!==null||!integer(value.aggregateVersion)||!projection||projection.topupIntentRef!==null||projection.dispatchIntentRef!==null)) throw new Error('P014_SAFE_PAYLOAD_MISMATCH')
  if(rule.payload==='resource'&&(!text(value.resourceRef)||!integer(value.aggregateVersion)||!projection||value.resourceRef!==projection.topupIntentRef)) throw new Error('P014_RESOURCE_PAYLOAD_MISMATCH')
  if(rule.payload==='projection'&&(!integer(value.aggregateVersion)||!projection||value.resourceRef!==projection.topupIntentRef)) throw new Error('P014_PROJECTION_PAYLOAD_MISMATCH')
  if(projection&&value.aggregateVersion!==projection.aggregateVersion) throw new Error('P014_AGGREGATE_VERSION_MISMATCH')
  return {...value,projectCode:code,currentProjection:projection} as P014Response
}

export const isP014AcceptedProjection=(response:P014Response):response is P014Response&{outcome:'ACCEPTED';currentProjection:P014Projection;aggregateVersion:number}=>response.outcome==='ACCEPTED'&&response.currentProjection!==null
