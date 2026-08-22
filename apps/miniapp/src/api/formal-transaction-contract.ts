export type WechatPrepayParameters = Readonly<{
  timeStamp:string;nonceStr:string;package:string;signType:'RSA';paySign:string
}>

export type PaymentState='NEW'|'PREPAY_CREATED'|'PROCESSING'|'PAID'|'CLOSED'|'REFUND_PROCESSING'|'REFUNDED'|'REJECTED'|'UNKNOWN'
export type RefundState='PENDING'|'SUCCEEDED'|'REJECTED'|'UNKNOWN'
export type TopupState='RESERVED'|'SUBMITTED'|'PROCESSING'|'DELIVERED'|'REJECTED'|'UNKNOWN'|'RECONCILIATION_REQUIRED'

export interface ReleaseQuoteView{
  quoteRef:string;requestRef:string;phoneMasked:string;operatorCode:string
  productRef:string;priceVersionRef:string;finalAmountCny:number;currency:string;catalogVersion:number
  validUntil:string;snapshotDigest:string
}
export interface ReleaseOrderView{
  orderRef:string;quoteRef:string;requestRef:string;snapshotDigest:string;orderState:string
  paymentState:string;deliveryState:string;refundState:string;projectionVersion:number;aggregateVersion:number
}
export interface ReleaseOrderProjection{
  orderRef:string;quoteRef:string;orderState:string;paymentState:string;upstreamDebitState:string
  deliveryState:string;refundState:string;totalAmountMinor:number;currency:string
  projectionVersion:number;aggregateVersion:number;nextAction:string
}
export interface PaymentView{
  orderRef:string;state:PaymentState;providerRef:string|null;amountMinor:number;currency:string;refundedMinor:number
  version:number;prepayParameters:WechatPrepayParameters|null
}
export interface RefundView{orderRef:string;refundRef:string;state:RefundState;amountMinor:number}
export interface TopupView{orderRef:string;requestRef:string;state:TopupState;providerRef:string|null;evidenceRef:string|null;version:number}

const object=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null&&!Array.isArray(value)
const text=(value:unknown):value is string=>typeof value==='string'&&value.length>0
const nullableText=(value:unknown):value is string|null=>value===null||text(value)
const positive=(value:unknown):value is number=>Number.isInteger(value)&&Number(value)>0
const nonNegative=(value:unknown):value is number=>Number.isInteger(value)&&Number(value)>=0
const exact=(value:Record<string,unknown>,keys:readonly string[])=>Object.keys(value).length===keys.length&&Object.keys(value).every(key=>keys.includes(key))
const instant=(value:unknown):value is string=>text(value)&&Number.isFinite(Date.parse(value))
const digest=(value:unknown):value is string=>typeof value==='string'&&/^[a-f0-9]{64}$/.test(value)
const currency=(value:unknown):value is string=>typeof value==='string'&&/^[A-Z]{3}$/.test(value)
const parseWechatPrepayParameters=(value:unknown):WechatPrepayParameters=>{
  const keys=['timeStamp','nonceStr','package','signType','paySign'] as const
  if(!object(value)||!exact(value,keys)||!text(value.timeStamp)||!/^\d+$/.test(value.timeStamp)
    ||!text(value.nonceStr)||!text(value.package)||!value.package.startsWith('prepay_id=')
    ||value.signType!=='RSA'||!text(value.paySign))throw new Error('WECHAT_PREPAY_PARAMETERS_INVALID')
  return Object.freeze({timeStamp:value.timeStamp,nonceStr:value.nonceStr,package:value.package,signType:'RSA',paySign:value.paySign})
}
const PAYMENT_STATES:readonly PaymentState[]=['NEW','PREPAY_CREATED','PROCESSING','PAID','CLOSED','REFUND_PROCESSING','REFUNDED','REJECTED','UNKNOWN']
const REFUND_STATES:readonly RefundState[]=['PENDING','SUCCEEDED','REJECTED','UNKNOWN']
const TOPUP_STATES:readonly TopupState[]=['RESERVED','SUBMITTED','PROCESSING','DELIVERED','REJECTED','UNKNOWN','RECONCILIATION_REQUIRED']

export function parseReleaseQuoteView(value:unknown):ReleaseQuoteView{
  const keys=['quoteRef','requestRef','phoneMasked','operatorCode','productRef','priceVersionRef','finalAmountCny','currency','catalogVersion','validUntil','snapshotDigest']
  if(!object(value)||!exact(value,keys)||!text(value.quoteRef)||!text(value.requestRef)
    ||!text(value.phoneMasked)||!text(value.operatorCode)||!text(value.productRef)||!text(value.priceVersionRef)
    ||typeof value.finalAmountCny!=='number'||!Number.isFinite(value.finalAmountCny)||value.finalAmountCny<=0||!currency(value.currency)
    ||!positive(value.catalogVersion)||!instant(value.validUntil)||!digest(value.snapshotDigest))throw new Error('INVALID_RELEASE_QUOTE_DTO')
  return value as unknown as ReleaseQuoteView
}

export function parseReleaseOrderView(value:unknown):ReleaseOrderView{
  const keys=['orderRef','quoteRef','requestRef','snapshotDigest','orderState','paymentState','deliveryState','refundState','projectionVersion','aggregateVersion']
  if(!object(value)||!exact(value,keys)||!text(value.orderRef)||!text(value.quoteRef)||!text(value.requestRef)
    ||!digest(value.snapshotDigest)||!text(value.orderState)||!text(value.paymentState)||!text(value.deliveryState)||!text(value.refundState)
    ||!positive(value.projectionVersion)||!positive(value.aggregateVersion))throw new Error('INVALID_RELEASE_ORDER_DTO')
  return value as unknown as ReleaseOrderView
}

export function parseReleaseOrderProjection(value:unknown):ReleaseOrderProjection{
  const keys=['orderRef','quoteRef','orderState','paymentState','upstreamDebitState','deliveryState','refundState','totalAmountMinor','currency','projectionVersion','aggregateVersion','nextAction']
  if(!object(value)||!exact(value,keys)||!text(value.orderRef)||!text(value.quoteRef)||!text(value.orderState)
    ||!text(value.paymentState)||!text(value.upstreamDebitState)||!text(value.deliveryState)||!text(value.refundState)
    ||!positive(value.totalAmountMinor)||!currency(value.currency)||!positive(value.projectionVersion)
    ||!positive(value.aggregateVersion)||!text(value.nextAction))throw new Error('INVALID_RELEASE_ORDER_PROJECTION_DTO')
  return value as unknown as ReleaseOrderProjection
}

export function parsePaymentView(value:unknown):PaymentView{
  const base=['orderRef','state','providerRef','amountMinor','currency','refundedMinor','version','prepayParameters'] as const
  if(!object(value)||!exact(value,base)||!text(value.orderRef)
    ||!PAYMENT_STATES.includes(value.state as PaymentState)||!nullableText(value.providerRef)||!positive(value.amountMinor)
    ||!currency(value.currency)||!nonNegative(value.refundedMinor)||!positive(value.version)
    ||!(value.prepayParameters===null||object(value.prepayParameters)))throw new Error('INVALID_PAYMENT_VIEW_DTO')
  let prepay:WechatPrepayParameters|null=null
  try{prepay=value.prepayParameters===null?null:parseWechatPrepayParameters(value.prepayParameters)}catch{throw new Error('INVALID_PAYMENT_VIEW_DTO')}
  return{orderRef:value.orderRef,state:value.state as PaymentState,providerRef:value.providerRef as string|null,
    amountMinor:value.amountMinor,currency:value.currency,refundedMinor:value.refundedMinor,version:value.version,prepayParameters:prepay}
}

export function parseRefundView(value:unknown):RefundView{
  const keys=['orderRef','refundRef','state','amountMinor']
  if(!object(value)||!exact(value,keys)||!text(value.orderRef)||!text(value.refundRef)
    ||!REFUND_STATES.includes(value.state as RefundState)||!positive(value.amountMinor))throw new Error('INVALID_REFUND_VIEW_DTO')
  return value as unknown as RefundView
}

export function parseTopupView(value:unknown):TopupView{
  const keys=['orderRef','requestRef','state','providerRef','evidenceRef','version']
  if(!object(value)||!exact(value,keys)||!text(value.orderRef)||!text(value.requestRef)||!TOPUP_STATES.includes(value.state as TopupState)
    ||!nullableText(value.providerRef)||!nullableText(value.evidenceRef)||!positive(value.version))throw new Error('INVALID_TOPUP_VIEW_DTO')
  return value as unknown as TopupView
}

export const paymentIsTrustedPaid=(value:PaymentView)=>value.state==='PAID'
export const topupMayQueryOriginal=(value:TopupView)=>value.state==='UNKNOWN'||value.state==='PROCESSING'
