import type { P021PriceSnapshotSummary, P021Projection, P021Response } from '../api/order-detail-contract.ts'
import { sameP021SessionBinding, validP021BuyerSession } from '../api/order-detail-contract.ts'
import { readSessionProjection, type SessionStorage } from './session.ts'

export type P021ViewState='LOADING'|'READY'|'NOT_AVAILABLE'|'READ_ERROR'|'INFORMATION_UPDATED'
export interface P021PageState { viewState:P021ViewState;projection:P021Projection|null;generation:number;acceptedProjectionVersion:number;acceptedProjectionDigest:string|null }
export interface P021ReadApi { getOrderDetail(orderRef:string):Promise<P021Response> }

export const createP021PageState=():P021PageState=>({viewState:'LOADING',projection:null,generation:0,acceptedProjectionVersion:0,acceptedProjectionDigest:null})
const frozenKey=(orderRef:string)=>`priceSnapshot:${orderRef}`
const object=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null&&!Array.isArray(value)

export function p021SnapshotMatches(storage:Pick<SessionStorage,'getStorageSync'>,orderRef:string,summary:P021PriceSnapshotSummary):boolean{
  const frozen=storage.getStorageSync(frozenKey(orderRef))
  if(frozen===undefined||frozen===null||frozen==='')return true
  return object(frozen)&&frozen.priceSnapshotRef===summary.priceSnapshotRef&&frozen.version===summary.displayVersion
    &&frozen.totalAmount===summary.totalMinor&&frozen.currency===summary.currency&&frozen.phone===summary.maskedTarget
    &&frozen.operator===summary.brandDisplayName&&frozen.product===summary.productDisplayName
    &&frozen.targetValueDisplay===summary.targetValueDisplay&&frozen.targetCurrency===summary.targetCurrency
    &&frozen.validUntil===summary.validUntil
}

function canonical(value:unknown):unknown{
  if(Array.isArray(value))return value.map(canonical)
  if(object(value))return Object.fromEntries(Object.keys(value).sort().map(key=>[key,canonical(value[key])]))
  return value
}
function projectionDigest(projection:P021Projection):string{return JSON.stringify(canonical(projection))}

export async function executeP021Read(state:P021PageState,storage:SessionStorage,api:P021ReadApi,orderRef:string,clock:()=>number=Date.now):Promise<P021PageState>{
  const startedAt=clock()
  const generation=++state.generation
  state.projection=null;state.viewState='LOADING'
  const startSession=readSessionProjection(storage,startedAt)
  if(!validP021BuyerSession(startSession,orderRef,startedAt)){state.viewState='NOT_AVAILABLE';return state}
  let response:P021Response
  try{response=await api.getOrderDetail(orderRef)}catch{if(generation===state.generation)state.viewState='READ_ERROR';return state}
  if(generation!==state.generation)return state
  const completedAt=clock()
  const currentSession=readSessionProjection(storage,completedAt)
  if(!sameP021SessionBinding(startSession,currentSession,orderRef,completedAt)){state.viewState='NOT_AVAILABLE';return state}
  if(response.projectCode==='ORDER_DETAIL_NOT_AVAILABLE'){state.viewState='NOT_AVAILABLE';return state}
  if(response.projectCode==='ORDER_DETAIL_READ_ERROR'||response.outcome!=='ACCEPTED'||!response.currentProjection){state.viewState='READ_ERROR';return state}
  const projection=response.currentProjection
  if(projection.orderRef!==orderRef||!p021SnapshotMatches(storage,orderRef,projection.priceSnapshotSummary)){state.viewState='INFORMATION_UPDATED';return state}
  const digest=projectionDigest(projection)
  if(projection.projectionVersion<state.acceptedProjectionVersion
    ||(projection.projectionVersion===state.acceptedProjectionVersion&&state.acceptedProjectionDigest!==null&&state.acceptedProjectionDigest!==digest)){
    state.viewState='INFORMATION_UPDATED';return state
  }
  state.acceptedProjectionVersion=projection.projectionVersion;state.acceptedProjectionDigest=digest
  state.projection=projection;state.viewState='READY';return state
}
