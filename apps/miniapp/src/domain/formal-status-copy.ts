import type { FactState } from './types'
import type { P014FactState, P014StateCode } from '../api/p014-topup-contract'
export type ReadOnlyStatusCopy = Readonly<{ label: string; unknown: boolean }>
const unknown = (label='暂时无法确认'):ReadOnlyStatusCopy=>Object.freeze({label,unknown:true})
const known = (label:string):ReadOnlyStatusCopy=>Object.freeze({label,unknown:false})
export function projectOrderStateCopy(value:string|null|undefined):ReadOnlyStatusCopy {
  const labels:Record<string,string>={CREATED:'等待付款',PAYMENT_PENDING:'等待付款',PAID:'付款成功',PROVIDER_PROCESSING:'充值处理中',FULFILLED:'充值成功',FAILED:'处理失败',REFUNDED:'已退款',CANCELLED:'已取消',AWAITING_PAYMENT:'等待付款',PAYMENT_PROCESSING:'支付确认中',PAID_AWAITING_TOPUP:'等待充值',TOPUP_PROCESSING:'充值处理中',DELIVERED:'充值已到账',REFUND_PROCESSING:'退款处理中',CONFIRMED_NOT_DELIVERED:'确认未到账',DELIVERY_REFUND_CONFLICT_REVIEW:'状态复核中',SUPPORT_REVIEW:'人工复核中'}
  return value&&labels[value]?known(labels[value]):unknown()
}
export function factStateCopy(value:FactState|string|null|undefined):ReadOnlyStatusCopy {
  const labels:Record<string,string>={CONFIRMED:'已确认',NOT_STARTED:'尚未开始',PENDING:'处理中',CONFLICT:'正在复核'}
  return value&&labels[value]?known(labels[value]):unknown()
}
export function p014StateCopy(value:P014StateCode|string|null|undefined):ReadOnlyStatusCopy {
  const labels:Record<string,string>={PAID_AWAITING_TOPUP:'等待充值',TOPUP_PROCESSING:'充值处理中',CONFIRMED_NOT_DELIVERED:'确认未到账',DELIVERED:'充值已到账',SUPPORT_REVIEW:'人工复核中'}
  return value&&labels[value]?known(labels[value]):unknown('充值结果暂时无法确认')
}
export function p014FactStateCopy(value:P014FactState|string|null|undefined):ReadOnlyStatusCopy {
  const labels:Record<string,string>={NOT_OBSERVED:'等待结果',CONFIRMED:'已确认',CONFLICT:'正在复核',ABSENT_CONFIRMED:'确认未到账'}
  return value&&labels[value]?known(labels[value]):unknown()
}
