import { parseP014Response, type P014CreateCommand, type P014OriginalResultQuery, type P014Projection, type P014Response } from './p014-topup-contract.ts'

const now='2026-08-03T00:00:00Z'
const price={priceSnapshotRef:'PS-P014-SYN-001',totalMinor:125000,currency:'BDT',displayVersion:'DISPLAY-V1',maskedRecipientNumber:'******1234',operatorDisplayName:'示例运营商',productDisplayName:'示例充值套餐',targetFaceValueMinor:100000,targetCurrency:'BDT',expiresAt:'2099-08-03T01:00:00Z'}
const action=(actionCode:string,projectionVersion:number)=>({actionCode,enabled:true,expectedProjectionVersion:projectionVersion,actionBindingVersion:`BIND-P014-${projectionVersion}-${actionCode}`})
const facts=[
  {factCode:'PAYMENT',state:'CONFIRMED',occurredAt:now,observedAt:now},
  {factCode:'UPSTREAM_DEBIT',state:'NOT_OBSERVED',occurredAt:now,observedAt:now},
  {factCode:'DELIVERY',state:'UNKNOWN',occurredAt:now,observedAt:now},
  {factCode:'ACCOUNTING_CLOSURE',state:'NOT_OBSERVED',occurredAt:now,observedAt:now},
]
function projection(created:boolean):P014Projection {
  const projectionVersion=created?2:1
  return parseP014Response({requestRef:null,outcome:'ACCEPTED',projectCode:'TOPUP_PROGRESS_READ',resourceRef:created?'topup_p014_builtin_001':null,aggregateVersion:projectionVersion,
    currentProjection:{orderRef:'ORDER-P014-SYN-001',stateCode:created?'TOPUP_PROCESSING':'PAID_AWAITING_TOPUP',schemaVersion:'P014_TOPUP_PROGRESS_V1',projectionVersion,aggregateVersion:projectionVersion,
      priceSnapshotSummary:price,topupIntentRef:created?'topup_p014_builtin_001':null,dispatchIntentRef:created?'dispatch_p014_builtin_001':null,
      paymentConfirmationState:'CONFIRMED',upstreamDebitState:'NOT_OBSERVED',deliveryState:'UNKNOWN',accountingClosureState:'NOT_OBSERVED',
      progressSummary:{userMessageCode:created?'TOPUP_PROCESSING_DELIVERY_UNCONFIRMED':'PAYMENT_CONFIRMED_READY_FOR_TOPUP',confirmedItems:['PAYMENT'],unknownItems:['UPSTREAM_DEBIT','DELIVERY','ACCOUNTING_CLOSURE'],responsibilityCode:created?'SYSTEM_RECHECK':'NONE',supportRef:null,updatedAt:now,nextReviewPoint:created?'2026-08-03T00:05:00Z':null},
      factTimeline:facts,allowedActions:(created?['QUERY_ORIGINAL_TOPUP','REFRESH_ORDER_PROJECTION','OPEN_SUPPORT','SAFE_LEAVE']:['CREATE_LOCAL_SYNTHETIC_TOPUP','REFRESH_ORDER_PROJECTION','OPEN_SUPPORT','SAFE_LEAVE']).map(code=>action(code,projectionVersion))},retryClass:'NONE',nextPollAt:null}).currentProjection!
}

const createdOrders=new Set<string>()
export const p014BuiltinSynthetic=Object.freeze({
  async create(orderRef:string,command:P014CreateCommand):Promise<P014Response>{
    if(orderRef!=='ORDER-P014-SYN-001'||command.topupCreationPrecondition!=='TOPUP_INTENT_MUST_NOT_EXIST') return parseP014Response({requestRef:command.commandId,outcome:'REJECTED',projectCode:'TOPUP_NOT_AVAILABLE',resourceRef:null,aggregateVersion:null,currentProjection:null,retryClass:'NONE',nextPollAt:null})
    const replay=createdOrders.has(orderRef);createdOrders.add(orderRef);const currentProjection=projection(true)
    return parseP014Response({requestRef:command.commandId,outcome:'ACCEPTED',projectCode:replay?'TOPUP_INTENT_REPLAYED':'TOPUP_INTENT_CREATED',resourceRef:currentProjection.topupIntentRef,aggregateVersion:currentProjection.aggregateVersion,currentProjection,retryClass:'NONE',nextPollAt:null})
  },
  async result(orderRef:string,query:P014OriginalResultQuery):Promise<P014Response>{
    if(orderRef!=='ORDER-P014-SYN-001'||!createdOrders.has(orderRef)) return parseP014Response({requestRef:query.commandId,outcome:'UNKNOWN',projectCode:'TOPUP_INTENT_RESULT_UNKNOWN',resourceRef:null,aggregateVersion:null,currentProjection:null,retryClass:'SAME_ACTION_QUERY_ONLY',nextPollAt:null})
    const currentProjection=projection(true)
    return parseP014Response({requestRef:query.commandId,outcome:'ACCEPTED',projectCode:'TOPUP_INTENT_RESULT_FOUND',resourceRef:currentProjection.topupIntentRef,aggregateVersion:currentProjection.aggregateVersion,currentProjection,retryClass:'NONE',nextPollAt:null})
  },
  async progress(orderRef:string):Promise<P014Response>{
    if(orderRef!=='ORDER-P014-SYN-001') return parseP014Response({requestRef:null,outcome:'REJECTED',projectCode:'TOPUP_PROGRESS_NOT_AVAILABLE',resourceRef:null,aggregateVersion:null,currentProjection:null,retryClass:'NONE',nextPollAt:null})
    const currentProjection=projection(createdOrders.has(orderRef))
    return parseP014Response({requestRef:null,outcome:'ACCEPTED',projectCode:'TOPUP_PROGRESS_READ',resourceRef:currentProjection.topupIntentRef,aggregateVersion:currentProjection.aggregateVersion,currentProjection,retryClass:'NONE',nextPollAt:null})
  },
  resetForContractTest(){createdOrders.clear()},
})
