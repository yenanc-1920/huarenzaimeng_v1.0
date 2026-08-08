import type { ProjectSessionProjection } from '../domain/types.ts'

export const P021_BACKEND_IMPLEMENTATION_SHA='C4A8D77A5422354C546473DAF013893952349A447349B3E8CC9F89A884CF0ADE'
export const P021_D3_03_SHA='33CCC6BB94B2D2A95E798C7CAD44C839CC35D8B577727492C64F10776E6FDA95'
export const P021_D3_04_SHA='B12E780F19AB333458018C908324EADA6F84E1ECD35349FB739A7DC5AE22E4DB'
export const P021_D3_05_SHA='CC27E94902FF2BCE86E02EE553F2E03F7E7752603AD247D826E619F929856282'
export const P021_D3_REGISTRY_SHA='6321D16CAC17D426801F26F5AD643BAB418D08FF72FD299BCEA7FB20C9522A0C'

export const P021_STATE_CODES=['AWAITING_PAYMENT','PAYMENT_PROCESSING','PAID_AWAITING_TOPUP','TOPUP_PROCESSING','TOPUP_RESULT_UNKNOWN','DELIVERED','CONFIRMED_NOT_DELIVERED','REFUND_PROCESSING','REFUNDED','DELIVERY_REFUND_CONFLICT_REVIEW','SUPPORT_REVIEW'] as const
export type P021StateCode=typeof P021_STATE_CODES[number]
export type P021ProjectCode='ORDER_DETAIL_READ'|'ORDER_DETAIL_NOT_AVAILABLE'|'ORDER_DETAIL_READ_ERROR'
export type P021UserItemCode='PAYMENT_CONFIRMATION'|'TOPUP_RESULT'|'DELIVERY_RESULT'|'REFUND_RESULT'|'ACCOUNTING_REVIEW'
export type P021ResponsibilityCode='NONE'|'USER_PAYMENT'|'SYSTEM_RECHECK'|'SUPPORT_REVIEW'
export type P021ActionCode='REFRESH_ORDER_DETAIL'|'OPEN_SUPPORT'|'SAFE_BACK'

export interface P021PriceSnapshotSummary { priceSnapshotRef:string;totalMinor:number;currency:string;displayVersion:string;maskedTarget:string;brandDisplayName:string;productDisplayName:string;targetValueDisplay:string;targetCurrency:string;validUntil:string }
export interface P021TimelineItem { timelineItemRef:string;sequence:number;projectionVersion:number;stateCode:P021StateCode;occurredAt:string|null;userMessageCode:string }
export interface P021AllowedAction { actionCode:P021ActionCode;enabled:true;actionBindingVersion:number;supportRef:string|null }
export interface P021Projection { orderRef:string;aggregateVersion:number;projectionVersion:number;stateCode:P021StateCode;priceSnapshotSummary:P021PriceSnapshotSummary;confirmedItems:P021UserItemCode[];unknownItems:P021UserItemCode[];responsibilityCode:P021ResponsibilityCode;updatedAt:string;nextReviewPoint:string|null;timeline:P021TimelineItem[];allowedActions:P021AllowedAction[];supportRef:string|null }
export interface P021Response { requestRef:string|null;outcome:'ACCEPTED'|'REJECTED';projectCode:P021ProjectCode;resourceRef:string|null;aggregateVersion:number|null;currentProjection:P021Projection|null;retryClass:'NONE'|'READ_SAFE';nextPollAt:null }

const object=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null&&!Array.isArray(value)
const exactKeys=(value:Record<string,unknown>,keys:readonly string[])=>Object.keys(value).length===keys.length&&Object.keys(value).every(key=>keys.includes(key))
const text=(value:unknown):value is string=>typeof value==='string'&&value.length>0
const positiveInteger=(value:unknown):value is number=>typeof value==='number'&&Number.isInteger(value)&&value>0
const nonNegativeInteger=(value:unknown):value is number=>typeof value==='number'&&Number.isInteger(value)&&value>=0
const nullableText=(value:unknown):value is string|null=>value===null||text(value)
const RFC3339=/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})$/
export const isP021Instant=(value:unknown):value is string=>{
  if(!text(value))return false
  const match=RFC3339.exec(value);if(!match)return false
  const year=Number(match[1]),month=Number(match[2]),day=Number(match[3]),hour=Number(match[4]),minute=Number(match[5]),second=Number(match[6]),offset=match[7]
  const maxDay=month>=1&&month<=12?new Date(Date.UTC(year,month,0)).getUTCDate():0
  if(day<1||day>maxDay||hour>23||minute>59||second>59)return false
  if(offset!=='Z'&&(Number(offset.slice(1,3))>23||Number(offset.slice(4,6))>59))return false
  return true
}
const nullableInstant=(value:unknown):value is string|null=>value===null||isP021Instant(value)
const opaque=(value:unknown):value is string=>text(value)&&/^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/.test(value)
const stateCode=(value:unknown):value is P021StateCode=>typeof value==='string'&&P021_STATE_CODES.includes(value as P021StateCode)
const itemCodes:readonly P021UserItemCode[]=['PAYMENT_CONFIRMATION','TOPUP_RESULT','DELIVERY_RESULT','REFUND_RESULT','ACCOUNTING_REVIEW']
const itemCode=(value:unknown):value is P021UserItemCode=>typeof value==='string'&&itemCodes.includes(value as P021UserItemCode)
const responsibilities:readonly P021ResponsibilityCode[]=['NONE','USER_PAYMENT','SYSTEM_RECHECK','SUPPORT_REVIEW']
const responsibilityCode=(value:unknown):value is P021ResponsibilityCode=>typeof value==='string'&&responsibilities.includes(value as P021ResponsibilityCode)
const supportStates:readonly P021StateCode[]=['TOPUP_RESULT_UNKNOWN','CONFIRMED_NOT_DELIVERED','DELIVERY_REFUND_CONFLICT_REVIEW','SUPPORT_REVIEW']

function parsePrice(value:unknown):P021PriceSnapshotSummary{
  const keys=['priceSnapshotRef','totalMinor','currency','displayVersion','maskedTarget','brandDisplayName','productDisplayName','targetValueDisplay','targetCurrency','validUntil']
  if(!object(value)||!exactKeys(value,keys)||!opaque(value.priceSnapshotRef)||!nonNegativeInteger(value.totalMinor)
    ||typeof value.currency!=='string'||!/^[A-Z]{3}$/.test(value.currency)||!text(value.displayVersion)||!text(value.maskedTarget)
    ||!/^[+*0-9 .()\-]+$/.test(value.maskedTarget)||((value.maskedTarget.match(/\*/g)||[]).length<4)
    ||((value.maskedTarget.match(/\d/g)||[]).length<1)||((value.maskedTarget.match(/\d/g)||[]).length>4)
    ||!text(value.brandDisplayName)||!text(value.productDisplayName)||!text(value.targetValueDisplay)
    ||typeof value.targetCurrency!=='string'||!/^[A-Z]{3}$/.test(value.targetCurrency)||!isP021Instant(value.validUntil))throw new Error('INVALID_P021_PRICE_SNAPSHOT')
  return value as unknown as P021PriceSnapshotSummary
}

function parseTimeline(value:unknown,currentVersion:number,currentState:P021StateCode):P021TimelineItem[]{
  if(!Array.isArray(value)||value.length===0)throw new Error('INVALID_P021_TIMELINE')
  const refs=new Set<string>();let lastVersion=0;let lastState:P021StateCode|null=null
  const items=value.map((raw,index)=>{
    const keys=['timelineItemRef','sequence','projectionVersion','stateCode','occurredAt','userMessageCode']
    if(!object(raw)||!exactKeys(raw,keys)||!opaque(raw.timelineItemRef)||refs.has(raw.timelineItemRef)||raw.sequence!==index+1
      ||!positiveInteger(raw.projectionVersion)||raw.projectionVersion<lastVersion||raw.projectionVersion>currentVersion
      ||!stateCode(raw.stateCode)||!nullableInstant(raw.occurredAt)||!text(raw.userMessageCode)
      ||(raw.projectionVersion===lastVersion&&lastState!==null&&raw.stateCode!==lastState))throw new Error('INVALID_P021_TIMELINE')
    refs.add(raw.timelineItemRef);lastVersion=raw.projectionVersion;lastState=raw.stateCode
    return raw as unknown as P021TimelineItem
  })
  const last=items[items.length-1]
  if(last.projectionVersion!==currentVersion||last.stateCode!==currentState)throw new Error('P021_TIMELINE_CURRENT_MISMATCH')
  return items
}

function parseActions(value:unknown,projectionVersion:number,state:P021StateCode,supportRef:string|null):P021AllowedAction[]{
  const shouldSupport=supportStates.includes(state)
  const expected:P021ActionCode[]=shouldSupport?['REFRESH_ORDER_DETAIL','OPEN_SUPPORT','SAFE_BACK']:['REFRESH_ORDER_DETAIL','SAFE_BACK']
  if(!Array.isArray(value)||value.length!==expected.length||(shouldSupport?!opaque(supportRef):supportRef!==null))throw new Error('INVALID_P021_ACTION_SET')
  return value.map((raw,index)=>{
    const keys=['actionCode','enabled','actionBindingVersion','supportRef']
    if(!object(raw)||!exactKeys(raw,keys)||raw.actionCode!==expected[index]||raw.enabled!==true||raw.actionBindingVersion!==projectionVersion
      ||(raw.actionCode==='OPEN_SUPPORT'?raw.supportRef!==supportRef:raw.supportRef!==null))throw new Error('INVALID_P021_ALLOWED_ACTION')
    return raw as unknown as P021AllowedAction
  })
}

function parseProjection(value:unknown):P021Projection{
  const keys=['orderRef','aggregateVersion','projectionVersion','stateCode','priceSnapshotSummary','confirmedItems','unknownItems','responsibilityCode','updatedAt','nextReviewPoint','timeline','allowedActions','supportRef']
  if(!object(value)||!exactKeys(value,keys)||!opaque(value.orderRef)||!positiveInteger(value.aggregateVersion)||!positiveInteger(value.projectionVersion)
    ||!stateCode(value.stateCode)||!Array.isArray(value.confirmedItems)||!Array.isArray(value.unknownItems)
    ||!value.confirmedItems.every(itemCode)||!value.unknownItems.every(itemCode)||new Set(value.confirmedItems).size!==value.confirmedItems.length
    ||new Set(value.unknownItems).size!==value.unknownItems.length||value.confirmedItems.some(item=>value.unknownItems.includes(item))
    ||!responsibilityCode(value.responsibilityCode)||!isP021Instant(value.updatedAt)||!nullableInstant(value.nextReviewPoint)||!nullableText(value.supportRef))throw new Error('INVALID_P021_PROJECTION')
  const priceSnapshotSummary=parsePrice(value.priceSnapshotSummary)
  const timeline=parseTimeline(value.timeline,value.projectionVersion,value.stateCode)
  const allowedActions=parseActions(value.allowedActions,value.projectionVersion,value.stateCode,value.supportRef)
  return {...value,priceSnapshotSummary,timeline,allowedActions} as unknown as P021Projection
}

export function parseP021Response(value:unknown):P021Response{
  const keys=['requestRef','outcome','projectCode','resourceRef','aggregateVersion','currentProjection','retryClass','nextPollAt']
  if(!object(value)||!exactKeys(value,keys)||!nullableText(value.requestRef)||value.nextPollAt!==null)throw new Error('INVALID_P021_RESPONSE')
  if(value.projectCode==='ORDER_DETAIL_READ'){
    if(value.outcome!=='ACCEPTED'||value.retryClass!=='NONE'||!opaque(value.resourceRef)||!positiveInteger(value.aggregateVersion)||value.currentProjection===null)throw new Error('INVALID_P021_ACCEPTED_RESPONSE')
    const currentProjection=parseProjection(value.currentProjection)
    if(currentProjection.orderRef!==value.resourceRef||currentProjection.aggregateVersion!==value.aggregateVersion)throw new Error('P021_RESPONSE_BINDING_MISMATCH')
    return {...value,currentProjection} as unknown as P021Response
  }
  const unavailable=value.projectCode==='ORDER_DETAIL_NOT_AVAILABLE'&&value.retryClass==='NONE'
  const readError=value.projectCode==='ORDER_DETAIL_READ_ERROR'&&value.retryClass==='READ_SAFE'
  if(value.outcome!=='REJECTED'||(!unavailable&&!readError)||value.resourceRef!==null||value.aggregateVersion!==null||value.currentProjection!==null)throw new Error('INVALID_P021_REJECTED_RESPONSE')
  return value as unknown as P021Response
}

export function validP021BuyerSession(session:ProjectSessionProjection,orderRef:string,now=Date.now()):boolean{
  return session.role==='BUYER'&&text(session.projectSubjectRef)&&session.sessionVersion>0&&text(session.authorizationSetRef)&&text(session.authorizationEvidenceVersion)
    &&session.authorizedOrderRefs.includes(orderRef)&&new Set(session.authorizedOrderRefs).size===session.authorizedOrderRefs.length
    &&isP021Instant(session.issuedAt)&&isP021Instant(session.expiresAt)&&Date.parse(session.issuedAt)<Date.parse(session.expiresAt)&&Date.parse(session.expiresAt)>now
}

export function sameP021SessionBinding(left:ProjectSessionProjection,right:ProjectSessionProjection,orderRef:string,now=Date.now()):boolean{
  return validP021BuyerSession(left,orderRef,now)&&validP021BuyerSession(right,orderRef,now)
    &&left.projectSubjectRef===right.projectSubjectRef&&left.sessionVersion===right.sessionVersion
    &&left.authorizationSetRef===right.authorizationSetRef&&left.authorizationEvidenceVersion===right.authorizationEvidenceVersion
    &&left.issuedAt===right.issuedAt&&left.expiresAt===right.expiresAt
    &&left.authorizedOrderRefs.length===right.authorizedOrderRefs.length&&left.authorizedOrderRefs.every(ref=>right.authorizedOrderRefs.includes(ref))
}
