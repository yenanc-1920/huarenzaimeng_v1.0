import type { CatalogItem, CatalogProjection, ContentErrorReportCommand, ContentErrorReportResult, DirectoryDetailResult, DirectoryItem, DirectorySummary, EligibilityResult, LifeContentDetailState, LifeContentReadState, OrderProjection, OrderSummary, ProjectSessionProjection, QuoteSnapshot, RechargeSelection, RecoveryResult, SupportCase } from '../domain/types'
import type { OrderCreationProjection } from './order-creation-contract'
import type { PaymentIntentCommand, PaymentIntentProjection, PaymentIntentQueryRequest, PaymentIntentQueryResult, PaymentIntentResult } from './payment-intent-contract.ts'
import type { TemporalOverview } from './temporal-overview-contract'

const wait = (ms = 180) => new Promise((resolve) => setTimeout(resolve, ms))
const MOCK_PAYMENT_INTENT_CREATION_PRECONDITION = 'PAYMENT_INTENT_MUST_NOT_EXIST' as const
const MOCK_PAYMENT_INTENT_ACTION = 'CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT' as const
const MOCK_PAYMENT_INTENT_QUERY_ACTION = 'QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT' as const
const MOCK_PAYMENT_INTENT_SCOPE = 'LOCAL_SYNTHETIC_ONLY' as const

const total = { minor: 1280, currency: 'CNY' as const }
const faceValue = { minor: 20000, currency: 'BDT' as const }

export async function mockEligibility(phone: string): Promise<EligibilityResult> {
  await wait()
  const maskedPhone = phone.length >= 4 ? `01•• •••• ${phone.slice(-2)}` : '01•• •••• 78'
  if (phone.endsWith('00')) {
    return { outcome: 'UNKNOWN', maskedPhone, caseKey: 'case_mock_a1', projectCode: 'PREPAY_MNP_UNKNOWN' }
  }
  if (phone.endsWith('99')) return { outcome: 'ELIGIBLE', maskedPhone, operatorCode: 'SYN-UNSUPPORTED', operatorName: '合成未支持运营商', caseKey: 'case_mock_unsupported' }
  if (phone.endsWith('88')) return { outcome: 'ELIGIBLE', maskedPhone, operatorCode: 'SYN-UNKNOWN', operatorName: '合成待确认运营商', caseKey: 'case_mock_unknown' }
  return { outcome: 'ELIGIBLE', maskedPhone, operatorCode: 'SYN-OP', operatorName: '合成运营商', caseKey: 'case_mock_ok' }
}

export async function mockCatalog(operatorCode: string): Promise<CatalogProjection> {
  await wait()
  if (operatorCode === 'SYN-UNKNOWN') return { operatorQualification:'UNKNOWN', supportedOperatorSetVersion:null, catalogVersion:null, operatorCode, items:[], evidenceSemantics:'LOCAL_MOCK_NO_REAL_OPERATOR_FACTS' }
  if (operatorCode !== 'SYN-OP') return { operatorQualification:'UNSUPPORTED', supportedOperatorSetVersion:1, catalogVersion:1, operatorCode, items:[], evidenceSemantics:'LOCAL_MOCK_NO_REAL_OPERATOR_FACTS' }
  const items:CatalogItem[] = [100, 200, 300, 500].map((amount) => ({
    operatorCode,
    productRef: `product_mock_${amount}`,
    denominationRef: `denomination_mock_${amount}`,
    itemKind: 'PRESET_DENOMINATION',
    faceValue: { minor: amount * 100, currency: 'BDT' as const },
    available: true as const,
  }))
  return { operatorQualification:'SUPPORTED', supportedOperatorSetVersion:1, catalogVersion:1, operatorCode, items, evidenceSemantics:'LOCAL_MOCK_NO_REAL_OPERATOR_FACTS' }
}

export async function mockQuote(selection: RechargeSelection): Promise<QuoteSnapshot> {
  await wait()
  return {
    orderRef: 'order_mock_001', priceSnapshotRef: 'quote_mock_001', maskedPhone:selection.maskedPhone, operatorCode:selection.operatorCode,
    operatorName:selection.operatorName, productRef:selection.productRef, productName:selection.itemKind==='PRESET_PACKAGE'?'合成预设套餐':'合成预设面额', denominationRef:selection.denominationRef,
    faceValue:selection.faceValue, total, priceVersion:'price-mock-v1', supportedOperatorSetVersion:selection.supportedOperatorSetVersion, catalogVersion:selection.catalogVersion,
    expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(), validUntil: new Date(Date.now() + 10 * 60 * 1000).toISOString(), valid: true,
  }
}

export async function mockProjection(orderRef: string): Promise<OrderProjection> {
  await wait()
  return {
    projectionId: `projection_${orderRef}_012`, orderRef, projectionVersion: orderRef === 'order_mock_002' ? 7 : 12,
    generatedAt: new Date().toISOString(), terminal: orderRef === 'order_mock_002', stateCode: orderRef === 'order_mock_002' ? 'COMPLETED' : 'TOPUP_RESULT_PENDING',
    facts: orderRef === 'order_mock_002'
      ? { payment: 'CONFIRMED', eligibility: 'CONFIRMED', upstream: 'CONFIRMED', delivery: 'CONFIRMED', refund: 'CONFIRMED', accounting: 'PENDING' }
      : { payment: 'CONFIRMED', eligibility: 'UNKNOWN', upstream: 'UNKNOWN', delivery: 'UNKNOWN', refund: 'NOT_STARTED', accounting: 'PENDING' },
    total, faceValue, priceSnapshotRef: 'quote_mock_001', priceValidUntil: new Date(Date.now() + 10 * 60 * 1000).toISOString(), maskedPhone: orderRef === 'order_mock_002' ? '01•• •••• 26' : '01•• •••• 78',
    operatorCode:'SYN-OP',operatorName:'合成运营商',productRef:'product_mock_200',productName:'合成预设面额',denominationRef:'denomination_mock_200',supportedOperatorSetVersion:1,catalogVersion:1,priceVersion:'price-mock-v1',
    allowedActions: [
      { actionCode: 'REFRESH', enabled: true, expectedProjectionVersion: 12 },
      { actionCode: 'SAFE_EXIT', enabled: true, expectedProjectionVersion: 12 },
    ],
    supportRef: 'SUP-MOCK-001', nextPollAt: new Date(Date.now() + 30000).toISOString(),
  }
}

interface PaymentIntentRecord { orderRef:string; command:PaymentIntentCommand; result:PaymentIntentResult }
const paymentIntentRecords = new Map<string,PaymentIntentRecord>()
const paymentIntentByCommandId = new Map<string,PaymentIntentRecord>()
const paymentIntentByIdempotencyKey = new Map<string,PaymentIntentRecord>()

export async function mockCreatePaymentIntent(
  orderRef:string,
  command:PaymentIntentCommand,
  trustedProjection:OrderCreationProjection,
):Promise<PaymentIntentResult>{
  await wait()
  if(!orderRef||command.paymentIntentCreationPrecondition!==MOCK_PAYMENT_INTENT_CREATION_PRECONDITION
    ||trustedProjection.orderRef!==orderRef||trustedProjection.stateCode!=='AWAITING_PAYMENT'
    ||trustedProjection.projectionVersion!==command.expectedProjectionVersion
    ||trustedProjection.aggregateVersion!==command.expectedAggregateVersion
    ||!trustedProjection.allowedActions.some((action)=>action.actionCode===MOCK_PAYMENT_INTENT_ACTION
      &&action.expectedProjectionVersion===command.expectedProjectionVersion
      &&action.expectedAggregateVersion===command.expectedAggregateVersion))throw new Error('PAYMENT_INTENT_NOT_AVAILABLE')
  const existingByOrder=paymentIntentRecords.get(orderRef)
  const existingByCommand=paymentIntentByCommandId.get(command.commandId)
  const existingByIdempotency=paymentIntentByIdempotencyKey.get(command.idempotencyKey)
  if(existingByCommand||existingByIdempotency){
    if(!existingByCommand||!existingByIdempotency||existingByCommand!==existingByIdempotency
      ||existingByCommand.orderRef!==orderRef||JSON.stringify(existingByCommand.command)!==JSON.stringify(command))throw new Error('IDEMPOTENCY_CONFLICT')
    return{...existingByCommand.result,projectCode:'PAYMENT_INTENT_REPLAYED'}
  }
  if(existingByOrder){
    if(JSON.stringify(existingByOrder.command)!==JSON.stringify(command))throw new Error('IDEMPOTENCY_CONFLICT')
    return{...existingByOrder.result,projectCode:'PAYMENT_INTENT_REPLAYED'}
  }
  const aggregateVersion=command.expectedAggregateVersion+1
  const currentProjection:PaymentIntentProjection={...trustedProjection,intentScope:MOCK_PAYMENT_INTENT_SCOPE,paymentInitiated:false,paymentConfirmed:false,
    projectionVersion:command.expectedProjectionVersion+1,aggregateVersion,
    allowedActions:[{actionCode:MOCK_PAYMENT_INTENT_QUERY_ACTION,expectedProjectionVersion:command.expectedProjectionVersion+1,expectedAggregateVersion:null}]}
  const result:PaymentIntentResult={requestRef:command.commandId,outcome:'ACCEPTED',projectCode:'PAYMENT_INTENT_CREATED',
    resourceRef:`PI-LOCAL-${orderRef}`,aggregateVersion,currentProjection,retryClass:'NONE'}
  const record={orderRef,command:{...command},result}
  paymentIntentRecords.set(orderRef,record);paymentIntentByCommandId.set(command.commandId,record);paymentIntentByIdempotencyKey.set(command.idempotencyKey,record)
  return result
}

export async function mockQueryPaymentIntent(orderRef:string,request:PaymentIntentQueryRequest):Promise<PaymentIntentQueryResult>{
  await wait()
  const byCommand=paymentIntentByCommandId.get(request.commandId),byIdempotency=paymentIntentByIdempotencyKey.get(request.idempotencyKey)
  const unavailable=():PaymentIntentQueryResult=>({requestRef:request.commandId,outcome:'REJECTED',projectCode:'PAYMENT_INTENT_QUERY_NOT_AVAILABLE',
    resourceRef:null,aggregateVersion:null,currentProjection:null,retryClass:'NONE',nextPollAt:null})
  if((byCommand&&byCommand.orderRef!==orderRef)||(byIdempotency&&byIdempotency.orderRef!==orderRef)||Boolean(byCommand)!==Boolean(byIdempotency))return unavailable()
  const existing=paymentIntentRecords.get(orderRef)
  if(!existing)return{requestRef:request.commandId,outcome:'UNKNOWN',projectCode:'PAYMENT_INTENT_RESULT_UNKNOWN',resourceRef:null,
    aggregateVersion:null,currentProjection:null,retryClass:'SAME_ACTION_QUERY_ONLY',nextPollAt:null}
  if(existing.command.commandId!==request.commandId||existing.command.idempotencyKey!==request.idempotencyKey)return unavailable()
  return{requestRef:request.commandId,outcome:'FOUND',projectCode:'PAYMENT_INTENT_RESULT_FOUND',resourceRef:existing.result.resourceRef,
    aggregateVersion:existing.result.aggregateVersion,currentProjection:existing.result.currentProjection,retryClass:'NONE',nextPollAt:null}
}

const orderRecords:OrderSummary[] = [
  { orderRef:'order_mock_001', stateCode:'TOPUP_RESULT_UNKNOWN', projectionVersion:12, updatedAt:new Date().toISOString() },
  { orderRef:'order_mock_002', stateCode:'DELIVERED', projectionVersion:7, updatedAt:new Date().toISOString() },
  { orderRef:'order_mock_003', stateCode:'SUPPORT_REVIEW', projectionVersion:5, updatedAt:new Date().toISOString() },
]
const guestSession=():ProjectSessionProjection=>({role:'GUEST',projectSubjectRef:null,sessionVersion:0,authorizationSetRef:null,authorizationEvidenceVersion:null,authorizedOrderRefs:[],issuedAt:null,expiresAt:null,semantics:'SERVER_PROJECTION_CACHE_NOT_AUTHORITY'})
let mockSession:ProjectSessionProjection=guestSession()
export async function mockOrders(session:ProjectSessionProjection): Promise<OrderSummary[]> {
  await wait()
  if(mockSession.role!=='BUYER'||session.role!=='BUYER')throw new Error('BUYER_SESSION_REQUIRED')
  if(Date.parse(session.expiresAt!)<=Date.now()||session.projectSubjectRef!==mockSession.projectSubjectRef||session.sessionVersion!==mockSession.sessionVersion
    ||session.authorizationSetRef!==mockSession.authorizationSetRef||session.authorizationEvidenceVersion!==mockSession.authorizationEvidenceVersion
    ||session.issuedAt!==mockSession.issuedAt||session.expiresAt!==mockSession.expiresAt
    ||session.authorizedOrderRefs.length!==mockSession.authorizedOrderRefs.length
    ||session.authorizedOrderRefs.some((ref,index)=>ref!==mockSession.authorizedOrderRefs[index]))throw new Error('SESSION_PROJECTION_STALE')
  const allowed=new Set(mockSession.authorizedOrderRefs);return orderRecords.filter((order)=>allowed.has(order.orderRef))
}
const recoveryCases=new Map<string,RecoveryResult>()
export async function mockRecovery(orderRef:string,recoveryMaterialRef:string): Promise<RecoveryResult> {
  await wait()
  const recoveryCaseRef=`RC-MOCK-${recoveryCases.size+1}`
  let result:RecoveryResult
  if(recoveryMaterialRef==='SYNTHETIC-RECOVERY-OK'){
    const sessionVersion=mockSession.sessionVersion+1
    const issuedAt=new Date().toISOString(),expiresAt=new Date(Date.now()+60*60*1000).toISOString()
    mockSession={role:'BUYER',projectSubjectRef:'SYN-SUBJECT-MOCK',sessionVersion,authorizationSetRef:`AUTHSET-MOCK-${sessionVersion}`,
      authorizationEvidenceVersion:`AUTH-EVIDENCE-MOCK-${sessionVersion}`,authorizedOrderRefs:['order_mock_001','order_mock_002'],issuedAt,expiresAt,
      semantics:'SERVER_PROJECTION_CACHE_NOT_AUTHORITY'}
    result={outcome:'RECOVERED',recoveryCaseRef,retryClass:'NONE',safeQueryPath:null,authorization:{projectSubjectRef:mockSession.projectSubjectRef!,sessionRole:'BUYER',
      sessionVersion,authorizationSetRef:mockSession.authorizationSetRef!,authorizationEvidenceVersion:mockSession.authorizationEvidenceVersion!,
      authorizedOrderRefs:[...mockSession.authorizedOrderRefs],issuedAt,expiresAt}}
  }else{
    mockSession=guestSession()
    result=recoveryMaterialRef==='SYNTHETIC-RECOVERY-UNKNOWN'
      ?{outcome:'UNKNOWN',recoveryCaseRef,retryClass:'READ_SAFE',safeQueryPath:`/api/v1/recovery-cases/${recoveryCaseRef}`,authorization:null}
      :{outcome:'REJECTED',recoveryCaseRef,retryClass:'NONE',safeQueryPath:null,authorization:null}
  }
  recoveryCases.set(recoveryCaseRef,result)
  return result
}
export async function mockRecoveryCase(recoveryCaseRef:string):Promise<RecoveryResult>{
  await wait()
  const result=recoveryCases.get(recoveryCaseRef)
  if(!result)throw new Error('RECOVERY_NOT_CONFIRMED')
  return result
}
export async function mockSupport(): Promise<SupportCase> { await wait(); return { supportRef:'SUP-MOCK-001', confirmed:'微信收款已确认', unknown:'用户到账待确认', responsibility:'公司继续查询', nextReviewAt:null } }
const directoryRecords: DirectoryItem[] = [
  { contentRef:'content_mock_001', contentVersion:3, title:'在孟实用信息（合成条目）', summary:'合成静态生活服务信息', category:'生活服务', sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2026-07-31T00:00:00Z', validUntil:'2099-08-01T00:00:00Z', updatedAt:'2026-08-01T00:00:00Z', disclaimer:'仅说明所列范围已核验，不构成官方认证、推荐或持续有效担保', publicationState:'ELIGIBLE' },
  { contentRef:'content_mock_expired', contentVersion:1, title:'过期合成条目（不可见）', summary:'过期状态样本', category:'生活服务', sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2025-01-01T00:00:00Z', validUntil:'2025-01-02T00:00:00Z', updatedAt:'2025-01-02T00:00:00Z', disclaimer:'不构成官方认证、推荐或持续有效担保', publicationState:'EXPIRED' },
  { contentRef:'content_mock_complaint', contentVersion:2, title:'投诉待查合成条目（不可见）', summary:'投诉待查状态样本', category:'生活服务', sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2026-07-01T00:00:00Z', validUntil:'2099-07-01T00:00:00Z', updatedAt:'2026-07-02T00:00:00Z', disclaimer:'不构成官方认证、推荐或持续有效担保', publicationState:'COMPLAINT_PENDING' },
  { contentRef:'content_mock_review', contentVersion:4, title:'复核中合成条目（不可见）', summary:'复核状态样本', category:'生活服务', sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2026-07-05T00:00:00Z', validUntil:'2099-07-05T00:00:00Z', updatedAt:'2026-07-06T00:00:00Z', disclaimer:'不构成官方认证、推荐或持续有效担保', publicationState:'REVIEW' },
  { contentRef:'content_mock_removed', contentVersion:5, title:'下架合成条目（不可见）', summary:'下架状态样本', category:'生活服务', sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2026-06-01T00:00:00Z', validUntil:'2099-06-01T00:00:00Z', updatedAt:'2026-06-02T00:00:00Z', disclaimer:'不构成官方认证、推荐或持续有效担保', publicationState:'REMOVED' },
  { contentRef:'content_mock_unknown', contentVersion:6, title:'状态未知合成条目（不可见）', summary:'未知状态样本', category:'生活服务', sourceCategory:'SELF_RESEARCH', sourceType:'自营调研来源', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2026-06-10T00:00:00Z', validUntil:'2099-06-10T00:00:00Z', updatedAt:'2026-06-11T00:00:00Z', disclaimer:'不构成官方认证、推荐或持续有效担保', publicationState:'UNKNOWN' },
]

export async function mockDirectory(): Promise<DirectorySummary[]> {
  await wait()
  return directoryRecords.filter((item) => item.publicationState === 'ELIGIBLE').map((item) => ({ contentRef:item.contentRef, contentVersion:item.contentVersion, title:item.title, summary:item.summary, category:item.category, validUntil:item.validUntil, updatedAt:item.updatedAt, publicationState:'ELIGIBLE' }))
}

export async function mockDirectoryDetail(contentRef: string, contentVersion: number): Promise<DirectoryDetailResult> {
  await wait()
  const item = directoryRecords.find((candidate) => candidate.contentRef === contentRef)
  if (!item) return { outcome: 'EMPTY' }
  if (item.contentVersion !== contentVersion) return { outcome: 'UNKNOWN' }
  if (item.publicationState === 'ELIGIBLE') return { outcome: 'READY', item: { ...item, publicationState: 'ELIGIBLE' } }
  return { outcome: item.publicationState }
}

const contentReportReplays = new Map<string, { identity:string; fingerprint:string; receipt:ContentErrorReportResult }>()
export async function mockReportDirectoryError(contentRef: string, command: ContentErrorReportCommand): Promise<ContentErrorReportResult> {
  await wait()
  const identity = `${command.commandId}|${command.idempotencyKey}`
  const fingerprint = JSON.stringify({ contentRef, ...command })
  const byCommand = contentReportReplays.get(`command:${command.commandId}`)
  const byIdempotency = contentReportReplays.get(`idempotency:${command.idempotencyKey}`)
  if (byCommand || byIdempotency) {
    const replay = byCommand || byIdempotency!
    if (replay.identity !== identity || replay.fingerprint !== fingerprint) throw new Error('IDEMPOTENCY_CONFLICT')
    return replay.receipt
  }
  if (command.contentVersion !== command.expectedAggregateVersion) throw new Error('CONTENT_VERSION_STALE')
  const detail = await mockDirectoryDetail(contentRef, command.contentVersion)
  if (detail.outcome !== 'READY') throw new Error('CONTENT_NOT_REPORTABLE')
  const receipt:ContentErrorReportResult = { outcome:'CONTENT_ERROR_REPORTED', supportRef:`SUPPORT-${contentRef}-V${command.contentVersion + 1}`, reviewTarget:'A120' }
  contentReportReplays.set(`command:${command.commandId}`, { identity, fingerprint, receipt })
  contentReportReplays.set(`idempotency:${command.idempotencyKey}`, { identity, fingerprint, receipt })
  const current = directoryRecords.find((item) => item.contentRef === contentRef)!
  current.contentVersion += 1
  current.publicationState = 'REVIEW'
  return receipt
}

const lifeContentFixtures = [
  {
    contentRef:'SYN-LIFE-REMINDER-001',contentVersion:'SYN-V1',category:'LIFE_REMINDER',title:'生活提醒示例',summary:'用于本地页面预览的占位信息',
    sourceType:'页面预览信息',jurisdiction:'页面预览范围',applicableAudience:'页面预览',publishedAt:'2026-08-01T00:00:00Z',
    updatedAt:'2026-08-01T00:00:00Z',effectiveFrom:'2026-08-01T00:00:00Z',effectiveTo:'2099-12-31T23:59:59Z',freshnessState:'CURRENT',
    coverState:'NOT_CONFIGURED',coverRef:null,body:'此处为本地页面占位内容，不代表真实资讯已经发布。',
  },
  {
    contentRef:'SYN-HOLIDAY-001',contentVersion:'SYN-V1',category:'HOLIDAY_EXPLANATION',title:'节假日说明示例',summary:'用于检查图片不可用时的文字阅读',
    sourceType:'页面预览信息',jurisdiction:'页面预览范围',applicableAudience:'页面预览',publishedAt:'2026-08-01T00:00:00Z',
    updatedAt:'2026-08-01T00:00:00Z',effectiveFrom:'2026-08-01T00:00:00Z',effectiveTo:'2099-12-31T23:59:59Z',freshnessState:'CURRENT',
    coverState:'IMAGE_UNAVAILABLE',coverRef:null,body:'图片暂时无法显示，页面仍保留这段占位文字供版式检查。',
  },
] as const

const lifeListProjectCodes:Record<LifeContentReadState,string> = {
  READY:'LIFE_CONTENT_LIST_READY',EMPTY:'LIFE_CONTENT_LIST_EMPTY',UNDER_REVIEW:'LIFE_CONTENT_LIST_UNDER_REVIEW',
  EXPIRED:'LIFE_CONTENT_LIST_EXPIRED',REMOVED:'LIFE_CONTENT_LIST_REMOVED',UNKNOWN:'LIFE_CONTENT_LIST_UNKNOWN',ERROR:'LIFE_CONTENT_LIST_ERROR',
}
const lifeDetailProjectCodes:Record<LifeContentDetailState,string> = {
  READY:'LIFE_CONTENT_DETAIL_READY',UNDER_REVIEW:'LIFE_CONTENT_DETAIL_UNDER_REVIEW',EXPIRED:'LIFE_CONTENT_DETAIL_EXPIRED',
  REMOVED:'LIFE_CONTENT_DETAIL_REMOVED',UNKNOWN:'LIFE_CONTENT_DETAIL_UNKNOWN',ERROR:'LIFE_CONTENT_DETAIL_ERROR',
}

export async function mockLifeContentListDto(viewState:LifeContentReadState='READY'):Promise<unknown> {
  await wait()
  return {requestRef:`SYN-LIFE-LIST-${viewState}`,viewState,projectCode:lifeListProjectCodes[viewState],schemaVersion:'LIFE_CONTENT_READ_V1',
    visibilityRuleVersion:'SYN-VISIBILITY-V1',items:viewState==='READY'?lifeContentFixtures.map(({body,...item})=>item):[],
    retryClass:viewState==='UNKNOWN'||viewState==='ERROR'?'USER_INITIATED_READ_ONLY':'NONE',nextReadAt:null}
}

export async function mockLifeContentDetailDto(contentRef:string,contentVersion:string,forcedState?:LifeContentDetailState):Promise<unknown> {
  await wait()
  const fixture=lifeContentFixtures.find((item)=>item.contentRef===contentRef&&item.contentVersion===contentVersion)
  const viewState:LifeContentDetailState=forcedState||(fixture?'READY':'REMOVED')
  return {requestRef:`SYN-LIFE-DETAIL-${viewState}`,viewState,projectCode:lifeDetailProjectCodes[viewState],schemaVersion:'LIFE_CONTENT_READ_V1',
    visibilityRuleVersion:'SYN-VISIBILITY-V1',contentRef,contentVersion,item:viewState==='READY'?fixture:null,
    retryClass:viewState==='UNKNOWN'||viewState==='ERROR'?'USER_INITIATED_READ_ONLY':'NONE',nextReadAt:null}
}

export async function mockTemporalOverviewDto():Promise<TemporalOverview> {
  await wait()
  const referenceInstant='2026-08-02T06:00:00Z'
  return {
    requestRef:'SYN-TEMPORAL-OVERVIEW-001',projectCode:'TEMPORAL_OVERVIEW_READY',schemaVersion:'TEMPORAL_OVERVIEW_V1',
    referenceInstant,generatedAt:'2026-08-02T06:00:01Z',timeZoneRuleVersion:'SYN-IANA-2026A',clockStaleAfterSeconds:300,
    clockState:'BOTH_AVAILABLE',
    clocks:{
      dhaka:{cityCode:'DHAKA',displayName:'达卡',zoneId:'Asia/Dhaka',localDate:'2026-08-02',localTime:'12:00',availabilityState:'AVAILABLE'},
      beijing:{cityCode:'BEIJING',displayName:'北京',zoneId:'Asia/Shanghai',localDate:'2026-08-02',localTime:'14:00',availabilityState:'AVAILABLE'},
    },
    holidayRuleVersion:'SYN-HOLIDAY-RULE-V1',
    holidays:{
      china:{countryCode:'CN',localDate:'2026-08-02',state:'NO_HOLIDAY_CONFIRMED',holidayId:null,name:null,note:null,
        sourceType:'LOCAL_SYNTHETIC_CALENDAR',sourceCoverageDate:'2026-08-02',effectiveFrom:'2026-08-02T00:00:00Z',effectiveTo:'2026-08-02T23:59:59Z',version:'SYN-CN-20260802'},
      bangladesh:{countryCode:'BD',localDate:'2026-08-02',state:'NO_HOLIDAY_CONFIRMED',holidayId:null,name:null,note:null,
        sourceType:'LOCAL_SYNTHETIC_CALENDAR',sourceCoverageDate:'2026-08-02',effectiveFrom:'2026-08-02T00:00:00Z',effectiveTo:'2026-08-02T23:59:59Z',version:'SYN-BD-20260802'},
    },
    retryClass:'NONE',
  }
}
