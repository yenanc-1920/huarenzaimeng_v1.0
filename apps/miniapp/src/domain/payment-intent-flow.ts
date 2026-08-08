import type { PaymentIntentQueryResult, PaymentIntentResult } from '../api/payment-intent-contract'

export type PaymentIntentAttemptOutcome = 'ACCEPTED' | 'REJECTED' | 'UNKNOWN' | 'NETWORK_INTERRUPTED' | 'CLIENT_FAIL_CLOSED'

export interface PaymentIntentAttemptEvidence {
  subcaseId: string
  outcome: PaymentIntentAttemptOutcome
  apiCallCount: number
  readOnlyQueryCount: number
  navigationCount: number
  automaticRetryCount: number
  successProjectionCount: number
  priceSnapshotCount: number
}

export interface PaymentIntentAttemptDependencies {
  createPaymentIntent: () => Promise<PaymentIntentResult>
  acceptProjection: (result: PaymentIntentResult) => void | Promise<void>
  navigate: () => void | Promise<void>
  automaticRetry: () => void | Promise<void>
}

export interface PaymentIntentWriteLatch { isLocked():boolean; tryLock():boolean }
export function createPaymentIntentWriteLatch():PaymentIntentWriteLatch{
  let locked=false
  return{isLocked:()=>locked,tryLock:()=>{if(locked)return false;locked=true;return true}}
}

export type P013RejectedCode = 'PAYMENT_INTENT_NOT_AVAILABLE' | 'IDEMPOTENCY_CONFLICT'
export interface P013RejectedPageProjection {
  viewState:'REJECTED'
  title:'暂时不能继续'
  message:'当前访问、报价或订单状态不满足继续条件；尚未付款，请查询最新订单或离开本页。'
  visibleActions:['查询最新订单','返回']
  writeButtonVisible:false
  originalRequestQueryVisible:false
  latestOrderQueryVisible:true
  safeLeaveVisible:true
}

export function buildP013RejectedPageProjection(code:P013RejectedCode):P013RejectedPageProjection{
  if(code!=='PAYMENT_INTENT_NOT_AVAILABLE'&&code!=='IDEMPOTENCY_CONFLICT')throw new Error('UNSUPPORTED_P013_REJECTION')
  return{viewState:'REJECTED',title:'暂时不能继续',
    message:'当前访问、报价或订单状态不满足继续条件；尚未付款，请查询最新订单或离开本页。',
    visibleActions:['查询最新订单','返回'],writeButtonVisible:false,originalRequestQueryVisible:false,
    latestOrderQueryVisible:true,safeLeaveVisible:true}
}

export interface P013AcceptedPageProjection {
  pageId:'UX-P013'
  viewState:'CREATED'|'REPLAYED'
  orderRef:string
  stateCode:'AWAITING_PAYMENT'
  heading:'尚未付款'
  message:'付款准备已完成，尚未发起真实付款，尚未确认收款。'|'已查询到原付款准备结果，没有重复处理；尚未发起真实付款。'
  priceSnapshot:PaymentIntentResult['currentProjection']['priceSnapshot']
  allowedActions:PaymentIntentResult['currentProjection']['allowedActions']
  visibleActions:['查询最新结果','返回']
  automaticNavigationTarget:null
  writeButtonVisible:false
  originalIntentQueryVisible:true
  safeLeaveVisible:true
  p014Visible:false
}

export function buildP013AcceptedPageProjection(result:PaymentIntentResult):P013AcceptedPageProjection{
  const projection=result.currentProjection
  if(projection.stateCode!=='AWAITING_PAYMENT'||projection.paymentInitiated!==false||projection.paymentConfirmed!==false
      ||projection.allowedActions.length!==1||projection.allowedActions[0].actionCode!=='QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT'){
    throw new Error('INVALID_P013_ACCEPTED_PROJECTION')
  }
  const replayed=result.projectCode==='PAYMENT_INTENT_REPLAYED'
  return{pageId:'UX-P013',viewState:replayed?'REPLAYED':'CREATED',orderRef:projection.orderRef,stateCode:'AWAITING_PAYMENT',
    heading:'尚未付款',message:replayed?'已查询到原付款准备结果，没有重复处理；尚未发起真实付款。':'付款准备已完成，尚未发起真实付款，尚未确认收款。',
    priceSnapshot:projection.priceSnapshot,allowedActions:projection.allowedActions,visibleActions:['查询最新结果','返回'],
    automaticNavigationTarget:null,writeButtonVisible:false,originalIntentQueryVisible:true,safeLeaveVisible:true,p014Visible:false}
}

export interface P013QueryPageProjection {
  pageId:'UX-P013'
  resultKind:'FOUND'|'UNKNOWN'|'REJECTED'|'NOT_AVAILABLE'|'ERROR'
  viewState:'REPLAYED'|'UNKNOWN'|'REJECTED'|'NETWORK_ERROR'
  title:'正在确认付款资格'|'查询最新结果'|'暂时不能继续'
  message:string
  heading:'尚未付款'
  orderRef:string|null
  stateCode:'AWAITING_PAYMENT'|null
  priceSnapshot:PaymentIntentResult['currentProjection']['priceSnapshot']|null
  allowedActions:PaymentIntentResult['currentProjection']['allowedActions']
  visibleActions:['查询最新结果','返回']|['查询最新订单','返回']
  writeButtonVisible:false
  originalIntentQueryVisible:boolean
  latestOrderQueryVisible:boolean
  safeLeaveVisible:true
  p014Visible:false
}

export function buildP013QueryPageProjection(result:PaymentIntentQueryResult):P013QueryPageProjection{
  if(result.outcome==='FOUND')return{pageId:'UX-P013',resultKind:'FOUND',viewState:'REPLAYED',title:'正在确认付款资格',
    message:'已查询到原付款准备结果；当前尚未付款，尚未确认收款。',heading:'尚未付款',orderRef:result.currentProjection.orderRef,
    stateCode:'AWAITING_PAYMENT',priceSnapshot:result.currentProjection.priceSnapshot,allowedActions:result.currentProjection.allowedActions,
    visibleActions:['查询最新结果','返回'],writeButtonVisible:false,originalIntentQueryVisible:true,latestOrderQueryVisible:false,safeLeaveVisible:true,p014Visible:false}
  if(result.outcome==='UNKNOWN')return{pageId:'UX-P013',resultKind:'UNKNOWN',viewState:'UNKNOWN',title:'查询最新结果',
    message:'原请求结果仍无法确认；当前尚未付款，不会再次提交。',heading:'尚未付款',orderRef:null,stateCode:null,priceSnapshot:null,allowedActions:[],
    visibleActions:['查询最新结果','返回'],writeButtonVisible:false,originalIntentQueryVisible:true,latestOrderQueryVisible:false,safeLeaveVisible:true,p014Visible:false}
  const unavailable=result.projectCode==='PAYMENT_INTENT_QUERY_NOT_AVAILABLE'
  return{pageId:'UX-P013',resultKind:unavailable?'NOT_AVAILABLE':'REJECTED',viewState:'REJECTED',title:'暂时不能继续',
    message:unavailable?'当前无法查询原请求结果；尚未付款，请查询最新订单或离开本页。':'原付款准备请求未完成；尚未付款，请查询最新订单或离开本页。',
    heading:'尚未付款',orderRef:null,stateCode:null,priceSnapshot:null,allowedActions:[],visibleActions:['查询最新订单','返回'],
    writeButtonVisible:false,originalIntentQueryVisible:false,latestOrderQueryVisible:true,safeLeaveVisible:true,p014Visible:false}
}

export function buildP013QueryErrorPageProjection():P013QueryPageProjection{
  return{pageId:'UX-P013',resultKind:'ERROR',viewState:'NETWORK_ERROR',title:'查询最新结果',
    message:'查询暂时失败，原请求结果仍无法确认；当前尚未付款，不会再次提交。',heading:'尚未付款',orderRef:null,stateCode:null,
    priceSnapshot:null,allowedActions:[],visibleActions:['查询最新结果','返回'],writeButtonVisible:false,originalIntentQueryVisible:true,
    latestOrderQueryVisible:false,safeLeaveVisible:true,p014Visible:false}
}

export function buildP013MissingWriteIdentityPageProjection():P013QueryPageProjection{
  return{pageId:'UX-P013',resultKind:'NOT_AVAILABLE',viewState:'REJECTED',title:'暂时不能继续',
    message:'原付款准备请求信息缺失或无法校验；当前不会查询或再次提交，请查询最新订单或安全离开本页。',heading:'尚未付款',
    orderRef:null,stateCode:null,priceSnapshot:null,allowedActions:[],visibleActions:['查询最新订单','返回'],writeButtonVisible:false,
    originalIntentQueryVisible:false,latestOrderQueryVisible:true,safeLeaveVisible:true,p014Visible:false}
}

const codeOf = (error: unknown) => error instanceof Error ? error.message : String(error)
const failureOutcome = (error: unknown): PaymentIntentAttemptOutcome => {
  const code=codeOf(error)
  if(code==='NETWORK_ERROR')return'NETWORK_INTERRUPTED'
  if(code.includes('UNKNOWN')||code==='INVALID_ENVELOPE')return'UNKNOWN'
  return'REJECTED'
}

export async function executePaymentIntentAttempt(
  subcaseId: string,
  dependencies: PaymentIntentAttemptDependencies,
): Promise<{ result: PaymentIntentResult|null; error: unknown; evidence: PaymentIntentAttemptEvidence }> {
  const evidence:PaymentIntentAttemptEvidence={subcaseId,outcome:'REJECTED',apiCallCount:0,readOnlyQueryCount:0,
    navigationCount:0,automaticRetryCount:0,successProjectionCount:0,priceSnapshotCount:0}
  let result:PaymentIntentResult|null=null
  // These injected functions are counters for proving that this slice never navigates or retries automatically.
  void dependencies.navigate
  void dependencies.automaticRetry
  try{
    evidence.apiCallCount++
    result=await dependencies.createPaymentIntent()
    await dependencies.acceptProjection(result)
    evidence.successProjectionCount++
    evidence.priceSnapshotCount++
    evidence.outcome='ACCEPTED'
    return{result,error:null,evidence}
  }catch(error){
    evidence.outcome=result?'CLIENT_FAIL_CLOSED':failureOutcome(error)
    return{result,error,evidence}
  }
}

export async function executePaymentIntentReadOnlyQuery(
  subcaseId:string,
  queryPaymentIntent:()=>Promise<PaymentIntentQueryResult>,
):Promise<{result:PaymentIntentQueryResult|null;error:unknown;evidence:PaymentIntentAttemptEvidence}>{
  const evidence:PaymentIntentAttemptEvidence={subcaseId,outcome:'UNKNOWN',apiCallCount:0,readOnlyQueryCount:1,
    navigationCount:0,automaticRetryCount:0,successProjectionCount:0,priceSnapshotCount:0}
  try{
    const result=await queryPaymentIntent()
    if(result.outcome==='FOUND'){
      evidence.outcome='ACCEPTED';evidence.successProjectionCount=1;evidence.priceSnapshotCount=1
    }else if(result.outcome==='REJECTED')evidence.outcome='REJECTED'
    return{result,error:null,evidence}
  }catch(error){
    evidence.outcome=codeOf(error)==='NETWORK_ERROR'?'NETWORK_INTERRUPTED':'CLIENT_FAIL_CLOSED'
    return{result:null,error,evidence}
  }
}
