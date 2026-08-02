import type { OrderSummary,ProjectSessionProjection } from './types'
import { readSessionProjection,revokeOrderListAccess,type SessionStorage } from './session.ts'

export type AuthorizedOrderListLoad=
  | {outcome:'GUEST';session:ProjectSessionProjection;orders:[]}
  | {outcome:'READY';session:ProjectSessionProjection;orders:OrderSummary[]}
  | {outcome:'ERROR';session:ProjectSessionProjection;orders:[]}

export async function loadAuthorizedOrderList(
  storage:SessionStorage,
  request:(session:ProjectSessionProjection)=>Promise<OrderSummary[]>,
  now=Date.now(),
):Promise<AuthorizedOrderListLoad>{
  const current=readSessionProjection(storage,now)
  if(current.role!=='BUYER')return{outcome:'GUEST',session:revokeOrderListAccess(storage),orders:[]}
  try{return{outcome:'READY',session:current,orders:await request(current)}}
  catch{return{outcome:'ERROR',session:revokeOrderListAccess(storage),orders:[]}}
}
