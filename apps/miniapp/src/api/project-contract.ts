import type { OrderProjection, QuoteSnapshot } from '../domain/types'

export interface ProjectQuote { quoteRef:string; maskedPhone:string; operatorCode:string; productCode:string; totalAmountMinor:number; currency:string; expiresAt:string }
export interface ProjectAction { actionCode:'REQUEST_MOCK_PAYMENT'|'REQUEST_MOCK_TOPUP'|'WAIT_OR_CONTACT_SUPPORT'; expectedProjectionVersion:number; expectedAggregateVersion:number|null }
export interface ProjectProjection { orderRef:string; quoteRef:string; orderState:string; priceSnapshot:ProjectQuote; facts:{semantics:'MOCK_PROJECTION_ONLY_NO_EXTERNAL_FACTS';payment:string;upstreamDebit:string;delivery:string;refund:string}; projectionVersion:number; aggregateVersion:number; allowedActions:ProjectAction[] }

const object=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null
const text=(v:unknown):v is string=>typeof v==='string'&&v.length>0
const number=(v:unknown):v is number=>typeof v==='number'&&Number.isFinite(v)
const actionCode=(v:unknown):v is ProjectAction['actionCode']=>v==='REQUEST_MOCK_PAYMENT'||v==='REQUEST_MOCK_TOPUP'||v==='WAIT_OR_CONTACT_SUPPORT'

export function parseProjectQuote(v:unknown):ProjectQuote{
  if(!object(v)||!text(v.quoteRef)||!text(v.maskedPhone)||!text(v.operatorCode)||!text(v.productCode)||!number(v.totalAmountMinor)||!text(v.currency)||!text(v.expiresAt))throw new Error('INVALID_QUOTE_DTO')
  return{quoteRef:v.quoteRef,maskedPhone:v.maskedPhone,operatorCode:v.operatorCode,productCode:v.productCode,totalAmountMinor:v.totalAmountMinor,currency:v.currency,expiresAt:v.expiresAt}
}
export function parseProjectProjection(v:unknown):ProjectProjection{
  if(!object(v)||!text(v.orderRef)||!text(v.quoteRef)||!text(v.orderState)||!object(v.facts)||v.facts.semantics!=='MOCK_PROJECTION_ONLY_NO_EXTERNAL_FACTS'||!number(v.projectionVersion)||!number(v.aggregateVersion)||!Array.isArray(v.allowedActions))throw new Error('INVALID_PROJECT_PROJECTION_DTO')
  const priceSnapshot=parseProjectQuote(v.priceSnapshot);const facts=v.facts
  if(!text(facts.payment)||!text(facts.upstreamDebit)||!text(facts.delivery)||!text(facts.refund))throw new Error('INVALID_PROJECT_FACTS_DTO')
  const allowedActions=v.allowedActions.map(a=>{if(!object(a)||!actionCode(a.actionCode)||!number(a.expectedProjectionVersion)||(a.expectedAggregateVersion!==null&&!number(a.expectedAggregateVersion)))throw new Error('INVALID_PROJECT_ACTION_DTO');return{actionCode:a.actionCode,expectedProjectionVersion:a.expectedProjectionVersion,expectedAggregateVersion:a.expectedAggregateVersion}})
  return{orderRef:v.orderRef,quoteRef:v.quoteRef,orderState:v.orderState,priceSnapshot,facts:{semantics:facts.semantics,payment:facts.payment,upstreamDebit:facts.upstreamDebit,delivery:facts.delivery,refund:facts.refund},projectionVersion:v.projectionVersion,aggregateVersion:v.aggregateVersion,allowedActions}
}
const fact=(v:string)=>v==='CONFIRMED'?'CONFIRMED':v==='CONFLICT'?'CONFLICT':v==='PENDING'?'PENDING':'UNKNOWN'
export function toQuoteSnapshot(q:ProjectQuote):QuoteSnapshot{return{orderRef:'',priceSnapshotRef:q.quoteRef,maskedPhone:q.maskedPhone,operatorName:q.operatorCode,productName:q.productCode,faceValue:{minor:q.totalAmountMinor,currency:'BDT'},total:{minor:q.totalAmountMinor,currency:q.currency==='BDT'?'BDT':'CNY'},priceVersion:q.quoteRef,expiresAt:q.expiresAt,validUntil:q.expiresAt,valid:Date.parse(q.expiresAt)>Date.now()}}
export function toOrderProjection(p:ProjectProjection):OrderProjection{return{projectionId:`project:${p.orderRef}:${p.projectionVersion}`,orderRef:p.orderRef,projectionVersion:p.projectionVersion,generatedAt:new Date().toISOString(),terminal:p.allowedActions.length===0,stateCode:p.orderState,facts:{payment:fact(p.facts.payment),eligibility:'CONFIRMED',upstream:fact(p.facts.upstreamDebit),delivery:fact(p.facts.delivery),refund:fact(p.facts.refund),accounting:'UNKNOWN'},total:{minor:p.priceSnapshot.totalAmountMinor,currency:p.priceSnapshot.currency==='BDT'?'BDT':'CNY'},faceValue:null,priceSnapshotRef:p.quoteRef,priceValidUntil:p.priceSnapshot.expiresAt,maskedPhone:p.priceSnapshot.maskedPhone,operatorName:p.priceSnapshot.operatorCode,productName:p.priceSnapshot.productCode,priceVersion:p.quoteRef,allowedActions:p.allowedActions.map(a=>({actionCode:a.actionCode==='WAIT_OR_CONTACT_SUPPORT'?'CONTACT_SUPPORT':'REFRESH',enabled:true,expectedProjectionVersion:a.expectedProjectionVersion})),supportRef:null,nextPollAt:null}}
