import { mockCatalog, mockDirectory, mockDirectoryDetail, mockEligibility, mockLifeContentDetailDto, mockLifeContentListDto, mockOrders, mockProjection, mockQuote, mockRecovery, mockRecoveryCase, mockReportDirectoryError, mockSupport, mockTemporalOverviewReadResponse } from './mock'
import { buildContentErrorReport, mapContentProjectCode, parseContentErrorReportReceipt, parsePublicContentPage, parsePublicContentProjection } from './content-contract'
import { parseLifeContentDetailResponse, parseLifeContentListResponse } from './life-content-contract'
import { parseProjectProjection, parseProjectQuote, toOrderProjection, toQuoteSnapshot, type ProjectProjection } from './project-contract'
import { parseAuthorizedOrders, parseCatalogProjection, parseRecoveryResult, selectionMatchesCatalog } from './topup-recovery-contract'
import { acceptNewerProjection } from '../domain/projection'
import { getOrCreateCommand } from '../domain/command-identity'
import { canonicalFingerprint } from '../domain/canonical-fingerprint'
import { readPendingRecoveryCaseRef } from '../domain/session'
import { parseAcceptedProjectEnvelope, ProjectApiError } from './project-envelope'
import { buildOrderCreationCommand, parseOrderCreationResult, type OrderCreationResult } from './order-creation-contract'
import { readSessionProjection } from '../domain/session'
import type { CatalogProjection, ContentErrorReportResult, DirectoryDetailResult, DirectorySummary, EligibilityResult, LifeContentDetailResult, LifeContentListResult, OrderProjection, OrderSummary, ProjectSessionProjection, QuoteSnapshot, RechargeSelection, RecoveryResult, SupportCase } from '../domain/types'
import type { TemporalOverviewReadResponse } from './temporal-overview-contract'
import { parseP014Response, P014_BACKEND_IMPLEMENTATION_SHA, storeP014OriginalWriteIdentity, type P014CreateCommand, type P014OriginalResultQuery, type P014Response } from './p014-topup-contract'
import { p014BuiltinSynthetic } from './p014-topup-synthetic'
import { parseP021Response, type P021Response } from './order-detail-contract'
import { p021BuiltinSynthetic } from './order-detail-synthetic'

const baseUrl = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
const contentBaseUrl = (import.meta.env.VITE_CONTENT_API_BASE_URL || '/project-api/v1').replace(/\/$/, '')
const useProjectMockApi = import.meta.env.VITE_USE_PROJECT_MOCK_API === 'true'
const useP021BuiltinSynthetic = import.meta.env.DEV && import.meta.env.VITE_P021_DATA_MODE === 'BUILTIN_SYNTHETIC'
const orderRecoveryExternalAuthReady = import.meta.env.VITE_ORDER_RECOVERY_EXTERNAL_AUTH_READY === 'true'
const projectSubjectRef = import.meta.env.VITE_MOCK_PROJECT_SUBJECT_REF || 'miniapp-local-mock-subject-v1'

export { ProjectApiError } from './project-envelope'

function requestBody(path: string, method: 'GET' | 'POST', data?: UniNamespace.RequestOptions['data'], root = baseUrl): Promise<unknown> {
  return new Promise((resolve, reject) => uni.request({
    url: `${root}${path}`,
    method,
    data,
    header: { 'X-Project-Subject-Ref': projectSubjectRef },
    success: ({ data: body }) => resolve(body),
    fail: () => reject(new ProjectApiError('NETWORK_ERROR')),
  }))
}
function requestAnonymousRead(path:string):Promise<unknown> {
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
  return new Promise((resolve,reject)=>uni.request({
    url:`${baseUrl}/home/temporal-overview`,
    method:'GET',
    success:({data:body,header,statusCode})=>resolve({body,statusCode,cacheControl:readCacheControlHeader(header)}),
    fail:()=>reject(new ProjectApiError('NETWORK_ERROR')),
  }))
}
function requestTrustedSessionRead(path:string):Promise<unknown>{
  return new Promise((resolve,reject)=>uni.request({
    url:`${baseUrl}${path}`,method:'GET',
    success:({data:body,statusCode})=>statusCode>=200&&statusCode<300?resolve(body):reject(new ProjectApiError('HTTP_STATUS_REJECTED')),
    fail:()=>reject(new ProjectApiError('NETWORK_ERROR')),
  }))
}
async function requestData(path: string, method: 'GET' | 'POST', data?: UniNamespace.RequestOptions['data'], root = baseUrl): Promise<unknown> {
  return parseAcceptedProjectEnvelope(await requestBody(path,method,data,root))
}

const projectProjection = async (path: string, method: 'GET' | 'POST', data?: UniNamespace.RequestOptions['data']): Promise<ProjectProjection> => parseProjectProjection(await requestData(path, method, data))
function localScopeFingerprint(value:string):string{let hash=2166136261;for(let index=0;index<value.length;index++){hash^=value.charCodeAt(index);hash=Math.imul(hash,16777619)}return(hash>>>0).toString(36)}

async function loadCatalog(operatorCode:string):Promise<CatalogProjection>{
  if(!operatorCode)throw new ProjectApiError('OPERATOR_CODE_REQUIRED')
  if(!useProjectMockApi)return mockCatalog(operatorCode)
  return parseCatalogProjection(await requestData(`/catalog?operatorCode=${encodeURIComponent(operatorCode)}`,'GET'))
}

export const api = {
  // 号码/MNP识别仍是明确的本地合成输入；项目API只提供批准支持集，不连接第三方识别。
  checkEligibility: (phone: string): Promise<EligibilityResult> => mockEligibility(phone),
  getCatalog: loadCatalog,
  async createQuote(selection:RechargeSelection): Promise<QuoteSnapshot> {
    const current=await loadCatalog(selection.operatorCode)
    if(!selectionMatchesCatalog(selection,current))throw new ProjectApiError('CATALOG_SELECTION_STALE')
    if (!useProjectMockApi) return mockQuote(selection)
    const identity=getOrCreateCommand(uni,`create-quote:${localScopeFingerprint(JSON.stringify(selection))}`)
    const quote = parseProjectQuote(await requestData('/quotes', 'POST', {
      phone:selection.maskedPhone,operatorCode:selection.operatorCode,productRef:selection.productRef,denominationRef:selection.denominationRef,
      supportedOperatorSetVersion:selection.supportedOperatorSetVersion,catalogVersion:selection.catalogVersion,
      commandId:identity.commandId,idempotencyKey:identity.idempotencyKey,mnpState:'CONFIRMED',
    }))
    if(quote.operatorCode!==selection.operatorCode||quote.productCode!==selection.productRef||quote.denominationRef!==selection.denominationRef
      ||quote.supportedOperatorSetVersion!==selection.supportedOperatorSetVersion||quote.catalogVersion!==selection.catalogVersion)throw new ProjectApiError('QUOTE_SELECTION_MISMATCH')
    return toQuoteSnapshot(quote)
  },
  async createOrder(quoteRef: string, commandId: string, idempotencyKey: string): Promise<OrderCreationResult> {
    if(!useProjectMockApi)throw new ProjectApiError('PROJECT_MOCK_API_DISABLED')
    const command=buildOrderCreationCommand({commandId,idempotencyKey},quoteRef,readSessionProjection(uni))
    const body=await requestBody('/orders','POST',command)
    try{return parseOrderCreationResult(body)}catch(error){
      try{parseAcceptedProjectEnvelope(body)}catch(apiError){
        if(apiError instanceof ProjectApiError&&apiError.projectCode!=='INVALID_ENVELOPE')throw apiError
      }
      throw new ProjectApiError(error instanceof Error?error.message:'INVALID_ORDER_CREATION_RESULT_DTO')
    }
  },
  async confirmMockPayment(orderRef: string, commandId: string, idempotencyKey: string, expectedProjectionVersion: number, expectedAggregateVersion: number): Promise<ProjectProjection> {
    return projectProjection(`/orders/${encodeURIComponent(orderRef)}/mock-payment`, 'POST', { commandId, idempotencyKey, expectedProjectionVersion, expectedAggregateVersion })
  },
  async createP014Topup(orderRef:string,command:P014CreateCommand):Promise<P014Response>{
    if(!orderRef)throw new ProjectApiError('ORDER_REF_REQUIRED')
    storeP014OriginalWriteIdentity(uni,orderRef,command)
    if(!useProjectMockApi)return p014BuiltinSynthetic.create(orderRef,command)
    return parseP014Response(await requestBody(`/orders/${encodeURIComponent(orderRef)}/topup-intents`,'POST',command))
  },
  async getP014TopupResult(orderRef:string,query:P014OriginalResultQuery):Promise<P014Response>{
    if(!orderRef)throw new ProjectApiError('ORDER_REF_REQUIRED')
    if(!useProjectMockApi)return p014BuiltinSynthetic.result(orderRef,query)
    const params=`commandId=${encodeURIComponent(query.commandId)}&idempotencyKey=${encodeURIComponent(query.idempotencyKey)}&sessionVersion=${query.sessionVersion}&authorizationSetRef=${encodeURIComponent(query.authorizationSetRef)}`
    return parseP014Response(await requestBody(`/orders/${encodeURIComponent(orderRef)}/topup-intents/result?${params}`,'GET'))
  },
  async getP014Progress(orderRef:string):Promise<P014Response>{
    if(!orderRef)throw new ProjectApiError('ORDER_REF_REQUIRED')
    if(!useProjectMockApi)return p014BuiltinSynthetic.progress(orderRef)
    return parseP014Response(await requestBody(`/orders/${encodeURIComponent(orderRef)}/projection`,'GET'))
  },
  async getOrderDetail(orderRef:string):Promise<P021Response>{
    if(!orderRef)throw new ProjectApiError('ORDER_REF_REQUIRED')
    if(useP021BuiltinSynthetic)return p021BuiltinSynthetic.read(orderRef)
    return parseP021Response(await requestTrustedSessionRead(`/orders/${encodeURIComponent(orderRef)}`))
  },
  async getProjection(orderRef: string): Promise<OrderProjection> {
    if (!orderRef) throw new Error('ORDER_REF_REQUIRED')
    const projection = useProjectMockApi ? toOrderProjection(await projectProjection(`/orders/${encodeURIComponent(orderRef)}/projection`, 'GET')) : await mockProjection(orderRef)
    return acceptNewerProjection(orderRef, projection)
  },
  async getOrders(session:ProjectSessionProjection):Promise<OrderSummary[]>{
    if(session.role!=='BUYER')throw new ProjectApiError('BUYER_SESSION_REQUIRED')
    if(!useProjectMockApi)return mockOrders(session)
    if(!orderRecoveryExternalAuthReady)throw new ProjectApiError('ORDER_RECOVERY_EXTERNAL_AUTH_NOT_READY')
    const query=`?sessionVersion=${session.sessionVersion}&authorizationSetRef=${encodeURIComponent(session.authorizationSetRef!)}`
    return parseAuthorizedOrders(await requestData(`/orders${query}`,'GET'),session)
  },
  async recoverOrder(orderRef:string,recoveryMaterialRef:string):Promise<RecoveryResult>{
    if(!orderRef||!recoveryMaterialRef)throw new ProjectApiError('RECOVERY_INPUT_REQUIRED')
    if(readPendingRecoveryCaseRef(uni))throw new ProjectApiError('RECOVERY_CASE_POLL_REQUIRED')
    if(!useProjectMockApi)return mockRecovery(orderRef,recoveryMaterialRef)
    if(!orderRecoveryExternalAuthReady)throw new ProjectApiError('ORDER_RECOVERY_EXTERNAL_AUTH_NOT_READY')
    const fingerprint=canonicalFingerprint(orderRef,recoveryMaterialRef)
    const identity=getOrCreateCommand(uni,`recover-order:${localScopeFingerprint(fingerprint)}`)
    return parseRecoveryResult(await requestData('/recovery-cases','POST',{commandId:identity.commandId,idempotencyKey:identity.idempotencyKey,
      recoveryInputFingerprint:fingerprint,creationPrecondition:'RECOVERY_CASE_MUST_NOT_EXIST',orderRef,recoveryMaterialRef}))
  },
  async getRecoveryCase(recoveryCaseRef:string):Promise<RecoveryResult>{
    if(!recoveryCaseRef)throw new ProjectApiError('RECOVERY_CASE_REF_REQUIRED')
    if(!useProjectMockApi)return mockRecoveryCase(recoveryCaseRef)
    if(!orderRecoveryExternalAuthReady)throw new ProjectApiError('ORDER_RECOVERY_EXTERNAL_AUTH_NOT_READY')
    return parseRecoveryResult(await requestData(`/recovery-cases/${encodeURIComponent(recoveryCaseRef)}`,'GET'))
  },
  getSupportCase: (): Promise<SupportCase> => mockSupport(),
  async getDirectory(): Promise<DirectorySummary[]> {
    if (!useProjectMockApi) return mockDirectory()
    return parsePublicContentPage(await requestData('/content/items', 'GET', undefined, contentBaseUrl))
  },
  async getDirectoryDetail(contentRef: string, contentVersion: number): Promise<DirectoryDetailResult> {
    if (!useProjectMockApi) return mockDirectoryDetail(contentRef, contentVersion)
    try {
      const item = parsePublicContentProjection(await requestData(`/content/items/${encodeURIComponent(contentRef)}?contentVersion=${contentVersion}`, 'GET', undefined, contentBaseUrl))
      if (item.contentVersion !== contentVersion) throw new Error('CONTENT_VERSION_RESPONSE_MISMATCH')
      return { outcome:'READY', item }
    } catch (error) {
      if (error instanceof ProjectApiError) {
        const mapped = mapContentProjectCode(error.projectCode)
        if (mapped) return mapped
      }
      throw error
    }
  },
  async reportDirectoryError(contentRef: string, contentVersion: number, reason: string): Promise<ContentErrorReportResult> {
    const identity = getOrCreateCommand(uni, `content-report:${contentRef}:${contentVersion}`)
    const command = buildContentErrorReport(identity, contentVersion, reason)
    if (!useProjectMockApi) return mockReportDirectoryError(contentRef, command)
    return parseContentErrorReportReceipt(await requestData(`/content/items/${encodeURIComponent(contentRef)}/reports`, 'POST', command, contentBaseUrl))
  },
  async getLifeContentList():Promise<LifeContentListResult> {
    const body=!useProjectMockApi ? await mockLifeContentListDto() : await requestAnonymousRead('/content/life-items')
    return parseLifeContentListResponse(body)
  },
  async getLifeContentDetail(contentRef:string,contentVersion:string):Promise<LifeContentDetailResult> {
    if(!contentRef||!contentVersion)throw new ProjectApiError('LIFE_CONTENT_READ_KEY_REQUIRED')
    const body=!useProjectMockApi
      ?await mockLifeContentDetailDto(contentRef,contentVersion)
      :await requestAnonymousRead(`/content/life-items/${encodeURIComponent(contentRef)}?contentVersion=${encodeURIComponent(contentVersion)}`)
    return parseLifeContentDetailResponse(body,contentRef,contentVersion)
  },
  async getTemporalOverview() {
    return !useProjectMockApi ? mockTemporalOverviewReadResponse() : requestTemporalOverviewRead()
  },
}

export const apiRuntime = Object.freeze({ mode:useProjectMockApi ? 'PROJECT_MOCK_API' : 'BUILTIN_MOCK', baseUrl, contentBaseUrl,
  p014BackendImplementationSha:P014_BACKEND_IMPLEMENTATION_SHA,
  p014AllowedActionsSemantics:'CONTROLLED_METADATA_NOT_CLIENT_AUTHORIZATION',p014RealWriteEligibility:0,
  orderRecoveryProjectApiEligibility:useProjectMockApi&&orderRecoveryExternalAuthReady?1:0,
  orderRecoveryAuthBoundary:'RUNTIME_EXTERNAL_SECURE_PROXY_OR_DEVELOPER_TOOL_MANUAL_HEADER_INJECTION_REQUIRED_NO_CLIENT_SECRET',
  subjectRefSemantics:'LOCAL_MOCK_ROUTING_ONLY_NOT_TRUSTED_IDENTITY' })
