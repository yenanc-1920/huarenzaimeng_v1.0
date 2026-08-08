import type { OrderCreationResult } from '../api/order-creation-contract'

export const M1_FIXED_DENOMINATOR = 31 as const

export type OrderCreationAttemptOutcome = 'ACCEPTED' | 'REJECTED' | 'UNKNOWN' | 'NETWORK_INTERRUPTED' | 'CLIENT_FAIL_CLOSED'

export interface OrderCreationAttemptEvidence {
  subcaseId: string
  fixedDenominator: typeof M1_FIXED_DENOMINATOR
  outcome: OrderCreationAttemptOutcome
  apiCallCount: number
  navigationCount: number
  automaticRetryCount: number
  successProjectionCount: number
  priceSnapshotCount: number
}

export interface OrderCreationAttemptResult {
  result: OrderCreationResult | null
  error: unknown
  evidence: OrderCreationAttemptEvidence
}

export interface OrderCreationAttemptDependencies {
  createOrder: () => Promise<OrderCreationResult>
  acceptProjection: (result: OrderCreationResult) => void | Promise<void>
  navigate: (orderRef: string) => void | Promise<void>
  automaticRetry: () => void | Promise<void>
}

export interface OrderCreationNavigationLedger {
  has(orderRef: string): boolean
  add(orderRef: string): void
}

export function createOrderCreationNavigationLedger(): OrderCreationNavigationLedger {
  const navigated = new Set<string>()
  return { has:(orderRef)=>navigated.has(orderRef), add:(orderRef)=>navigated.add(orderRef) }
}

const errorCode = (error: unknown) => error instanceof Error ? error.message : String(error)
const failureOutcome = (error: unknown): OrderCreationAttemptOutcome => {
  const code=errorCode(error)
  if(code==='NETWORK_ERROR')return'NETWORK_INTERRUPTED'
  if(code.includes('UNKNOWN'))return'UNKNOWN'
  return'REJECTED'
}

export async function executeOrderCreationAttempt(
  subcaseId: string,
  dependencies: OrderCreationAttemptDependencies,
  navigationLedger: OrderCreationNavigationLedger,
): Promise<OrderCreationAttemptResult> {
  const evidence:OrderCreationAttemptEvidence={subcaseId,fixedDenominator:M1_FIXED_DENOMINATOR,outcome:'REJECTED',apiCallCount:0,
    navigationCount:0,automaticRetryCount:0,successProjectionCount:0,priceSnapshotCount:0}
  let result:OrderCreationResult|null=null
  // automaticRetry is intentionally injectable evidence only; this flow never invokes it.
  void dependencies.automaticRetry
  try{
    evidence.apiCallCount++
    result=await dependencies.createOrder()
    evidence.successProjectionCount++
    evidence.priceSnapshotCount++
    await dependencies.acceptProjection(result)
    if(!navigationLedger.has(result.resourceRef)){
      evidence.navigationCount++
      await dependencies.navigate(result.resourceRef)
      navigationLedger.add(result.resourceRef)
    }
    evidence.outcome='ACCEPTED'
    return{result,error:null,evidence}
  }catch(error){
    evidence.outcome=result?'CLIENT_FAIL_CLOSED':failureOutcome(error)
    return{result,error,evidence}
  }
}

export function summarizeOrderCreationEvidence(subcaseId:string,attempts:OrderCreationAttemptEvidence[]):OrderCreationAttemptEvidence{
  if(attempts.length===0||attempts.some(attempt=>attempt.fixedDenominator!==M1_FIXED_DENOMINATOR))throw new Error('INVALID_M1_EVIDENCE_SET')
  return{subcaseId,fixedDenominator:M1_FIXED_DENOMINATOR,outcome:attempts.every(attempt=>attempt.outcome==='ACCEPTED')?'ACCEPTED':attempts[attempts.length-1].outcome,
    apiCallCount:attempts.reduce((sum,attempt)=>sum+attempt.apiCallCount,0),navigationCount:attempts.reduce((sum,attempt)=>sum+attempt.navigationCount,0),
    automaticRetryCount:attempts.reduce((sum,attempt)=>sum+attempt.automaticRetryCount,0),successProjectionCount:attempts.reduce((sum,attempt)=>sum+attempt.successProjectionCount,0),
    priceSnapshotCount:attempts.reduce((sum,attempt)=>sum+attempt.priceSnapshotCount,0)}
}
