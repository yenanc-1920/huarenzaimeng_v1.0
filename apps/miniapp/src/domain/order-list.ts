import type { OrderSummary } from './types'

export type AuthorizedOrderListLoad=
  | {outcome:'GUEST';orders:[]}
  | {outcome:'READY';orders:OrderSummary[]}
  | {outcome:'ERROR';orders:[]}

export async function loadAuthorizedOrderList(
  authenticated:boolean,
  request:()=>Promise<OrderSummary[]>,
):Promise<AuthorizedOrderListLoad>{
  if(!authenticated)return{outcome:'GUEST',orders:[]}
  try{return{outcome:'READY',orders:await request()}}
  catch{return{outcome:'ERROR',orders:[]}}
}
