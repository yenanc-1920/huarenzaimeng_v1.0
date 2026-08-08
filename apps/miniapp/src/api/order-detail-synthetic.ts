import { parseP021Response, type P021Response, type P021StateCode } from './order-detail-contract.ts'

const supportStates:P021StateCode[]=['TOPUP_RESULT_UNKNOWN','CONFIRMED_NOT_DELIVERED','DELIVERY_REFUND_CONFLICT_REVIEW','SUPPORT_REVIEW']
export function p021SyntheticResponse(orderRef:string,stateCode:P021StateCode='TOPUP_RESULT_UNKNOWN',projectionVersion=2):P021Response{
  const supportRef=supportStates.includes(stateCode)?'SUPPORT-P021-SYN-1':null
  const confirmedItems=stateCode==='DELIVERED'?['PAYMENT_CONFIRMATION','TOPUP_RESULT','DELIVERY_RESULT']
    :stateCode==='REFUNDED'?['PAYMENT_CONFIRMATION','REFUND_RESULT']
      :stateCode==='AWAITING_PAYMENT'||stateCode==='PAYMENT_PROCESSING'?[]:['PAYMENT_CONFIRMATION']
  const unknownItems=stateCode==='DELIVERED'||stateCode==='REFUNDED'||stateCode==='AWAITING_PAYMENT'?[]
    :stateCode==='PAYMENT_PROCESSING'?['PAYMENT_CONFIRMATION']:['TOPUP_RESULT']
  const actions=[{actionCode:'REFRESH_ORDER_DETAIL',enabled:true,actionBindingVersion:projectionVersion,supportRef:null},...(supportRef?[{actionCode:'OPEN_SUPPORT',enabled:true,actionBindingVersion:projectionVersion,supportRef}]:[]),{actionCode:'SAFE_BACK',enabled:true,actionBindingVersion:projectionVersion,supportRef:null}]
  return parseP021Response({requestRef:null,outcome:'ACCEPTED',projectCode:'ORDER_DETAIL_READ',resourceRef:orderRef,aggregateVersion:projectionVersion,currentProjection:{
    orderRef,aggregateVersion:projectionVersion,projectionVersion,stateCode,
    priceSnapshotSummary:{priceSnapshotRef:'PRICE-P021-SYN-1',totalMinor:125000,currency:'BDT',displayVersion:'DISPLAY-V1',maskedTarget:'******1234',brandDisplayName:'SYN Operator',productDisplayName:'SYN Package',targetValueDisplay:'1000 BDT',targetCurrency:'BDT',validUntil:'2099-08-03T01:00:00Z'},
    confirmedItems,unknownItems,responsibilityCode:supportRef?'SUPPORT_REVIEW':stateCode==='AWAITING_PAYMENT'?'USER_PAYMENT':'SYSTEM_RECHECK',updatedAt:'2026-08-03T00:00:00Z',nextReviewPoint:null,
    timeline:[{timelineItemRef:`TIMELINE-P021-${projectionVersion}`,sequence:1,projectionVersion,stateCode,occurredAt:'2026-08-03T00:00:00Z',userMessageCode:`ORDER_STATUS_${stateCode}`}],allowedActions:actions,supportRef
  },retryClass:'NONE',nextPollAt:null})
}
export const p021BuiltinSynthetic={read:async(orderRef:string)=>p021SyntheticResponse(orderRef)}
