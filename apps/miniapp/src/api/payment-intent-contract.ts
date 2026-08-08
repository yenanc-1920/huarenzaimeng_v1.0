import type { ProjectSessionProjection } from '../domain/types'
import type { OrderCreationProjection } from './order-creation-contract'
import { parseProjectQuote, type ProjectAction, type ProjectQuote } from './project-contract.ts'

export const PAYMENT_INTENT_CREATION_PRECONDITION = 'PAYMENT_INTENT_MUST_NOT_EXIST' as const
export const PAYMENT_INTENT_ACTION = 'CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT' as const
export const PAYMENT_INTENT_QUERY_ACTION = 'QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT' as const
export const PAYMENT_INTENT_SCOPE = 'LOCAL_SYNTHETIC_ONLY' as const

export interface PaymentIntentCommand {
  commandId: string
  idempotencyKey: string
  paymentIntentCreationPrecondition: typeof PAYMENT_INTENT_CREATION_PRECONDITION
  sessionVersion: number
  authorizationSetRef: string
  expectedProjectionVersion: number
  expectedAggregateVersion: number
}

export interface PaymentIntentResult {
  requestRef: string
  outcome: 'ACCEPTED'
  projectCode: 'PAYMENT_INTENT_CREATED' | 'PAYMENT_INTENT_REPLAYED'
  resourceRef: string
  aggregateVersion: number
  currentProjection: PaymentIntentProjection
  retryClass: 'NONE'
}

export interface PaymentIntentProjection extends OrderCreationProjection {
  intentScope: typeof PAYMENT_INTENT_SCOPE
  paymentInitiated: false
  paymentConfirmed: false
}

export interface PaymentIntentQueryRequest {
  commandId:string
  idempotencyKey:string
  sessionVersion:number
  authorizationSetRef:string
}

export interface PaymentIntentIdentityStorage {
  getStorageSync(key:string):unknown
  setStorageSync(key:string,value:unknown):void
}

interface StoredPaymentIntentWriteIdentity {
  orderRef:string
  commandId:string
  idempotencyKey:string
  sessionVersion:number
  authorizationSetRef:string
}

interface PaymentIntentQueryBase {
  requestRef:string
  nextPollAt:string|null
}
export type PaymentIntentQueryResult =
  | (PaymentIntentQueryBase & { outcome:'FOUND'; projectCode:'PAYMENT_INTENT_RESULT_FOUND'; resourceRef:string;
      aggregateVersion:number; currentProjection:PaymentIntentProjection; retryClass:'NONE'; nextPollAt:null })
  | (PaymentIntentQueryBase & { outcome:'REJECTED'; projectCode:'PAYMENT_INTENT_RESULT_REJECTED'|'PAYMENT_INTENT_QUERY_NOT_AVAILABLE';
      resourceRef:null; aggregateVersion:null; currentProjection:null; retryClass:'NONE'; nextPollAt:null })
  | (PaymentIntentQueryBase & { outcome:'UNKNOWN'; projectCode:'PAYMENT_INTENT_RESULT_UNKNOWN'; resourceRef:null;
      aggregateVersion:null; currentProjection:null; retryClass:'SAME_ACTION_QUERY_ONLY' })

export const P013_PAYMENT_SUMMARY_MISSING_FIELDS = Object.freeze(['updatedAt'] as const)
export interface P013PaymentSummary {
  totalAmountMinor:number
  currency:'CNY'|'BDT'
  maskedPhone:string
  operatorCode:string
  productCode:string
  denominationRef:string
  supportedOperatorSetVersion:number
  catalogVersion:number
  expiresAt:string
}

const object = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
const text = (value: unknown): value is string => typeof value === 'string' && value.length > 0
const positiveInteger = (value: unknown): value is number => Number.isInteger(value) && Number(value) > 0
const exactKeys = (value: Record<string, unknown>, allowed: string[]) =>
  Object.keys(value).length === allowed.length && Object.keys(value).every((key) => allowed.includes(key))
const sameQuote = (left: ProjectQuote, right: ProjectQuote) =>
  JSON.stringify(left) === JSON.stringify(right)
const rfc3339 = (value:unknown):value is string => typeof value === 'string'
  && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(value)
  && Number.isFinite(Date.parse(value))
const paymentIntentWriteIdentityKey=(orderRef:string)=>`paymentIntentWriteIdentity:${orderRef}`
const validQuerySession=(session:ProjectSessionProjection,orderRef:string,now:number)=>session.role==='BUYER'
  &&positiveInteger(session.sessionVersion)&&text(session.authorizationSetRef)&&text(session.authorizationEvidenceVersion)
  &&Array.isArray(session.authorizedOrderRefs)&&new Set(session.authorizedOrderRefs).size===session.authorizedOrderRefs.length
  &&session.authorizedOrderRefs.includes(orderRef)&&text(session.issuedAt)&&text(session.expiresAt)
  &&Number.isFinite(Date.parse(session.issuedAt))&&Number.isFinite(Date.parse(session.expiresAt))
  &&Date.parse(session.issuedAt)<=now&&Date.parse(session.expiresAt)>now

export function storePaymentIntentWriteIdentity(
  storage:PaymentIntentIdentityStorage,
  orderRef:string,
  identity:{commandId:string;idempotencyKey:string},
  session:ProjectSessionProjection,
  now=Date.now(),
):void{
  if(!text(orderRef)||!text(identity.commandId)||!text(identity.idempotencyKey)||!validQuerySession(session,orderRef,now)){
    throw new Error('PAYMENT_INTENT_WRITE_IDENTITY_BINDING_INVALID')
  }
  const record:StoredPaymentIntentWriteIdentity={orderRef,commandId:identity.commandId,idempotencyKey:identity.idempotencyKey,
    sessionVersion:session.sessionVersion,authorizationSetRef:session.authorizationSetRef!}
  storage.setStorageSync(paymentIntentWriteIdentityKey(orderRef),record)
}

export function readPaymentIntentWriteIdentity(
  storage:Pick<PaymentIntentIdentityStorage,'getStorageSync'>,
  orderRef:string,
  session:ProjectSessionProjection,
  now=Date.now(),
):{commandId:string;idempotencyKey:string}|null{
  if(!text(orderRef)||!validQuerySession(session,orderRef,now))return null
  const value=storage.getStorageSync(paymentIntentWriteIdentityKey(orderRef))
  if(!object(value)||!exactKeys(value,['orderRef','commandId','idempotencyKey','sessionVersion','authorizationSetRef'])
      ||value.orderRef!==orderRef||!text(value.commandId)||!text(value.idempotencyKey)
      ||value.sessionVersion!==session.sessionVersion||value.authorizationSetRef!==session.authorizationSetRef)return null
  return{commandId:value.commandId,idempotencyKey:value.idempotencyKey}
}

function parseAction(value: unknown, projectionVersion: number, aggregateVersion: number): ProjectAction {
  if (!object(value) || !exactKeys(value, ['actionCode','expectedProjectionVersion','expectedAggregateVersion'])
      || (value.actionCode !== 'REQUEST_MOCK_PAYMENT' && value.actionCode !== PAYMENT_INTENT_ACTION
        && value.actionCode !== PAYMENT_INTENT_QUERY_ACTION && value.actionCode !== 'REQUEST_MOCK_TOPUP'
        && value.actionCode !== 'WAIT_OR_CONTACT_SUPPORT')
      || value.expectedProjectionVersion !== projectionVersion
      || (value.expectedAggregateVersion !== null && value.expectedAggregateVersion !== aggregateVersion)) {
    throw new Error('INVALID_PAYMENT_INTENT_ACTION_DTO')
  }
  return { actionCode:value.actionCode, expectedProjectionVersion:projectionVersion,
    expectedAggregateVersion:value.expectedAggregateVersion as number|null }
}

function parseProjection(value: unknown): PaymentIntentProjection {
  if (!object(value) || !exactKeys(value, ['orderRef','stateCode','priceSnapshot','intentScope','paymentInitiated','paymentConfirmed','projectionVersion','aggregateVersion','allowedActions'])
      || !text(value.orderRef) || value.stateCode !== 'AWAITING_PAYMENT' || value.intentScope !== PAYMENT_INTENT_SCOPE
      || value.paymentInitiated !== false || value.paymentConfirmed !== false
      || !positiveInteger(value.projectionVersion) || !positiveInteger(value.aggregateVersion)
      || !Array.isArray(value.allowedActions)) throw new Error('INVALID_PAYMENT_INTENT_PROJECTION_DTO')
  const priceSnapshot = parseProjectQuote(value.priceSnapshot)
  const actions = value.allowedActions.map((action) => parseAction(action, value.projectionVersion as number, value.aggregateVersion as number))
  if (actions.length !== 1 || actions[0].actionCode !== PAYMENT_INTENT_QUERY_ACTION
      || actions[0].expectedProjectionVersion !== value.projectionVersion || actions[0].expectedAggregateVersion !== null) {
    throw new Error('INVALID_PAYMENT_INTENT_SAFE_ACTION_DTO')
  }
  return { orderRef:value.orderRef, quoteRef:priceSnapshot.quoteRef, stateCode:'AWAITING_PAYMENT', priceSnapshot,
    intentScope:PAYMENT_INTENT_SCOPE,paymentInitiated:false,paymentConfirmed:false,
    projectionVersion:value.projectionVersion as number, aggregateVersion:value.aggregateVersion as number, allowedActions:actions }
}

export function canCreateLocalSyntheticPaymentIntent(
  orderRef: string,
  session: ProjectSessionProjection,
  projection: OrderCreationProjection,
  frozenPriceSnapshot: ProjectQuote,
  now=Date.now(),
): boolean {
  if (!text(orderRef) || session.role !== 'BUYER' || !positiveInteger(session.sessionVersion)
      || !text(session.authorizationSetRef) || !text(session.authorizationEvidenceVersion)
      || !Array.isArray(session.authorizedOrderRefs) || new Set(session.authorizedOrderRefs).size !== session.authorizedOrderRefs.length
      || !session.authorizedOrderRefs.includes(orderRef)
      || !text(session.issuedAt) || !text(session.expiresAt) || !Number.isFinite(Date.parse(session.issuedAt))
      || !Number.isFinite(Date.parse(session.expiresAt)) || Date.parse(session.issuedAt) > now || Date.parse(session.expiresAt) <= now
      || projection.orderRef !== orderRef || projection.stateCode !== 'AWAITING_PAYMENT'
      || !positiveInteger(projection.projectionVersion) || !positiveInteger(projection.aggregateVersion)
      || !sameQuote(projection.priceSnapshot, frozenPriceSnapshot)) return false
  return projection.allowedActions.some((action) => action.actionCode === PAYMENT_INTENT_ACTION
    && action.expectedProjectionVersion === projection.projectionVersion
    && action.expectedAggregateVersion === projection.aggregateVersion)
}

export function buildPaymentIntentCommand(
  identity: { commandId: string; idempotencyKey: string },
  orderRef: string,
  session: ProjectSessionProjection,
  projection: OrderCreationProjection,
  frozenPriceSnapshot: ProjectQuote,
): PaymentIntentCommand {
  if (!text(identity.commandId) || !text(identity.idempotencyKey)) throw new Error('PAYMENT_INTENT_INPUT_REQUIRED')
  if (!canCreateLocalSyntheticPaymentIntent(orderRef, session, projection, frozenPriceSnapshot)) {
    throw new Error('PAYMENT_INTENT_CLIENT_GATE_CLOSED')
  }
  return {
    commandId:identity.commandId,
    idempotencyKey:identity.idempotencyKey,
    paymentIntentCreationPrecondition:PAYMENT_INTENT_CREATION_PRECONDITION,
    sessionVersion:session.sessionVersion,
    authorizationSetRef:session.authorizationSetRef!,
    expectedProjectionVersion:projection.projectionVersion,
    expectedAggregateVersion:projection.aggregateVersion,
  }
}

export function buildPaymentIntentQueryRequest(
  identity:{commandId:string;idempotencyKey:string},
  orderRef:string,
  session:ProjectSessionProjection,
  now=Date.now(),
):PaymentIntentQueryRequest{
  if(!text(identity.commandId)||!text(identity.idempotencyKey)||!text(orderRef)||!validQuerySession(session,orderRef,now)){
    throw new Error('PAYMENT_INTENT_QUERY_CLIENT_GATE_CLOSED')
  }
  return{commandId:identity.commandId,idempotencyKey:identity.idempotencyKey,sessionVersion:session.sessionVersion,
    authorizationSetRef:session.authorizationSetRef!}
}

export function parsePaymentIntentResult(value: unknown): PaymentIntentResult {
  if (!object(value) || !exactKeys(value, ['requestRef','outcome','projectCode','resourceRef','aggregateVersion','currentProjection','retryClass'])
      || !text(value.requestRef) || value.outcome !== 'ACCEPTED'
      || (value.projectCode !== 'PAYMENT_INTENT_CREATED' && value.projectCode !== 'PAYMENT_INTENT_REPLAYED')
      || !text(value.resourceRef) || !positiveInteger(value.aggregateVersion) || value.retryClass !== 'NONE') {
    throw new Error('INVALID_PAYMENT_INTENT_RESULT_DTO')
  }
  const currentProjection = parseProjection(value.currentProjection)
  if (value.aggregateVersion !== currentProjection.aggregateVersion) {
    throw new Error('PAYMENT_INTENT_RESULT_BINDING_MISMATCH')
  }
  return { requestRef:value.requestRef, outcome:'ACCEPTED', projectCode:value.projectCode,
    resourceRef:value.resourceRef, aggregateVersion:value.aggregateVersion, currentProjection, retryClass:'NONE' }
}

export function validatePaymentIntentResult(
  result: PaymentIntentResult,
  orderRef: string,
  command: PaymentIntentCommand,
  frozenPriceSnapshot: ProjectQuote,
): PaymentIntentResult {
  if (result.requestRef !== command.commandId || result.currentProjection.orderRef !== orderRef
      || !sameQuote(result.currentProjection.priceSnapshot, frozenPriceSnapshot)
      || result.currentProjection.aggregateVersion !== command.expectedAggregateVersion + 1
      || result.currentProjection.projectionVersion !== command.expectedProjectionVersion + 1) {
    throw new Error('PAYMENT_INTENT_RESULT_VERSION_OR_SNAPSHOT_MISMATCH')
  }
  return result
}

export function parsePaymentIntentQueryResult(value:unknown):PaymentIntentQueryResult{
  if(!object(value)||!exactKeys(value,['requestRef','outcome','projectCode','resourceRef','aggregateVersion','currentProjection','retryClass','nextPollAt'])
      ||!text(value.requestRef))throw new Error('INVALID_PAYMENT_INTENT_QUERY_DTO')
  if(value.outcome==='ACCEPTED'&&value.projectCode==='PAYMENT_INTENT_RESULT_FOUND'&&text(value.resourceRef)
      &&positiveInteger(value.aggregateVersion)&&value.retryClass==='NONE'&&value.nextPollAt===null){
    const currentProjection=parseProjection(value.currentProjection)
    if(currentProjection.aggregateVersion!==value.aggregateVersion)throw new Error('PAYMENT_INTENT_QUERY_BINDING_MISMATCH')
    return{requestRef:value.requestRef,outcome:'FOUND',projectCode:'PAYMENT_INTENT_RESULT_FOUND',resourceRef:value.resourceRef,
      aggregateVersion:value.aggregateVersion,currentProjection,retryClass:'NONE',nextPollAt:null}
  }
  if(value.outcome==='REJECTED'&&(value.projectCode==='PAYMENT_INTENT_RESULT_REJECTED'||value.projectCode==='PAYMENT_INTENT_QUERY_NOT_AVAILABLE')
      &&value.resourceRef===null&&value.aggregateVersion===null&&value.currentProjection===null&&value.retryClass==='NONE'&&value.nextPollAt===null){
    return{requestRef:value.requestRef,outcome:'REJECTED',projectCode:value.projectCode,resourceRef:null,aggregateVersion:null,
      currentProjection:null,retryClass:'NONE',nextPollAt:null}
  }
  if(value.outcome==='UNKNOWN'&&value.projectCode==='PAYMENT_INTENT_RESULT_UNKNOWN'&&value.resourceRef===null
      &&value.aggregateVersion===null&&value.currentProjection===null&&value.retryClass==='SAME_ACTION_QUERY_ONLY'
      &&(value.nextPollAt===null||rfc3339(value.nextPollAt))){
    return{requestRef:value.requestRef,outcome:'UNKNOWN',projectCode:'PAYMENT_INTENT_RESULT_UNKNOWN',resourceRef:null,
      aggregateVersion:null,currentProjection:null,retryClass:'SAME_ACTION_QUERY_ONLY',nextPollAt:value.nextPollAt as string|null}
  }
  throw new Error('INVALID_PAYMENT_INTENT_QUERY_DTO')
}

export function validatePaymentIntentQueryResult(
  result:PaymentIntentQueryResult,
  orderRef:string,
  request:PaymentIntentQueryRequest,
  sourceProjection:OrderCreationProjection,
  frozenPriceSnapshot:ProjectQuote,
):PaymentIntentQueryResult{
  if(result.requestRef!==request.commandId)throw new Error('PAYMENT_INTENT_QUERY_REQUEST_MISMATCH')
  if(result.outcome!=='FOUND')return result
  const sourceAlreadyAccepted=sourceProjection.allowedActions.some(action=>action.actionCode===PAYMENT_INTENT_QUERY_ACTION)
  const expectedProjectionVersion=sourceProjection.projectionVersion+(sourceAlreadyAccepted?0:1)
  const expectedAggregateVersion=sourceProjection.aggregateVersion+(sourceAlreadyAccepted?0:1)
  if(result.currentProjection.orderRef!==orderRef||!sameQuote(result.currentProjection.priceSnapshot,frozenPriceSnapshot)
      ||result.currentProjection.projectionVersion!==expectedProjectionVersion
      ||result.currentProjection.aggregateVersion!==expectedAggregateVersion||result.aggregateVersion!==expectedAggregateVersion){
    throw new Error('PAYMENT_INTENT_QUERY_VERSION_OR_SNAPSHOT_MISMATCH')
  }
  return result
}

export function buildP013PaymentSummary(frozen:ProjectQuote,current:ProjectQuote):P013PaymentSummary|null{
  if(!sameQuote(frozen,current))return null
  const parsed=parseProjectQuote(current)
  return{totalAmountMinor:parsed.totalAmountMinor,currency:parsed.currency,maskedPhone:parsed.maskedPhone,
    operatorCode:parsed.operatorCode,productCode:parsed.productCode,denominationRef:parsed.denominationRef,
    supportedOperatorSetVersion:parsed.supportedOperatorSetVersion,catalogVersion:parsed.catalogVersion,expiresAt:parsed.expiresAt}
}
