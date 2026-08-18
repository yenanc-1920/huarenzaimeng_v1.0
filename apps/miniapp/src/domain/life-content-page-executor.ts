import type {
  LifeContentDetailItem,
  LifeContentDetailResult,
  LifeContentReadState,
  LifeContentSummary,
  LifeContentListResult,
} from './types'

export type LifeContentListViewState = LifeContentReadState | 'LOADING' | 'READ_ERROR'
export type LifeContentDetailViewState = Exclude<LifeContentReadState,'EMPTY'> | 'LOADING' | 'STALE' | 'READ_ERROR'
export type LifeContentReadTrigger = 'FIRST_ENTRY' | 'REENTRY' | 'FOREGROUND_RESUME' | 'USER_RETRY'
export type LifeContentRuntimeMetric =
  | 'QueryCall'
  | 'UserRetry'
  | 'AutomaticRetry'
  | 'Projection'
  | 'Body'
  | 'Navigation'
  | 'IdentityHeader'
  | 'PersistentLog'
  | 'ExternalAnalytics'

export interface LifeContentRuntimeProbe {
  increment(metric:LifeContentRuntimeMetric,amount?:number):void
  event(code:'LIFE_CONTENT_LIST_READ_START'|'LIFE_CONTENT_DETAIL_READ_START'|'LIFE_CONTENT_DETAIL_NAVIGATION'):void
}

export interface LifeContentListPageState {
  viewState:LifeContentListViewState
  items:LifeContentSummary[]
  topic:'ALL'|'LIFE_REMINDER'|'HOLIDAY_EXPLANATION'
}

export interface LifeContentDetailPageState {
  viewState:LifeContentDetailViewState
  item:LifeContentDetailItem|null
  readGeneration:number
}

export interface LifeContentAnonymousReadApi {
  getList():Promise<LifeContentListResult>
  getDetail(contentRef:string,contentVersion:string|null):Promise<LifeContentDetailResult>
}

const NOOP_PROBE:LifeContentRuntimeProbe = Object.freeze({
  increment:()=>undefined,
  event:()=>undefined,
})

export async function executeLifeContentListRead(
  state:LifeContentListPageState,
  api:Pick<LifeContentAnonymousReadApi,'getList'>,
  trigger:LifeContentReadTrigger='FIRST_ENTRY',
  probe:LifeContentRuntimeProbe=NOOP_PROBE,
):Promise<void> {
  state.items=[]
  state.topic='ALL'
  state.viewState='LOADING'
  probe.event('LIFE_CONTENT_LIST_READ_START')
  if(trigger==='USER_RETRY')probe.increment('UserRetry')
  probe.increment('QueryCall')
  try{
    const result=await api.getList()
    state.viewState=result.viewState==='ERROR'?'READ_ERROR':result.viewState
    state.items=result.viewState==='READY'?result.items:[]
    if(state.items.length)probe.increment('Projection',state.items.length)
  }catch{
    state.items=[]
    state.viewState='READ_ERROR'
  }
}

export async function executeLifeContentDetailRead(
  state:LifeContentDetailPageState,
  api:Pick<LifeContentAnonymousReadApi,'getDetail'>,
  contentRef:string,
  contentVersion:string|null,
  trigger:LifeContentReadTrigger='FIRST_ENTRY',
  probe:LifeContentRuntimeProbe=NOOP_PROBE,
):Promise<void> {
  const generation=++state.readGeneration
  state.item=null
  state.viewState='LOADING'
  probe.event('LIFE_CONTENT_DETAIL_READ_START')
  if(!contentRef){state.viewState='READ_ERROR';return}
  if(trigger==='USER_RETRY')probe.increment('UserRetry')
  probe.increment('QueryCall')
  try{
    const result=await api.getDetail(contentRef,contentVersion)
    if(generation!==state.readGeneration)return
    state.viewState=result.viewState==='ERROR'?'READ_ERROR':result.viewState
    state.item=result.viewState==='READY'?result.item:null
    if(state.item){probe.increment('Projection');probe.increment('Body')}
  }catch{
    if(generation!==state.readGeneration)return
    state.item=null
    state.viewState='READ_ERROR'
  }
}

export function revokeLifeContentDetail(
  state:LifeContentDetailPageState,
  nextState:LifeContentDetailViewState='STALE',
):void {
  state.readGeneration+=1
  state.item=null
  state.viewState=nextState
}

export function buildLifeContentDetailRoute(item:Pick<LifeContentSummary,'contentRef'|'contentVersion'>):string {
  const version=item.contentVersion?`&contentVersion=${encodeURIComponent(item.contentVersion)}`:''
  return `/pages/life-content/detail?contentRef=${encodeURIComponent(item.contentRef)}${version}`
}

export function navigateToLifeContentDetail(
  item:Pick<LifeContentSummary,'contentRef'|'contentVersion'>,
  navigate:(url:string)=>void,
  probe:LifeContentRuntimeProbe=NOOP_PROBE,
):void {
  const url=buildLifeContentDetailRoute(item)
  probe.event('LIFE_CONTENT_DETAIL_NAVIGATION')
  probe.increment('Navigation')
  navigate(url)
}
