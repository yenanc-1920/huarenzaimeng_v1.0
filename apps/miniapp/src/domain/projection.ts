import type { OrderProjection } from './types'

const latestByOrderRef = new Map<string, OrderProjection>()

export function acceptNewerProjection(orderRef: string, candidate: OrderProjection): OrderProjection {
  if (candidate.orderRef !== orderRef) throw new Error('PROJECTION_ORDER_REF_MISMATCH')
  const latest = latestByOrderRef.get(orderRef)
  if (!latest || candidate.projectionVersion > latest.projectionVersion) {
    latestByOrderRef.set(orderRef, candidate)
  }
  return latestByOrderRef.get(orderRef)!
}

export function clearProjectionCache(orderRef?: string) {
  if (orderRef) latestByOrderRef.delete(orderRef)
  else latestByOrderRef.clear()
}
