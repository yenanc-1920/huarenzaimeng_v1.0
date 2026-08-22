import { parseProjectProjection, parseProjectQuote, toOrderProjection, toQuoteSnapshot, type ProjectProjection } from './project-contract'
import { parseCatalogProjection, parseRecoveryResult, selectionMatchesCatalog } from './topup-recovery-contract'
import { acceptNewerProjection } from '../domain/projection'
import { getOrCreateCommand } from '../domain/command-identity'
import { canonicalFingerprint } from '../domain/canonical-fingerprint'
import { readPendingRecoveryCaseRef } from '../domain/session'
import { parseAcceptedProjectEnvelope, ProjectApiError } from './project-envelope'
import { buildOrderCreationCommand, parseOrderCreationResult, type OrderCreationResult } from './order-creation-contract'
import type { CatalogProjection, ContentErrorReportResult, DirectoryCity, DirectoryItem, DirectorySummary, EligibilityResult, LifeContentDetailResult, LifeContentListResult, OrderProjection, OrderSummary, QuoteSnapshot, RechargeSelection, RecoveryResult, SupportCase } from '../domain/types'
import type { TemporalOverviewReadResponse } from './temporal-overview-contract'
import { P014_BACKEND_IMPLEMENTATION_SHA } from './p014-topup-contract'
import { callProjectApi } from './wechat-development-transport'
import { readBuyerSessionToken, requireBuyerBearerToken } from './buyer-session-token'
import { commitAnonymousSessionToken, parseAnonymousSessionResponse, readAnonymousSessionToken } from './anonymous-session-contract'
import { sessionConsentCommand } from '../domain/login-privacy-state'
import { readOrCreateBuyerGuestRef } from '../domain/buyer-guest-ref'
import { createFormalTransactionClient, type FormalTransactionResponse } from './formal-transaction-client'
import type { PaymentCreateContext } from './formal-transaction-client'
import { parseReleaseOrderProjection, parseReleaseOrderView, parseReleaseQuoteView, type ReleaseOrderProjection, type ReleaseOrderView } from './formal-transaction-contract'

const baseUrl = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
const buyerBaseUrl = (import.meta.env.VITE_BUYER_API_BASE_URL || '/buyer-api/v1').replace(/\/$/, '')
const buyerAuthBaseUrl = (import.meta.env.VITE_BUYER_AUTH_BASE_URL || '/buyer-auth/v1').replace(/\/$/, '')
const contentBaseUrl = (import.meta.env.VITE_CONTENT_API_BASE_URL || '/project-api/v1').replace(/\/$/, '')
const useWechatDevelopment = import.meta.env.VITE_PROJECT_TRANSPORT_MODE === 'WECHAT_DEVELOPMENT'
const orderRecoveryExternalAuthReady = import.meta.env.VITE_ORDER_RECOVERY_EXTERNAL_AUTH_READY === 'true'

export { ProjectApiError } from './project-envelope'

function anonymousSessionResponse(requestRef:string):Promise<{statusCode:number;data:unknown;header?:unknown}>{
  const consent=sessionConsentCommand({userAgreementAccepted:true,privacyPolicyAccepted:true})
  if(!consent)throw new ProjectApiError('ANONYMOUS_SESSION_REQUEST_INVALID')
  const body={requestRef,guestRef:readOrCreateBuyerGuestRef(uni),consent}
  if(useWechatDevelopment)return callProjectApi('/buyer-auth/v1/anonymous-sessions','POST',body)
  return new Promise((resolve,reject)=>uni.request({
    url:`${buyerAuthBaseUrl}/anonymous-sessions`,method:'POST',data:body,
    success:({data:response,statusCode,header})=>resolve({statusCode,data:response,header}),
    fail:()=>reject(new ProjectApiError('ANONYMOUS_SESSION_UNAVAILABLE')),
  }))
}
async function transactionBearer(data:unknown):Promise<string>{
  const buyer=readBuyerSessionToken()
  if(buyer)return buyer.token
  const cached=readAnonymousSessionToken()
  if(cached)return cached.token
  if(!data||typeof data!=='object'||Array.isArray(data)||typeof (data as Record<string,unknown>).requestRef!=='string')throw new ProjectApiError('ANONYMOUS_SESSION_REQUEST_INVALID')
  const requestRef=(data as Record<string,string>).requestRef
  try{
    const result=parseAnonymousSessionResponse(await anonymousSessionResponse(requestRef),requestRef)
    commitAnonymousSessionToken(result)
    return result.token
  }catch{throw new ProjectApiError('ANONYMOUS_SESSION_UNAVAILABLE')}
}
async function requestBody(path: string, method: 'GET' | 'POST', data?: UniNamespace.RequestOptions['data'], root = baseUrl, extraHeaders:Record<string,string>={}): Promise<unknown> {
  const protectedRequest = path === '/quotes' || path === '/orders' || path.startsWith('/orders/') || path === '/recovery-cases' || path.startsWith('/recovery-cases/')
  const anonymousEligible=method==='POST'&&root===baseUrl&&(path==='/quotes'||path==='/orders')
  const bearer=anonymousEligible?await transactionBearer(data):protectedRequest?requireBuyerBearerToken():undefined
  if (useWechatDevelopment) {
    const effectiveRoot=protectedRequest&&root===baseUrl?buyerBaseUrl:root
    return callProjectApi(`${effectiveRoot}${path}`, method, data, bearer,extraHeaders).then(result => {
      if (result.statusCode < 200 || result.statusCode >= 300) throw new ProjectApiError('HTTP_STATUS_REJECTED')
      return result.data
    })
  }
  return new Promise((resolve, reject) => uni.request({
    url: `${protectedRequest&&root===baseUrl?buyerBaseUrl:root}${path}`,
    method,
    data,
    header: protectedRequest
      ? { ...extraHeaders,Authorization: `Bearer ${bearer}` }
      : extraHeaders,
    success: ({ data: body }) => resolve(body),
    fail: () => reject(new ProjectApiError('NETWORK_ERROR')),
  }))
}
function requestBuyerTransaction(path:string,method:'GET'|'POST',data?:Record<string,unknown>):Promise<FormalTransactionResponse>{
  if(useWechatDevelopment)return callProjectApi(`${buyerBaseUrl}${path}`,method,data,requireBuyerBearerToken()).then(result=>({statusCode:result.statusCode,body:result.data}))
  return new Promise((resolve,reject)=>uni.request({
    url:`${buyerBaseUrl}${path}`,method,data,header:{Authorization:`Bearer ${requireBuyerBearerToken()}`},
    success:({data:body,statusCode})=>resolve({statusCode,body}),fail:()=>reject(new ProjectApiError('NETWORK_ERROR')),
  }))
}
const formalTransactions=createFormalTransactionClient({request:requestBuyerTransaction})
function requestAnonymousRead(path:string):Promise<unknown> {
  if(useWechatDevelopment)return callProjectApi(`${baseUrl}${path}`,'GET').then(result=>result.data)
  return new Promise((resolve,reject)=>uni.request({
    url:`${baseUrl}${path}`,
    method:'GET',
    success:({data:body})=>resolve(body),
    fail:()=>reject(new ProjectApiError('NETWORK_ERROR')),
  }))
}
function readCacheControlHeader(headers:unknown):string|null {
  if(!headers||typeof headers!=='object'||Array.isArray(headers))return null
  const entries=Object.entries(headers as Record<string,unknown>).filter(([key])=>key.toLowerCase()==='cache-control')
  return entries.length===1&&typeof entries[0][1]==='string'?entries[0][1]:null
}
function requestTemporalOverviewRead():Promise<TemporalOverviewReadResponse> {
  if(useWechatDevelopment)return callProjectApi(`${baseUrl}/home/temporal-overview`,'GET').then(result=>({body:result.data,statusCode:result.statusCode,cacheControl:readCacheControlHeader(result.header)}))
  return new Promise((resolve,reject)=>uni.request({
    url:`${baseUrl}/home/temporal-overview`,
    method:'GET',
    success:({data:body,header,statusCode})=>resolve({body,statusCode,cacheControl:readCacheControlHeader(header)}),
    fail:()=>reject(new ProjectApiError('NETWORK_ERROR')),
  }))
}
function requestTrustedSessionRead(path:string):Promise<unknown>{
  if(useWechatDevelopment)return callProjectApi(`/buyer-api/v1${path}`,'GET',undefined,requireBuyerBearerToken()).then(result=>result.statusCode>=200&&result.statusCode<300?result.data:Promise.reject(new ProjectApiError('HTTP_STATUS_REJECTED')))
  return new Promise((resolve,reject)=>uni.request({
    url:`${buyerBaseUrl}${path}`,method:'GET',header:{Authorization:`Bearer ${requireBuyerBearerToken()}`},
    success:({data:body,statusCode})=>statusCode>=200&&statusCode<300?resolve(body):reject(new ProjectApiError('HTTP_STATUS_REJECTED')),
    fail:()=>reject(new ProjectApiError('NETWORK_ERROR')),
  }))
}
async function requestData(path: string, method: 'GET' | 'POST', data?: UniNamespace.RequestOptions['data'], root = baseUrl): Promise<unknown> {
  return parseAcceptedProjectEnvelope(await requestBody(path,method,data,root))
}

const projectProjection = async (path: string, method: 'GET' | 'POST', data?: UniNamespace.RequestOptions['data']): Promise<ProjectProjection> => parseProjectProjection(await requestData(path, method, data))
function localScopeFingerprint(value:string):string{let hash=2166136261;for(let index=0;index<value.length;index++){hash^=value.charCodeAt(index);hash=Math.imul(hash,16777619)}return(hash>>>0).toString(36)}
const record=(value:unknown):value is Record<string,unknown>=>!!value&&typeof value==='object'&&!Array.isArray(value)
const requiredText=(value:unknown):value is string=>typeof value==='string'&&value.trim().length>0
function orderSummaryState(value:string):OrderSummary['stateCode']{
  const mapped:Record<string,OrderSummary['stateCode']>={AWAITING_PAYMENT:'AWAITING_PAYMENT',PAYMENT_CONFIRMED:'PAID_AWAITING_TOPUP',TOPUP_REVIEW:'TOPUP_PROCESSING',COMPLETED:'DELIVERED'}
  const state=mapped[value]
  if(!state)throw new ProjectApiError('INVALID_ORDER_STATE_DTO')
  return state
}
function parseSupportedOperators(value:unknown):Array<{operatorCode:string;displayName:string}>{
  if(!record(value)||!Array.isArray(value.supportedOperators))throw new ProjectApiError('INVALID_SUPPORTED_OPERATORS_DTO')
  return value.supportedOperators.map(entry=>{
    if(!record(entry)||!requiredText(entry.operatorCode)||!requiredText(entry.displayName))throw new ProjectApiError('INVALID_SUPPORTED_OPERATOR_DTO')
    return{operatorCode:entry.operatorCode,displayName:entry.displayName}
  })
}
function parseDirectoryCities(value:unknown):DirectoryCity[]{if(!Array.isArray(value))throw new ProjectApiError('INVALID_DIRECTORY_CITIES_DTO');return value.map(row=>{if(!record(row)||!requiredText(row.cityCode)||!requiredText(row.countryCode)||!requiredText(row.displayName)||!requiredText(row.localName)||!requiredText(row.timezoneId))throw new ProjectApiError('INVALID_DIRECTORY_CITY_DTO');return row as unknown as DirectoryCity})}
function parseDirectoryEntries(value:unknown):DirectorySummary[]{if(!Array.isArray(value))throw new ProjectApiError('INVALID_DIRECTORY_ENTRIES_DTO');return value.map(row=>{if(!record(row)||!requiredText(row.entryRef)||!requiredText(row.cityCode)||!requiredText(row.cityName)||!requiredText(row.category)||!requiredText(row.displayName)||!requiredText(row.summary)||!requiredText(row.localAddress)||!requiredText(row.phone)||!requiredText(row.verifiedAt)||!requiredText(row.validUntil)||!requiredText(row.updatedAt))throw new ProjectApiError('INVALID_DIRECTORY_ENTRY_DTO');return row as unknown as DirectorySummary})}
function parseDirectoryItem(value:unknown):DirectoryItem{const items=parseDirectoryEntries([value]);return value&&record(value)&&typeof value.sourceLabel==='string'?{...items[0],sourceLabel:value.sourceLabel}:items[0]}

async function loadCatalog(operatorCode:string):Promise<CatalogProjection>{
  if(!operatorCode)throw new ProjectApiError('OPERATOR_CODE_REQUIRED')
  return parseCatalogProjection(await requestData(`/catalog?operatorCode=${encodeURIComponent(operatorCode)}`,'GET'))
}

export const api = {
  createPayment:(orderRef:string,context:PaymentCreateContext)=>formalTransactions.createPayment(orderRef,context),
  getPaymentStatus:(orderRef:string)=>formalTransactions.paymentStatus(orderRef),
  queryPaymentOriginal:(orderRef:string)=>formalTransactions.queryPaymentOriginal(orderRef),
  createRefund:(orderRef:string,refundRef:string,amountMinor:number)=>formalTransactions.createRefund(orderRef,refundRef,amountMinor),
  getRefundStatus:(orderRef:string,refundRef:string)=>formalTransactions.refundStatus(orderRef,refundRef),
  queryRefundOriginal:(orderRef:string,refundRef:string)=>formalTransactions.queryRefundOriginal(orderRef,refundRef),
  submitTopup:(orderRef:string,requestRef:string)=>formalTransactions.submitTopup(orderRef,requestRef),
  getTopupStatus:(orderRef:string)=>formalTransactions.topupStatus(orderRef),
  queryTopupOriginal:(orderRef:string)=>formalTransactions.queryTopupOriginal(orderRef),
  checkEligibility: async (phone: string): Promise<EligibilityResult> => {
    if(!phone.trim())throw new ProjectApiError('PHONE_REQUIRED')
    const value=await requestData(`/eligibility?phone=${encodeURIComponent(phone.trim())}`,'GET')
    if(!record(value)||!requiredText(value.maskedPhone)||!requiredText(value.operatorCode)||!requiredText(value.operatorName)||!requiredText(value.projectCode))throw new ProjectApiError('INVALID_ELIGIBILITY_DTO')
    const caseKey=`ELIGIBILITY-${value.maskedPhone}`
    return value.operatorCode==='UNKNOWN'
      ?{outcome:'UNKNOWN',maskedPhone:value.maskedPhone,caseKey,projectCode:'PREPAY_MNP_UNKNOWN'}
      :{outcome:'ELIGIBLE',recipientPhone:`+880${phone.trim().replace(/\D/g,'').replace(/^880/,'').replace(/^0/,'')}`,maskedPhone:value.maskedPhone,operatorCode:value.operatorCode,operatorName:value.operatorName,caseKey}
  },
  async getSupportedOperators():Promise<Array<{operatorCode:string;displayName:string}>>{
    return parseSupportedOperators(await requestData('/catalog?operatorCode=UNKNOWN','GET'))
  },
  getCatalog: loadCatalog,
  async createQuote(selection:RechargeSelection): Promise<QuoteSnapshot> {
    const current=await loadCatalog(selection.operatorCode)
    if(!selectionMatchesCatalog(selection,current))throw new ProjectApiError('CATALOG_SELECTION_STALE')
    const identity=getOrCreateCommand(uni,`create-quote:${localScopeFingerprint(JSON.stringify(selection))}`)
    const quote=parseReleaseQuoteView(parseAcceptedProjectEnvelope(await requestBody('/quotes','POST',{
      requestRef:identity.commandId,phone:selection.recipientPhone,productRef:selection.productRef,
    },baseUrl,{'Idempotency-Key':identity.idempotencyKey})))
    if(quote.requestRef!==identity.commandId||quote.operatorCode!==selection.operatorCode||quote.productRef!==selection.productRef
      ||quote.catalogVersion!==selection.catalogVersion||quote.priceVersionRef!==selection.priceVersionRef)throw new ProjectApiError('QUOTE_SELECTION_MISMATCH')
    if(quote.currency!=='CNY')throw new ProjectApiError('QUOTE_CURRENCY_UNSUPPORTED')
    const totalMinor=Math.round(quote.finalAmountCny*100)
    return{orderRef:'',priceSnapshotRef:quote.quoteRef,maskedPhone:quote.phoneMasked,operatorCode:quote.operatorCode,operatorName:selection.operatorName,
      productRef:quote.productRef,productName:selection.displayName,denominationRef:selection.denominationRef,
      entitlement:{productType:selection.productType,displayName:selection.displayName,benefitText:selection.benefitText,validityText:selection.validityText,source:'CATALOG_SNAPSHOT'},
      faceValue:selection.faceValue,total:{minor:totalMinor,currency:quote.currency},priceVersion:quote.priceVersionRef,
      supportedOperatorSetVersion:selection.supportedOperatorSetVersion,catalogVersion:quote.catalogVersion,expiresAt:quote.validUntil,validUntil:quote.validUntil,valid:Date.parse(quote.validUntil)>Date.now()}
  },
  async createOrder(quoteRef: string, commandId: string, idempotencyKey: string): Promise<ReleaseOrderView> {
    if(!quoteRef||!commandId||!idempotencyKey)throw new ProjectApiError('ORDER_CREATION_INPUT_REQUIRED')
    return parseReleaseOrderView(parseAcceptedProjectEnvelope(await requestBody('/orders','POST',{requestRef:commandId,quoteRef},baseUrl,{'Idempotency-Key':idempotencyKey})))
  },
  async getP014Progress(orderRef:string):Promise<ReleaseOrderProjection>{
    if(!orderRef)throw new ProjectApiError('ORDER_REF_REQUIRED')
    return parseReleaseOrderProjection(parseAcceptedProjectEnvelope(await requestTrustedSessionRead(`/orders/${encodeURIComponent(orderRef)}`)))
  },
  async getP014TopupResult(orderRef:string,_query:unknown):Promise<ReleaseOrderProjection>{
    if(!orderRef)throw new ProjectApiError('ORDER_REF_REQUIRED')
    return parseReleaseOrderProjection(parseAcceptedProjectEnvelope(await requestTrustedSessionRead(`/orders/${encodeURIComponent(orderRef)}`)))
  },
  async getOrderDetail(orderRef:string):Promise<ReleaseOrderProjection>{
    if(!orderRef)throw new ProjectApiError('ORDER_REF_REQUIRED')
    return parseReleaseOrderProjection(parseAcceptedProjectEnvelope(await requestTrustedSessionRead(`/orders/${encodeURIComponent(orderRef)}`)))
  },
  async getCoreProjection(orderRef:string):Promise<ReleaseOrderView>{
    if(!orderRef)throw new ProjectApiError('ORDER_REF_REQUIRED')
    return parseReleaseOrderView(await requestData(`/orders/${encodeURIComponent(orderRef)}/projection`,'GET'))
  },
  async getProjection(orderRef: string): Promise<OrderProjection> {
    if (!orderRef) throw new Error('ORDER_REF_REQUIRED')
    const projection = toOrderProjection(await projectProjection(`/orders/${encodeURIComponent(orderRef)}/projection`, 'GET'))
    return acceptNewerProjection(orderRef, projection)
  },
  async getOrders():Promise<OrderSummary[]>{
    requireBuyerBearerToken()
    const data=parseAcceptedProjectEnvelope(await requestTrustedSessionRead('/orders'))
    if(!Array.isArray(data))throw new ProjectApiError('INVALID_ORDER_LIST_DTO')
    return data.map(value=>{const order=parseReleaseOrderProjection(value);return{orderRef:order.orderRef,stateCode:orderSummaryState(order.orderState),projectionVersion:order.projectionVersion,updatedAt:`v${order.projectionVersion}`}})
  },
  async recoverOrder(orderRef:string,recoveryMaterialRef:string):Promise<RecoveryResult>{
    if(!orderRef||!recoveryMaterialRef)throw new ProjectApiError('RECOVERY_INPUT_REQUIRED')
    if(readPendingRecoveryCaseRef(uni))throw new ProjectApiError('RECOVERY_CASE_POLL_REQUIRED')
    if(!orderRecoveryExternalAuthReady)throw new ProjectApiError('ORDER_RECOVERY_EXTERNAL_AUTH_NOT_READY')
    const fingerprint=canonicalFingerprint(orderRef,recoveryMaterialRef)
    const identity=getOrCreateCommand(uni,`recover-order:${localScopeFingerprint(fingerprint)}`)
    return parseRecoveryResult(await requestData('/recovery-cases','POST',{commandId:identity.commandId,idempotencyKey:identity.idempotencyKey,
      recoveryInputFingerprint:fingerprint,creationPrecondition:'RECOVERY_CASE_MUST_NOT_EXIST',orderRef,recoveryMaterialRef},buyerAuthBaseUrl))
  },
  async getRecoveryCase(recoveryCaseRef:string):Promise<RecoveryResult>{
    if(!recoveryCaseRef)throw new ProjectApiError('RECOVERY_CASE_REF_REQUIRED')
    if(!orderRecoveryExternalAuthReady)throw new ProjectApiError('ORDER_RECOVERY_EXTERNAL_AUTH_NOT_READY')
    return parseRecoveryResult(await requestData(`/recovery-cases/${encodeURIComponent(recoveryCaseRef)}`,'GET',undefined,buyerAuthBaseUrl))
  },
  getSupportCase: async (): Promise<SupportCase> => await requestTrustedSessionRead('/support/case') as SupportCase,
  async getDirectoryCities():Promise<DirectoryCity[]>{return parseDirectoryCities(await requestData('/directory/cities','GET'))},
  async getDirectory(cityCode?:string,category?:string): Promise<DirectorySummary[]> {const query:string[]=[];if(cityCode)query.push(`cityCode=${encodeURIComponent(cityCode)}`);if(category)query.push(`category=${encodeURIComponent(category)}`);return parseDirectoryEntries(await requestData(`/directory/entries${query.length?`?${query.join('&')}`:''}`,'GET'))},
  async getDirectoryDetail(entryRef:string):Promise<DirectoryItem>{if(!entryRef)throw new ProjectApiError('DIRECTORY_ENTRY_REF_REQUIRED');return parseDirectoryItem(await requestData(`/directory/entries/${encodeURIComponent(entryRef)}`,'GET'))},
  async reportDirectoryError(entryRef:string,reasonCode:string,description:string):Promise<ContentErrorReportResult>{
    const identity=getOrCreateCommand(uni,`directory-report:${entryRef}:${reasonCode}:${localScopeFingerprint(description)}`)
    const response=parseAcceptedProjectEnvelope(await requestBody(`/directory/entries/${encodeURIComponent(entryRef)}/reports`,'POST',{reasonCode,description},baseUrl,{'Idempotency-Key':identity.idempotencyKey}))
    if(!record(response)||!requiredText(response.reportRef)||response.state!=='OPEN')throw new ProjectApiError('INVALID_DIRECTORY_REPORT_DTO')
    return{outcome:'CONTENT_ERROR_REPORTED',supportRef:response.reportRef,reviewTarget:'A120'}
  },
  async getLifeContentList():Promise<LifeContentListResult> {
    const data=parseAcceptedProjectEnvelope(await requestAnonymousRead('/content/life-items'))
    if(!Array.isArray(data))throw new ProjectApiError('INVALID_LIFE_CONTENT_LIST_DTO')
    const items=data.map(row=>{if(!record(row)||!requiredText(row.contentRef)||!requiredText(row.category)||!requiredText(row.title)||!requiredText(row.summary)||!requiredText(row.publishedAt)||!requiredText(row.updatedAt)||!requiredText(row.validUntil))throw new ProjectApiError('INVALID_LIFE_CONTENT_ITEM_DTO');return{contentRef:row.contentRef,contentVersion:requiredText(row.contentVersion)?row.contentVersion:null,category:row.category as 'LIFE_REMINDER'|'HOLIDAY_EXPLANATION',title:row.title,summary:row.summary,sourceType:requiredText(row.sourceType)?row.sourceType:null,jurisdiction:requiredText(row.jurisdiction)?row.jurisdiction:null,applicableAudience:requiredText(row.applicableAudience)?row.applicableAudience:null,publishedAt:row.publishedAt,updatedAt:row.updatedAt,effectiveFrom:row.publishedAt,effectiveTo:row.validUntil,freshnessState:'CURRENT' as const,coverState:'NOT_CONFIGURED' as const,coverRef:null}})
    return{requestRef:null,viewState:items.length?'READY':'EMPTY',projectCode:items.length?'LIFE_CONTENT_LIST_READY':'LIFE_CONTENT_LIST_EMPTY',schemaVersion:'LIFE_CONTENT_READ_V1',visibilityRuleVersion:null,items,retryClass:'NONE',nextReadAt:null}
  },
  async getLifeContentDetail(contentRef:string,contentVersion:string|null):Promise<LifeContentDetailResult> {
    if(!contentRef)throw new ProjectApiError('LIFE_CONTENT_READ_KEY_REQUIRED')
    const versionQuery=contentVersion?`?contentVersion=${encodeURIComponent(contentVersion)}`:''
    const row=parseAcceptedProjectEnvelope(await requestAnonymousRead(`/content/life-items/${encodeURIComponent(contentRef)}${versionQuery}`))
    if(!record(row)||row.contentRef!==contentRef||!requiredText(row.category)||!requiredText(row.title)||!requiredText(row.summary)||!requiredText(row.bodyText)||!requiredText(row.publishedAt)||!requiredText(row.updatedAt)||!requiredText(row.validUntil))throw new ProjectApiError('INVALID_LIFE_CONTENT_DETAIL_DTO')
    const item={contentRef,contentVersion:requiredText(row.contentVersion)?row.contentVersion:null,category:row.category as 'LIFE_REMINDER'|'HOLIDAY_EXPLANATION',title:row.title,summary:row.summary,body:row.bodyText,sourceType:requiredText(row.sourceType)?row.sourceType:requiredText(row.sourceLabel)?row.sourceLabel:null,jurisdiction:requiredText(row.jurisdiction)?row.jurisdiction:null,applicableAudience:requiredText(row.applicableAudience)?row.applicableAudience:null,publishedAt:row.publishedAt,updatedAt:row.updatedAt,effectiveFrom:row.publishedAt,effectiveTo:row.validUntil,freshnessState:'CURRENT' as const,coverState:'NOT_CONFIGURED' as const,coverRef:null}
    return{requestRef:null,viewState:'READY',projectCode:'LIFE_CONTENT_DETAIL_READY',schemaVersion:'LIFE_CONTENT_READ_V1',visibilityRuleVersion:null,contentRef,contentVersion:item.contentVersion,item,retryClass:'NONE',nextReadAt:null}
  },
  async getTemporalOverview() {
    const response=await requestTemporalOverviewRead()
    const data=parseAcceptedProjectEnvelope(response.body)
    if(!record(data)||!requiredText(data.serverTime)||!record(data.dhaka)||!record(data.beijing))throw new ProjectApiError('INVALID_TEMPORAL_OVERVIEW_DTO')
    const referenceInstant=data.serverTime
    const clock=(source:Record<string,unknown>,cityCode:'DHAKA'|'BEIJING',displayName:string,zoneId:'Asia/Dhaka'|'Asia/Shanghai')=>{if(!requiredText(source.date)||!requiredText(source.localTime))throw new ProjectApiError('INVALID_TEMPORAL_CLOCK_DTO');return{cityCode,displayName,zoneId,localDate:source.date,localTime:source.localTime.slice(0,5),availabilityState:'AVAILABLE' as const}}
    const holiday=(source:Record<string,unknown>,countryCode:'CN'|'BD')=>{if(!requiredText(source.date))throw new ProjectApiError('INVALID_TEMPORAL_HOLIDAY_DTO');const validDayType=['WORK_DAY','REST_DAY','HOLIDAY'].includes(String(source.dayType));const complete=validDayType&&requiredText(source.ruleRef)&&requiredText(source.sourceLabel)&&requiredText(source.ruleVersion)&&requiredText(source.effectiveFrom)&&requiredText(source.effectiveUntil);if(!complete)return{countryCode,localDate:source.date,state:'READ_ERROR' as const,holidayId:null,name:null,note:null,sourceType:null,sourceCoverageDate:null,effectiveFrom:null,effectiveTo:null,version:null};const rest=source.dayType==='REST_DAY',confirmed=rest||source.dayType==='HOLIDAY';return{countryCode,localDate:source.date,state:confirmed?'CONFIRMED_HOLIDAY' as const:'NO_HOLIDAY_CONFIRMED' as const,holidayId:confirmed?source.ruleRef:null,name:confirmed?(rest?'休息日':requiredText(source.holidayName)?source.holidayName:'节假日'):null,note:null,sourceType:source.sourceLabel,sourceCoverageDate:source.date,effectiveFrom:source.effectiveFrom,effectiveTo:source.effectiveUntil,version:source.ruleVersion}}
    const chinaHoliday=holiday(data.beijing,'CN'),bangladeshHoliday=holiday(data.dhaka,'BD'),holidayReady=chinaHoliday.state!=='READ_ERROR'&&bangladeshHoliday.state!=='READ_ERROR'
    response.body={requestRef:'TEMPORAL-OVERVIEW',projectCode:holidayReady?'TEMPORAL_OVERVIEW_READY':'TEMPORAL_OVERVIEW_PARTIAL',schemaVersion:'TEMPORAL_OVERVIEW_V1',referenceInstant,generatedAt:referenceInstant,timeZoneRuleVersion:'IANA',clockStaleAfterSeconds:300,clockState:'BOTH_AVAILABLE',clocks:{dhaka:clock(data.dhaka,'DHAKA','达卡','Asia/Dhaka'),beijing:clock(data.beijing,'BEIJING','北京','Asia/Shanghai')},holidayRuleVersion:holidayReady?`${chinaHoliday.version}:${bangladeshHoliday.version}`:'UNAVAILABLE',holidays:{china:chinaHoliday,bangladesh:bangladeshHoliday},retryClass:holidayReady?'NONE':'USER_INITIATED_READ_ONLY'}
    return response
  },
}

export const apiRuntime = Object.freeze({ mode:'PROJECT_API', baseUrl, contentBaseUrl,
  p014BackendImplementationSha:P014_BACKEND_IMPLEMENTATION_SHA,
  p014AllowedActionsSemantics:'CONTROLLED_METADATA_NOT_CLIENT_AUTHORIZATION',p014RealWriteEligibility:0,
  orderRecoveryProjectApiEligibility:orderRecoveryExternalAuthReady?1:0,
  orderRecoveryAuthBoundary:'RUNTIME_EXTERNAL_SECURE_PROXY_OR_DEVELOPER_TOOL_MANUAL_HEADER_INJECTION_REQUIRED_NO_CLIENT_SECRET',
  subjectRefSemantics:'BUYER_BEARER_SERVER_AUTHORITY' })
