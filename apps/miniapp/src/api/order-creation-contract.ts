import type { ProjectSessionProjection } from '../domain/types'
import { parseProjectQuote, type ProjectReadAction, type ProjectQuote } from './project-contract.ts'

export const ORDER_CREATION_PRECONDITION = 'ORDER_MUST_NOT_EXIST' as const

export interface OrderCreationCommand {
  commandId: string
  idempotencyKey: string
  orderCreationPrecondition: typeof ORDER_CREATION_PRECONDITION
  quoteRef: string
  sessionVersion: number
  authorizationSetRef: string
}

export type OrderCreationState =
  | 'AWAITING_PAYMENT'
  | 'PAYMENT_PROCESSING'
  | 'PAID_AWAITING_TOPUP'
  | 'TOPUP_PROCESSING'
  | 'TOPUP_RESULT_UNKNOWN'
  | 'DELIVERED'
  | 'CONFIRMED_NOT_DELIVERED'
  | 'REFUND_PROCESSING'
  | 'REFUNDED'
  | 'DELIVERY_REFUND_CONFLICT_REVIEW'
  | 'SUPPORT_REVIEW'

export interface OrderCreationProjection {
  orderRef: string
  quoteRef: string
  stateCode: OrderCreationState
  priceSnapshot: ProjectQuote
  projectionVersion: number
  aggregateVersion: number
  allowedActions: ProjectReadAction[]
}

export interface OrderCreationResult {
  requestRef: string
  outcome: 'ACCEPTED'
  projectCode: 'ORDER_CREATED' | 'ORDER_REPLAYED'
  resourceRef: string
  aggregateVersion: number
  currentProjection: OrderCreationProjection
  retryClass: 'NONE'
}

const object = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
const text = (value: unknown): value is string => typeof value === 'string' && value.length > 0
const positiveInteger = (value: unknown): value is number => Number.isInteger(value) && Number(value) > 0
const exactKeys = (value: Record<string, unknown>, allowed: string[]) =>
  Object.keys(value).length === allowed.length && Object.keys(value).every((key) => allowed.includes(key))
const stateCode = (value: unknown): value is OrderCreationState => [
  'AWAITING_PAYMENT','PAYMENT_PROCESSING','PAID_AWAITING_TOPUP','TOPUP_PROCESSING','TOPUP_RESULT_UNKNOWN',
  'DELIVERED','CONFIRMED_NOT_DELIVERED','REFUND_PROCESSING','REFUNDED','DELIVERY_REFUND_CONFLICT_REVIEW','SUPPORT_REVIEW',
].includes(String(value))
const readActionCode = (value: unknown): value is ProjectReadAction['actionCode'] =>
  value === 'REFRESH_ORDER_STATUS' || value === 'CONTACT_SUPPORT' || value === 'SAFE_EXIT'

export function buildOrderCreationCommand(
  identity: { commandId: string; idempotencyKey: string },
  quoteRef: string,
  session: ProjectSessionProjection,
): OrderCreationCommand {
  if (!text(identity.commandId) || !text(identity.idempotencyKey) || !text(quoteRef)) throw new Error('ORDER_CREATION_INPUT_REQUIRED')
  if (session.role !== 'BUYER' || !positiveInteger(session.sessionVersion) || !text(session.authorizationSetRef)) {
    throw new Error('ORDER_CREATION_BUYER_SESSION_REQUIRED')
  }
  return {
    commandId: identity.commandId,
    idempotencyKey: identity.idempotencyKey,
    orderCreationPrecondition: ORDER_CREATION_PRECONDITION,
    quoteRef,
    sessionVersion: session.sessionVersion,
    authorizationSetRef: session.authorizationSetRef,
  }
}

function parseAction(value: unknown, projectionVersion: number, aggregateVersion: number): ProjectReadAction|null {
  if (!object(value) || !exactKeys(value, ['actionCode','expectedProjectionVersion','expectedAggregateVersion'])
      || !readActionCode(value.actionCode) || !positiveInteger(value.expectedProjectionVersion)
      || (value.expectedAggregateVersion !== null && !positiveInteger(value.expectedAggregateVersion))
      || value.expectedProjectionVersion !== projectionVersion
      || (value.expectedAggregateVersion !== null && value.expectedAggregateVersion !== aggregateVersion)) {
    return null
  }
  return { actionCode:value.actionCode, expectedProjectionVersion:value.expectedProjectionVersion,
    expectedAggregateVersion:value.expectedAggregateVersion as number|null }
}

export function parseOrderCreationResult(value: unknown): OrderCreationResult {
  if (!object(value) || !exactKeys(value, ['requestRef','outcome','projectCode','resourceRef','aggregateVersion','currentProjection','retryClass'])
      || !text(value.requestRef) || value.outcome !== 'ACCEPTED'
      || (value.projectCode !== 'ORDER_CREATED' && value.projectCode !== 'ORDER_REPLAYED')
      || !text(value.resourceRef) || !positiveInteger(value.aggregateVersion) || value.retryClass !== 'NONE'
      || !object(value.currentProjection)) throw new Error('INVALID_ORDER_CREATION_RESULT_DTO')

  const projection = value.currentProjection
  if (!exactKeys(projection, ['orderRef','quoteRef','stateCode','priceSnapshot','projectionVersion','aggregateVersion','allowedActions'])
      || !text(projection.orderRef) || !text(projection.quoteRef) || !stateCode(projection.stateCode)
      || !positiveInteger(projection.projectionVersion) || !positiveInteger(projection.aggregateVersion)
      || !Array.isArray(projection.allowedActions)) throw new Error('INVALID_ORDER_CREATION_PROJECTION_DTO')
  const priceSnapshot = parseProjectQuote(projection.priceSnapshot)
  const actions = projection.allowedActions.map((action) => parseAction(action, projection.projectionVersion as number, projection.aggregateVersion as number)).filter((action):action is ProjectReadAction=>action!==null)
  if (new Set(actions.map((action) => action.actionCode)).size !== actions.length) throw new Error('DUPLICATE_ORDER_CREATION_ACTION_DTO')
  if (value.resourceRef !== projection.orderRef || value.aggregateVersion !== projection.aggregateVersion
      || projection.quoteRef !== priceSnapshot.quoteRef) throw new Error('ORDER_CREATION_RESULT_BINDING_MISMATCH')
  if (value.projectCode === 'ORDER_CREATED' && (value.aggregateVersion !== 1 || projection.aggregateVersion !== 1
      || projection.projectionVersion !== 1 || projection.stateCode !== 'AWAITING_PAYMENT')) {
    throw new Error('INVALID_FIRST_ORDER_CREATION_STATE')
  }
  return {
    requestRef:value.requestRef,
    outcome:'ACCEPTED',
    projectCode:value.projectCode,
    resourceRef:value.resourceRef,
    aggregateVersion:value.aggregateVersion,
    currentProjection:{ orderRef:projection.orderRef, quoteRef:projection.quoteRef, stateCode:projection.stateCode,
      priceSnapshot, projectionVersion:projection.projectionVersion as number, aggregateVersion:projection.aggregateVersion as number,
      allowedActions:actions },
    retryClass:'NONE',
  }
}
