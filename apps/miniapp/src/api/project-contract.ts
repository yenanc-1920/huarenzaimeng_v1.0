import type { OrderProjection, QuoteSnapshot, RechargeSelection } from '../domain/types'

export interface ProjectQuote {
  quoteRef:string; maskedPhone:string; operatorCode:string; productCode:string; denominationRef:string
  supportedOperatorSetVersion:number; catalogVersion:number; totalAmountMinor:number; currency:'CNY'|'BDT'; expiresAt:string
}
export type ProjectReadActionCode='REFRESH_ORDER_STATUS'|'CONTACT_SUPPORT'|'SAFE_EXIT'
/** Compatibility shape for test-only legacy contracts; production projections expose ProjectReadAction only. */
export interface ProjectAction { actionCode:string; expectedProjectionVersion:number; expectedAggregateVersion:number|null }
export interface ProjectReadAction extends ProjectAction { actionCode:ProjectReadActionCode }
export interface ProjectProjection {
  orderRef:string; quoteRef:string; orderState:string; priceSnapshot:ProjectQuote
  facts:{payment:string;upstreamDebit:string;delivery:string;refund:string}
  projectionVersion:number; aggregateVersion:number; allowedActions:ProjectReadAction[]
}

const object=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v)
const text=(v:unknown):v is string=>typeof v==='string'&&v.length>0
const number=(v:unknown):v is number=>typeof v==='number'&&Number.isFinite(v)
const exactKeys=(v:Record<string,unknown>,allowed:string[])=>Object.keys(v).length===allowed.length&&Object.keys(v).every(key=>allowed.includes(key))
const READ_ACTIONS:readonly ProjectReadActionCode[]=['REFRESH_ORDER_STATUS','CONTACT_SUPPORT','SAFE_EXIT']
const readActionCode=(v:unknown):v is ProjectReadActionCode=>typeof v==='string'&&READ_ACTIONS.includes(v as ProjectReadActionCode)

export function parseProjectQuote(v:unknown):ProjectQuote{
  if(!object(v)||!exactKeys(v,['quoteRef','maskedPhone','operatorCode','productCode','denominationRef','supportedOperatorSetVersion','catalogVersion','totalAmountMinor','currency','expiresAt'])
    ||!text(v.quoteRef)||!text(v.maskedPhone)||!text(v.operatorCode)||!text(v.productCode)||!text(v.denominationRef)
    ||!Number.isInteger(v.supportedOperatorSetVersion)||Number(v.supportedOperatorSetVersion)<1
    ||!Number.isInteger(v.catalogVersion)||Number(v.catalogVersion)<1
    ||!number(v.totalAmountMinor)||v.totalAmountMinor<=0||(v.currency!=='CNY'&&v.currency!=='BDT')||!text(v.expiresAt))throw new Error('INVALID_QUOTE_DTO')
  return{quoteRef:v.quoteRef,maskedPhone:v.maskedPhone,operatorCode:v.operatorCode,productCode:v.productCode,denominationRef:v.denominationRef,supportedOperatorSetVersion:v.supportedOperatorSetVersion as number,catalogVersion:v.catalogVersion as number,totalAmountMinor:v.totalAmountMinor,currency:v.currency,expiresAt:v.expiresAt}
}

/** Read-only projection. Unknown server actions are discarded until real channels are configured. */
export function parseProjectProjection(v:unknown):ProjectProjection{
  if(!object(v)||!text(v.orderRef)||!text(v.quoteRef)||!text(v.orderState)||!object(v.facts)
    ||!number(v.projectionVersion)||!number(v.aggregateVersion)||!Array.isArray(v.allowedActions))throw new Error('INVALID_PROJECT_PROJECTION_DTO')
  const priceSnapshot=parseProjectQuote(v.priceSnapshot);const facts=v.facts
  if(!text(facts.payment)||!text(facts.upstreamDebit)||!text(facts.delivery)||!text(facts.refund))throw new Error('INVALID_PROJECT_FACTS_DTO')
  const allowedActions=v.allowedActions.flatMap(a=>{
    if(!object(a)||!readActionCode(a.actionCode)||!number(a.expectedProjectionVersion)||(a.expectedAggregateVersion!==null&&!number(a.expectedAggregateVersion)))return[]
    return[{actionCode:a.actionCode,expectedProjectionVersion:a.expectedProjectionVersion,expectedAggregateVersion:a.expectedAggregateVersion as number|null}]
  })
  return{orderRef:v.orderRef,quoteRef:v.quoteRef,orderState:v.orderState,priceSnapshot,
    facts:{payment:facts.payment,upstreamDebit:facts.upstreamDebit,delivery:facts.delivery,refund:facts.refund},
    projectionVersion:v.projectionVersion,aggregateVersion:v.aggregateVersion,allowedActions}
}

const fact=(v:string)=>v==='CONFIRMED'?'CONFIRMED':v==='CONFLICT'?'CONFLICT':v==='PENDING'?'PENDING':'UNKNOWN'
type EntitlementSource=Pick<RechargeSelection,'productType'|'displayName'|'benefitText'|'validityText'|'priceVersionRef'>
export function toQuoteSnapshot(q:ProjectQuote,source?:EntitlementSource):QuoteSnapshot{
  const displayName=source?.displayName||q.productCode
  return{orderRef:'',priceSnapshotRef:q.quoteRef,maskedPhone:q.maskedPhone,operatorCode:q.operatorCode,operatorName:q.operatorCode,
    productRef:q.productCode,productName:displayName,denominationRef:q.denominationRef,
    entitlement:source?{productType:source.productType,displayName,benefitText:source.benefitText,validityText:source.validityText,source:'CATALOG_SNAPSHOT'}
      :{productType:'UNCONFIRMED',displayName,benefitText:'权益待确认',validityText:null,source:'UNCONFIRMED'},
    faceValue:{minor:0,currency:q.currency},total:{minor:q.totalAmountMinor,currency:q.currency},priceVersion:source?.priceVersionRef||q.quoteRef,
    supportedOperatorSetVersion:q.supportedOperatorSetVersion,catalogVersion:q.catalogVersion,expiresAt:q.expiresAt,validUntil:q.expiresAt,valid:Date.parse(q.expiresAt)>Date.now()}
}
export function toOrderProjection(p:ProjectProjection):OrderProjection{return{projectionId:`project:${p.orderRef}:${p.projectionVersion}`,orderRef:p.orderRef,projectionVersion:p.projectionVersion,generatedAt:new Date().toISOString(),terminal:p.allowedActions.length===0,stateCode:p.orderState,facts:{payment:fact(p.facts.payment),eligibility:'CONFIRMED',upstream:fact(p.facts.upstreamDebit),delivery:fact(p.facts.delivery),refund:fact(p.facts.refund),accounting:'UNKNOWN'},total:{minor:p.priceSnapshot.totalAmountMinor,currency:p.priceSnapshot.currency},faceValue:null,priceSnapshotRef:p.quoteRef,priceValidUntil:p.priceSnapshot.expiresAt,maskedPhone:p.priceSnapshot.maskedPhone,operatorCode:p.priceSnapshot.operatorCode,operatorName:p.priceSnapshot.operatorCode,productRef:p.priceSnapshot.productCode,productName:p.priceSnapshot.productCode,denominationRef:p.priceSnapshot.denominationRef,supportedOperatorSetVersion:p.priceSnapshot.supportedOperatorSetVersion,catalogVersion:p.priceSnapshot.catalogVersion,priceVersion:p.quoteRef,allowedActions:p.allowedActions.map(a=>({actionCode:a.actionCode==='CONTACT_SUPPORT'?'CONTACT_SUPPORT':a.actionCode==='SAFE_EXIT'?'SAFE_EXIT':'REFRESH',enabled:true,expectedProjectionVersion:a.expectedProjectionVersion})),supportRef:null,nextPollAt:null}}
