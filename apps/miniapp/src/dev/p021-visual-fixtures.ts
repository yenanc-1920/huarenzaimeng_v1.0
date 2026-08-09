import { parseP021Response, type P021Response } from '../api/order-detail-contract.ts'
import { p021SyntheticResponse } from '../api/order-detail-synthetic.ts'
import { executeP021Read, type P021PageState } from '../domain/order-detail-flow.ts'

export const P021_VISUAL_SCENARIO_IDS=[
  'P021-V01','P021-V02','P021-V03','P021-V04','P021-V05','P021-V06','P021-V07','P021-V08',
  'P021-V09','P021-V10','P021-V11','P021-V12','P021-V13','P021-V14','P021-V15','P021-V16',
] as const
export type P021VisualScenarioId=typeof P021_VISUAL_SCENARIO_IDS[number]
export const P021_V14_SUBCASES=['UNAUTHENTICATED','CROSS_SUBJECT','NOT_FOUND','REVOKED'] as const
export const P021_V16_SUBCASES=['LOWER_VERSION','LATE_GENERATION','SAME_VERSION_DIFFERENT_PROJECTION','TIMELINE_CONFLICT'] as const

const stateByScenario={
  'P021-V01':'AWAITING_PAYMENT','P021-V02':'PAYMENT_PROCESSING','P021-V03':'PAID_AWAITING_TOPUP','P021-V04':'TOPUP_PROCESSING',
  'P021-V05':'TOPUP_RESULT_UNKNOWN','P021-V06':'DELIVERED','P021-V07':'CONFIRMED_NOT_DELIVERED','P021-V08':'REFUND_PROCESSING',
  'P021-V09':'REFUNDED','P021-V10':'DELIVERY_REFUND_CONFLICT_REVIEW','P021-V11':'SUPPORT_REVIEW',
} as const

const expectedTitle={
  'P021-V01':'等待付款','P021-V02':'正在确认付款','P021-V03':'已付款，等待充值','P021-V04':'正在充值','P021-V05':'充值结果待确认','P021-V06':'已到账',
  'P021-V07':'已确认未到账','P021-V08':'退款处理中','P021-V09':'已退款','P021-V10':'到账与退款核对中','P021-V11':'客服处理中',
  'P021-V12':'正在读取订单详情','P021-V13':'暂时无法读取','P021-V14':'当前无法安全显示订单详情','P021-V15':'当前无法安全显示订单详情','P021-V16':'订单信息已更新',
} as const
const expectedViewState={
  'P021-V01':'READY','P021-V02':'READY','P021-V03':'READY','P021-V04':'READY','P021-V05':'READY','P021-V06':'READY','P021-V07':'READY','P021-V08':'READY',
  'P021-V09':'READY','P021-V10':'READY','P021-V11':'READY','P021-V12':'LOADING','P021-V13':'READ_ERROR','P021-V14':'NOT_AVAILABLE','P021-V15':'NOT_AVAILABLE','P021-V16':'INFORMATION_UPDATED',
} as const
export const P021_VISUAL_EXPECTATIONS=Object.freeze(Object.fromEntries(P021_VISUAL_SCENARIO_IDS.map(id=>[id,{
  scenarioId:id,pageId:'UX-P021',viewport:id==='P021-V05'||id==='P021-V06'||id==='P021-V10'||id==='P021-V12'||id==='P021-V14'?'320x568':'375x812',
  expectedViewState:expectedViewState[id],expectedTitle:expectedTitle[id],detailNodeCount:id<='P021-V11'?1:0,
  priceSummaryVisible:id<='P021-V11',timelineVisible:id<='P021-V11',refreshVisible:id!=='P021-V12',supportVisible:['P021-V05','P021-V07','P021-V10','P021-V11'].includes(id),safeBackVisible:true,
  fullViewportScreenshot:true,domSelectors:['[data-page-id="UX-P021"]','[data-view-state]','[data-action-container="readonly"]'],
  expectedDom:{
    uniqueText:[expectedTitle[id]],selectors:{pageRoot:1,statusRegion:1,detailContent:id<='P021-V11'?1:0,priceSummary:id<='P021-V11'?1:0,timeline:id<='P021-V11'?1:0,actionContainer:1},
    actions:[...(id!=='P021-V12'?['REFRESH_ORDER_DETAIL']:[]),...(['P021-V05','P021-V07','P021-V10','P021-V11'].includes(id)?['OPEN_SUPPORT']:[]),'SAFE_BACK'],
    keyboardOrder:[...(id!=='P021-V12'?['重新读取']:[]),...(['P021-V05','P021-V07','P021-V10','P021-V11'].includes(id)?['联系客服核对']:[]),'返回订单'],
    focusVisible:true,ax:{status:{name:expectedTitle[id],role:id==='P021-V13'||id==='P021-V14'||id==='P021-V15'?'alert':'status',live:'polite'},timeline:id<='P021-V11'?{name:'订单事实时间线',role:'list'}:null},
  },
  requiredEvidence:['ExpectedDOM','ActualDOM','ScreenshotSha256','ScreenshotDimensions','Viewport','Dpr','KeyboardTrace','AccessibilityTree'],
  forbiddenVisible:['stateCode','sessionVersion','authorizationSetRef','EvidenceRef','LOCAL_SYNTHETIC','Fixture','完整号码','支付写动作','充值写动作','退款写动作'],
  executionStatus:'NOT_RUN',
}])))

const session={role:'BUYER' as const,projectSubjectRef:'P021-VISUAL-SUBJECT',sessionVersion:1,authorizationSetRef:'P021-VISUAL-AUTH',authorizationEvidenceVersion:'P021-VISUAL-EVIDENCE',authorizedOrderRefs:['ORDER-P021-VISUAL'],issuedAt:'2026-08-03T00:00:00Z',expiresAt:'2099-08-03T00:00:00Z',semantics:'SERVER_PROJECTION_CACHE_NOT_AUTHORITY' as const}
const snapshot={priceSnapshotRef:'PRICE-P021-SYN-1',version:'DISPLAY-V1',totalAmount:125000,currency:'BDT',phone:'******1234',operator:'SYN Operator',product:'SYN Package',targetValueDisplay:'1000 BDT',targetCurrency:'BDT',validUntil:'2099-08-03T01:00:00Z'}
const runtime=()=>{const memory=new Map<string,unknown>([['projectSessionProjection',structuredClone(session)],['priceSnapshot:ORDER-P021-VISUAL',structuredClone(snapshot)]]);return{memory,storage:{getStorageSync:(key:string)=>memory.get(key),setStorageSync:(key:string,value:unknown)=>memory.set(key,value)}}}
const unavailableResponse=():P021Response=>parseP021Response({requestRef:null,outcome:'REJECTED',projectCode:'ORDER_DETAIL_NOT_AVAILABLE',resourceRef:null,aggregateVersion:null,currentProjection:null,retryClass:'NONE',nextPollAt:null})
const domSignature=(state:P021PageState)=>`${state.viewState}|detail=${state.projection?1:0}|price=${state.projection?1:0}|timeline=${state.projection?1:0}`

export function isP021VisualScenarioId(value:unknown):value is P021VisualScenarioId{return typeof value==='string'&&P021_VISUAL_SCENARIO_IDS.includes(value as P021VisualScenarioId)}

export async function runP021VisualScenario(state:P021PageState,scenarioId:P021VisualScenarioId,parameterId?:string):Promise<void>{
  const test=runtime(),orderRef='ORDER-P021-VISUAL',clock=()=>Date.parse('2026-08-03T01:00:00Z')
  if(scenarioId==='P021-V14'&&parameterId&&P021_V14_SUBCASES.includes(parameterId as typeof P021_V14_SUBCASES[number])){
    await runVisualSubcaseIntoState(state,test,scenarioId,parameterId,orderRef,clock);return
  }
  if(scenarioId==='P021-V16'&&parameterId&&P021_V16_SUBCASES.includes(parameterId as typeof P021_V16_SUBCASES[number])){
    await runVisualSubcaseIntoState(state,test,scenarioId,parameterId,orderRef,clock);return
  }
  const publicState=stateByScenario[scenarioId as keyof typeof stateByScenario]
  if(publicState){await executeP021Read(state,test.storage,{getOrderDetail:async()=>p021SyntheticResponse(orderRef,publicState,2)},orderRef,clock);return}
  if(scenarioId==='P021-V12'){void executeP021Read(state,test.storage,{getOrderDetail:()=>new Promise(()=>{})},orderRef,clock);return}
  if(scenarioId==='P021-V13'){await executeP021Read(state,test.storage,{getOrderDetail:async()=>{throw new Error('P021_VISUAL_READ_ERROR')}},orderRef,clock);return}
  if(scenarioId==='P021-V14'){test.memory.set('projectSessionProjection',{...session,role:'GUEST',projectSubjectRef:null,authorizationSetRef:null,authorizationEvidenceVersion:null,authorizedOrderRefs:[],issuedAt:null,expiresAt:null});await executeP021Read(state,test.storage,{getOrderDetail:async()=>p021SyntheticResponse(orderRef)},orderRef,clock);return}
  if(scenarioId==='P021-V15'){await executeP021Read(state,test.storage,{getOrderDetail:async()=>{test.memory.set('projectSessionProjection',{...session,authorizationSetRef:'P021-VISUAL-DRIFT'});return p021SyntheticResponse(orderRef)}},orderRef,clock);return}
  state.acceptedProjectionVersion=3;state.acceptedProjectionDigest='FROZEN-NEWER-PROJECTION'
  await executeP021Read(state,test.storage,{getOrderDetail:async()=>p021SyntheticResponse(orderRef,'TOPUP_PROCESSING',2)},orderRef,clock)
}

export async function runP021VisualSubcase(scenarioId:'P021-V14'|'P021-V16',parameterId:string){
  const test=runtime(),state=createState(),orderRef='ORDER-P021-VISUAL',clock=()=>Date.parse('2026-08-03T01:00:00Z')
  const result=await runVisualSubcaseIntoState(state,test,scenarioId,parameterId,orderRef,clock)
  return{scenarioId,parameterId,...result}
}

async function runVisualSubcaseIntoState(state:P021PageState,test:ReturnType<typeof runtime>,scenarioId:'P021-V14'|'P021-V16',parameterId:string,orderRef:string,clock:()=>number){
  let apiCallCount=0
  const api=(read:()=>Promise<P021Response>)=>({getOrderDetail:async()=>{apiCallCount++;return read()}})
  const seedVersion=scenarioId==='P021-V16'&&parameterId==='LOWER_VERSION'?3:2
  await executeP021Read(state,test.storage,api(async()=>p021SyntheticResponse(orderRef,'DELIVERED',seedVersion)),orderRef,clock)
  if(state.viewState!=='READY'||!state.projection)throw new Error('P021_VISUAL_OLD_DETAIL_SEED_FAILED')
  const oldContentFingerprint=`${state.projection.orderRef}|${state.projection.projectionVersion}|${state.projection.stateCode}`
  const before={viewState:state.viewState,detailNodeCount:1,oldContentFingerprint}
  apiCallCount=0
  if(scenarioId==='P021-V14'){
    if(parameterId==='UNAUTHENTICATED')test.memory.set('projectSessionProjection',{...session,role:'GUEST',projectSubjectRef:null,authorizationSetRef:null,authorizationEvidenceVersion:null,authorizedOrderRefs:[],issuedAt:null,expiresAt:null})
    if(parameterId==='UNAUTHENTICATED')await executeP021Read(state,test.storage,api(async()=>p021SyntheticResponse(orderRef)),orderRef,clock)
    else if(parameterId==='NOT_FOUND')await executeP021Read(state,test.storage,api(async()=>unavailableResponse()),orderRef,clock)
    else if(parameterId==='CROSS_SUBJECT')await executeP021Read(state,test.storage,api(async()=>{test.memory.set('projectSessionProjection',{...session,projectSubjectRef:'P021-VISUAL-OTHER-SUBJECT'});return p021SyntheticResponse(orderRef)}),orderRef,clock)
    else if(parameterId==='REVOKED')await executeP021Read(state,test.storage,api(async()=>{test.memory.set('projectSessionProjection',{...session,authorizationSetRef:'P021-VISUAL-REVOKED'});return p021SyntheticResponse(orderRef)}),orderRef,clock)
    else throw new Error('UNKNOWN_P021_V14_SUBCASE')
  }else if(parameterId==='LOWER_VERSION'){
    await executeP021Read(state,test.storage,api(async()=>p021SyntheticResponse(orderRef,'TOPUP_PROCESSING',2)),orderRef,clock)
  }else if(parameterId==='SAME_VERSION_DIFFERENT_PROJECTION'){
    await executeP021Read(state,test.storage,api(async()=>p021SyntheticResponse(orderRef,'REFUNDED',2)),orderRef,clock)
  }else if(parameterId==='TIMELINE_CONFLICT'){
    await executeP021Read(state,test.storage,api(async()=>{const raw=structuredClone(p021SyntheticResponse(orderRef,'DELIVERED',2));raw.currentProjection!.timeline[0].stateCode='TOPUP_PROCESSING';return parseP021Response(raw)}),orderRef,clock)
  }else if(parameterId==='LATE_GENERATION'){
    let resolveOld!:(value:P021Response)=>void
    const old=executeP021Read(state,test.storage,api(()=>new Promise(resolve=>{resolveOld=resolve})),orderRef,clock)
    await executeP021Read(state,test.storage,api(async()=>unavailableResponse()),orderRef,clock)
    resolveOld(p021SyntheticResponse(orderRef,'DELIVERED',2));await old
  }else throw new Error('UNKNOWN_P021_V16_SUBCASE')
  const finalFingerprint=state.projection?`${state.projection.orderRef}|${state.projection.projectionVersion}|${state.projection.stateCode}`:null
  const after={viewState:state.viewState,detailNodeCount:state.projection?1:0,oldContentFingerprintCount:finalFingerprint===oldContentFingerprint?1:0,domSignature:domSignature(state)}
  return{before,after,viewState:state.viewState,detailNodeCount:after.detailNodeCount,oldProjectionRestored:after.oldContentFingerprintCount>0,apiCallCount,domSignature:after.domSignature}
}

function createState():P021PageState{return{viewState:'LOADING',projection:null,generation:0,acceptedProjectionVersion:0,acceptedProjectionDigest:null}}

export interface P021VisualActual{
  titleMatches:{text:string;count:number;locations:string[]}[];viewport:{width:number;height:number};screenshot:{sha256:string;width:number;height:number;dpr:number;fullViewport:boolean;cropped:boolean;padded:boolean}
  selectors:Record<string,number>;actions:string[];keyboardOrder:string[];focusVisible:boolean;ax:unknown
  forbiddenFindings:Record<string,{count:number;locations:string[]}>;requiredEvidence:Record<string,unknown>
}
export interface P021V14ActualDOM{
  structure:Record<string,number>
  uniqueText:{text:string;count:number;locations:string[]}[]
  actions:string[]
  forbiddenFindings:Record<string,{count:number;locations:string[]}>
}

export function assertP021V14ActualDomSet(expected:any,actualBySubcase:Record<string,P021V14ActualDOM>){
  const signatures:string[]=[]
  for(const parameterId of P021_V14_SUBCASES){
    const actual=actualBySubcase[parameterId]
    if(!actual)throw new Error(`P021_V14_ACTUAL_DOM_MISSING:${parameterId}`)
    const title=actual.uniqueText.find(item=>item.text===expected.expectedTitle)
    const forbiddenComplete=expected.forbiddenVisible.every((term:string)=>Object.prototype.hasOwnProperty.call(actual.forbiddenFindings,term))
    const forbiddenZero=forbiddenComplete&&Object.values(actual.forbiddenFindings).every(item=>item.count===0&&item.locations.length===0)
    if(!title||title.count!==1||title.locations.length!==1||!forbiddenZero
      ||JSON.stringify(actual.structure)!==JSON.stringify(expected.expectedDom.selectors)
      ||JSON.stringify(actual.actions)!==JSON.stringify(expected.expectedDom.actions))throw new Error(`P021_V14_ACTUAL_DOM_MISMATCH:${parameterId}`)
    signatures.push(JSON.stringify({structure:actual.structure,uniqueText:actual.uniqueText,actions:actual.actions,forbiddenFindings:actual.forbiddenFindings}))
  }
  if(new Set(signatures).size!==1)throw new Error('P021_V14_ACTUAL_DOM_NOT_ISOMORPHIC')
  return true
}
export function assertP021VisualActual(expected:any,actual:P021VisualActual){
  const [width,height]=expected.viewport.split('x').map(Number)
  const title=actual.titleMatches.find(item=>item.text===expected.expectedTitle)
  const requiredComplete=expected.requiredEvidence.every((field:string)=>Object.prototype.hasOwnProperty.call(actual.requiredEvidence,field)&&actual.requiredEvidence[field]!==null&&actual.requiredEvidence[field]!==undefined)
  const forbiddenComplete=expected.forbiddenVisible.every((term:string)=>Object.prototype.hasOwnProperty.call(actual.forbiddenFindings,term))
  const forbiddenZero=forbiddenComplete&&Object.values(actual.forbiddenFindings).every(item=>item.count===0&&item.locations.length===0)
  if(!title||title.count!==1||title.locations.length!==1||actual.viewport.width!==width||actual.viewport.height!==height
    ||actual.screenshot.width!==width*actual.screenshot.dpr||actual.screenshot.height!==height*actual.screenshot.dpr||!actual.screenshot.sha256
    ||actual.screenshot.fullViewport!==true||actual.screenshot.cropped!==false||actual.screenshot.padded!==false||!requiredComplete||!forbiddenZero
    ||JSON.stringify(actual.selectors)!==JSON.stringify(expected.expectedDom.selectors)
    ||JSON.stringify(actual.actions)!==JSON.stringify(expected.expectedDom.actions)||JSON.stringify(actual.keyboardOrder)!==JSON.stringify(expected.expectedDom.keyboardOrder)
    ||actual.focusVisible!==true||JSON.stringify(actual.ax)!==JSON.stringify(expected.expectedDom.ax))throw new Error(`P021_VISUAL_ACTUAL_MISMATCH:${expected.scenarioId}`)
  return true
}
